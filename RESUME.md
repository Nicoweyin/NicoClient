# Resume

1. Run `npm.cmd test`, then `node_modules\electron\dist\electron.exe scripts\smoke-test.cjs`; the smoke test stores Electron user data in `.smoke-test-data` inside the project.
2. Playing goes through the official Minecraft Launcher. NicoClient's own Microsoft sign-in and game launch were removed on 2026-11-04 because Minecraft Services rejected the sign-in with HTTP 403 and app approval was not pursued. The removed code is in commit `4d31924` if that is ever revisited; it would need app-ID approval at https://aka.ms/mce-reviewappid first.
3. The owner reported that playing through the official launcher works. Multiplayer and long sessions have not been tested systematically. The JAR cannot be replaced while Minecraft is running, so close the game before pressing PLAY after a rebuild.
4. Build `fabric-mod/` with Gradle 9.8.2 and Java 21 or later: `gradle build`. No Gradle wrapper is included yet; on this machine the Gradle executable is under `%USERPROFILE%\.gradle\wrapper\dists\gradle-9.8.2-bin`. `gradle runClientGameTest` runs the mod in a real client with a temporary world. Follow `fabric-mod/LOCAL_TESTING.md` for manual tests.
5. Before distributing, complete code-signing setup and test GitHub updates against an approved published release. See `RELEASE.md`; never publish without asking the owner. Remaining ideas: fuller dependency and conflict handling, cosmetics, profiles and a loading splash.
6. Local `main` has origin `https://github.com/Nicoweyin/NicoClient.git`. Inspect the remote history before a push; do not push or publish without approval. Artifacts in `release/` and `release-corrected/` predate this change and are stale.

To share NicoClient with friends, run `npm.cmd run package:win` after `gradle build` and send `release/NicoClient-Setup-<version>.exe`. The build is unsigned, so Windows SmartScreen warns on other PCs. Automatic updates only work once a release is published on GitHub, which needs the owner's approval.
