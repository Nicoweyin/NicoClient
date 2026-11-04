'use strict';

document.querySelectorAll('.nav').forEach((button) => {
  button.addEventListener('click', () => {
    document.querySelectorAll('.nav, .page').forEach((element) => element.classList.remove('active'));
    button.classList.add('active');
    document.getElementById(button.dataset.page).classList.add('active');
    if (button.dataset.page === 'mods') void openModsPage();
    if (button.dataset.page === 'home') void refreshStatus();
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

// Electron prefixes IPC errors with its channel name; users only need the reason.
function friendlyError(error, fallback) {
  const message = error instanceof Error ? error.message.replace(/^Error invoking remote method '[^']+': (?:Error: )?/, '') : '';
  return message || fallback;
}

const play = document.getElementById('play');
const gameStatus = document.getElementById('game-status');
play.addEventListener('click', async () => {
  play.disabled = true;
  gameStatus.textContent = 'Preparing the NicoClient profile…';
  try {
    const result = await window.nicoClient.play();
    gameStatus.textContent = result.ok ? 'Opening the Minecraft Launcher. Choose NicoClient Fabric 1.21.11 there and press Play. If the profile is missing, close the launcher and press PLAY again.' : result.message;
    void refreshMods();
    void refreshStatus();
  } catch { gameStatus.textContent = 'Could not prepare Minecraft.'; }
  finally { play.disabled = false; }
});

document.getElementById('get-launcher').addEventListener('click', () => { void window.nicoClient.openLauncherDownload(); });
async function refreshStatus(showHint = false) {
  try {
    const info = await window.nicoClient.launcherInfo();
    document.getElementById('app-version').textContent = info.version;
    document.getElementById('tile-launcher').textContent = info.launcherInstalled ? 'Installed' : 'Not found';
    document.getElementById('tile-profile').textContent = !info.launcherInstalled || info.profile === 'first-run' ? 'Not ready yet' : info.profile === 'ready' ? 'Ready' : 'Press PLAY to set up';
    document.getElementById('get-launcher').hidden = info.launcherInstalled;
    if (showHint && !info.launcherInstalled) gameStatus.textContent = 'NicoClient plays through the official Minecraft Launcher, which is not installed yet.';
  } catch { /* The status tiles are only informative. */ }
}
void refreshStatus(true);

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
// "entityculling-fabric-1.11.3-mc1.21.11.jar" reads better as a name and a version.
function describeMod(file) {
  const base = file.replace(/\.disabled$/, '').replace(/\.jar$/i, '');
  if (/^nicoclient-fabric-/i.test(base)) return { title: 'NicoClient', detail: 'The NicoClient mod', required: true };
  if (/^fabric-api-/i.test(base)) return { title: 'Fabric API', detail: base.replace(/^fabric-api-/i, ''), required: true };
  const match = base.match(/^(.+?)[-_ ]+(?:(?:fabric|mc)[-_ ]*)*v?(\d.*)$/i);
  const raw = match ? match[1] : base;
  const title = raw.replace(/[-_]+/g, ' ').replace(/\b[a-z]/g, letter => letter.toUpperCase());
  return { title, detail: match ? match[2] : '', required: false };
}
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
      const about = describeMod(mod.name);
      const label = document.createElement('div'); label.className = 'installed-name';
      const title = document.createElement('strong'); title.textContent = about.title;
      const detail = document.createElement('small'); detail.textContent = [about.detail, about.required ? 'Required' : mod.enabled ? '' : 'Disabled'].filter(Boolean).join(' · ');
      label.append(title, detail); row.append(label);
      if (!mod.enabled) row.classList.add('disabled');
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
      for (const [caption, action] of about.required ? [] : [[mod.enabled ? 'Disable' : 'Enable', 'toggle'], ['Uninstall', 'remove']]) {
        const button = document.createElement('button'); button.className = 'secondary'; button.textContent = caption;
        button.addEventListener('click', async () => {
          if (action === 'remove' && !confirm(`Uninstall ${about.title}? Other mods may depend on it.`)) return;
          button.disabled = true;
          try {
            await window.nicoClient.changeMod(mod.name, action);
            await refreshMods();
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
// Only the newest search may fill the list, however the answers arrive.
let searchNumber = 0;
async function runSearch() {
  const number = ++searchNumber;
  const query = document.getElementById('mod-query').value.trim();
  modStatus.textContent = query ? 'Searching Modrinth…' : 'Loading popular mods…';
  const results = document.getElementById('mod-results');
  try {
    const [mods, installed] = await Promise.all([window.nicoClient.searchMods(query), window.nicoClient.installedProjects().catch(() => [])]);
    if (number !== searchNumber) return;
    results.replaceChildren();
    modStatus.textContent = mods.length ? (query ? `${mods.length} compatible results` : 'Popular mods for Fabric 1.21.11') : 'No compatible mods found.';
    for (const mod of mods) {
      const card = document.createElement('article'); card.className = 'mod-card';
      if (mod.icon) { const image = document.createElement('img'); image.src = mod.icon; image.alt = ''; card.append(image); }
      const title = document.createElement('h3'); title.textContent = mod.title; card.append(title);
      const downloads = document.createElement('small'); downloads.textContent = `${Number(mod.downloads).toLocaleString()} downloads`; card.append(downloads);
      const description = document.createElement('p'); description.textContent = mod.description; card.append(description);
      const install = document.createElement('button'); install.className = 'secondary';
      const isInstalled = installed.includes(mod.id);
      install.textContent = isInstalled ? 'Installed' : 'Install';
      install.disabled = isInstalled;
      install.addEventListener('click', async () => {
        install.disabled = true; modProgress.hidden = false; modStatus.textContent = 'Resolving compatible dependencies…';
        try { await window.nicoClient.installMod(mod.id); modStatus.textContent = `${mod.title} installed. Restart Minecraft to load mods.`; install.textContent = 'Installed'; await refreshMods(); }
        catch (error) { modStatus.textContent = friendlyError(error, 'Installation failed. Check your connection and try again.'); install.disabled = false; }
        finally { modProgress.hidden = true; }
      });
      card.append(install); results.append(card);
    }
  } catch { if (number === searchNumber) modStatus.textContent = 'Could not reach Modrinth. Check your connection and try again.'; }
}
let searchTimer;
document.getElementById('mod-query').addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(runSearch, 300); });
document.getElementById('mod-search-form').addEventListener('submit', event => { event.preventDefault(); clearTimeout(searchTimer); void runSearch(); });
let modsPageOpened = false;
async function openModsPage() {
  if (modsPageOpened) return;
  modsPageOpened = true;
  await runSearch();
  await Promise.all(modUpdateChecks.map(check => check().catch(() => undefined)));
}
void refreshMods();

document.getElementById('open-game').addEventListener('click', async () => {
  try { await window.nicoClient.openGameFolder(); }
  catch { document.getElementById('mods-status').textContent = 'Could not open the game folder.'; }
});
