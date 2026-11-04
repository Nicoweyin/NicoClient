'use strict';
const { mkdtemp, rm, readdir } = require('node:fs/promises');
const path = require('node:path');
const { installMod } = require('../dist/modrinth');

(async () => {
  const dir = await mkdtemp(path.join(process.env.TEMP, 'opencode', 'nicoclient-fabric-api-'));
  try {
    await installMod(dir, 'P7dR8mSH', () => {});
    const files = await readdir(dir);
    if (!files.some(name => /^fabric-api.*\.jar$/i.test(name))) throw new Error('Fabric API jar was not installed.');
    console.log('PASS: compatible Fabric API installed and SHA-512 verified in a temporary directory:', files.join(', '));
  } finally { await rm(dir, { recursive: true, force: true }); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
