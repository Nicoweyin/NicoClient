# Build status

## Launcher
- Electron + TypeScript project with a dark blue/purple interface: Home, Mods and Settings. Build and smoke test pass.
- Home shows whether the official launcher and profile are ready and offers the launcher download when it is missing. The Mods page opens with popular mods and marks installed ones; required mods cannot be removed there.
- PLAY adds the NicoClient profile to the official Minecraft Launcher, installs Fabric API and the NicoClient JAR into `%APPDATA%\NicoClient`, and opens the official launcher, which handles sign-in, game files, Java and memory. NicoClient's own Microsoft sign-in and game launch were removed on 2026-11-04 (last present in commit `4d31924`) after Minecraft Services returned HTTP 403. NicoClient handles no account tokens.
- The owner reported on 2026-11-04 that playing through the official launcher with this profile works.
- The launcher creates `%APPDATA%\NicoClient` and its `mods`, `config`, `resourcepacks`, `shaderpacks` and `logs` folders on startup; the NSIS installer does the same.
- Modrinth search, dependency-aware installation with SHA-512 and size checks, listing, toggling, uninstalling, update checks and one-click updates with rollback are implemented. Unknown local mods and incompatibilities require manual review.
- The Cosmetics page was removed on 2026-11-04 because its settings had no effect in the game (last present in commit `74c58be`).
- Installed builds check GitHub Releases for `Nicoweyin/NicoClient` at start and every four hours, download updates on their own, show them on Home, and install them on restart. An update from a real GitHub release worked on this PC (0.3.2 to 0.3.5). Releases come from GitHub Actions; see `RELEASE.md`. See `RELEASE.md` for signing limitations. Existing installers older than the newest version in `release/` and `release-corrected/` are stale.
- Windows branding uses `NicoClient` for the app name, window title, packaged executable and NSIS shortcut, with AppUserModelID `dev.nicoweyin.nicoclient`.

## Fabric mod
- Client-only project targeting Minecraft 1.21.11, Fabric Loader 0.20.1 and Fabric API 0.141.6+1.21.11 with official Mojang mappings. The remapped JAR is `fabric-mod/build/libs/nicoclient-fabric-0.1.0.jar`.
- The in-game menu, settings screen, HUD and HUD editor use a custom rounded, animated look (`Ui`, `AnimatedScreen`) instead of vanilla buttons. Its text uses the bundled Inter font.
- Every module can be configured by right click, with per-module settings and toggle keys. Client-wide settings cover accent color and HUD appearance. The mod has 38 modules; see `FEATURES.md`.
- `gradle runClientGameTest` runs the mod in a real client with a temporary world. See `FEATURES.md` for what it covers and `fabric-mod/LOCAL_TESTING.md` for manual tests.

## Environment
- Windows; Node.js 24.15.0, npm 11.12.1 via `npm.cmd`, OpenJDK 25.0.3.
- PowerShell's execution policy blocks `npm.ps1`; `npm.cmd` works. Electron and TypeScript are installed locally in the project.
- An interrupted install left Electron's executable missing despite npm reporting dependencies up to date; `npm.cmd rebuild electron` repaired it. The smoke test uses project-local application data.
- Git for Windows 2.56.0 was installed with the owner's permission. A local `main` repository was initialized with origin `https://github.com/Nicoweyin/NicoClient.git`. No publication has been performed.
