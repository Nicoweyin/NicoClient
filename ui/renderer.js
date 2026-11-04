'use strict';

document.querySelectorAll('.nav').forEach((button) => {
  button.addEventListener('click', () => {
    document.querySelectorAll('.nav, .page').forEach((element) => element.classList.remove('active'));
    button.classList.add('active');
    document.getElementById(button.dataset.page).classList.add('active');
  });
});

document.getElementById('open-mods').addEventListener('click', async () => {
  const status = document.getElementById('mods-status');
  status.textContent = '';
  try {
    await window.nicoClient.openModsFolder();
  } catch (error) {
    status.textContent = 'Could not open the mods folder. Please check that it is accessible.';
  }
});

document.getElementById('official-launcher').addEventListener('click', async () => {
  const status = document.getElementById('official-launcher-status');
  try {
    await window.nicoClient.openOfficialLauncher();
    status.textContent = 'Opening Minecraft Launcher. Select the NicoClient Fabric profile there.';
  } catch {
    status.textContent = 'Could not open Minecraft Launcher. Start it from the Windows Start menu.';
  }
});

document.getElementById('official-profile').addEventListener('click', async event => {
  const status = document.getElementById('official-launcher-status');
  event.target.disabled = true;
  status.textContent = 'Setting up the NicoClient profile…';
  try {
    await window.nicoClient.setupOfficialProfile();
    status.textContent = 'Profile ready. Open the official launcher and choose NicoClient Fabric 1.21.11.';
    void refreshMods();
  } catch (error) {
    status.textContent = friendlyError(error, 'Could not set up the profile.');
  } finally { event.target.disabled = false; }
});

// Electron prefixes IPC errors with its channel name; users only need the reason.
function friendlyError(error, fallback) {
  const message = error instanceof Error ? error.message.replace(/^Error invoking remote method '[^']+': (?:Error: )?/, '') : '';
  return message || fallback;
}

function setAccount(name) {
  document.getElementById('account-chip').textContent = name ? name.toUpperCase() : 'NOT SIGNED IN';
  document.getElementById('account-chip').classList.toggle('connected', Boolean(name));
  document.getElementById('tile-account').textContent = name || 'Not signed in';
  if (!play.disabled) gameStatus.textContent = name ? `Ready to play as ${name}.` : 'Sign in with a Minecraft Java account to play.';
}

const signInButton = document.getElementById('sign-in');
const signOutButton = document.getElementById('sign-out');
const removeAccountButton = document.getElementById('remove-account');
const switchAccountButton = document.getElementById('switch-account');
const accountStatus = document.getElementById('account-status');
async function connectAccount() {
  signInButton.disabled = true;
  switchAccountButton.disabled = true;
  accountStatus.textContent = 'Complete Microsoft sign-in in your browser…';
  try {
    const result = await window.nicoClient.signIn();
    if (result.ok) {
      accountStatus.textContent = `Minecraft Java account verified: ${result.name}.`;
      setAccount(result.name);
      signInButton.hidden = true;
      signOutButton.hidden = false;
      removeAccountButton.hidden = false;
      switchAccountButton.hidden = false;
    } else {
      const message = result.message || 'Sign-in failed.';
      accountStatus.textContent = /HTTP 403/.test(message) ? `${message} Until this app is approved, you can play through the official launcher on the Home page.` : message;
    }
  } catch {
    accountStatus.textContent = 'Sign-in could not be started. Please try again.';
  } finally {
    signInButton.disabled = false;
    switchAccountButton.disabled = false;
  }
}
signInButton.addEventListener('click', connectAccount);
async function clearAccount(remove) {
  signOutButton.disabled = true;
  removeAccountButton.disabled = true;
  switchAccountButton.disabled = true;
  try {
    if (remove) await window.nicoClient.removeAccount();
    else await window.nicoClient.signOut();
    signOutButton.hidden = true;
    removeAccountButton.hidden = true;
    switchAccountButton.hidden = true;
    signInButton.hidden = false;
    setAccount();
    accountStatus.textContent = remove ? 'Account removed from this NicoClient session. Choose an account when signing in again.' : 'Signed out of NicoClient. Choose an account when signing in again.';
  } catch {
    accountStatus.textContent = 'Could not clear the launcher account session. Please try again.';
  } finally {
    signOutButton.disabled = false;
    removeAccountButton.disabled = false;
    switchAccountButton.disabled = false;
  }
}
signOutButton.addEventListener('click', () => clearAccount(false));
removeAccountButton.addEventListener('click', () => {
  if (confirm('Remove the connected account from this NicoClient session? Browser accounts and Minecraft data will not be removed.')) void clearAccount(true);
});
switchAccountButton.addEventListener('click', async () => {
  signOutButton.disabled = true;
  removeAccountButton.disabled = true;
  switchAccountButton.disabled = true;
  try {
    await window.nicoClient.signOut();
    setAccount();
    signOutButton.hidden = true;
    removeAccountButton.hidden = true;
    switchAccountButton.hidden = true;
    signInButton.hidden = false;
    await connectAccount();
  } catch {
    accountStatus.textContent = 'Could not switch the launcher account. Please try again.';
  } finally {
    signOutButton.disabled = false;
    removeAccountButton.disabled = false;
    switchAccountButton.disabled = false;
  }
});

