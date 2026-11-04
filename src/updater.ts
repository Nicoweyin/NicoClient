import { app, BrowserWindow, ipcMain } from 'electron';
import { autoUpdater } from 'electron-updater';

type UpdateState = { status: string; message: string; version?: string; notes?: string; percent?: number };
const CHECK_INTERVAL = 4 * 60 * 60 * 1000;
let state: UpdateState = { status: 'idle', message: 'Updates are available in installed builds.' };
let downloaded = false;

function notify(next: UpdateState): void {
  state = next;
  for (const window of BrowserWindow.getAllWindows()) window.webContents.send('update-state', state);
}

function trusted(event: Electron.IpcMainInvokeEvent): void {
  if (event.senderFrame !== event.sender.mainFrame || !BrowserWindow.fromWebContents(event.sender)) throw new Error('Untrusted request');
}

function check(): void {
  if (state.status === 'downloading' || downloaded) return;
  void autoUpdater.checkForUpdates().catch(() => { /* The error event updates the UI. */ });
}

// Updates download on their own; the next restart installs them, or the user restarts right away.
export function setupUpdater(): void {
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.on('checking-for-update', () => notify({ status: 'checking', message: 'Checking for updates…' }));
  autoUpdater.on('update-not-available', () => notify({ status: 'current', message: 'You are up to date.' }));
  autoUpdater.on('update-available', info => {
    const notes = typeof info.releaseNotes === 'string' ? info.releaseNotes : (info.releaseNotes || []).map(note => note.note).join('\n');
    notify({ status: 'downloading', message: 'Downloading update…', version: info.version, notes: notes || undefined, percent: 0 });
  });
  autoUpdater.on('download-progress', progress => notify({ ...state, status: 'downloading', message: 'Downloading update…', percent: progress.percent }));
  autoUpdater.on('update-downloaded', () => {
    downloaded = true;
    notify({ ...state, status: 'downloaded', message: 'Update ready. It installs the next time NicoClient starts.' });
  });
  autoUpdater.on('error', error => {
    // GitHub answers 404 until the first release exists; that is not a problem for the user.
    if (/\b404\b/.test(String(error?.message))) notify({ status: 'current', message: 'No published updates yet.' });
    else notify({ status: 'error', message: 'Update check or download failed. It is tried again later.' });
  });
  ipcMain.handle('update-state', event => { trusted(event); return state; });
  ipcMain.handle('update-check', event => {
    trusted(event);
    if (!app.isPackaged) { notify({ status: 'unavailable', message: 'Update checks require an installed release build.' }); return; }
    check();
  });
  ipcMain.handle('update-install', event => {
    trusted(event);
    if (downloaded) autoUpdater.quitAndInstall(true, true);
  });
  if (app.isPackaged) {
    check();
    setInterval(check, CHECK_INTERVAL).unref();
  }
}
