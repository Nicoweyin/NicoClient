// Reads what a mod JAR says about itself, and imports mods from the normal Minecraft folder.
import { constants } from 'node:fs';
import { copyFile, lstat, open, readdir } from 'node:fs/promises';
import path from 'node:path';
import { inflateRawSync } from 'node:zlib';

const GAME = '1.21.11';
const MAX_META = 1024 * 1024;
/** Mods that belong to another client and only run inside it. */
const LAUNCHER_ONLY: Record<string, string> = { feather: 'Part of Feather Client: runs only there', lunarclient: 'Part of Lunar Client: runs only there' };

export type ModInfo = {
  file: string;
  /** Fabric mod id, or '' when the JAR has none. */
  id: string;
  title: string;
  version: string;
  loader: 'fabric' | 'quilt' | 'forge' | 'neoforge' | 'unknown';
  /** False when the mod says it is made for another Minecraft version. */
  fitsGame: boolean;
  /** The Minecraft versions the mod asks for, for the message. */
  wants: string;
};

/** Reads one file out of a ZIP (JAR) without loading the whole archive. Returns null when it is not there. */
export async function readZipEntry(file: string, wanted: string): Promise<Buffer | null> {
  const handle = await open(file, 'r');
  try {
    const size = (await handle.stat()).size;
    const tailSize = Math.min(size, 65557);
    const tail = Buffer.alloc(tailSize);
    await handle.read(tail, 0, tailSize, size - tailSize);
    let end = -1;
    for (let i = tailSize - 22; i >= 0; i--) if (tail.readUInt32LE(i) === 0x06054b50) { end = i; break; }
    if (end < 0) return null;
    const count = tail.readUInt16LE(end + 10), dirSize = tail.readUInt32LE(end + 12), dirOffset = tail.readUInt32LE(end + 16);
    if (dirOffset + dirSize > size || dirSize > 64 * 1024 * 1024) return null;
    const dir = Buffer.alloc(dirSize);
    await handle.read(dir, 0, dirSize, dirOffset);
    let at = 0;
    for (let n = 0; n < count && at + 46 <= dir.length; n++) {
      if (dir.readUInt32LE(at) !== 0x02014b50) return null;
      const method = dir.readUInt16LE(at + 10), packed = dir.readUInt32LE(at + 20), unpacked = dir.readUInt32LE(at + 24);
      const nameLength = dir.readUInt16LE(at + 28), extra = dir.readUInt16LE(at + 30), comment = dir.readUInt16LE(at + 32), local = dir.readUInt32LE(at + 42);
      const name = dir.toString('utf8', at + 46, at + 46 + nameLength);
      at += 46 + nameLength + extra + comment;
      if (name !== wanted) continue;
      if (packed > MAX_META || unpacked > MAX_META) return null;
      const header = Buffer.alloc(30);
      await handle.read(header, 0, 30, local);
      if (header.readUInt32LE(0) !== 0x04034b50) return null;
      const data = Buffer.alloc(packed);
      await handle.read(data, 0, packed, local + 30 + header.readUInt16LE(26) + header.readUInt16LE(28));
      if (method === 0) return data;
      if (method === 8) return inflateRawSync(data, { maxOutputLength: MAX_META });
      return null;
    }
    return null;
  } finally { await handle.close(); }
}

/** Whether a version like 1.21.11 matches one Fabric version predicate such as ">=1.21.9 <1.22", "~1.21.11", "1.21.x" or "*". */
export function versionMatches(predicate: string, version = GAME): boolean {
  const parts = (v: string) => v.split(/[.+-]/).map(p => (/^\d+$/.test(p) ? Number(p) : p));
  const compare = (a: string, b: string) => {
    const x = parts(a), y = parts(b);
    for (let i = 0; i < Math.max(x.length, y.length); i++) {
      const p = x[i] ?? 0, q = y[i] ?? 0;
      if (typeof p === 'number' && typeof q === 'number') { if (p !== q) return p - q; }
      else if (String(p) !== String(q)) return String(p) < String(q) ? -1 : 1;
    }
    return 0;
  };
  return predicate.trim().split(/\s+/).every(term => {
    if (!term || term === '*' || term === 'x') return true;
    const match = /^(>=|<=|>|<|=|~|\^)?(.+)$/.exec(term);
    if (!match) return true;
    const [, op = '', raw] = match;
    if (/[x*]/i.test(raw)) {
      const prefix = raw.replace(/\.?[x*].*$/i, '');
      return version === prefix || version.startsWith(prefix + '.');
    }
    const c = compare(version, raw);
    switch (op) {
      case '>=': return c >= 0;
      case '<=': return c <= 0;
      case '>': return c > 0;
      case '<': return c < 0;
      case '~': { const [a, b] = raw.split('.'); return c >= 0 && version.startsWith(`${a}.${b}.`) || version === `${a}.${b}`; }
      case '^': return c >= 0 && version.split('.')[0] === raw.split('.')[0];
      default: return c === 0;
    }
  });
}

