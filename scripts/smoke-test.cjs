'use strict';

// Run with Electron after building. Keep the smoke test's application data local.
const { app, BrowserWindow, shell } = require('electron');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const testData = path.join(__dirname, '..', '.smoke-test-data');
fs.mkdirSync(testData, { recursive: true });
app.setPath('appData', testData);
let openedPath;
shell.openPath = async (target) => { openedPath = target; return ''; };
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
      const disabledControls = document.querySelector('.play').disabled && !document.querySelector('#sign-in').disabled;
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
    assert.equal(window.webContents.getLastWebPreferences().sandbox, true);
    assert.equal(window.webContents.getLastWebPreferences().contextIsolation, true);
    assert.equal(window.webContents.getLastWebPreferences().nodeIntegration, false);
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
