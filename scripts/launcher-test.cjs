'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { download } = require('../dist/game-install.js');
const { installJavaRuntime } = require('../dist/java-runtime.js');
const { installLocalMod } = require('../dist/game-launch.js');
const { installMod, ensureMod, updateMod, checkModUpdate } = require('../dist/modrinth.js');
const { defaultMemory, maximumMemory, loadSettings, saveSettings } = require('../dist/settings.js');

const GB = 1024 * 1048576;
const sha1 = bytes => crypto.createHash('sha1').update(bytes).digest('hex');
const sha512 = bytes => crypto.createHash('sha512').update(bytes).digest('hex');
async function withDir(run) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'nicoclient-test-'));
  try { return await run(dir); } finally { await fs.rm(dir, { recursive: true, force: true }); }
}

test('download retries temporary failures but not a wrong checksum', () => withDir(async dir => {
  const body = Buffer.from('file');
  const file = { path: 'a', url: 'https://piston-data.mojang.com/a', sha1: sha1(body), size: body.length };
  let calls = 0;
  await download(file, path.join(dir, 'a'), dir, async () => {
    if (++calls === 1) throw new TypeError('fetch failed');
    if (calls === 2) return new Response('', { status: 503 });
    return new Response(body);
  }, 1);
  assert.equal(calls, 3);
  calls = 0;
  await assert.rejects(download({ ...file, sha1: '0'.repeat(40) }, path.join(dir, 'b'), dir, async () => { calls++; return new Response(body); }, 1), /checksum/);
  assert.equal(calls, 1);
  calls = 0;
  await assert.rejects(download(file, path.join(dir, 'c'), dir, async () => { calls++; return new Response('', { status: 404 }); }, 1), /HTTP 404/);
  assert.equal(calls, 1);
}));

test('a damaged game file is kept aside and downloaded again', () => withDir(async dir => {
  const body = Buffer.from('good');
  const target = path.join(dir, 'lib.jar');
  await fs.writeFile(target, 'damaged');
  await download({ path: 'lib.jar', url: 'https://piston-data.mojang.com/l', sha1: sha1(body), size: body.length }, target, dir, async () => new Response(body), 1);
  assert.equal(await fs.readFile(target, 'utf8'), 'good');
  const names = await fs.readdir(dir);
  const damaged = names.find(name => name.endsWith('.damaged'));
  assert.ok(damaged);
  assert.equal(await fs.readFile(path.join(dir, damaged), 'utf8'), 'damaged');
}));

test('installs Mojang Java runtime with checksums and refuses unsafe paths', () => withDir(async dir => {
  const java = Buffer.from('java-binary');
  const manifest = Buffer.from(JSON.stringify({ files: { bin: { type: 'directory' }, 'bin/java.exe': { type: 'file', downloads: { raw: { sha1: sha1(java), size: java.length, url: 'https://piston-data.mojang.com/java' } } } } }));
  const index = { 'windows-x64': { 'java-runtime-delta': [{ manifest: { sha1: sha1(manifest), url: 'https://piston-meta.mojang.com/manifest' } }] } };
  const responses = async url => {
    if (String(url).includes('java-runtime')) return new Response(JSON.stringify(index));
    if (String(url).endsWith('/manifest')) return new Response(manifest);
    return new Response(java);
  };
  const seen = [];
  const executable = await installJavaRuntime(dir, 'java-runtime-delta', (done, total) => seen.push([done, total]), responses);
  assert.equal(executable, path.join(dir, 'runtime', 'java-runtime-delta', 'bin', 'java.exe'));
  assert.deepEqual(await fs.readFile(executable), java);
  assert.deepEqual(seen.at(-1), [1, 1]);
  await assert.rejects(installJavaRuntime(dir, '../evil', () => {}, responses), /Invalid Java runtime/);
  const unsafe = Buffer.from(JSON.stringify({ files: { 'bin/java.exe': { type: 'file', downloads: { raw: { sha1: sha1(java), size: java.length, url: 'https://piston-data.mojang.com/java' } } }, '../../evil.dll': { type: 'file', downloads: { raw: { sha1: sha1(java), size: java.length, url: 'https://piston-data.mojang.com/java' } } } } }));
  const unsafeIndex = { 'windows-x64': { 'java-runtime-delta': [{ manifest: { sha1: sha1(unsafe), url: 'https://piston-meta.mojang.com/manifest' } }] } };
  await assert.rejects(installJavaRuntime(dir, 'java-runtime-delta', () => {}, async url => String(url).includes('java-runtime') ? new Response(JSON.stringify(unsafeIndex)) : new Response(unsafe)), /Unsafe/);
}));

