import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('nicoClient', {
  openModsFolder: (): Promise<void> => ipcRenderer.invoke('open-mods-folder'),
  play: (): Promise<{ ok: boolean; message?: string }> => ipcRenderer.invoke('game-play'),
  openGameFolder: () => ipcRenderer.invoke('open-game-folder'),
  launcherInfo: () => ipcRenderer.invoke('launcher-info'),
  openLauncherDownload: () => ipcRenderer.invoke('open-launcher-download'),
  installedProjects: () => ipcRenderer.invoke('mods-installed-projects'),
  updateMod: (name: string) => ipcRenderer.invoke('mods-update', name),
  updateState: () => ipcRenderer.invoke('update-state'),
  checkUpdates: () => ipcRenderer.invoke('update-check'),
  installUpdate: () => ipcRenderer.invoke('update-install'),
  onUpdate: (callback: (state: unknown) => void) => ipcRenderer.on('update-state', (_event, state) => callback(state)),
  searchMods: (query: string) => ipcRenderer.invoke('mods-search', query),
  listMods: () => ipcRenderer.invoke('mods-list'),
  installMod: (id: string) => ipcRenderer.invoke('mods-install', id),
  changeMod: (name: string, action: string) => ipcRenderer.invoke('mods-change', name, action),
  checkModUpdate: (name: string) => ipcRenderer.invoke('mods-update-check', name),
  onModsProgress: (callback: (progress: unknown) => void) => ipcRenderer.on('mods-progress', (_event, progress) => callback(progress))
});