function fits(dependency: unknown): { fits: boolean; wants: string } {
  if (dependency == null) return { fits: true, wants: '' };
  const list = Array.isArray(dependency) ? dependency.map(String) : [String(dependency)];
  return { fits: list.some(item => versionMatches(item)), wants: list.join(' or ') };
}

/** What the JAR says about itself. Never throws: unreadable files come back as 'unknown'. */
export async function describeJar(file: string): Promise<ModInfo> {
  const base = path.basename(file);
  const info: ModInfo = { file: base, id: '', title: '', version: '', loader: 'unknown', fitsGame: true, wants: '' };
  try {
    const fabric = await readZipEntry(file, 'fabric.mod.json');
    if (fabric) {
      // Some mods put raw line breaks inside strings; JSON allows none, so they are turned into spaces first.
      const meta = JSON.parse(fabric.toString('utf8').replace(/^﻿/, '').replace(/[\r\n\t]+/g, ' '));
      info.loader = 'fabric';
      info.id = typeof meta.id === 'string' ? meta.id : '';
      info.title = typeof meta.name === 'string' ? meta.name : info.id;
      info.version = typeof meta.version === 'string' ? meta.version : '';
      const check = fits(meta.depends?.minecraft);
      info.fitsGame = check.fits; info.wants = check.wants;
      return info;
    }
    if (await readZipEntry(file, 'quilt.mod.json')) { info.loader = 'quilt'; return info; }
    if (await readZipEntry(file, 'META-INF/neoforge.mods.toml')) { info.loader = 'neoforge'; return info; }
    if (await readZipEntry(file, 'META-INF/mods.toml')) { info.loader = 'forge'; return info; }
  } catch { /* A damaged or unusual JAR stays 'unknown'. */ }
  return info;
}

/** A plain-language problem with a mod file, or '' when it should load. */
export function problem(info: ModInfo): string {
  if (LAUNCHER_ONLY[info.id]) return LAUNCHER_ONLY[info.id];
  if (info.loader === 'forge') return 'Forge mod: does not run on Fabric';
  if (info.loader === 'neoforge') return 'NeoForge mod: does not run on Fabric';
  if (info.loader === 'quilt') return 'Quilt mod: may not run on Fabric';
  if (info.loader === 'unknown') return 'Not a Fabric mod';
  if (!info.fitsGame) return `Made for Minecraft ${info.wants}`;
  return '';
}

async function realDirectory(dir: string): Promise<boolean> {
  try { const stat = await lstat(dir); return stat.isDirectory() && !stat.isSymbolicLink(); } catch { return false; }
}

async function jars(dir: string): Promise<string[]> {
  if (!await realDirectory(dir)) return [];
  const entries = await readdir(dir, { withFileTypes: true });
  return entries.filter(entry => entry.isFile() && /\.jar$/i.test(entry.name)).map(entry => entry.name);
}

/** Files in the mods folder that Minecraft never loads, such as a .zip or a mod left inside a subfolder. */
export async function strayFiles(dir: string): Promise<string[]> {
  if (!await realDirectory(dir)) return [];
  const entries = await readdir(dir, { withFileTypes: true });
  return entries.filter(entry => {
    if (entry.isDirectory()) return !/^\./.test(entry.name);
    return entry.isFile() && !/\.jar(?:\.disabled)?$/i.test(entry.name) && !/^\./.test(entry.name) && !/\.(?:txt|md|json|toml|log)$/i.test(entry.name);
  }).map(entry => entry.isDirectory() ? `${entry.name}/` : entry.name);
}

export type ImportCandidate = ModInfo & { status: 'ready' | 'installed' | 'incompatible'; reason: string };

