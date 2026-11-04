import { createHash, randomBytes } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { mkdir, open, link, unlink, lstat, rename } from 'node:fs/promises';
import path from 'node:path';

const VERSION = '1.21.11';
const LOADER = '0.20.1';
const HOSTS = new Set(['launchermeta.mojang.com', 'piston-meta.mojang.com', 'piston-data.mojang.com', 'libraries.minecraft.net', 'resources.download.minecraft.net', 'meta.fabricmc.net', 'maven.fabricmc.net']);
const MAX_FILE = 512 * 1024 * 1024;
type Fetcher = typeof fetch;
type Progress = (message: string, completed: number, total: number) => void;
export type Artifact = { path: string; url: string; sha1: string; size?: number };
type Library = { name: string; url?: string; sha1?: string; size?: number; rules?: Rule[]; downloads?: { artifact?: Artifact } };
type Rule = { action: 'allow' | 'disallow'; os?: { name?: string; arch?: string; version?: string }; features?: Record<string, boolean> };

function checkedUrl(value: string): string {
  const url = new URL(value);
  if (url.protocol !== 'https:' || !HOSTS.has(url.hostname) || url.username || url.password || url.port) throw new Error('Untrusted game download URL.');
  return url.href;
}
export function safePath(root: string, relative: string): string {
  if (!relative || relative.includes('\\') || relative.startsWith('/') || relative.split('/').some(part => !part || part === '.' || part === '..' || !/^[a-zA-Z0-9_.+-]+$/.test(part))) throw new Error('Unsafe game metadata path.');
  const dest = path.resolve(root, ...relative.split('/'));
  if (!dest.startsWith(path.resolve(root) + path.sep)) throw new Error('Unsafe game metadata path.');
  return dest;
}
async function request(url: string, fetcher: Fetcher): Promise<Response> {
  const result = await fetcher(checkedUrl(url), { redirect: 'error', signal: AbortSignal.timeout(120000) });
  if (!result.ok || !result.body) throw new Error(`Game download failed (HTTP ${result.status}).`);
  return result;
}
export async function json(url: string, fetcher: Fetcher, expected?: string): Promise<any> {
  const response = await request(url, fetcher);
  const chunks: Buffer[] = []; let length = 0;
  for await (const chunk of response.body as any) {
    length += chunk.length;
    if (length > 32 * 1024 * 1024) throw new Error('Game metadata is too large.');
    chunks.push(Buffer.from(chunk));
  }
  const bytes = Buffer.concat(chunks, length);
  if (expected && createHash('sha1').update(bytes).digest('hex') !== expected) throw new Error('Game metadata checksum mismatch.');
  return JSON.parse(bytes.toString('utf8'));
}
async function hashFile(file: string): Promise<string> {
  const hash = createHash('sha1');
  for await (const chunk of createReadStream(file)) hash.update(chunk as Buffer);
  return hash.digest('hex');
}
export async function safeDirectories(root: string, target: string): Promise<void> {
  const base = path.resolve(root);
  const relative = path.relative(base, path.dirname(target));
  if (relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('Unsafe game directory.');
  let current = base;
  const rootStat = await lstat(base);
  if (!rootStat.isDirectory() || rootStat.isSymbolicLink()) throw new Error('Game directory must be a real directory.');
  for (const part of relative.split(path.sep).filter(Boolean)) {
    current = path.join(current, part);
    await mkdir(current).catch((error: NodeJS.ErrnoException) => { if (error.code !== 'EEXIST') throw error; });
    const info = await lstat(current);
    if (!info.isDirectory() || info.isSymbolicLink()) throw new Error('Game directories cannot contain symbolic links.');
  }
}
// Network failures and server errors are worth retrying; a wrong checksum or bad metadata is not.
function isTransient(error: unknown): boolean {
  const message = error instanceof Error ? error.message : '';
  if (/checksum|Invalid|Untrusted|Unsafe|exceeds|symbolic/i.test(message)) return false;
  const status = message.match(/HTTP (\d+)/);
  return !status || Number(status[1]) >= 500 || Number(status[1]) === 429;
}
export async function download(file: Artifact, target: string, root: string, fetcher: Fetcher, retryDelay = 750): Promise<void> {
  for (let attempt = 1; ; attempt++) {
    try { await downloadOnce(file, target, root, fetcher); return; }
    catch (error) {
      if (attempt >= 4 || !isTransient(error)) throw error;
      await new Promise(resolve => setTimeout(resolve, retryDelay * attempt));
    }
  }
}
async function downloadOnce(file: Artifact, target: string, root: string, fetcher: Fetcher): Promise<void> {
  if (!/^[a-f0-9]{40}$/.test(file.sha1) || (file.size !== undefined && (!Number.isSafeInteger(file.size) || file.size < 0 || file.size > MAX_FILE))) throw new Error('Invalid game download metadata.');
  await safeDirectories(root, target);
  try {
    const existing = await lstat(target);
    if (existing.isSymbolicLink() || !existing.isFile()) throw new Error(`Unsafe game file (symbolic link or folder): ${path.basename(target)}.`);
    if ((file.size === undefined || existing.size === file.size) && await hashFile(target) === file.sha1) return;
    // A damaged launcher-managed file would otherwise block PLAY forever. Keep it beside the original location.
    await rename(target, `${target}.${randomBytes(4).toString('hex')}.damaged`);
  } catch (error: any) { if (error.code !== 'ENOENT') throw error; }
  const temp = `${target}.${randomBytes(12).toString('hex')}.tmp`;
  const handle = await open(temp, 'wx');
  try {
    const response = await request(file.url, fetcher);
    const hash = createHash('sha1'); let size = 0;
    for await (const chunk of response.body as any) {
      size += chunk.length;
      if (size > MAX_FILE || (file.size !== undefined && size > file.size)) throw new Error('Game download exceeds expected size.');
      hash.update(chunk); await handle.writeFile(chunk);
    }
    if (hash.digest('hex') !== file.sha1 || (file.size !== undefined && size !== file.size)) throw new Error('Game download checksum or size mismatch.');
    await handle.close();
    await link(temp, target);
  } finally { await handle.close().catch(() => {}); await unlink(temp).catch(() => {}); }
}
export function allowed(rules?: Rule[]): boolean {
  if (!rules) return true;
  let result = false;
  for (const rule of rules) {
    const os = rule.os;
    if (os?.name && os.name !== 'windows') continue;
    if (os?.arch && os.arch !== 'x86_64' && os.arch !== 'amd64') continue;
    if (os?.version || (rule.features && Object.values(rule.features).some(Boolean))) continue;
    result = rule.action === 'allow';
  }
  return result;
}
function mavenPath(name: string): string {
  const parts = name.split(':');
  if (parts.length !== 3 || parts.some(p => !/^[a-zA-Z0-9_.+-]+$/.test(p))) throw new Error('Invalid Fabric library coordinate.');
  return `${parts[0].replace(/\./g, '/')}/${parts[1]}/${parts[2]}/${parts[1]}-${parts[2]}.jar`;
}

export type GameInstall = { version: any; fabric: any; classpath: string[]; assetsDirectory: string; gameDirectory: string };
export async function installGame(gameDirectory: string, progress: Progress, fetcher: Fetcher = fetch): Promise<GameInstall> {
  if (process.platform !== 'win32' || process.arch !== 'x64') throw new Error('Minecraft installation currently supports 64-bit Windows only.');
  progress('Reading official Minecraft metadata', 0, 0);
  const manifest = await json('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', fetcher);
  const entry = manifest.versions?.find((v: any) => v.id === VERSION && v.type === 'release');
  if (!entry || !/^[a-f0-9]{40}$/.test(entry.sha1)) throw new Error('Minecraft version metadata is unavailable.');
  const version = await json(entry.url, fetcher, entry.sha1);
  if (version.id !== VERSION || version.javaVersion?.majorVersion !== 21) throw new Error('Unexpected Minecraft version metadata.');
  const fabric = await json(`https://meta.fabricmc.net/v2/versions/loader/${VERSION}/${LOADER}/profile/json`, fetcher);
  if (fabric.inheritsFrom !== VERSION || fabric.mainClass !== 'net.fabricmc.loader.impl.launch.knot.KnotClient' || !Array.isArray(fabric.libraries)) throw new Error('Invalid Fabric profile.');
  const index = await json(version.assetIndex.url, fetcher, version.assetIndex.sha1);
  const assetsDirectory = path.join(gameDirectory, 'assets');
  const jobs: { file: Artifact; target: string }[] = [];
  const classpath: string[] = [];
  const libraries = path.join(gameDirectory, 'libraries');
  for (const library of version.libraries as Library[]) {
    if (!allowed(library.rules)) continue;
    const artifact = library.downloads?.artifact;
    if (!artifact) throw new Error(`Missing library artifact: ${library.name}`);
    // Modern Minecraft ships platform native libraries as classpath jars.
    if (/natives-windows-(?:arm64|x86)(?:\.jar)?$/i.test(artifact.path)) continue;
    const target = safePath(libraries, artifact.path); jobs.push({ file: artifact, target }); classpath.push(target);
  }
  for (const library of fabric.libraries as Library[]) {
    const relative = mavenPath(library.name);
    const url = checkedUrl(new URL(relative, library.url || 'https://maven.fabricmc.net/').href);
    let sha1 = library.sha1;
    if (!sha1) {
      const response = await request(`${url}.sha1`, fetcher);
      sha1 = (await response.text()).trim().split(/\s+/)[0];
    }
    const target = safePath(libraries, relative); jobs.push({ file: { path: relative, url, sha1: sha1!, size: library.size }, target }); classpath.push(target);
  }
  const client = version.downloads?.client;
  const clientPath = safePath(gameDirectory, `versions/${VERSION}/${VERSION}.jar`);
  jobs.push({ file: client, target: clientPath }); classpath.push(clientPath);
  const indexPath = safePath(assetsDirectory, `indexes/${version.assetIndex.id}.json`);
  jobs.push({ file: version.assetIndex, target: indexPath });
  for (const item of Object.values(index.objects || {}) as any[]) {
    if (!/^[a-f0-9]{40}$/.test(item.hash)) throw new Error('Invalid asset hash.');
    const relative = `${item.hash.slice(0, 2)}/${item.hash}`;
    jobs.push({ file: { path: relative, url: `https://resources.download.minecraft.net/${relative}`, sha1: item.hash, size: item.size }, target: safePath(assetsDirectory, `objects/${relative}`) });
  }
  const unique = new Map<string, { file: Artifact; target: string }>();
  for (const job of jobs) {
    const prior = unique.get(job.target);
    if (prior && (prior.file.sha1 !== job.file.sha1 || prior.file.size !== job.file.size)) throw new Error('Conflicting game file metadata.');
    unique.set(job.target, job);
  }
  const downloads = [...unique.values()];
  let completed = 0, next = 0;
  let failure: unknown;
  progress('Installing Minecraft and Fabric libraries', 0, downloads.length);
  await Promise.all(Array.from({ length: 6 }, async () => {
    for (;;) {
      if (failure) break;
      const index = next++; if (index >= downloads.length) break;
      const job = downloads[index];
      try {
        await download(job.file, job.target, gameDirectory, fetcher);
        progress('Installing Minecraft and Fabric files', ++completed, downloads.length);
      } catch (error) { failure ||= error; break; }
    }
  }));
  if (failure) throw failure;
  return { version, fabric, classpath, assetsDirectory, gameDirectory };
}
