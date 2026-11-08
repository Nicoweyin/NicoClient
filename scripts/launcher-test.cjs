'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { installLocalMod, setupOfficialProfile, findOfficialLauncher, profileState } = require('../dist/official-profile.js');
const { readCosmetics, writeCosmetic, configFile, readSettings, writeSetting, listProfiles, applyProfile } = require('../dist/cosmetics-config.js');
const { installMod, ensureMod, updateMod, checkModUpdate, installedProjects } = require('../dist/modrinth.js');
const { describeJar, versionMatches, importCandidates, importMods, strayFiles, problem, addModFiles } = require('../dist/mod-files.js');
const zlib = require('node:zlib');

// A minimal JAR (ZIP) with the given files, the first one deflated, the rest stored.
function jar(files) {
  const locals = [], central = []; let offset = 0;
  Object.entries(files).forEach(([name, text], index) => {
    const raw = Buffer.from(text), data = index === 0 ? zlib.deflateRawSync(raw) : raw, method = index === 0 ? 8 : 0;
    const nameBytes = Buffer.from(name);
    const local = Buffer.alloc(30); local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(method, 8); local.writeUInt32LE(data.length, 18); local.writeUInt32LE(raw.length, 22); local.writeUInt16LE(nameBytes.length, 26);
    const entry = Buffer.alloc(46); entry.writeUInt32LE(0x02014b50, 0); entry.writeUInt16LE(method, 10); entry.writeUInt32LE(data.length, 20); entry.writeUInt32LE(raw.length, 24); entry.writeUInt16LE(nameBytes.length, 28); entry.writeUInt32LE(offset, 42);
    locals.push(local, nameBytes, data); central.push(entry, nameBytes);
    offset += 30 + nameBytes.length + data.length;
  });
  const dir = Buffer.concat(central), end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(central.length / 2, 8); end.writeUInt16LE(central.length / 2, 10); end.writeUInt32LE(dir.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, dir, end]);
}
const fabricJar = (id, minecraft, extra = {}) => jar({ 'fabric.mod.json': JSON.stringify({ id, name: id.toUpperCase(), version: '1.0', depends: minecraft ? { minecraft } : {} }), ...extra });

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

test('finds the Store launcher, then an older install, and reports when there is none', () => {
  const env = { LOCALAPPDATA: 'C:\\Local', ProgramFiles: 'C:\\PF', 'ProgramFiles(x86)': 'C:\\PF86' };
  const store = path.join('C:\\Local', 'Packages', 'Microsoft.4297127D64EC6_8wekyb3d8bbwe');
  const legacy = path.join('C:\\PF86', 'Minecraft Launcher', 'MinecraftLauncher.exe');
  assert.deepEqual(findOfficialLauncher(env, file => file === store), { kind: 'store' });
  assert.deepEqual(findOfficialLauncher(env, file => file === legacy), { kind: 'exe', path: legacy });
  assert.equal(findOfficialLauncher(env, () => false), undefined);
  assert.equal(findOfficialLauncher({}, () => true), undefined);
});

test('profile state follows the launcher file', () => withDir(async dir => {
  assert.equal(await profileState(dir), 'first-run');
  const file = path.join(dir, 'launcher_profiles.json');
  await fs.writeFile(file, JSON.stringify({ profiles: { a: { name: 'Mine' } } }));
  assert.equal(await profileState(dir), 'missing');
  await fs.writeFile(file, JSON.stringify({ profiles: { a: { name: 'NicoClient Fabric 1.21.11' } } }));
  assert.equal(await profileState(dir), 'ready');
  await fs.writeFile(file, 'broken');
  assert.equal(await profileState(dir), 'missing');
}));

