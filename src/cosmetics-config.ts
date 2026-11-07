import { promises as fs } from 'node:fs';
import path from 'node:path';

/** How many styles each cosmetic has, including Off. Must match Cosmetics.java in the mod. */
export const COSMETIC_COUNTS = { cape: 12, wings: 8, halo: 5, trail: 6 } as const;
export type CosmeticSlot = keyof typeof COSMETIC_COUNTS;
export interface CosmeticChoice { cape: number; wings: number; halo: number; trail: number; flap: boolean; accent: number }

const ACCENTS = [0, 1, 2, 3, 4];

export function configFile(gameDirectory: string): string {
  return path.join(gameDirectory, 'config', 'nicoclient.properties');
}

/** Reads key=value lines the way Java writes them; comments and blank lines are skipped. */
function parse(text: string): Map<string, string> {
  const values = new Map<string, string>();
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('!')) continue;
    const at = trimmed.search(/[=:]/);
    if (at > 0) values.set(trimmed.slice(0, at).trim(), trimmed.slice(at + 1).trim());
  }
  return values;
}

function number(value: string | undefined, count: number): number {
  const parsed = Number.parseInt(value ?? '', 10);
  return Number.isInteger(parsed) && parsed >= 0 && parsed < count ? parsed : 0;
}

export async function readCosmetics(gameDirectory: string): Promise<CosmeticChoice> {
  let text = '';
  try { text = await fs.readFile(configFile(gameDirectory), 'utf8'); } catch { /* No settings yet: everything is off. */ }
  const values = parse(text);
  const accent = Number.parseInt(values.get('client.accent') ?? '0', 10);
  return {
    cape: number(values.get('cosmetics.cape'), COSMETIC_COUNTS.cape),
    wings: number(values.get('cosmetics.wings'), COSMETIC_COUNTS.wings),
    halo: number(values.get('cosmetics.halo'), COSMETIC_COUNTS.halo),
    trail: number(values.get('cosmetics.trail'), COSMETIC_COUNTS.trail),
    flap: values.get('cosmetics.flap') !== 'false',
    accent: ACCENTS.includes(accent) ? accent : 0
  };
}

/**
 * Modules the launcher can switch from its Home page, with their default when the game has not saved yet.
 * Ids and defaults must match ModuleManager.java in the mod.
 */
export const QUICK_MODULES = {
  fps: true, keys: false, coords: false, armor: false, zoom: true, fullbright: false,
  crosshair: false, togglesprint: false, waypoints: false, particles: false, dynamicfps: false, nofog: false
} as const;
export type QuickModule = keyof typeof QUICK_MODULES;
const HUD_SCALES = [75, 100, 125, 150];
export interface GameSettings { modules: Record<QuickModule, boolean>; accent: number; serverSafe: boolean; hudScale: number }

export async function readSettings(gameDirectory: string): Promise<GameSettings> {
  let text = '';
  try { text = await fs.readFile(configFile(gameDirectory), 'utf8'); } catch { /* No settings yet: the game's defaults apply. */ }
  const values = parse(text);
  const modules = {} as Record<QuickModule, boolean>;
  for (const [id, fallback] of Object.entries(QUICK_MODULES) as [QuickModule, boolean][]) {
    const value = values.get(`${id}.enabled`);
    modules[id] = value === undefined ? fallback : value === 'true';
  }
  const accent = Number.parseInt(values.get('client.accent') ?? '0', 10);
  const scale = Number.parseInt(values.get('client.hudscale') ?? '100', 10);
  return {
    modules,
    accent: ACCENTS.includes(accent) ? accent : 0,
    serverSafe: values.get('client.serversafe') !== 'false',
    hudScale: HUD_SCALES.includes(scale) ? scale : 100
  };
}

/** Changes one game setting from the launcher. Only the keys listed here can be written. */
export function writeSetting(gameDirectory: string, name: string, value: unknown): Promise<GameSettings> {
  let key: string, text: string;
  if (typeof name === 'string' && name.startsWith('module:') && Object.hasOwn(QUICK_MODULES, name.slice(7))) {
    if (typeof value !== 'boolean') return Promise.reject(new Error('A module can only be on or off.'));
    key = `${name.slice(7)}.enabled`; text = String(value);
  } else if (name === 'accent') {
    if (typeof value !== 'number' || !ACCENTS.includes(value)) return Promise.reject(new Error('That color does not exist.'));
    key = 'client.accent'; text = String(value);
  } else if (name === 'serverSafe') {
    if (typeof value !== 'boolean') return Promise.reject(new Error('Server-safe mode can only be on or off.'));
    key = 'client.serversafe'; text = String(value);
  } else if (name === 'hudScale') {
    if (typeof value !== 'number' || !HUD_SCALES.includes(value)) return Promise.reject(new Error('That HUD size does not exist.'));
    key = 'client.hudscale'; text = String(value);
  } else {
    return Promise.reject(new Error('Unknown setting.'));
  }
  return queued(() => writeLine(gameDirectory, key, text)).then(() => readSettings(gameDirectory));
}

