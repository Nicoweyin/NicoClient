# Build status

## Launcher foundation
- Electron + TypeScript project: dark blue/purple interface with Home, Mods, Cosmetics, Accounts, and Settings. Build and automated shell smoke test pass; game launch remains disabled.
- Browser-based Microsoft authorization code + PKCE, Xbox/XSTS and Minecraft token exchanges, Java entitlement check and profile retrieval are implemented using the supplied public client ID. Mocked protocol tests pass; real sign-in, application approval and ownership checks are unverified. Tokens are held only in memory, without refresh or persistence.
- The launcher creates `%APPDATA%\NicoClient` and its `mods`, `config`, `resourcepacks`, `shaderpacks`, and `logs` folders on startup. The NSIS installer is configured to create the same folders for the installing user.
- Modrinth search, dependency-aware installation with SHA-512 and size checks, mod listing/toggling/uninstalling, update checks and progress are implemented. A live Sodium search/download into a temporary test directory and mocked dependency/corruption tests passed. Automatic mod update replacement is not implemented; unknown local mods and incompatibilities require manual review. In-game loading is untested.
- Free cosmetic preference controls persist locally; Fabric-side rendering for capes, wings, particles, HUD and animations is not implemented.
- GitHub `electron-updater` integration targets `Nicoweyin/NicoClient` with manual download and restart confirmation. The corrected-owner unsigned installer, blockmap and `latest.yml` are in `release-corrected/`; the embedded provider and installer metadata were checked. Earlier `release/` artifacts retain the old owner. Installation and updater delivery from a published release have not been tested; see `RELEASE.md` for signing limitations.
- Original diamond-pickaxe icon generated in PNG and ICO formats; existence verified. Visual icon verification, splash screen, real-account authentication verification, game installation/launch, profiles and logging: pending.
- Minecraft 1.21.11 Fabric client and modules: pending.

## Environment
- Windows; Node.js 24.15.0, npm 11.12.1 via `npm.cmd`, OpenJDK 25.0.3.
- PowerShell's execution policy blocks `npm.ps1`; `npm.cmd` works. Electron and TypeScript are installed locally in the project.
- An interrupted install left Electron's executable missing despite npm reporting dependencies up to date; `npm.cmd rebuild electron` repaired it. The smoke test uses project-local application data. Minecraft is not playable yet, and Fabric mod loading has not been tested.
- Git for Windows 2.56.0 was installed with the owner's permission. A local `main` repository was initialized with origin `https://github.com/Nicoweyin/NicoClient.git`. No publication has been performed.
