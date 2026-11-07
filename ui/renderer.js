'use strict';

// Short messages in the corner, so feedback shows wherever you are in the launcher.
function toast(text, kind = 'good') {
  const box = document.getElementById('toasts');
  const item = document.createElement('div');
  item.className = `toast ${kind}`;
  item.textContent = text;
  box.append(item);
  while (box.childElementCount > 3) box.firstElementChild.remove();
  setTimeout(() => { item.classList.add('leaving'); setTimeout(() => item.remove(), 300); }, 3200);
}

function showPage(page) {
  document.querySelectorAll('.nav, .page').forEach((element) => element.classList.remove('active'));
  document.querySelector(`.nav[data-page="${page}"]`).classList.add('active');
  document.getElementById(page).classList.add('active');
  document.getElementById('page-title').textContent = page.toUpperCase();
  window.scrollTo(0, 0);
  if (page === 'mods') void openModsPage();
  if (page === 'home') { void refreshStatus(); void refreshSettings();
void refreshProfiles(); void renderHomeLook(); void refreshProfiles(); }
  if (page === 'cosmetics') void openCosmeticsPage();
  if (page === 'settings') void refreshSettings();
}
document.querySelectorAll('.nav').forEach((button) => button.addEventListener('click', () => showPage(button.dataset.page)));
// Ctrl+1 to Ctrl+4 switch pages; / jumps to the mod search.
document.addEventListener('keydown', event => {
  const pages = ['home', 'mods', 'cosmetics', 'settings'];
  if (event.ctrlKey && !event.shiftKey && !event.altKey && /^[1-4]$/.test(event.key)) { event.preventDefault(); showPage(pages[Number(event.key) - 1]); return; }
  const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName || '');
  if (event.key === '/' && !typing) { event.preventDefault(); showPage('mods'); document.getElementById('mod-query').focus(); }
});
document.getElementById('look-card').addEventListener('click', () => showPage('cosmetics'));

// ---- Theme and game settings. The accent matches the in-game accent colors in Ui.java.
const ACCENT_COLORS = [['Violet', '#736af5'], ['Blue', '#4c8dff'], ['Cyan', '#22b8cf'], ['Green', '#34c77b'], ['Pink', '#ec5fa6']];
const QUICK_MODULES = [
  ['fps', 'FPS Counter', 'Frames per second'], ['keys', 'Keystrokes', 'The movement keys you press'],
  ['coords', 'Coordinates', 'Your position in the world'], ['armor', 'Armor HUD', 'Armor and durability'],
  ['zoom', 'Zoom', 'Hold C to look closer'], ['fullbright', 'Fullbright', 'See clearly in the dark'],
  ['crosshair', 'Custom Crosshair', 'Your own crosshair style'], ['togglesprint', 'Toggle Sprint', 'Press once to keep sprinting'],
  ['waypoints', 'Waypoints', 'Save places and blocks'], ['nofog', 'No Fog', 'Removes distance fog'],
  ['particles', 'Reduced Particles', 'Fewer particles, more FPS'], ['dynamicfps', 'Dynamic FPS', 'Saves power in the background']
];
const HUD_SCALES = [75, 100, 125, 150];
let gameSettings = null;

