# NicoClient by Nicoweyin

An independent Windows Minecraft Java launcher, in early development. This project is not affiliated with Mojang or Microsoft.

## Development

Requires Node.js and npm. After approving and installing dependencies with `npm.cmd install`, run `npm.cmd start`. Run `npm.cmd run package:win` to build an NSIS installer once the application is verified. Packaging may download additional build tools.

The Accounts page implements browser-based Microsoft sign-in and Minecraft Java entitlement/profile checks, but has not yet been tested with a real account. Sign-in is session-only; no tokens are saved. Game installation and launch are not yet implemented. On startup the launcher creates `%APPDATA%\NicoClient` with `mods`, `config`, `resourcepacks`, `shaderpacks`, and `logs` directories. Settings includes an Open Mods Folder button. The installer also creates these directories for the installing user. Future game launches must use this directory as the game directory, outside `.minecraft`.

For the configured Entra application, register `http://localhost/nicoclient-auth` under **Mobile and desktop applications**. The actual loopback port is chosen dynamically. Minecraft Services access for new app IDs also requires application review; see `RESUME.md` before testing sign-in.

The Mods page searches Modrinth for Fabric 1.21.11, installs compatible versions and required dependencies with SHA-512 verification, and manages files in the isolated mods folder. Update checks identify newer versions, but replacement is manual; remove the old version before installing the new one. Mod compatibility and dependencies cannot be guaranteed for unrecognized local files. Cosmetics preferences are stored locally, but in-game rendering is not implemented.

An unsigned Windows installer and `latest.yml` can be built with `npm.cmd run package:win` and are written to `release`; no upload occurs. The GitHub updater is configured but has not been tested against a published release. See `RELEASE.md` for signing limitations and the approval-gated release process.

See `BUILD_STATUS.md`, `FEATURES.md`, `TEST_RESULTS.md`, and `RESUME.md` for progress.
