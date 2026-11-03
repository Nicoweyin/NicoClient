'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const { createHash } = require('node:crypto');
const { installMod, listMods, changeMod } = require('../dist/modrinth.js');
const { loadCosmetics, saveCosmetics, defaults } = require('../dist/cosmetics.js');

test('installs dependencies with hash verification, and manages mod files without overwrite', async () => {
  const base = path.join(__dirname, '..', '.smoke-test-data'); await fs.mkdir(base, { recursive: true });
  const dir = await fs.mkdtemp(path.join(base, 'mod-test-'));
  const original = global.fetch;
  const bytes = { root: Buffer.from('root jar'), dep: Buffer.from('dependency jar') };
  const version = (id, deps = []) => ({ id, project_id: id, game_versions: ['1.21.11'], loaders: ['fabric'], dependencies: deps, files: [{ filename: `${id}.jar`, url: `https://cdn.modrinth.com/${id}`, primary: true, size: bytes[id].length, hashes: { sha512: createHash('sha512').update(bytes[id]).digest('hex') } }] });
  global.fetch = async input => {
    const url = new URL(String(input));
    if (url.hostname === 'cdn.modrinth.com') return new Response(bytes[url.pathname.slice(1)]);
    if (url.pathname.endsWith('/project/root/version')) return Response.json([version('root', [{ dependency_type: 'required', project_id: 'dep', version_id: null }])]);
    if (url.pathname.endsWith('/project/dep/version')) return Response.json([version('dep')]);
    if (url.pathname.includes('/version_file/')) return new Response('', { status: 404 });
    throw new Error('Unexpected URL');
  };
  try {
    const progress = [];
    await installMod(dir, 'root', (_name, percent) => progress.push(percent));
    assert.deepEqual((await listMods(dir)).map(x => x.name).sort(), ['dep.jar', 'root.jar']);
    assert.equal(progress.at(-1), 100);
    await changeMod(dir, 'root.jar', 'toggle');
    assert.equal((await listMods(dir)).find(x => x.name === 'root.jar.disabled').enabled, false);
    await fs.writeFile(path.join(dir, 'root.jar'), 'do not replace');
    await assert.rejects(changeMod(dir, 'root.jar.disabled', 'toggle'));
    assert.equal(await fs.readFile(path.join(dir, 'root.jar'), 'utf8'), 'do not replace');
    await assert.rejects(changeMod(dir, '../root.jar', 'remove'));
    await changeMod(dir, 'root.jar.disabled', 'remove');
  } finally { global.fetch = original; await fs.rm(dir, { recursive: true, force: true }); }
});

test('rejects a corrupt download without leaving installed files', async () => {
  const base = path.join(__dirname, '..', '.smoke-test-data'); await fs.mkdir(base, { recursive: true });
  const dir = await fs.mkdtemp(path.join(base, 'mod-test-')); const original = global.fetch;
  global.fetch = async input => String(input).startsWith('https://cdn.modrinth.com/') ? new Response('bad') : Response.json([{ id: 'root', project_id: 'root', game_versions: ['1.21.11'], loaders: ['fabric'], dependencies: [], files: [{ filename: 'root.jar', url: 'https://cdn.modrinth.com/root', primary: true, size: 3, hashes: { sha512: '0'.repeat(128) } }] }]);
  try { await assert.rejects(installMod(dir, 'root', () => {}), /hash/); assert.deepEqual(await fs.readdir(dir), []); }
  finally { global.fetch = original; await fs.rm(dir, { recursive: true, force: true }); }
});

test('cosmetics persist and reject malformed settings', async () => {
  const base = path.join(__dirname, '..', '.smoke-test-data'); await fs.mkdir(base, { recursive: true });
  const dir = await fs.mkdtemp(path.join(base, 'cosmetic-test-'));
  try {
    assert.deepEqual(await loadCosmetics(dir), defaults);
    await saveCosmetics(dir, { ...defaults, color: '#112233', cape: 'classic' });
    assert.equal((await loadCosmetics(dir)).cape, 'classic');
    await assert.rejects(saveCosmetics(dir, { ...defaults, color: 'bad' }));
    assert.equal((await loadCosmetics(dir)).color, '#112233');
  } finally { await fs.rm(dir, { recursive: true, force: true }); }
});