const play = document.getElementById('play');
const gameStatus = document.getElementById('game-status');
const gameProgress = document.getElementById('game-progress');
const gameLog = document.getElementById('game-log');
const stopGame = document.getElementById('stop-game');
const logPanel = document.getElementById('log-panel');
stopGame.addEventListener('click', () => {
  if (confirm('Stop Minecraft now? Unsaved progress may be lost.')) void window.nicoClient.stopGame();
});
document.getElementById('copy-log').addEventListener('click', () => { void navigator.clipboard.writeText(gameLog.textContent); });
window.nicoClient.onGameState(state => {
  gameStatus.textContent = state.message;
  stopGame.hidden = state.status !== 'running';
  if (state.status === 'error') logPanel.open = true;
  if (typeof state.percent === 'number') { gameProgress.hidden = false; gameProgress.value = state.percent; }
  if (state.log) { gameLog.textContent = (gameLog.textContent + state.log + '\n').slice(-30000); gameLog.scrollTop = gameLog.scrollHeight; }
  if (state.status === 'running' && state.percent === 100) gameProgress.hidden = true;
  if (state.status === 'exited' || state.status === 'error') { play.disabled = false; gameProgress.hidden = true; void refreshMods(); }
});
play.addEventListener('click', async () => {
  play.disabled = true; gameLog.textContent = ''; gameStatus.textContent = 'Preparing Minecraft…';
  try { const result = await window.nicoClient.play(); if (!result.ok) { gameStatus.textContent = result.message; play.disabled = false; } }
  catch { gameStatus.textContent = 'Could not start the game installation.'; play.disabled = false; }
});

function renderUpdate(state) {
  document.getElementById('update-status').textContent = `${state.message}${state.version ? ` (${state.version})` : ''}`;
  const notes = document.getElementById('update-notes');
  notes.textContent = state.notes || '';
  notes.hidden = !state.notes;
  const progress = document.getElementById('update-progress');
  progress.hidden = state.status !== 'downloading';
  progress.value = state.percent || 0;
  document.getElementById('update-download').hidden = state.status !== 'available';
  document.getElementById('update-install').hidden = state.status !== 'downloaded';
}
window.nicoClient.onUpdate(renderUpdate);
window.nicoClient.updateState().then(renderUpdate);
document.getElementById('update-check').addEventListener('click', () => window.nicoClient.checkUpdates());
document.getElementById('update-download').addEventListener('click', () => window.nicoClient.downloadUpdate());
document.getElementById('update-install').addEventListener('click', () => window.nicoClient.installUpdate());