// ---- Profiles, written by the game into config/nicoclient-profiles (see Profiles.java).
const PROFILE_NAME = /^[A-Za-z0-9 _-]{1,24}$/;
const PROFILE_ORDER = ['PvP', 'Survival', 'Performance', 'Minimal'];
const PROFILE_KEY = /^[a-z0-9]+\.[a-z0-9]+$/;
const PROFILE_VALUE = /^[A-Za-z0-9.-]{1,16}$/;

export function profileFolder(gameDirectory: string): string { return path.join(gameDirectory, 'config', 'nicoclient-profiles'); }

/** Profile names, presets first. Empty until the game has run once with profiles. */
export async function listProfiles(gameDirectory: string): Promise<string[]> {
  let files: string[] = [];
  try { files = await fs.readdir(profileFolder(gameDirectory)); } catch { return []; }
  const names = files.filter(file => file.endsWith('.properties')).map(file => file.slice(0, -'.properties'.length)).filter(name => PROFILE_NAME.test(name) && name.trim() === name);
  const rank = (name: string) => { const index = PROFILE_ORDER.indexOf(name); return index < 0 ? 99 : index; };
  return names.sort((a, b) => rank(a) - rank(b) || a.toLowerCase().localeCompare(b.toLowerCase()));
}

/** Copies a profile's modules, settings and HUD positions into the game settings. Cosmetics stay. */
export function applyProfile(gameDirectory: string, name: unknown): Promise<GameSettings> {
  if (typeof name !== 'string' || !PROFILE_NAME.test(name) || name.trim() !== name) return Promise.reject(new Error('That profile does not exist.'));
  return queued(async () => {
    let text: string;
    try { text = await fs.readFile(path.join(profileFolder(gameDirectory), `${name}.properties`), 'utf8'); }
    catch { throw new Error('That profile does not exist.'); }
    const values = new Map<string, string>();
    for (const [key, value] of parse(text)) {
      if (PROFILE_KEY.test(key) && PROFILE_VALUE.test(value) && !key.startsWith('cosmetics.') && key !== 'client.welcomed') values.set(key, value);
    }
    if (!values.size) throw new Error('That profile is empty.');
    await writeLines(gameDirectory, values);
  }).then(() => readSettings(gameDirectory));
}

let queue: Promise<unknown> = Promise.resolve();

/** Writes run one after another, so quick clicks cannot overwrite each other. */
function queued<T>(task: () => Promise<T>): Promise<T> {
  const next = queue.then(task);
  queue = next.catch(() => undefined);
  return next;
}

/**
 * Changes one cosmetic in the game's settings file and keeps every other line as it was.
 * Writes run one after another, so quick clicks cannot overwrite each other.
 */
export function writeCosmetic(gameDirectory: string, slot: string, value: unknown): Promise<CosmeticChoice> {
  return queued(() => writeNow(gameDirectory, slot, value));
}

async function writeNow(gameDirectory: string, slot: string, value: unknown): Promise<CosmeticChoice> {
  let key: string, text: string;
  if (slot === 'flap') {
    if (typeof value !== 'boolean') throw new Error('Animate wings must be on or off.');
    key = 'cosmetics.flap'; text = String(value);
  } else if (Object.hasOwn(COSMETIC_COUNTS, slot)) {
    const count = COSMETIC_COUNTS[slot as CosmeticSlot];
    if (typeof value !== 'number' || !Number.isInteger(value) || value < 0 || value >= count) throw new Error('That style does not exist.');
    key = `cosmetics.${slot}`; text = String(value);
  } else {
    throw new Error('Unknown cosmetic.');
  }
  await writeLine(gameDirectory, key, text);
  return readCosmetics(gameDirectory);
}

/** Replaces or adds one key=value line and keeps every other line as it was. */
function writeLine(gameDirectory: string, key: string, text: string): Promise<void> {
  return writeLines(gameDirectory, new Map([[key, text]]));
}

/** Replaces or adds several key=value lines in one write; every other line stays as it was. */
async function writeLines(gameDirectory: string, values: Map<string, string>): Promise<void> {
  const file = configFile(gameDirectory);
  let lines: string[] = [];
  try { lines = (await fs.readFile(file, 'utf8')).split(/\r?\n/); } catch { /* Start a new file. */ }
  if (lines.length && lines[lines.length - 1] === '') lines.pop();
  for (const [key, text] of values) {
    const index = lines.findIndex(line => line.trim().startsWith(`${key}=`) || line.trim().startsWith(`${key}:`));
    if (index >= 0) lines[index] = `${key}=${text}`; else lines.push(`${key}=${text}`);
  }
  await fs.mkdir(path.dirname(file), { recursive: true });
  // Write a copy first, so the game never reads a half-written file.
  const temporary = `${file}.${process.pid}.tmp`;
  await fs.writeFile(temporary, `${lines.join('\n')}\n`, 'utf8');
  await fs.rename(temporary, file);
}