function lighten(hex, amount) {
  const value = Number.parseInt(hex.slice(1), 16);
  const channel = shift => Math.round(((value >> shift) & 255) + (255 - ((value >> shift) & 255)) * amount);
  return `rgb(${channel(16)}, ${channel(8)}, ${channel(0)})`;
}
function applyTheme(accent) {
  const hex = (ACCENT_COLORS[accent] || ACCENT_COLORS[0])[1];
  const value = Number.parseInt(hex.slice(1), 16);
  const root = document.documentElement.style;
  root.setProperty('--accent', hex);
  root.setProperty('--accent-light', lighten(hex, 0.4));
  root.setProperty('--accent-rgb', `${value >> 16 & 255},${value >> 8 & 255},${value & 255}`);
}
function monogram(name) {
  const words = name.split(/\s+/);
  return words.length > 1 ? (words[0][0] + words[1][0]).toUpperCase() : name.slice(0, 2);
}
const settingsStatus = document.getElementById('settings-status');
const quickStatus = document.getElementById('quick-status');
async function changeSetting(name, value, status) {
  try {
    gameSettings = await window.nicoClient.setSetting(name, value);
    status.textContent = 'Saved. A running game follows within two seconds.';
    if (name.startsWith('module:')) {
      const entry = QUICK_MODULES.find(([id]) => `module:${id}` === name);
      toast(`${entry ? entry[1] : 'Module'} ${value ? 'on' : 'off'}`);
    } else toast('Saved');
  } catch (error) {
    status.textContent = friendlyError(error, 'Could not save the setting.');
    toast(status.textContent, 'bad');
  }
  renderSettings();
}
function renderSettings() {
  if (!gameSettings) return;
  applyTheme(gameSettings.accent);
  const quick = document.getElementById('quick-modules');
  if (!quick.childElementCount) {
    for (const [id, name, detail] of QUICK_MODULES) {
      const tile = document.createElement('button');
      tile.className = 'quick'; tile.dataset.module = id;
      const mono = document.createElement('span'); mono.className = 'mono'; mono.textContent = monogram(name);
      const text = document.createElement('div');
      const title = document.createElement('strong'); title.textContent = name;
      const small = document.createElement('small'); small.textContent = detail;
      text.append(title, small);
      const toggle = document.createElement('input'); toggle.type = 'checkbox'; toggle.className = 'switch'; toggle.tabIndex = -1; toggle.setAttribute('aria-hidden', 'true');
      tile.append(mono, text, toggle);
      tile.addEventListener('click', () => changeSetting(`module:${id}`, !gameSettings.modules[id], quickStatus));
      quick.append(tile);
    }
  }
  quick.querySelectorAll('.quick').forEach(tile => {
    const on = Boolean(gameSettings.modules[tile.dataset.module]);
    tile.classList.toggle('on', on);
    tile.setAttribute('aria-pressed', String(on));
    tile.querySelector('.switch').checked = on;
  });
  const swatches = document.getElementById('accent-swatches');
  if (!swatches.childElementCount) {
    ACCENT_COLORS.forEach(([name, hex], index) => {
      const swatch = document.createElement('button');
      swatch.className = 'swatch'; swatch.setAttribute('role', 'radio'); swatch.setAttribute('aria-label', name); swatch.title = name;
      swatch.style.background = hex; swatch.style.setProperty('--glow', hex);
      swatch.addEventListener('click', () => changeSetting('accent', index, settingsStatus));
      swatches.append(swatch);
    });
  }
  swatches.querySelectorAll('.swatch').forEach((swatch, index) => swatch.setAttribute('aria-checked', String(index === gameSettings.accent)));
  const scale = document.getElementById('hud-scale');
  if (!scale.childElementCount) {
    for (const value of HUD_SCALES) {
      const option = document.createElement('button');
      option.textContent = `${value}%`; option.setAttribute('role', 'radio'); option.dataset.value = String(value);
      option.addEventListener('click', () => changeSetting('hudScale', value, settingsStatus));
      scale.append(option);
    }
  }
  scale.querySelectorAll('button').forEach(option => option.setAttribute('aria-checked', String(Number(option.dataset.value) === gameSettings.hudScale)));
  document.getElementById('server-safe').checked = gameSettings.serverSafe;
}
document.getElementById('server-safe').addEventListener('change', event => changeSetting('serverSafe', event.target.checked, settingsStatus));
// Profiles are saved in the game; the launcher lists them and applies one with a click.
let lastProfile = null;
async function refreshProfiles() {
  const list = document.getElementById('profile-list');
  let names = [];
  try { names = await window.nicoClient.profiles(); } catch { /* Shown as empty. */ }
  list.replaceChildren();
  document.getElementById('profile-hint').textContent = names.length ? 'Save your own in the game: Right Shift, then Profiles.' : 'Start the game once to get the PvP, Survival and Performance presets.';
  for (const name of names) {
    const button = document.createElement('button');
    button.className = 'profile';
    button.classList.toggle('active', name === lastProfile);
    const mono = document.createElement('span'); mono.className = 'mono'; mono.textContent = monogram(name);
    const label = document.createElement('span'); label.textContent = name;
    button.append(mono, label);
    button.addEventListener('click', async () => {
      try {
        gameSettings = await window.nicoClient.applyProfile(name);
        lastProfile = name;
        renderSettings();
        toast(`Profile ${name} applied`);
        void refreshProfiles();
      } catch (error) { toast(friendlyError(error, 'Could not apply the profile.'), 'bad'); }
    });
    list.append(button);
  }
}

