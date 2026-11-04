'use strict';

// Run with the official launcher closed. Only adds NicoClient's profile and files.
const path = require('node:path');
const { setupOfficialProfile } = require('../dist/official-profile');

if (process.platform !== 'win32' || !process.env.APPDATA) {
  console.error('Setup failed: this setup requires Windows and APPDATA.');
  process.exitCode = 1;
} else {
  const jar = path.join(__dirname, '..', 'fabric-mod', 'build', 'libs', 'nicoclient-fabric-0.1.0.jar');
  setupOfficialProfile(path.join(process.env.APPDATA, '.minecraft'), path.join(process.env.APPDATA, 'NicoClient'), jar)
    .then(() => console.log('NicoClient Fabric profile prepared; existing profiles and account information were preserved.'))
    .catch(error => { console.error(`Setup failed: ${error.message}`); process.exitCode = 1; });
}
