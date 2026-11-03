import { app, BrowserWindow, dialog, ipcMain } from 'electron';
import { autoUpdater } from 'electron-updater';

type UpdateState = { status: string; message: string; version?: string; notes?: string; percent?: number };
let state: UpdateState = { status: 'idle', message: 'Updates are available in installed builds.' };
let downloaded = false;

function notify(next: UpdateState): void {
  state = next;
  for (const window of BrowserWindow.getAllWindows()) window.webContents.send('update-state', state);
}

function trusted(event: Electron.IpcMainInvokeEvent): void {
  if (event.senderFrame !== event.sender.mainFrame || !BrowserWindow.fromWebContents(event.sender)) throw new Error('Untrusted request');
}

export function setupUpdater(): void {
  autoUpdater.autoDownload = false;
  autoUpdater.autoInstallOnAppQuit = false;
  autoUpdater.on('checking-for-update', () => notify({ status: 'checking', message: 'Checking for updates…' }));
  autoUpdater.on('update-not-available', () => notify({ status: 'current', message: 'You are up to date.' }));
  autoUpdater.on('update-available', info => {
    downloaded = false;
    const notes = typeof info.releaseNotes === 'string' ? info.releaseNotes : (info.releaseNotes || []).map(note => note.note).join('\n');
    notify({ status: 'available', message: 'A new version is available.', version: info.version, notes: notes || 'No release notes provided.' });
  });
  autoUpdater.on('download-progress', progress => notify({ ...state, status: 'downloading', message: 'Downloading update…', percent: progress.percent }));
  autoUpdater.on('update-downloaded', () => { downloaded = true; notify({ ...state, status: 'downloaded', message: 'Update downloaded. Restart when ready.' }); });
  autoUpdater.on('error', () => notify({ status: 'error', message: 'Update check or download failed. Check your connection and try again.' }));
  ipcMain.handle('update-state', event => { trusted(event); return state; });
  ipcMain.handle('update-check', async event => {
    trusted(event);
    if (!app.isPackaged) { notify({ status: 'unavailable', message: 'Update checks require an installed release build.' }); return; }
    try { await autoUpdater.checkForUpdates(); } catch { /* error event updates the UI */ }
  });
  ipcMain.handle('update-download', async event => {
    trusted(event);
    if (state.status !== 'available') return;
    try { await autoUpdater.downloadUpdate(); } catch { /* error event updates the UI */ }
  });
  ipcMain.handle('update-install', async event => {
    trusted(event);
    if (!downloaded) return;
    const owner = BrowserWindow.fromWebContents(event.sender);
    if (!owner) return;
    const answer = await dialog.showMessageBox(owner, { type: 'question', buttons: ['Later', 'Restart and install'], defaultId: 0, cancelId: 0, message: 'Restart NicoClient and install the update?' });
    if (answer.response === 1) autoUpdater.quitAndInstall();
  });
  if (app.isPackaged) void autoUpdater.checkForUpdates().catch(() => {});
}