test('PLAY moves the profile up and adds the icon without touching other profiles', () => withDir(async dir => {
  const minecraft = path.join(dir, '.minecraft'), game = path.join(dir, 'NicoClient'), jar = path.join(dir, 'nicoclient-fabric-0.1.0.jar');
  await fs.mkdir(minecraft); await fs.writeFile(jar, 'mod');
  const file = path.join(minecraft, 'launcher_profiles.json');
  const other = { name: 'Mine', lastUsed: '2020-01-01T00:00:00.000Z' };
  const old = { name: 'NicoClient Fabric 1.21.11', type: 'custom', lastUsed: '2020-01-01T00:00:00.000Z', lastVersionId: 'fabric-loader-0.20.1-1.21.11', gameDir: game };
  await fs.writeFile(file, JSON.stringify({ profiles: { keep: other, nico: old } }));
  const restore = modrinth([fabricNew]);
  const mocked = global.fetch;
  global.fetch = async (input, init) => String(input).startsWith('https://meta.fabricmc.net/') ? Response.json({ id: 'fabric-loader-0.20.1-1.21.11', inheritsFrom: '1.21.11', mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient' }) : mocked(input, init);
  try {
    await setupOfficialProfile(minecraft, game, jar, 'data:image/png;base64,AAAA');
    const saved = JSON.parse(await fs.readFile(file, 'utf8')).profiles;
    assert.deepEqual(saved.keep, other);
    assert.ok(saved.nico.lastUsed > old.lastUsed);
    assert.equal(saved.nico.icon, 'data:image/png;base64,AAAA');
    assert.equal(Object.keys(saved).length, 2);
  } finally { restore(); }
}));

test('replacing a locked NicoClient JAR asks to close Minecraft', () => withDir(async dir => {
  const source = path.join(dir, 'nicoclient-fabric-0.1.0.jar');
  const mods = path.join(dir, 'mods'); await fs.mkdir(mods);
  await fs.writeFile(source, 'new build');
  const target = path.join(mods, 'nicoclient-fabric-0.1.0.jar');
  await fs.writeFile(target, 'running build');
  await fs.chmod(target, 0o444); // Windows refuses to replace a read-only file, like a file in use.
  try {
    await assert.rejects(installLocalMod(source, mods), /Close Minecraft first/);
    assert.equal(await fs.readFile(target, 'utf8'), 'running build');
    assert.deepEqual(await fs.readdir(mods), ['nicoclient-fabric-0.1.0.jar']);
  } finally { await fs.chmod(target, 0o666); }
}));

test('search results can tell which projects are already installed', () => withDir(async dir => {
  const restore = modrinth([fabricNew]);
  try {
    await fs.writeFile(path.join(dir, fabricNew.file), fabricNew.bytes);
    await fs.writeFile(path.join(dir, 'local.jar'), 'not on modrinth');
    assert.deepEqual(await installedProjects(dir), ['P7dR8mSH']);
  } finally { restore(); }
}));

test('the Cosmetics tab changes only cosmetic lines in the game settings', () => withDir(async dir => {
  assert.deepEqual(await readCosmetics(dir), { cape: 0, wings: 0, halo: 0, trail: 0, flap: true, accent: 0 });
  await fs.mkdir(path.join(dir, 'config'));
  await fs.writeFile(configFile(dir), '#NicoClient settings\nfps.enabled=true\nclient.accent=3\ncosmetics.cape=2\n');
  const after = await writeCosmetic(dir, 'wings', 5);
  assert.deepEqual(after, { cape: 2, wings: 5, halo: 0, trail: 0, flap: true, accent: 3 });
  await writeCosmetic(dir, 'cape', 11);
  await writeCosmetic(dir, 'flap', false);
  const text = await fs.readFile(configFile(dir), 'utf8');
  assert.match(text, /^#NicoClient settings$/m);
  assert.match(text, /^fps\.enabled=true$/m);
  assert.match(text, /^cosmetics\.cape=11$/m);
  assert.match(text, /^cosmetics\.flap=false$/m);
  assert.equal(text.match(/cosmetics\.cape/g).length, 1);
  await assert.rejects(writeCosmetic(dir, 'cape', 12), /does not exist/);
  await assert.rejects(writeCosmetic(dir, 'wings', -1), /does not exist/);
  await assert.rejects(writeCosmetic(dir, 'client.accent', 1), /Unknown/);
  await assert.rejects(writeCosmetic(dir, 'flap', 'yes'), /on or off/);
  assert.deepEqual((await fs.readdir(path.join(dir, 'config'))).sort(), ['nicoclient.properties']);
}));

test('quick clicks in the Cosmetics tab all reach the settings file', () => withDir(async dir => {
  await Promise.all([writeCosmetic(dir, 'cape', 4), writeCosmetic(dir, 'wings', 3), writeCosmetic(dir, 'halo', 2), writeCosmetic(dir, 'trail', 5)]);
  assert.deepEqual(await readCosmetics(dir), { cape: 4, wings: 3, halo: 2, trail: 5, flap: true, accent: 0 });
}));

test('the launcher switches only listed game settings and keeps the rest', () => withDir(async dir => {
  const first = await readSettings(dir);
  assert.equal(first.modules.fps, true);
  assert.equal(first.modules.zoom, true);
  assert.equal(first.modules.fullbright, false);
  assert.equal(first.serverSafe, true);
  assert.equal(first.hudScale, 100);
  await fs.mkdir(path.join(dir, 'config'));
  await fs.writeFile(configFile(dir), '#NicoClient settings\nfps.enabled=false\ncosmetics.cape=2\nfps.x=40\n');
  assert.equal((await readSettings(dir)).modules.fps, false);
  const after = await writeSetting(dir, 'module:fullbright', true);
  assert.equal(after.modules.fullbright, true);
  await writeSetting(dir, 'accent', 4);
  await writeSetting(dir, 'serverSafe', false);
  const last = await writeSetting(dir, 'hudScale', 125);
  assert.deepEqual([last.accent, last.serverSafe, last.hudScale], [4, false, 125]);
  const text = await fs.readFile(configFile(dir), 'utf8');
  assert.match(text, /^cosmetics\.cape=2$/m);
  assert.match(text, /^fps\.x=40$/m);
  assert.match(text, /^client\.serversafe=false$/m);
  await assert.rejects(writeSetting(dir, 'module:freelook', true), /Unknown/);
  await assert.rejects(writeSetting(dir, 'fps.x', 3), /Unknown/);
  await assert.rejects(writeSetting(dir, 'accent', 9), /does not exist/);
  await assert.rejects(writeSetting(dir, 'hudScale', 110), /does not exist/);
  await assert.rejects(writeSetting(dir, 'module:fps', 'yes'), /on or off/);
  await Promise.all([writeSetting(dir, 'module:keys', true), writeSetting(dir, 'module:coords', true), writeCosmetic(dir, 'wings', 2)]);
  const both = await readSettings(dir);
  assert.equal(both.modules.keys && both.modules.coords, true);
  assert.equal((await readCosmetics(dir)).wings, 2);
}));

test('a profile applies modules and positions but never cosmetics, and only real profiles load', () => withDir(async dir => {
  assert.deepEqual(await listProfiles(dir), []);
  const folder = path.join(dir, 'config', 'nicoclient-profiles');
  await fs.mkdir(folder, { recursive: true });
  await fs.writeFile(configFile(dir), 'fps.enabled=true\nkeys.enabled=true\ncosmetics.cape=4\nfps.x=5\n');
  await fs.writeFile(path.join(folder, 'Minimal.properties'), '#preset\nfps.enabled=true\nkeys.enabled=false\n');
  await fs.writeFile(path.join(folder, 'PvP.properties'), 'keys.enabled=true\n');
  await fs.writeFile(path.join(folder, 'my setup.properties'), 'keys.enabled=false\nfps.x=40\ncosmetics.cape=9\nclient.welcomed=false\nbad key=1\nzoom.factor=../../x\n');
  await fs.writeFile(path.join(folder, 'notes.txt'), 'ignore me');
  assert.deepEqual(await listProfiles(dir), ['PvP', 'Minimal', 'my setup']);
  const after = await applyProfile(dir, 'my setup');
  assert.equal(after.modules.keys, false);
  const text = await fs.readFile(configFile(dir), 'utf8');
  assert.match(text, /^fps\.x=40$/m);
  assert.match(text, /^cosmetics\.cape=4$/m);
  assert.doesNotMatch(text, /welcomed|bad key|\.\.\//);
  assert.equal((await applyProfile(dir, 'PvP')).modules.keys, true);
  await assert.rejects(applyProfile(dir, '../config/nicoclient'), /does not exist/);
  await assert.rejects(applyProfile(dir, 'Nope'), /does not exist/);
  await assert.rejects(applyProfile(dir, 42), /does not exist/);
}));

test('Fabric version predicates match 1.21.11 like Fabric does', () => {
  for (const yes of ['*', '1.21.11', '>=1.21.9 <1.22', '~1.21.11', '1.21.x', '>=1.21', '^1.21.0']) assert.equal(versionMatches(yes), true, yes);
  for (const no of ['1.20.1', '>=1.21 <=1.21.4', '<1.21.11', '1.20.x', '~1.21.5 <1.21.10']) assert.equal(versionMatches(no), false, no);
});

test('mod JARs explain themselves: Fabric, Forge, wrong version, Feather', () => withDir(async dir => {
  const write = async (name, bytes) => { await fs.writeFile(path.join(dir, name), bytes); return path.join(dir, name); };
  const good = await describeJar(await write('good.jar', fabricJar('sodium', ['>=1.21.11', '1.21.x'])));
  assert.deepEqual([good.id, good.title, good.loader, good.fitsGame, problem(good)], ['sodium', 'SODIUM', 'fabric', true, '']);
  assert.match(problem(await describeJar(await write('old.jar', fabricJar('old', '1.20.1')))), /Made for Minecraft 1\.20\.1/);
  assert.match(problem(await describeJar(await write('forge.jar', jar({ 'META-INF/mods.toml': 'modLoader="javafml"' })))), /Forge/);
  assert.match(problem(await describeJar(await write('feather.jar', fabricJar('feather', '1.21.11')))), /Feather/);
  assert.equal(problem(await describeJar(await write('broken.jar', 'not a zip'))), 'Not a Fabric mod');
}));

test('importing copies only new, compatible mods and never overwrites or deletes', () => withDir(async dir => {
  const from = path.join(dir, '.minecraft', 'mods'), to = path.join(dir, 'NicoClient', 'mods');
  await fs.mkdir(from, { recursive: true }); await fs.mkdir(to, { recursive: true });
  await fs.writeFile(path.join(from, 'sodium-1.0.temp.jar'), fabricJar('sodium', '>=1.21'));
  await fs.writeFile(path.join(from, 'fabric-api-old.jar'), fabricJar('fabric-api', '*'));
  await fs.writeFile(path.join(from, 'forge-mod.jar'), jar({ 'META-INF/mods.toml': 'x' }));
  await fs.writeFile(path.join(from, 'old.jar'), fabricJar('old', '1.20.1'));
  await fs.writeFile(path.join(from, 'sodium-copy.jar'), fabricJar('sodium', '*'));
  await fs.writeFile(path.join(to, 'fabric-api-new.jar'), fabricJar('fabric-api', '*'));
  const found = Object.fromEntries((await importCandidates(from, to)).map(item => [item.file, item.status]));
  assert.deepEqual(found, { 'fabric-api-old.jar': 'installed', 'forge-mod.jar': 'incompatible', 'old.jar': 'incompatible', 'sodium-1.0.temp.jar': 'ready', 'sodium-copy.jar': 'installed' });
  const result = await importMods(from, to);
  assert.deepEqual(result.imported, ['SODIUM']);
  assert.deepEqual((await fs.readdir(to)).sort(), ['fabric-api-new.jar', 'sodium-1.0.jar']);
  assert.equal((await fs.readdir(from)).length, 5, 'the normal Minecraft folder is left as it was');
  assert.deepEqual((await importMods(from, to)).imported, [], 'a second import adds nothing');
  await fs.writeFile(path.join(to, 'shaders.zip'), 'x'); await fs.mkdir(path.join(to, 'more mods'));
  assert.deepEqual((await strayFiles(to)).sort(), ['more mods/', 'shaders.zip']);
}));

test('dropped mods are copied in, originals stay, bad files are explained', () => withDir(async dir => {
  const downloads = path.join(dir, 'Downloads'), to = path.join(dir, 'mods');
  await fs.mkdir(downloads); await fs.mkdir(to);
  const put = async (name, bytes) => { await fs.writeFile(path.join(downloads, name), bytes); return path.join(downloads, name); };
  const files = [await put('sodium.jar', fabricJar('sodium', '*')), await put('pack.zip', 'x'), await put('forge.jar', jar({ 'META-INF/mods.toml': 'x' })), await put('sodium-again.jar', fabricJar('sodium', '*'))];
  const result = await addModFiles(files, to);
  assert.deepEqual(result.added, ['SODIUM']);
  assert.deepEqual(result.skipped.map(item => item.reason), ['A .zip: unpack it first', 'Forge mod: does not run on Fabric', 'Already in NicoClient']);
  assert.deepEqual(await fs.readdir(to), ['sodium.jar']);
  assert.equal((await fs.readdir(downloads)).length, 4, 'the dropped originals are still there');
  await assert.rejects(addModFiles('nope', to), /Invalid/);
  assert.equal((await addModFiles(['relative.jar'], to)).skipped[0].reason, 'Not a file on this PC');
}));

test('failed update checks are retried within minutes, then hourly', () => {
  const Module = require('node:module');
  const load = Module._load;
  Module._load = function (request, ...rest) {
    if (request === 'electron') return { app: {}, BrowserWindow: { getAllWindows: () => [] }, ipcMain: { handle() {} } };
    if (request === 'electron-updater') return { autoUpdater: { on() {} } };
    return load.call(this, request, ...rest);
  };
  try {
    delete require.cache[require.resolve('../dist/updater.js')];
    const { retryDelay } = require('../dist/updater.js');
    assert.deepEqual([1, 2, 3, 4, 9].map(n => retryDelay(n) / 60000), [5, 15, 30, 60, 60]);
  } finally { Module._load = load; }
});
