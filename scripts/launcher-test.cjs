'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { installLocalMod, setupOfficialProfile } = require('../dist/official-profile.js');
const { installMod, ensureMod, updateMod, checkModUpdate } = require('../dist/modrinth.js');

const sha1 = bytes => crypto.createHash('sha1').update(bytes).digest('hex');
const sha512 = bytes => crypto.createHash('sha512').update(bytes).digest('hex');
async function withDir(run) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'nicoclient-test-'));
  try { return await run(dir); } finally { await fs.rm(dir, { recursive: true, force: true }); }
}

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

test('the official profile is added once and other profiles stay untouched', () => withDir(async dir => {
  const minecraft = path.join(dir, '.minecraft'), game = path.join(dir, 'NicoClient'), jar = path.join(dir, 'nicoclient-fabric-0.1.0.jar');
  await fs.mkdir(minecraft); await fs.writeFile(jar, 'mod');
  const file = path.join(minecraft, 'launcher_profiles.json');
  const fabricProfile = { id: 'fabric-loader-0.20.1-1.21.11', inheritsFrom: '1.21.11', mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient' };
  const restore = modrinth([fabricNew]);
  const mocked = global.fetch;
  global.fetch = async (input, init) => String(input).startsWith('https://meta.fabricmc.net/') ? Response.json(fabricProfile) : mocked(input, init);
  try {
    await assert.rejects(setupOfficialProfile(minecraft, game, jar), /has not been set up yet/);
    const other = { name: 'Mine', lastVersionId: 'latest-release' };
    await fs.writeFile(file, JSON.stringify({ profiles: { keep: other }, settings: { locale: 'de' } }));
    await setupOfficialProfile(minecraft, game, jar);
    await setupOfficialProfile(minecraft, game, jar);
    const saved = JSON.parse(await fs.readFile(file, 'utf8'));
    assert.deepEqual(saved.profiles.keep, other);
    assert.deepEqual(saved.settings, { locale: 'de' });
    const added = Object.values(saved.profiles).filter(profile => profile.name === 'NicoClient Fabric 1.21.11');
    assert.equal(added.length, 1);
    assert.equal(added[0].lastVersionId, fabricProfile.id);
    assert.equal(added[0].gameDir, game);
    assert.deepEqual((await fs.readdir(path.join(game, 'mods'))).sort(), ['fabric-api-2.jar', 'nicoclient-fabric-0.1.0.jar']);
    assert.deepEqual(JSON.parse(await fs.readFile(path.join(minecraft, 'versions', fabricProfile.id, fabricProfile.id + '.json'), 'utf8')), fabricProfile);
    assert.deepEqual((await fs.readdir(minecraft)).sort(), ['launcher_profiles.json', 'versions']);
  } finally { restore(); }
}));

test('an unknown profile with the NicoClient name is not overwritten', () => withDir(async dir => {
  const minecraft = path.join(dir, '.minecraft'), jar = path.join(dir, 'nicoclient-fabric-0.1.0.jar');
  await fs.mkdir(minecraft); await fs.writeFile(jar, 'mod');
  const text = JSON.stringify({ profiles: { a: { name: 'NicoClient Fabric 1.21.11', lastVersionId: 'other', gameDir: dir } } });
  await fs.writeFile(path.join(minecraft, 'launcher_profiles.json'), text);
  await assert.rejects(setupOfficialProfile(minecraft, path.join(dir, 'game'), jar), /different profile/);
  assert.equal(await fs.readFile(path.join(minecraft, 'launcher_profiles.json'), 'utf8'), text);
}));
