# NicoClient by Nicoweyin

An independent Windows Minecraft Java launcher, in early development. This project is not affiliated with Mojang or Microsoft.

Copyright © 2026 Nicoweyin. All rights reserved. Original NicoClient code is publicly viewable but requires permission to copy, modify, redistribute, or use commercially. See [COPYRIGHT.md](COPYRIGHT.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Development

Requires Node.js and npm, plus Java 21 or later to build the mod. Build `fabric-mod/` with Gradle before starting or packaging so `fabric-mod/build/libs/nicoclient-fabric-0.1.0.jar` exists. Install dependencies with `npm.cmd install`, then run `npm.cmd start`. Run `npm.cmd run package:win` to build an NSIS installer once the application is verified. Packaging may download additional build tools.

The Accounts page implements browser-based Microsoft sign-in and Minecraft Java entitlement/profile checks, but has not yet been tested with a real account. Sign-in is session-only; no tokens are saved by the launcher. PLAY installs official Minecraft 1.21.11 files and Fabric Loader into `%APPDATA%\NicoClient`, installs compatible Fabric API and the built NicoClient mod, rechecks ownership, and starts Java. The full sign-in and launch flow is **not yet verified in-game**. PLAY installs Mojang's official Java runtime automatically and only falls back to Java 21+ from `JAVA_HOME` or PATH; current installation supports Windows x64. Memory for the game is set in Settings. Mojang game files are downloaded from their official services, not bundled. A separate launch bootstrap receives the Minecraft access token through a private standard-input pipe instead of the Java command line; displayed game output is redacted. The launcher creates isolated `mods`, `config`, `resourcepacks`, `shaderpacks`, and `logs` directories, and Settings can open the mods folder.

For the configured Entra application, register `http://localhost/nicoclient-auth` under **Mobile and desktop applications**. The actual loopback port is chosen dynamically. Minecraft Services access for new app IDs also requires application review; see `RESUME.md` before testing sign-in.

The Mods page searches Modrinth for Fabric 1.21.11, installs compatible versions and required dependencies with SHA-512 verification, and manages files in the isolated mods folder. Update checks identify newer versions and offer a one-click update that replaces the old file, restoring it if the update fails. Mod compatibility and dependencies cannot be guaranteed for unrecognized local files. Cosmetics preferences are stored locally, but in-game rendering is not implemented.

An unsigned Windows installer and `latest.yml` can be built with `npm.cmd run package:win` and are written to `release`; no upload occurs. The GitHub updater is configured but has not been tested against a published release. See `RELEASE.md` for signing limitations and the approval-gated release process.

Run `npm.cmd test` for the launcher tests and `gradle runClientGameTest` in `fabric-mod/` to run the mod in a real Minecraft client with a temporary world; that test uses a development profile, not your account or game data, and opens a game window.

See `BUILD_STATUS.md`, `FEATURES.md`, `TEST_RESULTS.md`, and `RESUME.md` for progress.