async function refreshSettings() {
  try { gameSettings = await window.nicoClient.settings(); renderSettings(); }
  catch (error) { quickStatus.textContent = friendlyError(error, 'Could not read the game settings.'); }
}
void refreshSettings();

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
const playSide = document.getElementById('play-side');
const gameStatus = document.getElementById('game-status');
playSide.addEventListener('click', () => { showPage('home'); play.click(); });
play.addEventListener('click', async () => {
  if (play.disabled) return;
  play.disabled = true; playSide.disabled = true; play.classList.add('busy');
  gameStatus.textContent = 'Preparing the NicoClient profile…';
  try {
    const result = await window.nicoClient.play();
    gameStatus.textContent = result.ok ? 'Opening the Minecraft Launcher. Choose NicoClient Fabric 1.21.11 there and press Play. If the profile is missing, close the launcher and press PLAY again.' : result.message;
    toast(result.ok ? 'Minecraft Launcher is opening' : 'Could not start: see the message under PLAY', result.ok ? 'good' : 'bad');
    void refreshMods();
    void refreshStatus();
  } catch { gameStatus.textContent = 'Could not prepare Minecraft.'; }
  finally { play.disabled = false; playSide.disabled = false; play.classList.remove('busy'); }
});

document.getElementById('get-launcher').addEventListener('click', () => { void window.nicoClient.openLauncherDownload(); });
async function refreshStatus(showHint = false) {
  try {
    const info = await window.nicoClient.launcherInfo();
    document.getElementById('app-version').textContent = info.version;
    document.getElementById('news-title').textContent = `New in ${info.version}`;
    const ready = info.launcherInstalled && info.profile === 'ready';
    const tile = (id, text, good) => { const element = document.getElementById(id); element.textContent = text; element.closest('.status-tile').className = `status-tile ${good ? 'good' : 'warn'}`; };
    tile('tile-launcher', info.launcherInstalled ? 'Installed' : 'Not found', info.launcherInstalled);
    tile('tile-profile', !info.launcherInstalled || info.profile === 'first-run' ? 'Not ready yet' : info.profile === 'ready' ? 'Ready' : 'Press PLAY to set up', ready);
    document.getElementById('status-dot').className = `status-dot ${ready ? 'good' : 'warn'}`;
    document.getElementById('status-text').textContent = ready ? 'Ready to play' : 'Setup needed';
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
  document.getElementById('update-install').hidden = state.status !== 'downloaded';
  const active = state.status === 'downloading' || state.status === 'downloaded';
  const chip = document.getElementById('update-chip');
  chip.textContent = state.status === 'downloaded' ? 'UPDATE READY' : state.status === 'downloading' ? 'UPDATING…' : 'EARLY ACCESS';
  chip.classList.toggle('update', active);
  document.getElementById('update-banner').hidden = !active;
  if (active) {
    const ready = state.status === 'downloaded';
    document.getElementById('update-banner-title').textContent = ready ? `NicoClient ${state.version} is ready` : `Downloading NicoClient ${state.version}…`;
    document.getElementById('update-banner-text').textContent = ready ? 'Restart now, or it installs the next time you start NicoClient.' : `${Math.round(state.percent || 0)}%`;
    const bar = document.getElementById('update-banner-progress');
    bar.hidden = ready;
    bar.value = state.percent || 0;
    document.getElementById('update-banner-install').hidden = !ready;
  }
}
window.nicoClient.onUpdate(renderUpdate);
window.nicoClient.updateState().then(renderUpdate);
document.getElementById('update-check').addEventListener('click', () => window.nicoClient.checkUpdates());
document.getElementById('update-install').addEventListener('click', () => window.nicoClient.installUpdate());
document.getElementById('update-banner-install').addEventListener('click', () => window.nicoClient.installUpdate());

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
    document.getElementById('tile-mods').closest('.status-tile').className = 'status-tile good';
    container.replaceChildren();
    if (!mods.length) container.textContent = 'No installed mods yet.';
    for (const mod of mods) {
      const row = document.createElement('div'); row.className = 'installed-row';
      const about = describeMod(mod.name);
      if (mod.title && !about.required) about.title = mod.title;
      if (mod.version) about.detail = mod.version;
      const label = document.createElement('div'); label.className = 'installed-name';
      const title = document.createElement('strong'); title.textContent = about.title;
      const detail = document.createElement('small'); detail.textContent = [about.detail, about.required ? 'Required' : mod.enabled ? '' : 'Disabled'].filter(Boolean).join(' · ');
      label.append(title, detail);
      if (mod.problem && mod.enabled) {
        const warning = document.createElement('small'); warning.className = 'problem'; warning.textContent = `⚠ ${mod.problem}. It will not load; Minecraft may not start.`;
        label.append(warning);
      }
      const mono = document.createElement('span'); mono.className = 'mono'; mono.textContent = monogram(about.title);
      const left = document.createElement('div'); left.className = 'installed-left'; left.append(mono, label);
      row.append(left);
      if (!mod.enabled) row.classList.add('disabled');
      const actions = document.createElement('div'); actions.className = 'button-row';
      const update = document.createElement('button'); update.className = 'secondary'; update.textContent = 'Update'; update.hidden = true;
      update.addEventListener('click', async () => {
        update.disabled = true; modProgress.hidden = false;
        try { await window.nicoClient.updateMod(mod.name); modStatus.textContent = `${mod.name} updated. Restart Minecraft to load it.`; toast(`${about.title} updated`); await refreshMods(); }
        catch (error) { modStatus.textContent = friendlyError(error, 'Update failed.'); toast(modStatus.textContent, 'bad'); update.disabled = false; }
        finally { modProgress.hidden = true; }
      });
      actions.append(update);
      modUpdateChecks.push(async () => {
        const result = await window.nicoClient.checkModUpdate(mod.name);
        update.hidden = result.status !== 'available';
        return result;
      });
      if (about.required) {
        const pill = document.createElement('span'); pill.className = 'pill'; pill.textContent = 'Required'; actions.append(pill);
      } else {
        const remove = document.createElement('button'); remove.className = 'secondary danger'; remove.textContent = 'Uninstall';
        const toggle = document.createElement('input'); toggle.type = 'checkbox'; toggle.className = 'switch'; toggle.checked = mod.enabled;
        toggle.setAttribute('aria-label', `${about.title} enabled`);
        const run = async (action, control) => {
          control.disabled = true;
          try {
            await window.nicoClient.changeMod(mod.name, action);
            toast(action === 'remove' ? `${about.title} uninstalled` : `${about.title} ${mod.enabled ? 'disabled' : 'enabled'}`);
            await refreshMods();
          } catch {
            modStatus.textContent = 'Mod operation failed. The file may be unmanaged, unavailable, or in use.';
            toast('Mod operation failed', 'bad');
            if (control === toggle) toggle.checked = mod.enabled;
          } finally { control.disabled = false; }
        };
        remove.addEventListener('click', () => { if (confirm(`Uninstall ${about.title}? Other mods may depend on it.`)) void run('remove', remove); });
        toggle.addEventListener('change', () => run('toggle', toggle));
        actions.append(remove, toggle);
      }
      row.append(actions); container.append(row);
    }
  } catch { container.textContent = 'Could not read the NicoClient mods folder.'; }
  void refreshImport();
  void refreshStray();
}
document.getElementById('refresh-mods').addEventListener('click', refreshMods);
document.getElementById('open-mods-here').addEventListener('click', async () => {
  try { await window.nicoClient.openModsFolder(); } catch { toast('Could not open the mods folder', 'bad'); }
});
// Mods dropped into the folder show up at once.
window.nicoClient.onModsChanged(() => { void refreshMods(); });

