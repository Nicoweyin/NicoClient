import { mkdir, readFile, rename, unlink, writeFile } from 'node:fs/promises';
import { totalmem } from 'node:os';
import path from 'node:path';

export type LauncherSettings = { memoryMb: number };

const MINIMUM = 1024;
const STEP = 512;

// Leave room for Windows and the launcher itself.
export function maximumMemory(total = totalmem()): number {
  const usable = Math.floor(total / 1048576) - 2048;
  return Math.max(MINIMUM, Math.floor(usable / STEP) * STEP);
}
export function defaultMemory(total = totalmem()): number {
  return Math.min(4096, maximumMemory(total));
}
function validMemory(value: unknown, total?: number): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= MINIMUM && value % STEP === 0 && value <= maximumMemory(total);
}
function file(config: string): string { return path.join(config, 'nicoclient-launcher.json'); }

export async function loadSettings(config: string): Promise<LauncherSettings> {
  try {
    const saved = JSON.parse(await readFile(file(config), 'utf8'));
    if (validMemory(saved?.memoryMb)) return { memoryMb: saved.memoryMb };
  } catch { /* Missing or damaged settings fall back to defaults. */ }
  return { memoryMb: defaultMemory() };
}
export async function saveSettings(config: string, settings: unknown): Promise<LauncherSettings> {
  const memoryMb = (settings as LauncherSettings | undefined)?.memoryMb;
  if (!validMemory(memoryMb)) throw new Error('Invalid memory setting.');
  await mkdir(config, { recursive: true });
  const temp = `${file(config)}.${process.pid}.tmp`;
  try { await writeFile(temp, JSON.stringify({ memoryMb }, null, 2)); await rename(temp, file(config)); }
  finally { await unlink(temp).catch(() => {}); }
  return { memoryMb };
}
