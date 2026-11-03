'use strict';
// Explicit live integration test: all downloaded files go in an expendable project-local directory.
const fs = require('node:fs/promises');
const path = require('node:path');
const { searchMods, installMod, listMods } = require('../dist/modrinth.js');

(async () => {
  const base = path.join(__dirname, '..', '.smoke-test-data');
  await fs.mkdir(base, { recursive: true });
  const dir = await fs.mkdtemp(path.join(base, 'modrinth-live-'));
  try {
    const results = await searchMods('sodium');
    const sodium = results.find(item => item.id === 'AANobbMI');
    if (!sodium) throw new Error('Compatible Sodium result not found');
    await installMod(dir, sodium.id, () => {});
    const installed = await listMods(dir);
    if (!installed.length) throw new Error('No mod installed');
    console.log(`PASS: live Modrinth search and verified install (${installed.length} JARs)`);
  } finally { await fs.rm(dir, { recursive: true, force: true }); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
