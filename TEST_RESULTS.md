# Test results

- Environment inspection: Node.js, npm via `npm.cmd`, and Java detected.
- `node --check ui\renderer.js`: passed. Generated PNG and ICO files exist; visual and packaging checks pending.
- `npm.cmd install --no-audit --no-fund`: completed after an interrupted install; `npm.cmd rebuild electron` restored the missing Electron executable.
- `npm.cmd run build`: passed.
- Electron smoke test (`scripts/smoke-test.cjs`): passed. The launcher window loaded; navigation, disabled Play/enabled account control, sandbox/context-isolation/Node-integration settings, isolated directory creation, and the Open Mods Folder target were checked. The folder-open operation was mocked; the test uses project-local application data.
- `node --test scripts/auth-test.cjs`: passed (3 tests). Mocked services verify PKCE and state handling, protocol exchange payloads, refusal of missing Java entitlement, and reporting a possible application-approval 403. No live Microsoft, Xbox, or Minecraft authentication was performed.
- `node --test scripts/auth-test.cjs scripts/modrinth-test.cjs`: passed (6 tests), including mocked required dependency installation, hash mismatch cleanup, mod toggle collision/path checks, and cosmetic persistence/validation.
- `node scripts/modrinth-live-test.cjs`: passed; searched Modrinth for Sodium and installed a SHA-512 verified JAR into a temporary project-local directory, then removed the test directory. No game was launched.
- `npm.cmd run package:win`: passed without publishing. The unsigned `NicoClient-Setup-0.1.0.exe`, blockmap and `latest.yml` were generated; metadata filename and SHA-512 were checked against the installer. Running the installer and updater against a published release has not been tested.
- Game installation, game launch, Fabric mod loading, and AFK Together compatibility: not implemented or tested.
- Corrected GitHub owner build: TypeScript build, all six mocked authentication/mod/cosmetic tests, JavaScript syntax check, and Electron smoke test passed. The corrected NSIS installer and metadata were built locally with `--publish never` under `release-corrected/`; the embedded update provider is `Nicoweyin/NicoClient`, and the installer filename, size, SHA-512 and blockmap presence match the metadata. No real Microsoft login, game launch, installation of the NSIS package, or update delivery was tested.
