import { app, BrowserWindow, dialog, ipcMain, shell } from 'electron';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { setupUpdater } from './updater';
import { searchMods, listMods, installMod, changeMod, checkModUpdate, updateMod } from './modrinth';
import { loadCosmetics, saveCosmetics } from './cosmetics';
import { setupOfficialProfile } from './official-profile';
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

function openOfficialLauncher(): Promise<void> {
  const child = spawn('explorer.exe', ['shell:AppsFolder\\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft'], { detached: true, stdio: 'ignore', windowsHide: true });
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
      await setupOfficialProfile(path.join(app.getPath('appData'), '.minecraft'), gameDirectory, resourcePath('nicoclient-fabric-0.1.0.jar'));
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
  ipcMain.handle('launcher-info', event => { trusted(event); return { version: app.getVersion() }; });
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
  ipcMain.handle('cosmetics-load', event => { trusted(event); return loadCosmetics(path.join(gameDirectory, 'config')); });
  ipcMain.handle('cosmetics-save', (event, settings: unknown) => { trusted(event); return saveCosmetics(path.join(gameDirectory, 'config'), settings); });
  createWindow();
  setupUpdater();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
