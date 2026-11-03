import { randomBytes } from 'node:crypto';
import { readFile, writeFile, rename, unlink } from 'node:fs/promises';
import path from 'node:path';

export type CosmeticSettings = { cape: string; wings: string; particles: string; hud: string; animation: string; color: string; preset: string };
export const defaults: CosmeticSettings = { cape: 'none', wings: 'none', particles: 'none', hud: 'default', animation: 'none', color: '#a994ff', preset: 'custom' };
const choices: Record<Exclude<keyof CosmeticSettings, 'color'>, string[]> = {
  cape: ['none', 'classic', 'gradient'], wings: ['none', 'classic', 'glow'], particles: ['none', 'sparkle', 'trail'],
  hud: ['default', 'minimal', 'compact'], animation: ['none', 'gentle', 'dynamic'], preset: ['custom', 'violet', 'ocean']
};
function validate(value: any): CosmeticSettings {
  if (!value || typeof value !== 'object') throw new Error('Invalid cosmetic settings.');
  for (const key of Object.keys(choices) as (keyof typeof choices)[]) if (!choices[key].includes(value[key])) throw new Error('Invalid cosmetic setting.');
  if (typeof value.color !== 'string' || !/^#[0-9a-f]{6}$/i.test(value.color)) throw new Error('Invalid cosmetic color.');
  return { cape: value.cape, wings: value.wings, particles: value.particles, hud: value.hud, animation: value.animation, color: value.color, preset: value.preset };
}
export async function loadCosmetics(dir: string): Promise<CosmeticSettings> {
  try { return validate(JSON.parse(await readFile(path.join(dir, 'nicoclient-cosmetics.json'), 'utf8'))); }
  catch (error: any) { if (error?.code === 'ENOENT') return { ...defaults }; throw error; }
}
export async function saveCosmetics(dir: string, value: unknown): Promise<void> {
  const settings = validate(value);
  const target = path.join(dir, 'nicoclient-cosmetics.json');
  const temporary = path.join(dir, `.nicoclient-cosmetics-${randomBytes(12).toString('hex')}.tmp`);
  try { await writeFile(temporary, JSON.stringify(settings, null, 2), { flag: 'wx' }); await rename(temporary, target); }
  finally { await unlink(temporary).catch(() => {}); }
}