test('a rebuilt NicoClient JAR replaces the old one instead of blocking PLAY', () => withDir(async dir => {
  const source = path.join(dir, 'nicoclient-fabric-0.1.0.jar');
  const mods = path.join(dir, 'mods'); await fs.mkdir(mods);
  await fs.writeFile(source, 'build one');
  await installLocalMod(source, mods);
  await fs.writeFile(source, 'build two');
  await installLocalMod(source, mods);
  assert.equal(await fs.readFile(path.join(mods, 'nicoclient-fabric-0.1.0.jar'), 'utf8'), 'build two');
  assert.deepEqual(await fs.readdir(mods), ['nicoclient-fabric-0.1.0.jar']);
  await fs.rename(path.join(mods, 'nicoclient-fabric-0.1.0.jar'), path.join(mods, 'nicoclient-fabric-0.1.0.jar.disabled'));
  await assert.rejects(installLocalMod(source, mods), /disabled/);
}));

function modrinth(catalog) {
  const original = global.fetch;
  const version = item => ({ id: item.id, project_id: item.project, version_number: item.number || item.id, version_type: item.type || 'release', game_versions: ['1.21.11'], loaders: ['fabric'], dependencies: item.dependencies || [], files: [{ filename: item.file, url: `https://cdn.modrinth.com/${item.id}`, primary: true, size: item.bytes.length, hashes: { sha512: sha512(item.bytes) } }] });
  global.fetch = async input => {
    const url = new URL(String(input));
    if (url.hostname === 'cdn.modrinth.com') return new Response(catalog.find(item => item.id === url.pathname.slice(1)).bytes);
    const byHash = url.pathname.match(/\/version_file\/([a-f0-9]+)/);
    if (byHash) { const item = catalog.find(entry => sha1(entry.bytes) === byHash[1]); return item ? Response.json(version(item)) : new Response('', { status: 404 }); }
    const byProject = url.pathname.match(/\/project\/([^/]+)\/version/);
    if (byProject) return Response.json(catalog.filter(item => item.project === byProject[1] && !item.old).map(version));
    throw new Error(`Unexpected URL ${url}`);
  };
  return () => { global.fetch = original; };
}
const fabricOld = { id: 'fapi1', project: 'P7dR8mSH', file: 'fabric-api-1.jar', bytes: Buffer.from('fabric api 1'), old: true };
const fabricNew = { id: 'fapi2', project: 'P7dR8mSH', file: 'fabric-api-2.jar', bytes: Buffer.from('fabric api 2') };

test('a newer Fabric API release does not block PLAY', () => withDir(async dir => {
  const restore = modrinth([fabricOld, fabricNew]);
  try {
    await fs.writeFile(path.join(dir, fabricOld.file), fabricOld.bytes);
    await ensureMod(dir, 'P7dR8mSH', /^fabric-api-/i, () => {});
    assert.deepEqual(await fs.readdir(dir), ['fabric-api-1.jar']);
  } finally { restore(); }
}));

test('ensureMod installs when missing, reports disabled, and tolerates offline Modrinth', () => withDir(async dir => {
  let restore = modrinth([fabricNew]);
  try {
    await ensureMod(dir, 'P7dR8mSH', /^fabric-api-/i, () => {});
    assert.deepEqual(await fs.readdir(dir), ['fabric-api-2.jar']);
    await fs.rename(path.join(dir, 'fabric-api-2.jar'), path.join(dir, 'fabric-api-2.jar.disabled'));
    await assert.rejects(ensureMod(dir, 'P7dR8mSH', /^fabric-api-/i, () => {}), /required but disabled/);
    await fs.rename(path.join(dir, 'fabric-api-2.jar.disabled'), path.join(dir, 'fabric-api-2.jar'));
  } finally { restore(); }
  const original = global.fetch;
  global.fetch = async () => { throw new TypeError('offline'); };
  try { await ensureMod(dir, 'P7dR8mSH', /^fabric-api-/i, () => {}); }
  finally { global.fetch = original; }
}));

