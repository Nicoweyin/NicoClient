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

const signInButton = document.getElementById('sign-in');
const signOutButton = document.getElementById('sign-out');
const accountStatus = document.getElementById('account-status');
signInButton.addEventListener('click', async () => {
  signInButton.disabled = true;
  accountStatus.textContent = 'Complete Microsoft sign-in in your browser…';
  try {
    const result = await window.nicoClient.signIn();
    if (result.ok) {
      accountStatus.textContent = `Minecraft Java account verified: ${result.name}. Game launch is not available yet.`;
      signInButton.hidden = true;
      signOutButton.hidden = false;
    } else {
      accountStatus.textContent = result.message || 'Sign-in failed.';
    }
  } catch {
    accountStatus.textContent = 'Sign-in could not be started. Please try again.';
  } finally {
    signInButton.disabled = false;
  }
});
signOutButton.addEventListener('click', async () => {
  await window.nicoClient.signOut();
  signOutButton.hidden = true;
  signInButton.hidden = false;
  accountStatus.textContent = 'Signed out of NicoClient. Game launch is not available yet.';
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
async function refreshMods() {
  const container = document.getElementById('installed-mods');
  try {
    const mods = await window.nicoClient.listMods();
    container.replaceChildren();
    if (!mods.length) container.textContent = 'No installed mods yet.';
    for (const mod of mods) {
      const row = document.createElement('div'); row.className = 'installed-row';
      const label = document.createElement('span'); label.textContent = `${mod.name} · ${mod.enabled ? 'Enabled' : 'Disabled'}`; row.append(label);
      const actions = document.createElement('div'); actions.className = 'button-row';
      for (const [caption, action] of [[mod.enabled ? 'Disable' : 'Enable', 'toggle'], ['Check update', 'update'], ['Uninstall', 'remove']]) {
        const button = document.createElement('button'); button.className = 'secondary'; button.textContent = caption;
        button.addEventListener('click', async () => {
          if (action === 'remove' && !confirm(`Uninstall ${mod.name}? Other mods may depend on it.`)) return;
          button.disabled = true;
          try {
            if (action === 'update') modStatus.textContent = `${mod.name}: ${await window.nicoClient.checkModUpdate(mod.name)}`;
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
        catch { modStatus.textContent = 'Installation failed. Check connectivity, compatibility, required dependencies and filename conflicts.'; }
        finally { install.disabled = false; modProgress.hidden = true; }
      });
      card.append(install); results.append(card);
    }
  } catch { modStatus.textContent = 'Could not reach Modrinth. Check your connection and try again.'; }
});
void refreshMods();

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