const modStatus = document.getElementById('mod-status');
const modProgress = document.getElementById('mod-progress');
window.nicoClient.onModsProgress(({ name, percent }) => {
  modProgress.hidden = false;
  modProgress.value = percent;
  modStatus.textContent = `${name} · ${Math.round(percent)}%`;
});
let modUpdateChecks = [];
document.getElementById('check-all-mods').addEventListener('click', async event => {
  event.target.disabled = true;
  modStatus.textContent = 'Checking installed mods for updates…';
  const results = await Promise.all(modUpdateChecks.map(check => check().catch(() => undefined)));
  const available = results.filter(result => result?.status === 'available').length;
  const failed = results.filter(result => !result).length;
  modStatus.textContent = `${available ? `${available} update${available === 1 ? '' : 's'} available.` : 'No updates found.'}${failed ? ` ${failed} could not be checked.` : ''}`;
  event.target.disabled = false;
});
async function refreshMods() {
  const container = document.getElementById('installed-mods');
  modUpdateChecks = [];
  try {
    const mods = await window.nicoClient.listMods();
    document.getElementById('tile-mods').textContent = String(mods.filter(mod => mod.enabled).length);
    container.replaceChildren();
    if (!mods.length) container.textContent = 'No installed mods yet.';
    for (const mod of mods) {
      const row = document.createElement('div'); row.className = 'installed-row';
      const label = document.createElement('span'); label.textContent = `${mod.name} · ${mod.enabled ? 'Enabled' : 'Disabled'}`; row.append(label);
      const actions = document.createElement('div'); actions.className = 'button-row';
      const update = document.createElement('button'); update.className = 'secondary'; update.textContent = 'Update'; update.hidden = true;
      update.addEventListener('click', async () => {
        update.disabled = true; modProgress.hidden = false;
        try { await window.nicoClient.updateMod(mod.name); modStatus.textContent = `${mod.name} updated. Restart Minecraft to load it.`; await refreshMods(); }
        catch (error) { modStatus.textContent = friendlyError(error, 'Update failed.'); update.disabled = false; }
        finally { modProgress.hidden = true; }
      });
      actions.append(update);
      modUpdateChecks.push(async () => {
        const result = await window.nicoClient.checkModUpdate(mod.name);
        update.hidden = result.status !== 'available';
        return result;
      });
      const checkUpdate = modUpdateChecks.at(-1);
      for (const [caption, action] of [[mod.enabled ? 'Disable' : 'Enable', 'toggle'], ['Check update', 'update'], ['Uninstall', 'remove']]) {
        const button = document.createElement('button'); button.className = 'secondary'; button.textContent = caption;
        button.addEventListener('click', async () => {
          if (action === 'remove' && !confirm(`Uninstall ${mod.name}? Other mods may depend on it.`)) return;
          button.disabled = true;
          try {
            if (action === 'update') modStatus.textContent = `${mod.name}: ${(await checkUpdate()).message}`;
            else { await window.nicoClient.changeMod(mod.name, action); await refreshMods(); }
          } catch { modStatus.textContent = 'Mod operation failed. The file may be unmanaged, unavailable, or in use.'; }
          finally { button.disabled = false; }
        });
        actions.append(button);
      }
      row.append(actions); container.append(row);
    }
  } catch { container.textContent = 'Could not read the NicoClient mods folder.'; }
}
document.getElementById('refresh-mods').addEventListener('click', refreshMods);
document.getElementById('mod-search-form').addEventListener('submit', async event => {
  event.preventDefault();
  modStatus.textContent = 'Searching Modrinth…';
  const results = document.getElementById('mod-results'); results.replaceChildren();
  try {
    const mods = await window.nicoClient.searchMods(document.getElementById('mod-query').value);
    modStatus.textContent = mods.length ? `${mods.length} compatible search results` : 'No compatible mods found.';
    for (const mod of mods) {
      const card = document.createElement('article'); card.className = 'mod-card';
      if (mod.icon) { const image = document.createElement('img'); image.src = mod.icon; image.alt = ''; card.append(image); }
      const title = document.createElement('h3'); title.textContent = mod.title; card.append(title);
      const downloads = document.createElement('small'); downloads.textContent = `${Number(mod.downloads).toLocaleString()} downloads`; card.append(downloads);
      const description = document.createElement('p'); description.textContent = mod.description; card.append(description);
      const install = document.createElement('button'); install.className = 'secondary'; install.textContent = 'Install';
      install.addEventListener('click', async () => {
        install.disabled = true; modProgress.hidden = false; modStatus.textContent = 'Resolving compatible dependencies…';
        try { await window.nicoClient.installMod(mod.id); modStatus.textContent = `${mod.title} installed. Restart Minecraft to load mods.`; await refreshMods(); }
        catch (error) { modStatus.textContent = friendlyError(error, 'Installation failed. Check your connection and try again.'); }
        finally { install.disabled = false; modProgress.hidden = true; }
      });
      card.append(install); results.append(card);
    }
  } catch { modStatus.textContent = 'Could not reach Modrinth. Check your connection and try again.'; }
});
void refreshMods();

