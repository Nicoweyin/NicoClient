# NicoClient by Nicoweyin

An independent Windows launcher and Fabric mod for Minecraft Java 1.21.11, in early development. This project is not affiliated with Mojang or Microsoft.

Copyright © 2026 Nicoweyin. All rights reserved. Original NicoClient code is publicly viewable but requires permission to copy, modify, redistribute, or use commercially. See [COPYRIGHT.md](COPYRIGHT.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## How playing works

NicoClient does not sign in to Microsoft or start the game itself. PLAY adds a **NicoClient Fabric 1.21.11** profile to the official Minecraft Launcher, installs Fabric API and the NicoClient mod into `%APPDATA%\NicoClient`, and opens the official launcher. Choose that profile there and press Play; the official launcher handles your account, the game download, Java and memory. Existing profiles and accounts stay untouched. The official launcher must be installed and started at least once, and it can overwrite a newly added profile if it was open during setup; in that case close it and press PLAY again.

## Development

Requires Node.js, npm, and Java 21 or later. Build `fabric-mod/` with Gradle before starting or packaging so `fabric-mod/build/libs/nicoclient-fabric-0.1.0.jar` exists. Install dependencies with `npm.cmd install`, then run `npm.cmd start`. Run `npm.cmd run package:win` to build an NSIS installer. Packaging may download additional build tools.

Run `npm.cmd test` for the launcher tests and `gradle runClientGameTest` in `fabric-mod/` to run the mod in a real Minecraft client with a temporary world; that test uses a development profile, not your account or game data, and opens a game window.

## Sharing with friends

Run `gradle build` in `fabric-mod/`, then `npm.cmd run package:win`, and send `release/NicoClient-Setup-<version>.exe`. It installs for the current Windows user and adds Start menu and desktop shortcuts. Friends need the official Minecraft Launcher with their own Minecraft Java account. The installer is unsigned, so Windows SmartScreen shows a warning: choose **More info**, then **Run anyway**.

## In-game controls

Right Shift opens the menu. Left click a module to switch it, right click to configure it, including a toggle key. **Settings** holds the accent color, HUD size, background and text color. **Edit HUD** moves HUD modules; right click one there to configure it.

## Mods

The Mods page searches Modrinth for Fabric 1.21.11, installs compatible versions and required dependencies with SHA-512 verification, and manages files in the isolated mods folder. Update checks identify newer versions and offer a one-click update that replaces the old file, restoring it if the update fails. Mod compatibility and dependencies cannot be guaranteed for unrecognized local files.

## Releases

An unsigned Windows installer and `latest.yml` can be built with `npm.cmd run package:win` and are written to `release`; no upload occurs. Installed builds download updates from GitHub Releases on their own and install them on restart. That worked in a local test, but no release is published yet, so nothing arrives until one is. See `RELEASE.md` for signing limitations and the approval-gated release process.

See `BUILD_STATUS.md`, `FEATURES.md`, `TEST_RESULTS.md`, and `RESUME.md` for progress.