/** Mods in the normal Minecraft folder and whether each can come over. */
export async function importCandidates(from: string, to: string): Promise<ImportCandidate[]> {
  const ownIds = new Set<string>(), ownNames = new Set<string>();
  for (const name of await jars(to)) { ownNames.add(name.toLowerCase()); const info = await describeJar(path.join(to, name)); if (info.id) ownIds.add(info.id); }
  if (await realDirectory(to)) {
    for (const entry of await readdir(to)) if (/\.jar\.disabled$/i.test(entry)) {
      ownNames.add(entry.slice(0, -9).toLowerCase());
      const info = await describeJar(path.join(to, entry)); if (info.id) ownIds.add(info.id);
    }
  }
  const seen = new Set<string>();
  const result: ImportCandidate[] = [];
  for (const name of await jars(from)) {
    const info = await describeJar(path.join(from, name));
    const issue = problem(info);
    let status: ImportCandidate['status'] = 'ready', reason = '';
    if (issue) { status = 'incompatible'; reason = issue; }
    else if ((info.id && ownIds.has(info.id)) || ownNames.has(targetName(name).toLowerCase())) { status = 'installed'; reason = 'Already in NicoClient'; }
    else if (info.id && seen.has(info.id)) { status = 'installed'; reason = 'Another copy of this mod is in the list'; }
    if (status === 'ready' && info.id) seen.add(info.id);
    result.push({ ...info, status, reason });
  }
  return result;
}

/** Feather and similar launchers name files "mod.temp.jar"; the copy gets the plain name. */
function targetName(name: string): string { return name.replace(/\.temp\.jar$/i, '.jar'); }

/**
 * Installs mod files dropped onto the launcher. Always copies, so the originals stay where they were.
 * Each file comes back added or with the reason it was left out.
 */
export async function addModFiles(files: unknown, to: string): Promise<{ added: string[]; skipped: { file: string; reason: string }[] }> {
  if (!Array.isArray(files) || files.length > 200 || files.some(file => typeof file !== 'string')) throw new Error('Invalid file list.');
  if (!await realDirectory(to)) throw new Error('Mods directory must be a real directory.');
  const ownIds = new Set<string>();
  for (const entry of await readdir(to)) if (/\.jar(?:\.disabled)?$/i.test(entry)) { const info = await describeJar(path.join(to, entry)); if (info.id) ownIds.add(info.id); }
  const added: string[] = [], skipped: { file: string; reason: string }[] = [];
  for (const source of files as string[]) {
    const file = path.basename(source);
    if (!path.isAbsolute(source)) { skipped.push({ file, reason: 'Not a file on this PC' }); continue; }
    if (!/\.jar$/i.test(file)) { skipped.push({ file, reason: /\.zip$/i.test(file) ? 'A .zip: unpack it first' : 'Not a mod file (.jar)' }); continue; }
    const stat = await lstat(source).catch(() => undefined);
    if (!stat?.isFile() || stat.isSymbolicLink() || stat.size > 200 * 1024 * 1024) { skipped.push({ file, reason: 'Not a regular file' }); continue; }
    if (path.resolve(path.dirname(source)).toLowerCase() === path.resolve(to).toLowerCase()) { skipped.push({ file, reason: 'Already in NicoClient' }); continue; }
    const info = await describeJar(source);
    const issue = problem(info);
    if (issue) { skipped.push({ file, reason: issue }); continue; }
    if (info.id && ownIds.has(info.id)) { skipped.push({ file, reason: 'Already in NicoClient' }); continue; }
    try {
      await copyFile(source, path.join(to, targetName(file)), constants.COPYFILE_EXCL);
      if (info.id) ownIds.add(info.id);
      added.push(info.title || file);
    } catch { skipped.push({ file, reason: 'A file with this name is already there' }); }
  }
  return { added, skipped };
}

/** Copies every importable mod. Never overwrites a file, and never imports two copies of one mod. */
export async function importMods(from: string, to: string): Promise<{ imported: string[]; skipped: number }> {
  if (!await realDirectory(to)) throw new Error('Mods directory must be a real directory.');
  const imported: string[] = [];
  let skipped = 0;
  for (const candidate of await importCandidates(from, to)) {
    if (candidate.status !== 'ready') { skipped++; continue; }
    const source = path.join(from, candidate.file);
    const stat = await lstat(source);
    if (!stat.isFile() || stat.isSymbolicLink()) { skipped++; continue; }
    try { await copyFile(source, path.join(to, targetName(candidate.file)), constants.COPYFILE_EXCL); imported.push(candidate.title || candidate.file); }
    catch { skipped++; }
  }
  return { imported, skipped };
}
