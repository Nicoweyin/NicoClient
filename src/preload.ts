import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('nicoClient', {
  openModsFolder: (): Promise<void> => ipcRenderer.invoke('open-mods-folder'),
  signIn: (): Promise<{ ok: boolean; name?: string; message?: string }> => ipcRenderer.invoke('account-sign-in'),
  signOut: (): Promise<void> => ipcRenderer.invoke('account-sign-out'),
  updateState: () => ipcRenderer.invoke('update-state'),
  checkUpdates: () => ipcRenderer.invoke('update-check'),
  downloadUpdate: () => ipcRenderer.invoke('update-download'),
  installUpdate: () => ipcRenderer.invoke('update-install'),
  onUpdate: (callback: (state: unknown) => void) => ipcRenderer.on('update-state', (_event, state) => callback(state)),
  searchMods: (query: string) => ipcRenderer.invoke('mods-search', query),
  listMods: () => ipcRenderer.invoke('mods-list'),
  installMod: (id: string) => ipcRenderer.invoke('mods-install', id),
  changeMod: (name: string, action: string) => ipcRenderer.invoke('mods-change', name, action),
  checkModUpdate: (name: string) => ipcRenderer.invoke('mods-update-check', name),
  onModsProgress: (callback: (progress: unknown) => void) => ipcRenderer.on('mods-progress', (_event, progress) => callback(progress)),
  loadCosmetics: () => ipcRenderer.invoke('cosmetics-load'),
  saveCosmetics: (settings: unknown) => ipcRenderer.invoke('cosmetics-save', settings)
});
