import { randomUUID } from 'node:crypto';
import { mkdir, readFile, rename, unlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { installLocalMod } from './game-launch';
import { ensureMod } from './modrinth';

const VERSION = 'fabric-loader-0.20.1-1.21.11';
const NAME = 'NicoClient Fabric 1.21.11';
const PROFILE_URL = 'https://meta.fabricmc.net/v2/versions/loader/1.21.11/0.20.1/profile/json';

// Only adds NicoClient's own profile and files; existing profiles and accounts are left untouched.
export async function setupOfficialProfile(minecraft: string, game: string, modJar: string): Promise<void> {
  const profileFile = path.join(minecraft, 'launcher_profiles.json');
  const original = await readFile(profileFile).catch(() => { throw new Error('No official launcher profile file was found. Start the official Minecraft Launcher once, close it, and retry.'); });
  const profiles = JSON.parse(original.toString('utf8'));
  if (!profiles.profiles || typeof profiles.profiles !== 'object' || Array.isArray(profiles.profiles)) throw new Error('Unrecognized launcher profile file; it was not changed.');
  const matches = (Object.values(profiles.profiles) as any[]).filter(profile => profile?.name === NAME);
  if (matches.some(profile => profile.lastVersionId !== VERSION || path.resolve(profile.gameDir || '') !== path.resolve(game))) throw new Error('A different profile already has the NicoClient name; it was not changed.');
  if (matches.length > 1) throw new Error('Multiple NicoClient profiles already exist; none were changed.');

  const response = await fetch(PROFILE_URL, { redirect: 'error', signal: AbortSignal.timeout(20000) });
  if (!response.ok) throw new Error(`Fabric profile request failed (HTTP ${response.status}).`);
  const bytes = Buffer.from(await response.arrayBuffer());
  const metadata = JSON.parse(bytes.toString('utf8'));
  if (metadata.id !== VERSION || metadata.inheritsFrom !== '1.21.11' || metadata.mainClass !== 'net.fabricmc.loader.impl.launch.knot.KnotClient') throw new Error('Unexpected Fabric profile metadata.');
  const versionDir = path.join(minecraft, 'versions', VERSION);
  await mkdir(versionDir, { recursive: true });
  await writeFile(path.join(versionDir, `${VERSION}.json`), bytes);
  const mods = path.join(game, 'mods');
  await mkdir(mods, { recursive: true });
  await ensureMod(mods, 'P7dR8mSH', /^fabric-api-/i, () => {});
  await installLocalMod(modJar, mods);

  if (matches.length) return;
  profiles.profiles[randomUUID().replace(/-/g, '')] = { name: NAME, type: 'custom', created: new Date().toISOString(), lastVersionId: VERSION, gameDir: game };
  const temp = path.join(minecraft, `.nicoclient-profile-${randomUUID()}.tmp`);
  try {
    await writeFile(temp, JSON.stringify(profiles, null, 2), { flag: 'wx' });
    if (!original.equals(await readFile(profileFile))) throw new Error('Launcher profiles changed during setup. Close the official launcher and retry.');
    await rename(temp, profileFile);
  } finally { await unlink(temp).catch(() => {}); }
}
