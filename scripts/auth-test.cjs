'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const { signIn } = require('../dist/auth.js');

function mockBrowser(capture) {
  return async (url) => {
    const auth = new URL(url);
    capture.params = auth.searchParams;
    const callback = new URL(auth.searchParams.get('redirect_uri'));
    callback.hostname = '127.0.0.1';
    callback.searchParams.set('state', 'wrong');
    callback.searchParams.set('code', 'code');
    assert.equal((await fetch(callback)).status, 400);
    callback.searchParams.set('state', auth.searchParams.get('state'));
    assert.equal((await fetch(callback)).status, 200);
  };
}

function mockServices(capture, options = {}) {
  const steps = [
    ['login.microsoftonline.com', { access_token: 'ms-token' }],
    ['user.auth.xboxlive.com', { Token: 'xbox-token' }],
    ['xsts.auth.xboxlive.com', { Token: 'xsts-token', DisplayClaims: { xui: [{ uhs: 'hash' }] } }],
    ['api.minecraftservices.com', { access_token: 'mc-token', expires_in: 3600 }],
    ['api.minecraftservices.com', { items: options.unowned ? [] : [{ name: 'game_minecraft' }] }],
    ['api.minecraftservices.com', { id: '0123456789abcdef0123456789abcdef', name: 'TestPlayer' }]
  ];
  return async (url, init) => {
    const index = capture.calls.length;
    capture.calls.push({ url, init });
    assert.equal(new URL(url).hostname, steps[index][0]);
    if (options.forbidden && index === 3) return new Response('{}', { status: 403 });
    return Response.json(steps[index][1]);
  };
}

test('OAuth PKCE, Xbox exchanges, Java entitlement and profile', async () => {
  const capture = { calls: [] };
  const result = await signIn(mockBrowser(capture), mockServices(capture));
  assert.equal(capture.params.get('client_id'), '6608da6f-c687-477c-aef9-237955e7de90');
  assert.equal(capture.params.get('scope'), 'XboxLive.signin');
  assert.equal(capture.params.get('code_challenge_method'), 'S256');
  const form = capture.calls[0].init.body;
  assert.equal(createHash('sha256').update(form.get('code_verifier')).digest('base64url'), capture.params.get('code_challenge'));
  assert.equal(form.get('redirect_uri'), capture.params.get('redirect_uri'));
  assert.equal(JSON.parse(capture.calls[1].init.body).Properties.RpsTicket, 'd=ms-token');
  assert.equal(JSON.parse(capture.calls[2].init.body).RelyingParty, 'rp://api.minecraftservices.com/');
  assert.equal(JSON.parse(capture.calls[3].init.body).identityToken, 'XBL3.0 x=hash;xsts-token');
  assert.equal(capture.calls[4].init.headers.Authorization, 'Bearer mc-token');
  assert.equal(result.name, 'TestPlayer');
  assert.equal(capture.calls.length, 6);
});

test('rejects accounts without Java entitlement before profile retrieval', async () => {
  const capture = { calls: [] };
  await assert.rejects(signIn(mockBrowser(capture), mockServices(capture, { unowned: true })), /no.*confirmed|does not have a confirmed/i);
  assert.equal(capture.calls.length, 5);
});

test('reports possible Minecraft Services approval without exposing tokens', async () => {
  const capture = { calls: [] };
  await assert.rejects(signIn(mockBrowser(capture), mockServices(capture, { forbidden: true })), /403.*approval may be required/);
  assert.equal(capture.calls.length, 4);
});
