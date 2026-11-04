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
  xuid?: string;
}

type OpenBrowser = (url: string) => Promise<void>;
type Fetch = typeof fetch;
type AuthStage = 'Microsoft sign-in' | 'Xbox sign-in' | 'Xbox authorization' | 'Minecraft authentication' | 'Minecraft ownership check' | 'Minecraft profile';

// Never copy arbitrary service text into logs or the UI: it may contain credentials.
function safeMinecraftError(data: unknown): string {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return '';
  const fields = data as Record<string, unknown>;
  const types = new Set(['ForbiddenOperationException', 'UnauthorizedException', 'AccessDeniedException', 'FORBIDDEN', 'UNAUTHORIZED', 'access_denied']);
  const messages = new Set(['Forbidden', 'Access denied', 'Access Denied', 'Unauthorized']);
  const details: string[] = [];
  if (typeof fields.error === 'string' && types.has(fields.error)) details.push(`type ${fields.error}`);
  for (const key of ['errorCode', 'error_code']) {
    const value = fields[key];
    if (typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 && value <= 0x7fffffff) {
      details.push(`code ${value}`);
      break;
    }
  }
  if (typeof fields.errorMessage === 'string' && messages.has(fields.errorMessage)) details.push(`message ${fields.errorMessage}`);
  return details.join(', ');
}

function randomValue(): string {
  return randomBytes(32).toString('base64url');
}

async function authorize(openBrowser: OpenBrowser): Promise<{ code: string; verifier: string; redirectUri: string }> {
  const verifier = randomValue();
  const state = randomValue();
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  let resolveCode!: (code: string) => void;
  let rejectCode!: (error: Error) => void;
  let consumed = false;
  const result = new Promise<string>((resolve, reject) => { resolveCode = resolve; rejectCode = reject; });
  const server = createServer((req, res) => {
    let url: URL;
    try { url = new URL(req.url || '/', 'http://localhost'); }
    catch { res.writeHead(400, { 'Cache-Control': 'no-store' }).end('Invalid sign-in response.'); return; }
    if (req.method !== 'GET' || url.pathname !== CALLBACK_PATH || url.searchParams.getAll('state').length !== 1 || url.searchParams.get('state') !== state) {
      res.writeHead(400, { 'Cache-Control': 'no-store' }).end('Invalid sign-in response.');
      return;
    }
    if (consumed) {
      res.writeHead(409, { 'Cache-Control': 'no-store' }).end('This sign-in response has already been used.');
      return;
    }
    const codes = url.searchParams.getAll('code');
    const errors = url.searchParams.getAll('error');
    if (codes.length + errors.length !== 1 || !(codes[0] || errors[0])) {
      res.writeHead(400, { 'Cache-Control': 'no-store' }).end('Incomplete sign-in response.');
      return;
    }
    consumed = true;
    res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer', 'Content-Security-Policy': "default-src 'none'" });
    if (errors.length) {
      res.end('Microsoft authorization was not completed. Return to NicoClient for details.');
      rejectCode(new Error('Microsoft sign-in was cancelled or denied.'));
    } else {
      res.end('Microsoft authorization received. Return to NicoClient; Minecraft account verification is still in progress.');
      resolveCode(codes[0]);
    }
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

async function requestJson(fetcher: Fetch, url: string, init: RequestInit, stage: AuthStage): Promise<any> {
  let response: Response;
  try {
    response = await fetcher(url, { ...init, redirect: 'error', signal: AbortSignal.timeout(20_000) });
  } catch {
    console.warn(`[auth] ${stage}: network request failed or timed out`);
    throw new Error(`${stage}: network request failed or timed out.`);
  }
  if (!response.ok) {
    let code = '';
    let serviceDetail = '';
    try {
      const data = await response.json() as { XErr?: unknown; error_codes?: unknown };
      if (typeof data.XErr === 'number' && Number.isSafeInteger(data.XErr) && data.XErr >= 0) code = ` (Xbox error ${data.XErr})`;
      else if (Array.isArray(data.error_codes) && Number.isSafeInteger(data.error_codes[0]) && data.error_codes[0] >= 0) code = ` (AADSTS${data.error_codes[0]})`;
      if (stage === 'Minecraft authentication') serviceDetail = safeMinecraftError(data);
    } catch { /* Keep the error free of service response bodies and tokens. */ }
    console.warn(`[auth] ${stage}: HTTP ${response.status}${code}${serviceDetail ? `; ${serviceDetail}` : ''}`);
    if (stage === 'Minecraft authentication' && response.status === 403) {
      const detail = serviceDetail ? `Recognized service detail: ${serviceDetail}.` : 'No recognized safe service detail was returned.';
      throw new Error(`Minecraft Services token exchange failed (HTTP 403) at /authentication/login_with_xbox. ${detail} Microsoft and Xbox sign-in completed, but Minecraft Services rejected the exchange. Application approval may be required; the exact reason is not confirmed. Minecraft Java ownership has not yet been checked.`);
    }
    if (stage === 'Minecraft ownership check') {
      throw new Error(`Minecraft Java ownership check failed (HTTP ${response.status})${code}. Ownership could not be determined.`);
    }
    throw new Error(`${stage} failed (HTTP ${response.status})${code}.`);
  }
  try { return await response.json(); } catch {
    console.warn(`[auth] ${stage}: invalid service response`);
    throw new Error(`${stage}: invalid service response.`);
  }
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
  const session = { id: '', name: '', accessToken, expiresAt: Date.now() + minecraft.expires_in * 1000, xuid: typeof xsts.DisplayClaims?.xui?.[0]?.xid === 'string' ? xsts.DisplayClaims.xui[0].xid : undefined };
  return verifyMinecraftSession(session, fetcher);
}

export async function verifyMinecraftSession(session: MinecraftSession, fetcher: Fetch = fetch): Promise<MinecraftSession> {
  if (!session.accessToken || session.expiresAt <= Date.now() + 60_000) throw new Error('Minecraft session has expired or will expire shortly. Sign in again.');
  const headers = { Authorization: `Bearer ${session.accessToken}`, Accept: 'application/json' };
  const entitlements = await requestJson(fetcher, 'https://api.minecraftservices.com/entitlements/mcstore', { headers }, 'Minecraft ownership check');
  if (!Array.isArray(entitlements.items) || !entitlements.items.some((item: any) => item?.name === 'game_minecraft' || item?.name === 'product_minecraft')) {
    throw new Error('This account does not have a confirmed Minecraft Java Edition entitlement.');
  }
  const profile = await requestJson(fetcher, 'https://api.minecraftservices.com/minecraft/profile', { headers }, 'Minecraft profile');
  const id = token(profile.id, 'Minecraft profile');
  const name = token(profile.name, 'Minecraft profile');
  if (!/^[0-9a-f]{32}$/i.test(id) || !/^[a-zA-Z0-9_]{3,16}$/.test(name)) throw new Error('Minecraft profile: invalid profile data.');
  if (session.id && session.id !== id) throw new Error('Minecraft account changed. Sign in again.');
  return { ...session, id, name };
}