// Mods from the normal Minecraft folder (.minecraft\mods) can come over in one click.
let importBusy = false;
async function refreshImport() {
  const card = document.getElementById('import-card');
  let found = [];
  try { found = await window.nicoClient.scanImport(); } catch { card.hidden = true; return; }
  const ready = found.filter(item => item.status === 'ready');
  const incompatible = found.filter(item => item.status === 'incompatible');
  if (!ready.length) { card.hidden = true; return; }
  card.hidden = false;
  document.getElementById('import-title').textContent = `${ready.length} mod${ready.length === 1 ? '' : 's'} found in your normal Minecraft folder`;
  document.getElementById('import-text').textContent = 'NicoClient uses its own mods folder. Import them to use them here' + (incompatible.length ? `; ${incompatible.length === 1 ? 'one that would not run is' : incompatible.length + ' that would not run are'} left out.` : '.');
  const button = document.getElementById('import-mods');
  button.textContent = `Import ${ready.length === 1 ? 'mod' : `all ${ready.length}`}`;
  const list = document.getElementById('import-list');
  list.replaceChildren(...found.map(item => {
    const li = document.createElement('li'); li.className = item.status;
    const name = document.createElement('span'); name.textContent = item.title;
    const state = document.createElement('span'); state.textContent = item.status === 'ready' ? 'Will be imported' : item.reason;
    li.append(name, state); return li;
  }));
}
document.getElementById('import-details').addEventListener('click', event => {
  const list = document.getElementById('import-list');
  list.hidden = !list.hidden;
  event.target.textContent = list.hidden ? 'Details' : 'Hide';
});
document.getElementById('import-mods').addEventListener('click', async event => {
  if (importBusy) return;
  importBusy = true; event.target.disabled = true;
  try {
    const result = await window.nicoClient.importMods();
    const count = result.imported.length;
    toast(count ? `${count} mod${count === 1 ? '' : 's'} imported` : 'Nothing new to import');
    modStatus.textContent = count ? `Imported ${result.imported.join(', ')}. Restart Minecraft to load them.` : '';
    await refreshMods();
  } catch (error) { toast(friendlyError(error, 'Import failed.'), 'bad'); }
  finally { importBusy = false; event.target.disabled = false; }
});
async function refreshStray() {
  const note = document.getElementById('stray-files');
  let stray = [];
  try { stray = await window.nicoClient.strayMods(); } catch { /* not important */ }
  note.hidden = !stray.length;
  if (stray.length) {
    const shown = stray.slice(0, 4).join(', ') + (stray.length > 4 ? ` and ${stray.length - 4} more` : '');
    note.textContent = `⚠ Minecraft ignores ${shown}. Mods must be .jar files directly in the mods folder: unpack .zip downloads and move mods out of subfolders.`;
  }
}
// Only the newest search may fill the list, however the answers arrive.
let searchNumber = 0;
async function runSearch() {
  const number = ++searchNumber;
  const query = document.getElementById('mod-query').value.trim();
  const category = document.querySelector('.chip.active')?.dataset.category || '';
  modStatus.textContent = query ? 'Searching Modrinth…' : 'Loading mods…';
  const results = document.getElementById('mod-results');
  // Placeholder cards keep the page steady while Modrinth answers.
  results.replaceChildren(...Array.from({ length: 6 }, () => { const card = document.createElement('div'); card.className = 'mod-card skeleton'; return card; }));
  try {
    const [mods, installed] = await Promise.all([window.nicoClient.searchMods(query, category), window.nicoClient.installedProjects().catch(() => [])]);
    if (number !== searchNumber) return;
    results.replaceChildren();
    const label = document.querySelector('.chip.active')?.textContent || 'Popular';
    modStatus.textContent = mods.length ? (query ? `${mods.length} compatible results` : `${label} mods for Fabric 1.21.11`) : 'No compatible mods found.';
    for (const mod of mods) {
      const card = document.createElement('article'); card.className = 'mod-card';
      const image = document.createElement('img'); image.alt = ''; if (mod.icon) image.src = mod.icon; card.append(image);
      const title = document.createElement('h3'); title.textContent = mod.title; card.append(title);
      const downloads = document.createElement('small'); downloads.textContent = `${Number(mod.downloads).toLocaleString()} downloads`; card.append(downloads);
      const description = document.createElement('p'); description.textContent = mod.description; card.append(description);
      const install = document.createElement('button'); install.className = 'secondary';
      const isInstalled = installed.includes(mod.id);
      install.textContent = isInstalled ? 'Installed' : 'Install';
      install.disabled = isInstalled;
      install.addEventListener('click', async () => {
        install.disabled = true; modProgress.hidden = false; modStatus.textContent = 'Resolving compatible dependencies…';
        try { await window.nicoClient.installMod(mod.id); modStatus.textContent = `${mod.title} installed. Restart Minecraft to load mods.`; install.textContent = 'Installed'; toast(`${mod.title} installed`); await refreshMods(); }
        catch (error) { modStatus.textContent = friendlyError(error, 'Installation failed. Check your connection and try again.'); toast(modStatus.textContent, 'bad'); install.disabled = false; }
        finally { modProgress.hidden = true; }
      });
      card.append(install); results.append(card);
    }
  } catch { if (number === searchNumber) { results.replaceChildren(); modStatus.textContent = 'Could not reach Modrinth. Check your connection and try again.'; } }
}
let searchTimer;
document.getElementById('mod-query').addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(runSearch, 300); });
document.querySelectorAll('.chip').forEach(chip => chip.addEventListener('click', () => {
  document.querySelectorAll('.chip').forEach(other => other.classList.toggle('active', other === chip));
  clearTimeout(searchTimer); void runSearch();
}));
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

