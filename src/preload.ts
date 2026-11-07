import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('nicoClient', {
  openModsFolder: (): Promise<void> => ipcRenderer.invoke('open-mods-folder'),
  play: (): Promise<{ ok: boolean; message?: string }> => ipcRenderer.invoke('game-play'),
  openGameFolder: () => ipcRenderer.invoke('open-game-folder'),
  launcherInfo: () => ipcRenderer.invoke('launcher-info'),
  openLauncherDownload: () => ipcRenderer.invoke('open-launcher-download'),
  cosmetics: () => ipcRenderer.invoke('cosmetics-get'),
  setCosmetic: (slot: string, value: number | boolean) => ipcRenderer.invoke('cosmetics-set', slot, value),
  settings: () => ipcRenderer.invoke('settings-get'),
  profiles: () => ipcRenderer.invoke('profiles-list'),
  applyProfile: (name: string) => ipcRenderer.invoke('profiles-apply', name),
  setSetting: (name: string, value: number | boolean) => ipcRenderer.invoke('settings-set', name, value),
  installedProjects: () => ipcRenderer.invoke('mods-installed-projects'),
  updateMod: (name: string) => ipcRenderer.invoke('mods-update', name),
  updateState: () => ipcRenderer.invoke('update-state'),
  checkUpdates: () => ipcRenderer.invoke('update-check'),
  installUpdate: () => ipcRenderer.invoke('update-install'),
  onUpdate: (callback: (state: unknown) => void) => ipcRenderer.on('update-state', (_event, state) => callback(state)),
  searchMods: (query: string, category = '') => ipcRenderer.invoke('mods-search', query, category),
  listMods: () => ipcRenderer.invoke('mods-list'),
  installMod: (id: string) => ipcRenderer.invoke('mods-install', id),
  changeMod: (name: string, action: string) => ipcRenderer.invoke('mods-change', name, action),
  checkModUpdate: (name: string) => ipcRenderer.invoke('mods-update-check', name),
  onModsProgress: (callback: (progress: unknown) => void) => ipcRenderer.on('mods-progress', (_event, progress) => callback(progress))
});
