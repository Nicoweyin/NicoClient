# Third-party notices

NicoClient includes or uses software owned by its respective authors and made available under separate licenses. Those licenses and their copyright and permission notices continue to apply; the NicoClient copyright notice does not replace them. Versions below reflect `package-lock.json`.

## Runtime components

| Component | Version | License |
| --- | --- | --- |
| Electron | 35.7.5 | MIT |
| electron-updater | 6.8.11 | MIT |
| builder-util-runtime | 9.7.0 | MIT |
| debug | 4.4.3 | MIT |
| ms | 2.1.3 | MIT |
| sax | 1.6.1 | BlueOak-1.0.0 |
| fs-extra | 10.1.0 | MIT |
| graceful-fs | 4.2.11 | ISC |
| jsonfile | 6.2.1 | MIT |
| universalify | 2.0.1 | MIT |
| js-yaml | 4.3.2 | MIT |
| argparse | 2.0.1 | Python-2.0 |
| lazy-val | 1.0.5 | MIT (declared in package metadata) |
| lodash.escaperegexp | 4.1.2 | MIT |
| lodash.isequal | 4.5.0 | MIT |
| semver | 7.7.4 | ISC |
| tiny-typed-emitter | 2.1.0 | MIT |

The installed npm packages contain their respective license and copyright notices. In packaged builds, these files are retained alongside the packages in `resources/app.asar/node_modules`; Electron's own license and its bundled Chromium and other component notices are supplied as `LICENSE.electron.txt` and `LICENSES.chromium.html` in the application directory. Electron incorporates additional third-party software under the licenses described in those files.

The published `lazy-val` 1.0.5 npm package declares MIT and names Vladimir Krivosheev as author, but does not include a separate license file; its [upstream repository](https://github.com/develar/lazy-val) likewise did not contain one when checked. Preserve its package metadata and obtain any missing upstream notice as needed for distribution.

## Development tools

Direct development dependencies include electron-builder 26.18.0 (MIT), TypeScript 5.9.3 (Apache-2.0), and @types/node 22.20.5 (MIT). Electron is listed above. These tools have their own transitive dependencies; consult `package-lock.json` and each installed package's license and notice files for their applicable terms.

The separate Fabric mod build uses Fabric Loader, Fabric API, Fabric Loom and Gradle, each under its own terms; consult their distributed license and notice files. It uses official Minecraft mappings for development, subject to Mojang's mapping terms, and does not redistribute the complete unmodified mappings. Minecraft itself and Fabric API are separate dependencies, not bundled NicoClient code.

## Fonts

The Fabric mod bundles the Inter typeface (`Inter-Regular.ttf`, version 4.1), Copyright (c) 2016 The Inter Project Authors (https://github.com/rsms/inter), licensed under the SIL Open Font License, Version 1.1. The license is supplied with the font as `assets/nicoclient/font/inter-license.txt` in the mod.

## Other third-party rights

Minecraft, Mojang, Microsoft, Fabric, Modrinth, Electron, and their names, logos, software, and other assets belong to their respective owners. NicoClient is independent and is not an official Minecraft product or affiliated with Mojang or Microsoft. Mods and other content obtained through third-party services are subject to their respective owners' terms; they are not covered by NicoClient's copyright notice.
