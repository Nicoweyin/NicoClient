import { execFile, spawn, type ChildProcess } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFile, lstat, mkdir, readFile, rename, stat, unlink } from 'node:fs/promises';
import path from 'node:path';
import { verifyMinecraftSession, type MinecraftSession } from './auth';
import { allowed, installGame, safeDirectories, type GameInstall } from './game-install';
import { ensureMod } from './modrinth';
import { installJavaRuntime } from './java-runtime';

type Event = { status: 'installing' | 'running' | 'exited' | 'error'; message: string; percent?: number; log?: string };
type Emit = (event: Event) => void;
type LaunchArgument = string | { rules?: any[]; value: string | string[] };

export function makeArguments(install: GameInstall, session: MinecraftSession, natives: string, bootstrap: string, memoryMb = 2048): string[] {
  const substitutions: Record<string, string> = {
    auth_player_name: session.name, version_name: install.fabric.id, game_directory: install.gameDirectory,
    assets_root: install.assetsDirectory, assets_index_name: install.version.assetIndex.id,
    auth_uuid: session.id, auth_access_token: '__NICOCLIENT_SESSION_TOKEN__', clientid: '6608da6f-c687-477c-aef9-237955e7de90',
    auth_xuid: session.xuid || '', version_type: 'release', natives_directory: natives,
    launcher_name: 'NicoClient', launcher_version: '0.1.0', classpath: [...install.classpath, bootstrap].join(path.delimiter)
  };
  function expand(items: LaunchArgument[]): string[] {
    return items.flatMap(item => {
      if (typeof item === 'string') return [item];
      if (!allowed(item.rules)) return [];
      return Array.isArray(item.value) ? item.value : [item.value];
    }).map(value => value.replace(/\$\{([^}]+)\}/g, (_match, key: string) => {
      if (!(key in substitutions)) throw new Error(`Unsupported Minecraft launch variable: ${key}`);
      return substitutions[key];
    }));
  }
  if (install.fabric.mainClass !== 'net.fabricmc.loader.impl.launch.knot.KnotClient') throw new Error('Invalid Fabric entry point.');
  return [`-Xmx${memoryMb}M`, ...expand(install.version.arguments.jvm), ...expand(install.fabric.arguments.jvm), 'dev.nicoweyin.nicoclient.bootstrap.LaunchBootstrap',
    ...expand(install.version.arguments.game), ...expand(install.fabric.arguments.game)];
}

function systemJava(): Promise<string> {
  const executable = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java.exe') : 'java';
  return new Promise((resolve, reject) => {
    execFile(executable, ['-version'], { encoding: 'utf8', timeout: 15000, windowsHide: true }, (error, stdout, stderr) => {
      const match = `${stderr || ''}\n${stdout || ''}`.match(/version "(?:1\.)?(\d+)/);
      if (error || !match || Number(match[1]) < 21) reject(new Error('Java could not be installed automatically and no Java 21+ was found. Check your connection or install Java 21.'));
      else resolve(executable);
    });
  });
}
// Mojang's own runtime is the exact Java this version expects; an installed Java is only the fallback.
async function javaExecutable(gameDirectory: string, component: string, emit: Emit): Promise<string> {
  try {
    return await installJavaRuntime(gameDirectory, component, (done, total) => emit({ status: 'installing', message: `Installing Java (${done}/${total})`, percent: 90 }));
  } catch { return systemJava(); }
}
// The NicoClient JAR is owned by the launcher, so a rebuilt or updated mod simply replaces the old one.
export async function installLocalMod(source: string, mods: string): Promise<void> {
  const info = await stat(source).catch(() => undefined);
  if (!info?.isFile()) throw new Error('The built NicoClient Fabric JAR is missing. Build the Fabric mod before launching.');
  const target = path.join(mods, path.basename(source));
  if (await lstat(`${target}.disabled`).catch(() => undefined)) throw new Error('The NicoClient mod is disabled. Enable it on the Mods page before launching.');
  const existing = await lstat(target).catch(() => undefined);
  if (existing && (!existing.isFile() || existing.isSymbolicLink())) throw new Error('The existing NicoClient mod is not a regular file.');
  const hash = (buffer: Buffer) => createHash('sha256').update(buffer).digest('hex');
  if (existing && hash(await readFile(source)) === hash(await readFile(target))) return;
  const temp = `${target}.${process.pid}.tmp`;
  try { await copyFile(source, temp); await rename(temp, target); }
  finally { await unlink(temp).catch(() => {}); }
}

export async function launchGame(gameDirectory: string, modJar: string, bootstrap: string, session: MinecraftSession, valid: () => boolean, emit: Emit, memoryMb = 2048): Promise<ChildProcess> {
  if (!(await stat(bootstrap).catch(() => undefined))?.isFile()) throw new Error('The NicoClient launch bootstrap is missing. Build the Fabric project first.');
  const mods = path.join(gameDirectory, 'mods');
  await mkdir(mods, { recursive: true });
  const installed = await installGame(gameDirectory, (message, done, total) => emit({ status: 'installing', message: total ? `${message} (${done}/${total})` : message, percent: total ? Math.round(done / total * 90) : 0 }));
  const java = await javaExecutable(gameDirectory, installed.version.javaVersion?.component || 'java-runtime-delta', emit);
  emit({ status: 'installing', message: 'Installing Fabric API', percent: 90 });
  await ensureMod(mods, 'P7dR8mSH', /^fabric-api-/i, (message, percent) => emit({ status: 'installing', message: `Fabric API: ${message}`, percent: 90 + Math.round(percent * 0.08) }));
  await installLocalMod(modJar, mods);
  if (!valid()) throw new Error('Your account changed while installing. Sign in again.');
  emit({ status: 'installing', message: 'Rechecking Minecraft ownership', percent: 99 });
  const verified = await verifyMinecraftSession(session);
  if (!valid()) throw new Error('Your account changed while checking ownership. Sign in again.');
  const natives = path.join(gameDirectory, 'natives', '1.21.11');
  await safeDirectories(gameDirectory, path.join(natives, '.placeholder'));
  if (!verified.accessToken || /[\r\n]/.test(verified.accessToken)) throw new Error('Invalid Minecraft session. Sign in again.');
  const args = makeArguments(installed, verified, natives, bootstrap, memoryMb);
  if (!valid()) throw new Error('Your account changed before launch. Sign in again.');
  const child = spawn(java, args, { cwd: gameDirectory, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  await new Promise<void>((resolve, reject) => { child.once('spawn', resolve); child.once('error', () => reject(new Error('Unable to start Java.'))); });
  child.stdin?.on('error', () => {});
  child.stdin?.end(`${verified.accessToken}\n`);
  emit({ status: 'running', message: 'Java started; waiting for Minecraft to load.', percent: 100 });
  for (const stream of [child.stdout, child.stderr]) {
    let pending = '';
    stream?.setEncoding('utf8');
    stream?.on('data', (chunk: string) => {
      pending += chunk;
      const lines = pending.split(/\r?\n/); pending = lines.pop() || '';
      for (const line of lines) emit({ status: 'running', message: 'Minecraft process running', log: line.split(verified.accessToken).join('[redacted]').slice(0, 2000) });
      if (pending.length > 16384) pending = '[long log line suppressed]';
    });
  }
  child.once('exit', (code, signal) => emit({ status: 'exited', message: `Minecraft process exited (${signal || code}).` }));
  return child;
}
