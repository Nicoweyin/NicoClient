import { createHash, randomBytes } from 'node:crypto';
import { createServer } from 'node:http';
import type { AddressInfo } from 'node:net';

const CLIENT_ID = '6608da6f-c687-477c-aef9-237955e7de90';
const OAUTH = 'https://login.microsoftonline.com/consumers/oauth2/v2.0';
const SCOPE = 'XboxLive.signin';
const CALLBACK_PATH = '/nicoclient-auth';

export interface MinecraftSession {
  name: string;
  id: string;
  accessToken: string;
  expiresAt: number;
}

type OpenBrowser = (url: string) => Promise<void>;
type Fetch = typeof fetch;

function randomValue(): string {
  return randomBytes(32).toString('base64url');
}

async function authorize(openBrowser: OpenBrowser): Promise<{ code: string; verifier: string; redirectUri: string }> {
  const verifier = randomValue();
  const state = randomValue();
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  let resolveCode!: (code: string) => void;
  let rejectCode!: (error: Error) => void;
  const result = new Promise<string>((resolve, reject) => { resolveCode = resolve; rejectCode = reject; });
  const server = createServer((req, res) => {
    const url = new URL(req.url || '/', 'http://localhost');
    if (req.method !== 'GET' || url.pathname !== CALLBACK_PATH || url.searchParams.get('state') !== state) {
      res.writeHead(400, { 'Cache-Control': 'no-store' }).end('Invalid sign-in response.');
      return;
    }
    const code = url.searchParams.get('code');
    const error = url.searchParams.get('error');
    if (!code && !error) {
      res.writeHead(400, { 'Cache-Control': 'no-store' }).end('Incomplete sign-in response.');
      return;
    }
    res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer', 'Content-Security-Policy': "default-src 'none'" });
    res.end('You may close this window and return to NicoClient.');
    if (error) rejectCode(new Error('Microsoft sign-in was cancelled or denied.'));
    else resolveCode(code!);
  });
  await new Promise<void>((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => resolve());
  });
  const redirectUri = `http://localhost:${(server.address() as AddressInfo).port}${CALLBACK_PATH}`;
  const parameters = new URLSearchParams({
    client_id: CLIENT_ID, response_type: 'code', redirect_uri: redirectUri,
    response_mode: 'query', scope: SCOPE, state, code_challenge: challenge,
    code_challenge_method: 'S256', prompt: 'select_account'
  });
  const timeout = setTimeout(() => rejectCode(new Error('Microsoft sign-in timed out.')), 180_000);
  try {
    // Attach a rejection handler before opening the browser in case opening it fails.
    const opening = openBrowser(`${OAUTH}/authorize?${parameters}`).catch(() => { throw new Error('Could not open the system browser.'); });
    await Promise.race([opening.then(() => result), result]);
    return { code: await result, verifier, redirectUri };
  } finally {
    clearTimeout(timeout);
    server.close();
  }
}

async function requestJson(fetcher: Fetch, url: string, init: RequestInit, stage: string): Promise<any> {
  let response: Response;
  try {
    response = await fetcher(url, { ...init, redirect: 'error', signal: AbortSignal.timeout(20_000) });
  } catch {
    throw new Error(`${stage}: network request failed or timed out.`);
  }
  if (!response.ok) {
    let code = '';
    try {
      const data = await response.json() as { XErr?: unknown; error_codes?: unknown };
      if (typeof data.XErr === 'number') code = ` (Xbox error ${data.XErr})`;
      else if (Array.isArray(data.error_codes) && typeof data.error_codes[0] === 'number') code = ` (AADSTS${data.error_codes[0]})`;
    } catch { /* Keep the error free of service response bodies and tokens. */ }
    const review = stage === 'Minecraft authentication' && response.status === 403
      ? ' Minecraft Services app approval may be required.' : '';
    throw new Error(`${stage} failed (HTTP ${response.status})${code}.${review}`);
  }
  try { return await response.json(); } catch { throw new Error(`${stage}: invalid service response.`); }
}

function token(value: unknown, stage: string): string {
  if (typeof value !== 'string' || !value) throw new Error(`${stage}: incomplete service response.`);
  return value;
}

export async function signIn(openBrowser: OpenBrowser, fetcher: Fetch = fetch): Promise<MinecraftSession> {
  const { code, verifier, redirectUri } = await authorize(openBrowser);
  const microsoft = await requestJson(fetcher, `${OAUTH}/token`, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: CLIENT_ID, grant_type: 'authorization_code', code, redirect_uri: redirectUri, code_verifier: verifier, scope: SCOPE })
  }, 'Microsoft sign-in');
  const jsonHeaders = { 'Content-Type': 'application/json', Accept: 'application/json' };
  const xbox = await requestJson(fetcher, 'https://user.auth.xboxlive.com/user/authenticate', {
    method: 'POST', headers: jsonHeaders, body: JSON.stringify({
      Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: `d=${token(microsoft.access_token, 'Microsoft sign-in')}` },
      RelyingParty: 'http://auth.xboxlive.com', TokenType: 'JWT'
    })
  }, 'Xbox sign-in');
  const xsts = await requestJson(fetcher, 'https://xsts.auth.xboxlive.com/xsts/authorize', {
    method: 'POST', headers: jsonHeaders, body: JSON.stringify({
      Properties: { SandboxId: 'RETAIL', UserTokens: [token(xbox.Token, 'Xbox sign-in')] },
      RelyingParty: 'rp://api.minecraftservices.com/', TokenType: 'JWT'
    })
  }, 'Xbox authorization');
  const uhs = token(xsts.DisplayClaims?.xui?.[0]?.uhs, 'Xbox authorization');
  const minecraft = await requestJson(fetcher, 'https://api.minecraftservices.com/authentication/login_with_xbox', {
    method: 'POST', headers: jsonHeaders,
    body: JSON.stringify({ identityToken: `XBL3.0 x=${uhs};${token(xsts.Token, 'Xbox authorization')}` })
  }, 'Minecraft authentication');
  const accessToken = token(minecraft.access_token, 'Minecraft authentication');
  if (typeof minecraft.expires_in !== 'number' || !Number.isFinite(minecraft.expires_in) || minecraft.expires_in <= 0) {
    throw new Error('Minecraft authentication: invalid token expiry.');
  }
  const headers = { Authorization: `Bearer ${accessToken}`, Accept: 'application/json' };
  const entitlements = await requestJson(fetcher, 'https://api.minecraftservices.com/entitlements/mcstore', { headers }, 'Minecraft ownership check');
  if (!Array.isArray(entitlements.items) || !entitlements.items.some((item: any) => item?.name === 'game_minecraft' || item?.name === 'product_minecraft')) {
    throw new Error('This account does not have a confirmed Minecraft Java Edition entitlement.');
  }
  const profile = await requestJson(fetcher, 'https://api.minecraftservices.com/minecraft/profile', { headers }, 'Minecraft profile');
  const id = token(profile.id, 'Minecraft profile');
  const name = token(profile.name, 'Minecraft profile');
  if (!/^[0-9a-f]{32}$/i.test(id) || !/^[a-zA-Z0-9_]{3,16}$/.test(name)) throw new Error('Minecraft profile: invalid profile data.');
  return { id, name, accessToken, expiresAt: Date.now() + minecraft.expires_in * 1000 };
}
