'use strict';

// Run with Electron after building. Keep the smoke test's application data local.
const { app, BrowserWindow, nativeImage, shell } = require('electron');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const childProcess = require('node:child_process');
const { EventEmitter } = require('node:events');

const project = path.join(__dirname, '..');
const testData = path.join(project, '.smoke-test-data');
const gameDirectory = path.join(testData, 'NicoClient');
const minecraft = path.join(testData, '.minecraft');
fs.rmSync(gameDirectory, { recursive: true, force: true });
fs.rmSync(minecraft, { recursive: true, force: true });
fs.mkdirSync(testData, { recursive: true });
app.setPath('appData', testData);
Object.defineProperty(app, 'getAppPath', { value: () => project });

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

// No real services: only the Fabric profile and an empty mod lookup are needed.
const { createHash } = require('node:crypto');
const fabricApi = Buffer.from('fabric api fixture');
const realFetch = global.fetch;
global.fetch = async (input, init) => {
  const url = new URL(String(input));
  if (url.hostname === 'meta.fabricmc.net') return Response.json({ id: 'fabric-loader-0.20.1-1.21.11', inheritsFrom: '1.21.11', mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient' });
  if (url.hostname === 'cdn.modrinth.com') return new Response(fabricApi);
  if (url.pathname.includes('/version_file/')) return new Response('', { status: 404 });
  if (url.pathname.includes('/project/P7dR8mSH/version')) return Response.json([{ id: 'fapi', project_id: 'P7dR8mSH', version_type: 'release', game_versions: ['1.21.11'], loaders: ['fabric'], dependencies: [], files: [{ filename: 'fabric-api-test.jar', url: 'https://cdn.modrinth.com/fapi', primary: true, size: fabricApi.length, hashes: { sha512: createHash('sha512').update(fabricApi).digest('hex') } }] }]);
  return realFetch(input, init);
};
require('../dist/main.js');

const timeout = setTimeout(() => {
  console.error('Smoke test timed out');
  app.exit(1);
}, 20000);
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

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
    const run = code => window.webContents.executeJavaScript(code);
    const results = await run(`(() => {
      const activePage = () => document.querySelector('.page.active')?.id;
      const pages = [activePage()];
      for (const page of ['mods', 'cosmetics', 'settings', 'home']) { document.querySelector('[data-page="' + page + '"]').click(); pages.push(activePage()); }
      return { pages, signInRemoved: !document.getElementById('sign-in') && !document.querySelector('[data-page="accounts"]') };
    })()`);
    assert.deepEqual(results, { pages: ['home', 'mods', 'cosmetics', 'settings', 'home'], signInRemoved: true });
    assert.equal(window.webContents.getTitle(), 'NicoClient');
    assert.equal(app.getName(), 'NicoClient');
    if (process.platform === 'win32') assert.equal(appUserModelId, 'dev.nicoweyin.nicoclient');
    const icon = nativeImage.createFromPath(path.join(project, 'assets', process.platform === 'win32' ? 'icon.ico' : 'icon.png'));
    assert.equal(icon.isEmpty(), false, 'application icon can be loaded');
    assert.equal(window.webContents.getLastWebPreferences().sandbox, true);
    assert.equal(window.webContents.getLastWebPreferences().contextIsolation, true);
    assert.equal(window.webContents.getLastWebPreferences().nodeIntegration, false);

    // PLAY explains what to do when the official launcher has never been started.
    await run("document.getElementById('play').click()");
    await wait(300);
    assert.match(await run("document.getElementById('game-status').textContent"), /has not been set up yet/);
    assert.equal(officialLauncherArgs, undefined);
    assert.equal(await run("document.getElementById('play').disabled"), false);

    // PLAY sets up the profile and opens the official launcher.
    fs.mkdirSync(minecraft, { recursive: true });
    fs.writeFileSync(path.join(minecraft, 'launcher_profiles.json'), JSON.stringify({ profiles: { keep: { name: 'Mine' } } }));
    await run("document.getElementById('play').click()");
    for (let i = 0; !officialLauncherArgs && i < 100; i++) await wait(50);
    assert.deepEqual(officialLauncherArgs, ['shell:AppsFolder\\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft']);
    await wait(100);
    assert.match(await run("document.getElementById('game-status').textContent"), /Opening the Minecraft Launcher/);
    const profiles = Object.values(JSON.parse(fs.readFileSync(path.join(minecraft, 'launcher_profiles.json'), 'utf8')).profiles).map(profile => profile.name).sort();
    assert.deepEqual(profiles, ['Mine', 'NicoClient Fabric 1.21.11']);
    assert.deepEqual(fs.readdirSync(path.join(gameDirectory, 'mods')).sort(), ['fabric-api-test.jar', 'nicoclient-fabric-0.1.0.jar']);

    await run("document.getElementById('update-check').click()");
    await wait(100);
    assert.match(await run("document.getElementById('update-status').textContent"), /installed release build/i);
    for (const name of ['mods', 'config', 'resourcepacks', 'shaderpacks', 'logs']) {
      assert.ok(fs.statSync(path.join(gameDirectory, name)).isDirectory(), `${name} directory exists`);
    }
    await run("document.getElementById('open-mods').click()");
    for (let i = 0; !openedPath && i < 50; i++) await wait(20);
    assert.equal(openedPath, path.join(gameDirectory, 'mods'));
    console.log('PASS: launcher, navigation, security settings, PLAY via the official launcher, isolated directories, and Open Mods Folder verified');
    clearTimeout(timeout);
    app.exit(0);
  } catch (error) {
    console.error(error);
    clearTimeout(timeout);
    app.exit(1);
  }
});