const memory = document.getElementById('memory');
const memoryValue = document.getElementById('memory-value');
const memoryStatus = document.getElementById('memory-status');
function showMemory() {
  const text = `${(Number(memory.value) / 1024).toFixed(1).replace('.0', '')} GB`;
  memoryValue.textContent = text;
  document.getElementById('tile-memory').textContent = text;
}
memory.addEventListener('input', showMemory);
memory.addEventListener('change', async () => {
  try { await window.nicoClient.saveSettings({ memoryMb: Number(memory.value) }); memoryStatus.textContent = 'Saved. Applies the next time you press PLAY.'; }
  catch { memoryStatus.textContent = 'Could not save the memory setting.'; }
});
document.getElementById('open-game').addEventListener('click', async () => {
  try { await window.nicoClient.openGameFolder(); }
  catch { document.getElementById('mods-status').textContent = 'Could not open the game folder.'; }
});
window.nicoClient.launcherInfo().then(info => {
  document.getElementById('app-version').textContent = info.version;
  memory.max = info.maximumMemoryMb;
  memory.value = info.settings.memoryMb;
  showMemory();
  setAccount(info.account);
}).catch(() => {});

const cosmeticFields = {
  cape: ['none', 'classic', 'gradient'], wings: ['none', 'classic', 'glow'],
  particles: ['none', 'sparkle', 'trail'], hud: ['default', 'minimal', 'compact'],
  animation: ['none', 'gentle', 'dynamic'], preset: ['custom', 'violet', 'ocean']
};
const cosmeticContainer = document.getElementById('cosmetic-settings');
for (const [name, options] of Object.entries(cosmeticFields)) {
  const item = document.createElement('div'); item.className = 'cosmetic-item';
  const label = document.createElement('label'); label.htmlFor = `cosmetic-${name}`; label.textContent = name[0].toUpperCase() + name.slice(1); item.append(label);
  const select = document.createElement('select'); select.id = label.htmlFor;
  for (const choice of options) { const option = document.createElement('option'); option.value = choice; option.textContent = choice; select.append(option); }
  item.append(select); cosmeticContainer.append(item);
}
const colorItem = document.createElement('div'); colorItem.className = 'cosmetic-item';
const colorLabel = document.createElement('label'); colorLabel.textContent = 'Custom color'; colorLabel.htmlFor = 'cosmetic-color';
const color = document.createElement('input'); color.type = 'color'; color.id = colorLabel.htmlFor; colorItem.append(colorLabel, color); cosmeticContainer.append(colorItem);
document.getElementById('cosmetic-preset').addEventListener('change', event => {
  if (event.target.value === 'violet') color.value = '#a994ff';
  if (event.target.value === 'ocean') color.value = '#54bfff';
});
color.addEventListener('input', () => { document.getElementById('cosmetic-preset').value = 'custom'; });
window.nicoClient.loadCosmetics().then(settings => {
  for (const [key, value] of Object.entries(settings)) document.getElementById(`cosmetic-${key}`).value = value;
}).catch(() => { document.getElementById('cosmetic-status').textContent = 'Could not read saved preferences.'; });
document.getElementById('save-cosmetics').addEventListener('click', async () => {
  const settings = Object.fromEntries([...Object.keys(cosmeticFields), 'color'].map(key => [key, document.getElementById(`cosmetic-${key}`).value]));
  try { await window.nicoClient.saveCosmetics(settings); document.getElementById('cosmetic-status').textContent = 'Preferences saved. In-game rendering is not implemented yet.'; }
  catch { document.getElementById('cosmetic-status').textContent = 'Could not save preferences.'; }
});
