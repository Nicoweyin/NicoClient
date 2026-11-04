'use strict';

// Run with Electron after building. Keep the smoke test's application data local.
const { app, BrowserWindow, nativeImage, shell } = require('electron');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const childProcess = require('node:child_process');
const { EventEmitter } = require('node:events');

const testData = path.join(__dirname, '..', '.smoke-test-data');
fs.mkdirSync(testData, { recursive: true });
app.setPath('appData', testData);
let openedPath;
let officialLauncherArgs;
const spawn = childProcess.spawn;
childProcess.spawn = (command, args, options) => {
  if (command !== 'explorer.exe') return spawn(command, args, options);
  officialLauncherArgs = args;
  const process = new EventEmitter();
  process.unref = () => {};
  queueMicrotask(() => process.emit('spawn'));
  return process;
};
let appUserModelId;
const setAppUserModelId = app.setAppUserModelId.bind(app);
app.setAppUserModelId = id => { appUserModelId = id; setAppUserModelId(id); };
shell.openPath = async (target) => { openedPath = target; return ''; };
// Exercise the account UI without opening a browser or using a real credential.
let mockSignIns = 0;
require('../dist/auth.js').signIn = async () => ({ name: `TestPlayer${++mockSignIns}`, id: '0123456789abcdef0123456789abcdef', accessToken: 'test-session', expiresAt: Date.now() + 3600000 });
require('../dist/main.js');

const timeout = setTimeout(() => {
  console.error('Smoke test timed out');
  app.exit(1);
}, 15000);

app.whenReady().then(async () => {
  try {
    const window = BrowserWindow.getAllWindows()[0];
    assert.ok(window, 'launcher window was created');
    if (window.webContents.isLoading()) {
      await new Promise((resolve, reject) => {
        window.webContents.once('did-finish-load', resolve);
        window.webContents.once('did-fail-load', (_event, code, description) => reject(new Error(`${code}: ${description}`)));
      });
    }
    const results = await window.webContents.executeJavaScript(`(() => {
      const activePage = () => document.querySelector('.page.active')?.id;
      const initialPage = activePage();
      const disabledControls = !document.querySelector('.play').disabled && !document.querySelector('#sign-in').disabled;
      document.querySelector('[data-page="mods"]').click();
      const modsPage = activePage();
      document.querySelector('[data-page="cosmetics"]').click();
      const cosmeticsPage = activePage();
      document.querySelector('[data-page="accounts"]').click();
      const accountsPage = activePage();
      document.querySelector('[data-page="settings"]').click();
      const settingsPage = activePage();
      document.querySelector('[data-page="home"]').click();
      return { initialPage, modsPage, cosmeticsPage, accountsPage, settingsPage, homePage: activePage(), disabledControls };
    })()`);
    assert.deepEqual(results, {
      initialPage: 'home', modsPage: 'mods', cosmeticsPage: 'cosmetics', accountsPage: 'accounts', settingsPage: 'settings',
      homePage: 'home', disabledControls: true
    });
    assert.equal(window.webContents.getTitle(), 'NicoClient');
    assert.equal(app.getName(), 'NicoClient');
    if (process.platform === 'win32') assert.equal(appUserModelId, 'dev.nicoweyin.nicoclient');
    const icon = nativeImage.createFromPath(path.join(__dirname, '..', 'assets', process.platform === 'win32' ? 'icon.ico' : 'icon.png'));
    assert.equal(icon.isEmpty(), false, 'application icon can be loaded');
    assert.equal(window.webContents.getLastWebPreferences().sandbox, true);
    assert.equal(window.webContents.getLastWebPreferences().contextIsolation, true);
    assert.equal(window.webContents.getLastWebPreferences().nodeIntegration, false);
    if (process.platform === 'win32') {
      await window.webContents.executeJavaScript("document.getElementById('official-launcher').click()");
      await new Promise(resolve => setTimeout(resolve, 100));
      assert.deepEqual(officialLauncherArgs, ['shell:AppsFolder\\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft']);
      assert.match(await window.webContents.executeJavaScript("document.getElementById('official-launcher-status').textContent"), /Opening Minecraft Launcher/);
    }
    await window.webContents.executeJavaScript("document.getElementById('play').click()");
    await new Promise(resolve => setTimeout(resolve, 100));
    const gameStatus = await window.webContents.executeJavaScript("document.getElementById('game-status').textContent");
    assert.match(gameStatus, /sign in/i);
    assert.equal(await window.webContents.executeJavaScript("document.getElementById('play').disabled"), false);
    await window.webContents.executeJavaScript("document.getElementById('sign-in').click()");
    await new Promise(resolve => setTimeout(resolve, 100));
    assert.equal(await window.webContents.executeJavaScript("document.getElementById('remove-account').hidden"), false);
    await window.webContents.executeJavaScript("document.getElementById('switch-account').click()");
    await new Promise(resolve => setTimeout(resolve, 100));
    assert.match(await window.webContents.executeJavaScript("document.getElementById('account-status').textContent"), /TestPlayer2/);
    await window.webContents.executeJavaScript('window.confirm = () => true; true');
    await window.webContents.executeJavaScript("document.getElementById('remove-account').click()");
    await new Promise(resolve => setTimeout(resolve, 100));
    assert.equal(await window.webContents.executeJavaScript("document.getElementById('sign-in').hidden"), false);
    assert.match(await window.webContents.executeJavaScript("document.getElementById('account-status').textContent"), /removed/i);
    await window.webContents.executeJavaScript("document.getElementById('play').click()");
    await new Promise(resolve => setTimeout(resolve, 100));
    assert.match(await window.webContents.executeJavaScript("document.getElementById('game-status').textContent"), /sign in/i);
    await window.webContents.executeJavaScript("document.getElementById('sign-in').click()");
    await new Promise(resolve => setTimeout(resolve, 100));
    await window.webContents.executeJavaScript("document.getElementById('sign-out').click()");
    await new Promise(resolve => setTimeout(resolve, 100));
    assert.match(await window.webContents.executeJavaScript("document.getElementById('account-status').textContent"), /signed out/i);
    await window.webContents.executeJavaScript("document.getElementById('play').click()");
    await new Promise(resolve => setTimeout(resolve, 100));
    assert.match(await window.webContents.executeJavaScript("document.getElementById('game-status').textContent"), /sign in/i);
    await window.webContents.executeJavaScript("document.getElementById('update-check').click()");
    await new Promise(resolve => setTimeout(resolve, 100));
    const updateStatus = await window.webContents.executeJavaScript("document.getElementById('update-status').textContent");
    assert.match(updateStatus, /installed release build/i);
    const gameDirectory = path.join(testData, 'NicoClient');
    for (const name of ['mods', 'config', 'resourcepacks', 'shaderpacks', 'logs']) {
      assert.ok(fs.statSync(path.join(gameDirectory, name)).isDirectory(), `${name} directory exists`);
    }
    await window.webContents.executeJavaScript("document.getElementById('open-mods').click()");
    for (let i = 0; !openedPath && i < 50; i++) {
      await new Promise(resolve => setTimeout(resolve, 20));
    }
    assert.equal(openedPath, path.join(gameDirectory, 'mods'));
    console.log('PASS: launcher, navigation, security settings, isolated directories, and Open Mods Folder verified');
    clearTimeout(timeout);
    app.exit(0);
  } catch (error) {
    console.error(error);
    clearTimeout(timeout);
    app.exit(1);
  }
});
