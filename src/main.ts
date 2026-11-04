import { app, BrowserWindow, dialog, ipcMain, nativeImage, shell } from 'electron';
import { existsSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { setupUpdater } from './updater';
import { searchMods, listMods, installMod, changeMod, checkModUpdate, updateMod, installedProjects } from './modrinth';
import { findOfficialLauncher, profileState, setupOfficialProfile } from './official-profile';
import { spawn } from 'node:child_process';

app.setName('NicoClient');
if (process.platform === 'win32') app.setAppUserModelId('dev.nicoweyin.nicoclient');

// Keep all launcher data separate from the normal Minecraft installation.
const gameDirectory = path.join(app.getPath('appData'), 'NicoClient');
const modsDirectory = path.join(gameDirectory, 'mods');
let modsBusy = false;
app.setPath('userData', gameDirectory);

function ensureGameDirectories(): void {
  for (const directory of ['mods', 'config', 'resourcepacks', 'shaderpacks', 'logs']) {
    mkdirSync(path.join(gameDirectory, directory), { recursive: true });
  }
}

function resourcePath(name: string): string {
  return app.isPackaged ? path.join(process.resourcesPath, name) : path.join(app.getAppPath(), 'fabric-mod', 'build', 'libs', name);
}

const LAUNCHER_MISSING = 'The official Minecraft Launcher was not found. Install it, start it once, then press PLAY again.';
const LAUNCHER_DOWNLOAD = 'https://www.minecraft.net/en-us/download';

function minecraftDirectory(): string { return path.join(app.getPath('appData'), '.minecraft'); }

function profileIcon(): string | undefined {
  const image = nativeImage.createFromPath(path.join(__dirname, '../assets/icon.png'));
  return image.isEmpty() ? undefined : image.resize({ width: 128, height: 128 }).toDataURL();
}

function openOfficialLauncher(): Promise<void> {
  const launcher = findOfficialLauncher();
  if (!launcher) return Promise.reject(new Error(LAUNCHER_MISSING));
  const child = launcher.kind === 'exe'
    ? spawn(launcher.path, [], { detached: true, stdio: 'ignore', windowsHide: true })
    : spawn('explorer.exe', ['shell:AppsFolder\\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft'], { detached: true, stdio: 'ignore', windowsHide: true });
  return new Promise((resolve, reject) => {
    child.once('spawn', () => { child.unref(); resolve(); });
    child.once('error', () => reject(new Error('Could not open the official Minecraft Launcher. Start it from the Windows Start menu.')));
  });
}

function createWindow(): void {
  const window = new BrowserWindow({
    width: 1200,
    height: 760,
    minWidth: 900,
    minHeight: 620,
    backgroundColor: '#080a13',
    title: 'NicoClient',
    icon: path.join(__dirname, process.platform === 'win32' ? '../assets/icon.ico' : '../assets/icon.png'),
    show: false,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      preload: path.join(__dirname, 'preload.js')
    }
  });
  window.setMenuBarVisibility(false);
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  window.webContents.on('will-navigate', (event, url) => {
    if (url !== window.webContents.getURL()) event.preventDefault();
  });
  window.once('ready-to-show', () => window.show());
  void window.loadFile(path.join(__dirname, '../ui/index.html'));
}

void app.whenReady().then(() => {
  try {
    ensureGameDirectories();
  } catch (error) {
    dialog.showErrorBox('NicoClient', `Could not create the game directories: ${String(error)}`);
    app.quit();
    return;
  }
  ipcMain.handle('open-mods-folder', async (event) => {
    if (event.senderFrame !== event.sender.mainFrame || !BrowserWindow.fromWebContents(event.sender)) {
      throw new Error('Untrusted request');
    }
    mkdirSync(modsDirectory, { recursive: true });
    const error = await shell.openPath(modsDirectory);
    if (error) throw new Error(error);
  });
  function trusted(event: Electron.IpcMainInvokeEvent): void {
    if (event.senderFrame !== event.sender.mainFrame || !BrowserWindow.fromWebContents(event.sender)) throw new Error('Untrusted request');
  }
  // Sign-in and the game itself are handled by the official launcher.
  ipcMain.handle('game-play', async event => {
    trusted(event);
    if (process.platform !== 'win32') return { ok: false, message: 'PLAY is currently supported on Windows only.' };
    if (modsBusy) return { ok: false, message: 'Wait for the current mod operation to finish.' };
    modsBusy = true;
    try {
      if (!findOfficialLauncher()) return { ok: false, message: LAUNCHER_MISSING };
      if (!existsSync(path.join(minecraftDirectory(), 'launcher_profiles.json'))) {
        // A launcher that has never run has no profile file yet, so let it create one.
        await openOfficialLauncher();
        return { ok: false, message: 'The Minecraft Launcher is opening for its first start. Sign in there, close it, then press PLAY again.' };
      }
      await setupOfficialProfile(minecraftDirectory(), gameDirectory, resourcePath('nicoclient-fabric-0.1.0.jar'), profileIcon());
      await openOfficialLauncher();
      return { ok: true };
    } catch (error) {
      return { ok: false, message: error instanceof Error ? error.message : 'Could not prepare Minecraft.' };
    } finally { modsBusy = false; }
  });
  ipcMain.handle('open-game-folder', async event => {
    trusted(event);
    const error = await shell.openPath(gameDirectory);
    if (error) throw new Error(error);
  });
  ipcMain.handle('launcher-info', async event => {
    trusted(event);
    return { version: app.getVersion(), launcherInstalled: Boolean(findOfficialLauncher()), profile: await profileState(minecraftDirectory()) };
  });
  ipcMain.handle('open-launcher-download', async event => { trusted(event); await shell.openExternal(LAUNCHER_DOWNLOAD); });
  ipcMain.handle('mods-installed-projects', async event => { trusted(event); return installedProjects(modsDirectory); });
  ipcMain.handle('mods-search', async (event, query: string) => { trusted(event); return searchMods(query); });
  ipcMain.handle('mods-list', async event => { trusted(event); return listMods(modsDirectory); });
  ipcMain.handle('mods-update-check', async (event, name: string) => { trusted(event); return checkModUpdate(modsDirectory, name); });
  ipcMain.handle('mods-change', async (event, name: string, action: string) => {
    trusted(event); if (modsBusy) throw new Error('Another mod operation is running.');
    modsBusy = true; try { await changeMod(modsDirectory, name, action); } finally { modsBusy = false; }
  });
  ipcMain.handle('mods-install', async (event, project: string) => {
    trusted(event); if (modsBusy) throw new Error('Another mod operation is running.');
    modsBusy = true;
    try { await installMod(modsDirectory, project, (name, percent) => { if (!event.sender.isDestroyed()) event.sender.send('mods-progress', { name, percent }); }); }
    finally { modsBusy = false; }
  });
  ipcMain.handle('mods-update', async (event, name: string) => {
    trusted(event); if (modsBusy) throw new Error('Another mod operation is running.');
    modsBusy = true;
    try { await updateMod(modsDirectory, name, (text, percent) => { if (!event.sender.isDestroyed()) event.sender.send('mods-progress', { name: text, percent }); }); }
    finally { modsBusy = false; }
  });
  createWindow();
  setupUpdater();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
