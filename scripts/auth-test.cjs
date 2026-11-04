'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const { signIn, verifyMinecraftSession } = require('../dist/auth.js');

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
    const response = await fetch(callback);
    assert.equal(response.status, 200);
    assert.match(await response.text(), /Minecraft account verification is still in progress/);
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
    if (options.forbidden && index === 3) return Response.json(options.forbiddenBody || { errorMessage: 'private-token-DO-NOT-LOG', error: 'private-token-DO-NOT-LOG' }, { status: 403 });
    return Response.json(steps[index][1]);
  };
}

test('OAuth PKCE, Xbox exchanges, Java entitlement and profile', async () => {
  const capture = { calls: [] };
  const result = await signIn(mockBrowser(capture), mockServices(capture));
  assert.equal(capture.params.get('client_id'), '6608da6f-c687-477c-aef9-237955e7de90');
  assert.equal(capture.params.get('scope'), 'XboxLive.signin');
  assert.equal(capture.params.get('code_challenge_method'), 'S256');
  assert.equal(capture.params.get('prompt'), 'select_account');
  const form = capture.calls[0].init.body;
  assert.equal(createHash('sha256').update(form.get('code_verifier')).digest('base64url'), capture.params.get('code_challenge'));
  assert.equal(form.get('redirect_uri'), capture.params.get('redirect_uri'));
  assert.equal(JSON.parse(capture.calls[1].init.body).Properties.RpsTicket, 'd=ms-token');
  assert.equal(capture.calls[1].url, 'https://user.auth.xboxlive.com/user/authenticate');
  assert.deepEqual(JSON.parse(capture.calls[2].init.body), {
    Properties: { SandboxId: 'RETAIL', UserTokens: ['xbox-token'] },
    RelyingParty: 'rp://api.minecraftservices.com/', TokenType: 'JWT'
  });
  assert.equal(capture.calls[2].url, 'https://xsts.auth.xboxlive.com/xsts/authorize');
  assert.equal(capture.calls[3].url, 'https://api.minecraftservices.com/authentication/login_with_xbox');
  assert.equal(capture.calls[3].init.method, 'POST');
  assert.equal(capture.calls[3].init.headers['Content-Type'], 'application/json');
  assert.equal(capture.calls[3].init.headers.Accept, 'application/json');
  assert.deepEqual(JSON.parse(capture.calls[3].init.body), { identityToken: 'XBL3.0 x=hash;xsts-token' });
  assert.equal(capture.calls[4].init.headers.Authorization, 'Bearer mc-token');
  assert.equal(result.name, 'TestPlayer');
  assert.equal(capture.calls.length, 6);
});

test('fresh authorizations use distinct state and PKCE challenges', async () => {
  const first = { calls: [] }, second = { calls: [] };
  await signIn(mockBrowser(first), mockServices(first));
  await signIn(mockBrowser(second), mockServices(second));
  assert.equal(first.params.get('prompt'), 'select_account');
  assert.equal(second.params.get('prompt'), 'select_account');
  assert.notEqual(first.params.get('state'), second.params.get('state'));
  assert.notEqual(first.params.get('code_challenge'), second.params.get('code_challenge'));
});

test('unsolicited, ambiguous and repeated callbacks cannot trigger another exchange', async () => {
  const capture = { calls: [] };
  let responses;
  await signIn(async url => {
    const params = new URL(url).searchParams;
    const callback = new URL(params.get('redirect_uri'));
    callback.hostname = '127.0.0.1';
    const unsolicited = new URL(callback);
    unsolicited.searchParams.set('code', 'fixture');
    assert.equal((await fetch(unsolicited)).status, 400);
    const ambiguous = new URL(callback);
    ambiguous.searchParams.set('state', params.get('state'));
    ambiguous.searchParams.set('code', 'fixture');
    ambiguous.searchParams.set('error', 'fixture');
    assert.equal((await fetch(ambiguous)).status, 400);
    callback.searchParams.set('state', params.get('state'));
    callback.searchParams.set('code', 'fixture');
    responses = Promise.allSettled([fetch(callback), fetch(callback)]);
  }, mockServices(capture));
  const results = await responses;
  const accepted = results.filter(result => result.status === 'fulfilled' && result.value.status === 200);
  assert.equal(accepted.length, 1);
  for (const result of results) if (result.status === 'fulfilled' && result.value.status !== 200) assert.equal(result.value.status, 409);
  assert.equal(capture.calls.length, 6);
});

test('rejects accounts without Java entitlement before profile retrieval', async () => {
  const capture = { calls: [] };
  await assert.rejects(signIn(mockBrowser(capture), mockServices(capture, { unowned: true })), /no.*confirmed|does not have a confirmed/i);
  assert.equal(capture.calls.length, 5);
});

test('reports possible Minecraft Services approval without exposing tokens', async () => {
  const capture = { calls: [] };
  const warnings = [];
  const warn = console.warn;
  console.warn = (...args) => warnings.push(args.join(' '));
  try {
    await assert.rejects(signIn(mockBrowser(capture), mockServices(capture, { forbidden: true })), error => {
      assert.match(error.message, /403.*login_with_xbox.*approval may be required/i);
      assert.match(error.message, /No recognized safe service detail/);
      assert.match(error.message, /ownership has not yet been checked/i);
      assert.doesNotMatch(error.message, /private-token|ms-token|xsts-token/);
      return true;
    });
  } finally { console.warn = warn; }
  assert.equal(capture.calls.length, 4);
  assert.equal(new URL(capture.calls[3].url).pathname, '/authentication/login_with_xbox');
  assert.deepEqual(warnings, ['[auth] Minecraft authentication: HTTP 403']);
  assert.doesNotMatch(warnings.join(' '), /private-token|ms-token|xsts-token/);
});

test('Minecraft exchange reports only explicitly allowlisted service details', async () => {
  const capture = { calls: [] }, warnings = [];
  const warn = console.warn;
  console.warn = (...args) => warnings.push(args.join(' '));
  try {
    await assert.rejects(signIn(mockBrowser(capture), mockServices(capture, {
      forbidden: true,
      forbiddenBody: { error: 'ForbiddenOperationException', errorCode: 42, errorMessage: 'Access denied', detail: 'secret-token-DO-NOT-LOG' }
    })), error => {
      assert.match(error.message, /type ForbiddenOperationException, code 42, message Access denied/);
      assert.doesNotMatch(error.message, /secret-token|xsts-token|ms-token/);
      return true;
    });
  } finally { console.warn = warn; }
  assert.deepEqual(warnings, ['[auth] Minecraft authentication: HTTP 403; type ForbiddenOperationException, code 42, message Access denied']);
});

test('a later ownership HTTP failure does not claim the account is unowned', async () => {
  const session = { id: '0123456789abcdef0123456789abcdef', name: 'TestPlayer', accessToken: 'mc-token-private', expiresAt: Date.now() + 300000 };
  const warnings = [];
  const warn = console.warn;
  console.warn = (...args) => warnings.push(args.join(' '));
  try {
    await assert.rejects(verifyMinecraftSession(session, async url => {
      assert.equal(new URL(url).pathname, '/entitlements/mcstore');
      return Response.json({ errorMessage: 'mc-token-private' }, { status: 403 });
    }), error => {
      assert.match(error.message, /ownership check failed \(HTTP 403\).*could not be determined/i);
      assert.doesNotMatch(error.message, /mc-token-private/);
      return true;
    });
  } finally { console.warn = warn; }
  assert.deepEqual(warnings, ['[auth] Minecraft ownership check: HTTP 403']);
});
