import { createHash, randomBytes } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { lstat, readdir, open, link, unlink } from 'node:fs/promises';
import path from 'node:path';

const API = 'https://api.modrinth.com/v2';
const AGENT = 'NicoClient/0.1.0 (https://github.com/Nicoweyin/NicoClient)';
const GAME = '1.21.11';
const MAX_SIZE = 200 * 1024 * 1024;
const idPattern = /^[A-Za-z0-9]{1,64}$/;
type FileInfo = { filename: string; url: string; size: number; hashes: { sha512: string }; primary: boolean };
type Version = { id: string; project_id: string; game_versions: string[]; loaders: string[]; files: FileInfo[]; dependencies: { dependency_type: string; project_id: string | null; version_id: string | null }[] };

async function json(endpoint: string): Promise<any> {
  const response = await fetch(`${API}${endpoint}`, { headers: { 'User-Agent': AGENT }, signal: AbortSignal.timeout(20000) });
  if (!response.ok) throw new Error(`Modrinth request failed (HTTP ${response.status}).`);
  return response.json();
}
function id(value: string): string {
  if (typeof value !== 'string' || !idPattern.test(value)) throw new Error('Invalid Modrinth ID.');
  return value;
}
function safeName(name: string): string {
  if (typeof name !== 'string' || !/^[^<>:"/\\|?*\x00-\x1f]+\.jar(?:\.disabled)?$/i.test(name) || name.length > 200 || /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(name)) throw new Error('Unsafe mod filename.');
  return name;
}
async function checkDirectory(dir: string): Promise<void> {
  const stat = await lstat(dir);
  if (!stat.isDirectory() || stat.isSymbolicLink()) throw new Error('Mods directory must be a real directory.');
}
async function fileHash(file: string, algorithm: 'sha1' | 'sha512'): Promise<string> {
  const hash = createHash(algorithm);
  for await (const chunk of createReadStream(file)) hash.update(chunk as Buffer);
  return hash.digest('hex');
}
export async function searchMods(query: string): Promise<any[]> {
  if (typeof query !== 'string' || query.length > 120) throw new Error('Invalid search query.');
  const params = new URLSearchParams({ query, limit: '24', facets: JSON.stringify([['project_type:mod'], ['categories:fabric'], [`versions:${GAME}`]]) });
  const result = await json(`/search?${params}`);
  return (result.hits || []).map((hit: any) => ({ id: hit.project_id, title: hit.title, description: hit.description, downloads: hit.downloads, icon: typeof hit.icon_url === 'string' && hit.icon_url.startsWith('https://cdn.modrinth.com/') ? hit.icon_url : '' }));
}
export async function listMods(dir: string): Promise<{ name: string; enabled: boolean }[]> {
  await checkDirectory(dir);
  const entries = await readdir(dir, { withFileTypes: true });
  return entries.filter(entry => entry.isFile() && /\.jar(?:\.disabled)?$/i.test(entry.name)).map(entry => ({ name: entry.name, enabled: !entry.name.endsWith('.disabled') }));
}
export async function changeMod(dir: string, name: string, action: string): Promise<void> {
  await checkDirectory(dir);
  safeName(name);
  const source = path.join(dir, name);
  const stat = await lstat(source);
  if (!stat.isFile() || stat.isSymbolicLink()) throw new Error('Invalid mod file.');
  if (action === 'remove') { await unlink(source); return; }
  if (action !== 'toggle') throw new Error('Invalid mod operation.');
  const target = path.join(dir, name.endsWith('.disabled') ? name.slice(0, -9) : `${name}.disabled`);
  await link(source, target); // Exclusive: never overwrite another mod.
  try { await unlink(source); } catch (error) { await unlink(target); throw error; }
}
async function versions(project: string): Promise<Version[]> {
  const params = new URLSearchParams({ loaders: JSON.stringify(['fabric']), game_versions: JSON.stringify([GAME]) });
  return json(`/project/${id(project)}/version?${params}`);
}
export async function checkModUpdate(dir: string, name: string): Promise<string> {
  await checkDirectory(dir); safeName(name);
  const file = path.join(dir, name);
  const stat = await lstat(file);
  if (!stat.isFile() || stat.isSymbolicLink()) throw new Error('Invalid mod file.');
  const hash = await fileHash(file, 'sha1');
  const current = await json(`/version_file/${hash}?algorithm=sha1`);
  const latest = (await versions(current.project_id))[0];
  if (!latest) return 'No compatible version found.';
  return latest.id === current.id ? 'Up to date.' : `Update available: ${latest.id}. Search the project to review and install it.`;
}
export async function installMod(dir: string, project: string, progress: (text: string, percent: number) => void): Promise<void> {
  await checkDirectory(dir);
  const planned = new Map<string, Version>();
  async function resolve(projectId: string | null, versionId: string | null): Promise<void> {
    const version: Version = versionId ? await json(`/version/${id(versionId)}`) : (await versions(id(projectId!)))[0];
    if (!version || !version.game_versions?.includes(GAME) || !version.loaders?.includes('fabric')) throw new Error('A required dependency has no compatible Fabric 1.21.11 version.');
    id(version.project_id); id(version.id);
    const existing = planned.get(version.project_id);
    if (existing) { if (existing.id !== version.id) throw new Error('Conflicting dependency versions.'); return; }
    if (planned.size >= 40) throw new Error('Too many dependencies.');
    planned.set(version.project_id, version);
    for (const dependency of version.dependencies || []) if (dependency.dependency_type === 'required') {
      if (!dependency.project_id && !dependency.version_id) throw new Error('A required dependency must be installed manually.');
      await resolve(dependency.project_id, dependency.version_id);
    }
  }
  await resolve(id(project), null);
  const installed = await listMods(dir);
  const existingHashes = new Map<string, boolean>();
  for (const item of installed) {
    const file = path.join(dir, item.name);
    existingHashes.set(await fileHash(file, 'sha512'), item.enabled);
    const sha1 = await fileHash(file, 'sha1');
    const response = await fetch(`${API}/version_file/${sha1}?algorithm=sha1`, { headers: { 'User-Agent': AGENT }, signal: AbortSignal.timeout(20000) });
    if (response.status === 404) continue; // Unrecognized local files are never modified.
    if (!response.ok) throw new Error('Could not check existing mod versions.');
    const existing = await response.json() as Version;
    const desired = planned.get(existing.project_id);
    if (desired && desired.id !== existing.id) throw new Error(`Remove the existing version of ${item.name} before installing another.`);
  }
  const staged: { temp: string; target: string }[] = [];
  const created: string[] = [];
  try {
    const all = [...planned.values()];
    for (let index = 0; index < all.length; index++) {
      const version = all[index];
      const file = version.files.find(item => item.primary) || version.files[0];
      if (!file || !/^[a-f0-9]{128}$/i.test(file.hashes?.sha512 || '') || !Number.isSafeInteger(file.size) || file.size <= 0 || file.size > MAX_SIZE) throw new Error('Invalid Modrinth file metadata.');
      const name = safeName(file.filename);
      if (!name.toLowerCase().endsWith('.jar')) throw new Error('Modrinth file is not a mod JAR.');
      const url = new URL(file.url);
      if (url.protocol !== 'https:' || url.hostname !== 'cdn.modrinth.com' || url.username || url.password) throw new Error('Unsafe download URL.');
      if (existingHashes.has(file.hashes.sha512.toLowerCase())) {
        if (!existingHashes.get(file.hashes.sha512.toLowerCase())) throw new Error(`Enable the existing dependency ${name} before installing.`);
        continue;
      }
      const target = path.join(dir, name);
      const temp = path.join(dir, `.nicoclient-${randomBytes(16).toString('hex')}.tmp`);
      const handle = await open(temp, 'wx');
      staged.push({ temp, target });
      try {
        const response = await fetch(url, { headers: { 'User-Agent': AGENT }, redirect: 'error', signal: AbortSignal.timeout(120000) });
        if (!response.ok || !response.body) throw new Error(`Download failed (HTTP ${response.status}).`);
        const hash = createHash('sha512'); let received = 0;
        for await (const chunk of response.body as any) {
          received += chunk.length;
          if (received > file.size || received > MAX_SIZE) throw new Error('Download exceeded expected size.');
          hash.update(chunk); await handle.writeFile(chunk);
          progress(name, ((index + received / file.size) / all.length) * 100);
        }
        if (received !== file.size || hash.digest('hex') !== file.hashes.sha512.toLowerCase()) throw new Error('Download hash or size does not match Modrinth metadata.');
      } finally { await handle.close(); }
    }
    for (const item of staged) { await link(item.temp, item.target); created.push(item.target); }
    progress('Complete', 100);
  } catch (error) {
    for (const file of created) await unlink(file).catch(() => {});
    throw error;
  } finally {
    for (const item of staged) await unlink(item.temp).catch(() => {});
  }
}
