import { app, BrowserWindow, dialog, ipcMain, shell } from 'electron';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { signIn, type MinecraftSession } from './auth';
import { setupUpdater } from './updater';
import { searchMods, listMods, installMod, changeMod, checkModUpdate } from './modrinth';
import { loadCosmetics, saveCosmetics } from './cosmetics';

// Keep all launcher data separate from the normal Minecraft installation.
const gameDirectory = path.join(app.getPath('appData'), 'NicoClient');
const modsDirectory = path.join(gameDirectory, 'mods');
let session: MinecraftSession | undefined;
let signingIn = false;
let sessionGeneration = 0;
let modsBusy = false;
app.setPath('userData', gameDirectory);

function ensureGameDirectories(): void {
  for (const directory of ['mods', 'config', 'resourcepacks', 'shaderpacks', 'logs']) {
    mkdirSync(path.join(gameDirectory, directory), { recursive: true });
  }
}

function createWindow(): void {
  const window = new BrowserWindow({
    width: 1200,
    height: 760,
    minWidth: 900,
    minHeight: 620,
    backgroundColor: '#080a13',
    title: 'NicoClient',
    icon: path.join(__dirname, '../assets/icon.png'),
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
  ipcMain.handle('account-sign-in', async (event) => {
    if (event.senderFrame !== event.sender.mainFrame || !BrowserWindow.fromWebContents(event.sender)) throw new Error('Untrusted request');
    if (signingIn) return { ok: false, message: 'A sign-in is already in progress.' };
    signingIn = true;
    const generation = ++sessionGeneration;
    session = undefined;
    try {
      const next = await signIn(url => shell.openExternal(url));
      if (generation !== sessionGeneration) return { ok: false, message: 'Sign-in was cancelled.' };
      session = next;
      return { ok: true, name: next.name };
    } catch (error) {
      return { ok: false, message: error instanceof Error ? error.message : 'Sign-in failed.' };
    } finally {
      signingIn = false;
    }
  });
  ipcMain.handle('account-sign-out', (event) => {
    if (event.senderFrame !== event.sender.mainFrame || !BrowserWindow.fromWebContents(event.sender)) throw new Error('Untrusted request');
    sessionGeneration++;
    session = undefined;
  });
  function trusted(event: Electron.IpcMainInvokeEvent): void {
    if (event.senderFrame !== event.sender.mainFrame || !BrowserWindow.fromWebContents(event.sender)) throw new Error('Untrusted request');
  }
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
