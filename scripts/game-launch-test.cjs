'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { makeArguments } = require('../dist/game-launch.js');
const { verifyMinecraftSession } = require('../dist/auth.js');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

test('constructs launch arguments without shell interpolation and skips unsupported feature rules', () => {
  const install = {
    gameDirectory: 'C:\\test folder\\NicoClient', assetsDirectory: 'C:\\test folder\\assets', classpath: ['C:\\test folder\\game.jar'],
    version: { assetIndex: { id: '29' }, arguments: { jvm: ['-cp', '${classpath}'], game: ['--username', '${auth_player_name}', '--accessToken', '${auth_access_token}', { rules: [{ action: 'allow', features: { is_demo_user: true } }], value: '--demo' }] } },
    fabric: { id: 'fabric-loader-0.20.1-1.21.11', mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient', arguments: { jvm: [], game: [] } }
  };
  const args = makeArguments(install, { name: 'Player', id: '123', accessToken: 'token-fixture', expiresAt: Date.now() + 120000 }, 'C:\\test folder\\natives', 'C:\\bootstrap.jar');
  assert.match(args[2], /game\.jar/);
  assert.equal(args[args.indexOf('--accessToken') + 1], '__NICOCLIENT_SESSION_TOKEN__');
  assert.equal(args.some(arg => arg.includes('token-fixture')), false);
  assert.equal(args.includes('--demo'), false);
  assert.throws(() => makeArguments({ ...install, version: { ...install.version, arguments: { ...install.version.arguments, game: ['${unknown}'] } } }, { name: 'Player', id: '123', accessToken: 'token-fixture', expiresAt: Date.now() + 120000 }, '', 'bootstrap.jar'), /Unsupported Minecraft launch variable/);
});

test('rejects expired sessions before making an ownership request', async () => {
  let called = false;
  await assert.rejects(verifyMinecraftSession({ name: 'Player', id: '1', accessToken: 'token-fixture', expiresAt: Date.now() }, async () => { called = true; }), /expired/);
  assert.equal(called, false);
});

test('bootstrap delivers a session over stdin to the Fabric entry point', () => {
  const dir = fs.mkdtempSync(path.join(require('node:os').tmpdir(), 'nicoclient-bootstrap-test-'));
  try {
    const compile = spawnSync('javac', ['-d', dir, path.join(__dirname, 'fixtures', 'KnotClient.java')], { encoding: 'utf8' });
    assert.equal(compile.status, 0, compile.stderr);
    const jar = path.join(__dirname, '..', 'fabric-mod', 'build', 'libs', 'nicoclient-bootstrap.jar');
    const run = spawnSync('java', ['-cp', [dir, jar].join(path.delimiter), 'dev.nicoweyin.nicoclient.bootstrap.LaunchBootstrap', '--accessToken', '__NICOCLIENT_SESSION_TOKEN__'], { input: 'test-session-value\n', encoding: 'utf8' });
    assert.equal(run.status, 0, run.stderr);
    assert.equal(run.stdout.trim(), 'BOOTSTRAP_OK');
    assert.doesNotMatch(run.stdout + run.stderr, /test-session-value/);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});