test('updating a mod replaces the old file; compatible dependencies stay; betas are skipped', () => withDir(async dir => {
  const dep = { id: 'dep1', project: 'dep', file: 'dep-1.jar', bytes: Buffer.from('dep one') };
  const oldMod = { id: 'mod1', project: 'mod', file: 'mod-1.jar', bytes: Buffer.from('mod one'), old: true };
  const beta = { id: 'mod3', project: 'mod', file: 'mod-3.jar', bytes: Buffer.from('mod three'), type: 'beta' };
  const newMod = { id: 'mod2', number: '2.0', project: 'mod', file: 'mod-2.jar', bytes: Buffer.from('mod two'), dependencies: [{ dependency_type: 'required', project_id: 'dep', version_id: null }] };
  const restore = modrinth([dep, oldMod, beta, newMod]);
  try {
    await fs.writeFile(path.join(dir, oldMod.file), oldMod.bytes);
    await fs.writeFile(path.join(dir, dep.file), dep.bytes);
    assert.deepEqual(await checkModUpdate(dir, oldMod.file), { status: 'available', message: 'Update available: 2.0.' });
    await updateMod(dir, oldMod.file, () => {});
    assert.deepEqual((await fs.readdir(dir)).sort(), ['dep-1.jar', 'mod-2.jar']);
    assert.equal((await checkModUpdate(dir, 'mod-2.jar')).status, 'current');
    await fs.writeFile(path.join(dir, 'local.jar'), 'not on modrinth');
    assert.equal((await checkModUpdate(dir, 'local.jar')).status, 'unknown');
    await assert.rejects(updateMod(dir, 'local.jar', () => {}), /not recognized/);
    await assert.rejects(updateMod(dir, 'mod-2.jar.disabled', () => {}), /Enable/);
  } finally { restore(); }
}));

test('an update with the same filename replaces the file and can be repeated', () => withDir(async dir => {
  const oldMod = { id: 'mod1', project: 'mod', file: 'mod.jar', bytes: Buffer.from('mod one'), old: true };
  const newMod = { id: 'mod2', project: 'mod', file: 'mod.jar', bytes: Buffer.from('mod two') };
  const restore = modrinth([oldMod, newMod]);
  try {
    await fs.writeFile(path.join(dir, 'mod.jar'), oldMod.bytes);
    await updateMod(dir, 'mod.jar', () => {});
    assert.equal(await fs.readFile(path.join(dir, 'mod.jar'), 'utf8'), 'mod two');
    assert.deepEqual(await fs.readdir(dir), ['mod.jar']);
    await installMod(dir, 'mod', () => {});
    assert.deepEqual(await fs.readdir(dir), ['mod.jar']);
  } finally { restore(); }
}));

test('a failed update restores the old mod file', () => withDir(async dir => {
  const oldMod = { id: 'mod1', project: 'mod', file: 'mod-1.jar', bytes: Buffer.from('mod one'), old: true };
  const newMod = { id: 'mod2', project: 'mod', file: 'mod-2.jar', bytes: Buffer.from('mod two') };
  const restore = modrinth([oldMod, newMod]);
  try {
    await fs.writeFile(path.join(dir, oldMod.file), oldMod.bytes);
    // An unrelated local file already has the new name, so placing the update fails after the old file moved.
    await fs.writeFile(path.join(dir, newMod.file), 'someone else');
    await assert.rejects(updateMod(dir, oldMod.file, () => {}));
    assert.deepEqual((await fs.readdir(dir)).sort(), ['mod-1.jar', 'mod-2.jar']);
    assert.equal(await fs.readFile(path.join(dir, oldMod.file), 'utf8'), 'mod one');
    assert.equal(await fs.readFile(path.join(dir, newMod.file), 'utf8'), 'someone else');
  } finally { restore(); }
}));

test('memory settings use sensible limits and reject bad values', () => withDir(async dir => {
  assert.equal(maximumMemory(4 * GB), 2048);
  assert.equal(defaultMemory(4 * GB), 2048);
  assert.equal(defaultMemory(32 * GB), 4096);
  assert.equal(maximumMemory(GB), 1024);
  assert.equal((await loadSettings(dir)).memoryMb, defaultMemory());
  await saveSettings(dir, { memoryMb: 1536 });
  assert.equal((await loadSettings(dir)).memoryMb, 1536);
  for (const memoryMb of [0, 1000, 999999, '4096', undefined]) await assert.rejects(saveSettings(dir, { memoryMb }), /Invalid memory/);
  await fs.writeFile(path.join(dir, 'nicoclient-launcher.json'), 'not json');
  assert.equal((await loadSettings(dir)).memoryMb, defaultMemory());
}));
