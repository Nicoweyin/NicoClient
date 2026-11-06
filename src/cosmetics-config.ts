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

let queue: Promise<unknown> = Promise.resolve();

/**
 * Changes one cosmetic in the game's settings file and keeps every other line as it was.
 * Writes run one after another, so quick clicks cannot overwrite each other.
 */
export function writeCosmetic(gameDirectory: string, slot: string, value: unknown): Promise<CosmeticChoice> {
  const next = queue.then(() => writeNow(gameDirectory, slot, value));
  queue = next.catch(() => undefined);
  return next;
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
  const file = configFile(gameDirectory);
  let lines: string[] = [];
  try { lines = (await fs.readFile(file, 'utf8')).split(/\r?\n/); } catch { /* Start a new file. */ }
  if (lines.length && lines[lines.length - 1] === '') lines.pop();
  const index = lines.findIndex(line => line.trim().startsWith(`${key}=`) || line.trim().startsWith(`${key}:`));
  if (index >= 0) lines[index] = `${key}=${text}`; else lines.push(`${key}=${text}`);
  await fs.mkdir(path.dirname(file), { recursive: true });
  // Write a copy first, so the game never reads a half-written file.
  const temporary = `${file}.${process.pid}.tmp`;
  await fs.writeFile(temporary, `${lines.join('\n')}\n`, 'utf8');
  await fs.rename(temporary, file);
  return readCosmetics(gameDirectory);
}