// Drag mods onto the window: they are copied into the NicoClient mods folder.
(() => {
  const zone = document.getElementById('drop-zone');
  let depth = 0;
  const hasFiles = event => [...(event.dataTransfer?.types || [])].includes('Files');
  window.addEventListener('dragenter', event => { if (!hasFiles(event)) return; event.preventDefault(); depth++; zone.hidden = false; });
  window.addEventListener('dragover', event => { if (!hasFiles(event)) return; event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; });
  window.addEventListener('dragleave', event => { if (!hasFiles(event)) return; depth = Math.max(0, depth - 1); if (!depth) zone.hidden = true; });
  window.addEventListener('drop', async event => {
    if (!hasFiles(event)) return;
    event.preventDefault(); depth = 0; zone.hidden = true;
    const files = [...event.dataTransfer.files];
    if (!files.length) return;
    try {
      const result = await window.nicoClient.addModFiles(files);
      if (result.added.length) toast(`${result.added.length} mod${result.added.length === 1 ? '' : 's'} installed`);
      if (result.skipped.length) toast(`${result.skipped.length} left out: ${result.skipped.slice(0, 2).map(item => `${item.file} (${item.reason})`).join(', ')}${result.skipped.length > 2 ? ' …' : ''}`, result.added.length ? undefined : 'bad');
      modStatus.textContent = [result.added.length ? `Installed ${result.added.join(', ')}. Restart Minecraft to load them.` : '', ...result.skipped.map(item => `${item.file}: ${item.reason}.`)].filter(Boolean).join(' ');
      showPage('mods');
      await refreshMods();
      document.getElementById('installed-mods').scrollIntoView({ behavior: 'smooth', block: 'center' });
    } catch (error) { toast(friendlyError(error, 'Could not install the dropped files.'), 'bad'); }
  });
})();
