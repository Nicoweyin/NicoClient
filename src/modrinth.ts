import { createHash, randomBytes } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { lstat, readdir, open, link, rename, unlink } from 'node:fs/promises';
import path from 'node:path';

const API = 'https://api.modrinth.com/v2';
const AGENT = 'NicoClient/0.1.0 (https://github.com/Nicoweyin/NicoClient)';
const GAME = '1.21.11';
const MAX_SIZE = 200 * 1024 * 1024;
const idPattern = /^[A-Za-z0-9]{1,64}$/;
type FileInfo = { filename: string; url: string; size: number; hashes: { sha512: string }; primary: boolean };
type Version = { id: string; project_id: string; version_number?: string; version_type?: string; game_versions: string[]; loaders: string[]; files: FileInfo[]; dependencies: { dependency_type: string; project_id: string | null; version_id: string | null }[] };

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
  const params = new URLSearchParams({ query, limit: '24', index: query.trim() ? 'relevance' : 'downloads', facets: JSON.stringify([['project_type:mod'], ['categories:fabric'], [`versions:${GAME}`]]) });
  const result = await json(`/search?${params}`);
  return (result.hits || []).map((hit: any) => ({ id: hit.project_id, title: hit.title, description: hit.description, downloads: hit.downloads, icon: typeof hit.icon_url === 'string' && hit.icon_url.startsWith('https://cdn.modrinth.com/') ? hit.icon_url : '' }));
}
// Which Modrinth projects are already in the folder, so search results can say so.
export async function installedProjects(dir: string): Promise<string[]> {
  const projects: string[] = [];
  for (const item of await listMods(dir)) {
    const version = await identify(path.join(dir, item.name)).catch(() => undefined);
    if (version?.project_id) projects.push(version.project_id);
  }
  return projects;
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
// Modrinth lists newest first; do not pick a beta when a stable release exists.
function newest(list: Version[]): Version | undefined {
  return list.find(version => version.version_type === 'release') || list[0];
}
async function identify(file: string): Promise<Version | undefined> {
  const sha1 = await fileHash(file, 'sha1');
  const response = await fetch(`${API}/version_file/${sha1}?algorithm=sha1`, { headers: { 'User-Agent': AGENT }, signal: AbortSignal.timeout(20000) });
  if (response.status === 404) return undefined; // Unrecognized local files are never modified.
  if (!response.ok) throw new Error('Could not check existing mod versions.');
  return await response.json() as Version;
}
async function versions(project: string): Promise<Version[]> {
  const params = new URLSearchParams({ loaders: JSON.stringify(['fabric']), game_versions: JSON.stringify([GAME]) });
  return json(`/project/${id(project)}/version?${params}`);
}
export type ModUpdate = { status: 'unknown' | 'current' | 'available' | 'none'; message: string };
export async function checkModUpdate(dir: string, name: string): Promise<ModUpdate> {
  await checkDirectory(dir); safeName(name);
  const file = path.join(dir, name);
  const stat = await lstat(file);
  if (!stat.isFile() || stat.isSymbolicLink()) throw new Error('Invalid mod file.');
  const current = await identify(file);
  if (!current) return { status: 'unknown', message: 'Not a Modrinth file, so updates cannot be checked.' };
  const latest = newest(await versions(current.project_id));
  if (!latest) return { status: 'none', message: 'No compatible version found.' };
  if (latest.id === current.id) return { status: 'current', message: 'Up to date.' };
  return { status: 'available', message: `Update available: ${latest.version_number || latest.id}.` };
}
export async function updateMod(dir: string, name: string, progress: (text: string, percent: number) => void): Promise<void> {
  await checkDirectory(dir); safeName(name);
  if (name.endsWith('.disabled')) throw new Error('Enable the mod before updating it.');
  const file = path.join(dir, name);
  const stat = await lstat(file);
  if (!stat.isFile() || stat.isSymbolicLink()) throw new Error('Invalid mod file.');
  const current = await identify(file);
  if (!current) throw new Error('This file is not recognized by Modrinth.');
  await installMod(dir, current.project_id, progress);
}
// Used by PLAY: any compatible installed version is fine, so a new upstream release can never block launching.
export async function ensureMod(dir: string, project: string, filePrefix: RegExp, progress: (text: string, percent: number) => void): Promise<void> {
  await checkDirectory(dir);
  const installed = await listMods(dir);
  try {
    for (const item of installed) {
      const version = await identify(path.join(dir, item.name));
      if (version?.project_id !== project || !version.game_versions?.includes(GAME)) continue;
      if (!item.enabled) throw new Error(`${item.name} is required but disabled. Enable it on the Mods page.`);
      return;
    }
  } catch (error) {
    if (error instanceof Error && error.message.includes('required but disabled')) throw error;
    if (installed.some(item => item.enabled && filePrefix.test(item.name))) return; // Modrinth is unreachable; trust the local copy.
    throw error;
  }
  await installMod(dir, project, progress);
}
export async function installMod(dir: string, project: string, progress: (text: string, percent: number) => void): Promise<void> {
  await checkDirectory(dir);
  const planned = new Map<string, Version>();
  async function resolve(projectId: string | null, versionId: string | null): Promise<void> {
    const version: Version | undefined = versionId ? await json(`/version/${id(versionId)}`) : newest(await versions(id(projectId!)));
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
  const replaced: string[] = [];
  for (const item of installed) {
    const file = path.join(dir, item.name);
    existingHashes.set(await fileHash(file, 'sha512'), item.enabled);
    const existing = await identify(file);
    if (!existing) continue;
    const desired = planned.get(existing.project_id);
    if (!desired || desired.id === existing.id) continue;
    if (existing.project_id === project) {
      // The requested mod itself: install the new version and retire the old file.
      if (!item.enabled) throw new Error(`Enable ${item.name} before updating it.`);
      replaced.push(file);
    } else if (existing.game_versions?.includes(GAME) && existing.loaders?.includes('fabric')) {
      // An already installed, compatible dependency stays as it is.
      planned.delete(existing.project_id);
    } else throw new Error(`Remove the incompatible ${item.name} before installing.`);
  }
  const staged: { temp: string; target: string }[] = [];
  const created: string[] = [];
  const retired: { from: string; to: string }[] = [];
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
    // Move old versions aside first so a new file with the same name can never collide.
    for (const file of replaced) {
      const to = `${file}.${randomBytes(6).toString('hex')}.replaced`;
      await rename(file, to); retired.push({ from: file, to });
    }
    for (const item of staged) { await link(item.temp, item.target); created.push(item.target); }
    for (const item of retired) await unlink(item.to).catch(() => {});
    progress('Complete', 100);
  } catch (error) {
    for (const file of created) await unlink(file).catch(() => {});
    for (const item of retired) await rename(item.to, item.from).catch(() => {});
    throw error;
  } finally {
    for (const item of staged) await unlink(item.temp).catch(() => {});
  }
}
