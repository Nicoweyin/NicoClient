'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const { installGame, allowed } = require('../dist/game-install.js');
const sha = bytes => crypto.createHash('sha1').update(bytes).digest('hex');
const bytes = value => Buffer.from(JSON.stringify(value));

test('metadata rules and verified installation, duplicate assets, and damaged file repair', async () => {
  assert.equal(allowed([{ action: 'allow', os: { name: 'windows' } }]), true);
  assert.equal(allowed([{ action: 'allow', os: { name: 'linux' } }]), false);
  assert.equal(allowed([{ action: 'allow', features: { is_demo_user: true } }]), false);
  const root = await fs.mkdtemp(path.join(require('node:os').tmpdir(), 'nicoclient-install-test-'));
  const client = Buffer.from('minecraft-client-fixture');
  const lib = Buffer.from('fabric-loader-fixture');
  const asset = Buffer.from('asset-fixture');
  const assetIndex = bytes({ objects: { a: { hash: sha(asset), size: asset.length }, b: { hash: sha(asset), size: asset.length } } });
  const version = bytes({ id: '1.21.11', javaVersion: { majorVersion: 21 }, libraries: [], assetIndex: { id: '29', sha1: sha(assetIndex), size: assetIndex.length, url: 'https://piston-meta.mojang.com/index' }, downloads: { client: { sha1: sha(client), size: client.length, url: 'https://piston-data.mojang.com/client' } } });
  const manifest = bytes({ versions: [{ id: '1.21.11', type: 'release', sha1: sha(version), url: 'https://piston-meta.mojang.com/version' }] });
  const fabric = bytes({ inheritsFrom: '1.21.11', mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient', libraries: [{ name: 'net.fabricmc:fabric-loader:0.20.1', url: 'https://maven.fabricmc.net/', sha1: sha(lib), size: lib.length }] });
  const urls = new Map([
    ['https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', manifest],
    ['https://piston-meta.mojang.com/version', version], ['https://piston-meta.mojang.com/index', assetIndex],
    ['https://piston-data.mojang.com/client', client], ['https://meta.fabricmc.net/v2/versions/loader/1.21.11/0.20.1/profile/json', fabric],
    ['https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar', lib],
    [`https://resources.download.minecraft.net/${sha(asset).slice(0, 2)}/${sha(asset)}`, asset]
  ]);
  const fetcher = async url => urls.has(String(url)) ? new Response(urls.get(String(url))) : new Response('', { status: 404 });
  try {
    urls.set('https://piston-data.mojang.com/client', Buffer.from('wrong'));
    await assert.rejects(installGame(root, () => {}, fetcher), /checksum or size mismatch/);
    await assert.rejects(fs.stat(path.join(root, 'versions', '1.21.11', '1.21.11.jar')), { code: 'ENOENT' });
    urls.set('https://piston-data.mojang.com/client', client);
    const result = await installGame(root, () => {}, fetcher);
    assert.equal(result.classpath.length, 2);
    assert.deepEqual(await fs.readFile(result.classpath[1]), client);
    assert.deepEqual(await fs.readFile(path.join(root, 'assets', 'objects', sha(asset).slice(0, 2), sha(asset))), asset);
    await installGame(root, () => {}, fetcher);
    await fs.writeFile(result.classpath[1], 'user-modified');
    // A damaged game file must not block PLAY: repair it, but keep the old copy.
    await installGame(root, () => {}, fetcher);
    assert.deepEqual(await fs.readFile(result.classpath[1]), client);
    const damaged = (await fs.readdir(path.dirname(result.classpath[1]))).find(name => name.endsWith('.damaged'));
    assert.equal(await fs.readFile(path.join(path.dirname(result.classpath[1]), damaged), 'utf8'), 'user-modified');
  } finally { await fs.rm(root, { recursive: true, force: true }); }
});
