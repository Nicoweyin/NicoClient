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
- Earlier status: game installation and launch were not implemented at that point; see the latest results below.
- Corrected GitHub owner build: TypeScript build, all six mocked authentication/mod/cosmetic tests, JavaScript syntax check, and Electron smoke test passed. The corrected NSIS installer and metadata were built locally with `--publish never` under `release-corrected/`; the embedded update provider is `Nicoweyin/NicoClient`, and the installer filename, size, SHA-512 and blockmap presence match the metadata. No real Microsoft login, game launch, installation of the NSIS package, or update delivery was tested.
- Fabric toolchain: the Fabric example project for Minecraft 1.21.11 built with Gradle 9.8.2, Loom 1.19.3 and OpenJDK 25. The independent `fabric-mod/` project also completed `gradle build`, compiling and remapping its FPS HUD. No Fabric automated tests or in-game tests ran; rendering, configuration persistence and key behavior remain unverified at runtime. Compilation does not establish in-game functionality.
- Subsequent Fabric milestones: Gradle builds passed after adding the initial module screen and Keystrokes, after the HUD editor, after Zoom, and after Low Fire and Reduced Particles. A compilation error identifying the 1.21.11 `ParticleStatus` package was fixed before the last successful builds. The build has no Fabric automated tests; no client, world, mixin-application, rendering, drag or configuration tests were run in game.
- Fullbright/category/settings milestone and Trajectories milestone: Gradle `build` passed after each group, including a rebuild with egg variants. The remapped jar's entries include the Fabric metadata, client mixins and trajectory classes; inspection of `LightTextureMixin` confirmed its target method was remapped. The build reports `test NO-SOURCE`. No Minecraft runtime or in-world test was performed. `%APPDATA%\NicoClient\mods` was absent on inspection; no mod was installed.
- Launcher install/launch milestone: `npm.cmd run build` passed; nine mocked auth, installer, argument construction, expiry and Modrinth/cosmetic tests passed. The installer test covers checksums, corrupt-download cleanup, repeat install, duplicate assets and refusal to overwrite modified files. The Electron smoke test passed, including PLAY's signed-out response; a GPU shutdown warning appeared after the pass.
- A live official Minecraft/Fabric metadata and file installation downloaded and SHA-1/size verified 4,660 files into a temporary test directory, and a second pass rechecked them. A separate live Fabric API installation obtained and SHA-512 verified `fabric-api-0.141.6+1.21.11.jar` in a temporary directory. These component tests did not sign in or launch Minecraft.
- Local unsigned NSIS packaging with `--publish never` succeeded to a temporary directory, and its unpacked resources included the NicoClient Fabric JAR and copyright notices; updater metadata still targets `Nicoweyin/NicoClient`. No live Microsoft sign-in, combined PLAY run, actual Minecraft startup, gameplay, Fabric mod loading, or updater delivery was tested.
- First real-launch preparation: Fabric and TypeScript builds passed; Windows and Java are x64, Java 21.0.11 and Java 25.0.3 were detected, and the previously downloaded LWJGL native JAR contains `windows/x64` libraries. The Fabric API live component test passed. A bootstrap was added to hand the Minecraft token to Java through stdin instead of its command line; a mock Fabric entry-point test verified the handoff without printing the token. Real authentication and game startup still require interactive verification.
- Real sign-in attempt reported by the user: Microsoft sign-in succeeded, but Minecraft Services returned HTTP 403 from `POST /authentication/login_with_xbox`. The later ownership and profile requests were not reached. The prior diagnostic did not retain the response body; approval and other causes remain unconfirmed. No real game launch was performed.
- Authentication diagnostics update: TypeScript build and all 11 mocked tests passed, including explicit endpoint/order checks, the Minecraft exchange 403 message, a distinct ownership-service failure, and ensuring service-provided secret-like text is excluded from messages and logs. Electron smoke test passed; Chromium emitted GPU shutdown warnings afterward. No new real-account authentication attempt was made.
- Windows branding: TypeScript build, 11 automated tests, development Electron smoke test (app name, window title, app ID and icon load), and an unsigned local NSIS build passed. A temporary per-user install confirmed `NicoClient.exe` file/product descriptions, a responsive `NicoClient` window, the Start Menu shortcut target and `dev.nicoweyin.nicoclient` shortcut AppUserModelID. The executable icon's center pixel matched the source PNG, and no desktop shortcut was created. The temporary install and shortcut were removed; `%APPDATA%\NicoClient` was retained. Taskbar labels and icon appearance were not visually inspected.
- Account switching: no NicoClient credential files or running launcher sessions were found during the reset. TypeScript build and 11 mocked tests passed, including the OAuth account-selection parameter. Electron smoke tests with a mocked account confirmed both Remove account and Sign out clear the launcher session and prevent PLAY until sign-in. No browser-managed sessions or Minecraft data were removed; real account switching remains to be tested interactively.
- OAuth callback hardening: TypeScript build and 13 mocked tests passed, including fresh state/PKCE challenges, account-picker parameter, invalid or repeated callback handling, Minecraft exchange 403 messaging, and separate ownership failures. The Electron smoke test passed with mocked account switching, confirmation, removal and sign-out. No new real Microsoft or Minecraft sign-in was performed.
- Minecraft exchange diagnostic: the documented XSTS relying party and Minecraft request shape were checked; mocked tests verify the complete XSTS request and Minecraft endpoint, headers and body. TypeScript build, 14 mocked tests and the Electron smoke test passed, including allowlisted service-detail reporting and exclusion of arbitrary secret-like error text. The original 403 body and actual issued token audience remain unavailable; no new real sign-in was made.

## 2026-11-04
- `npm.cmd test`: 24 tests passed, including download retries, damaged file repair, Java runtime checksums and path safety, NicoClient JAR replacement, Fabric API updates not blocking PLAY, mod updates and rollback, and memory limits. Service responses are mocked.
- Electron smoke test: passed.
- Live Java runtime download into a temporary folder: 402 files; `java -version` reported 21.0.7. The folder was removed afterward.
- `gradle runClientGameTest` (Minecraft 1.21.11, temporary world, development profile): passed with no mixin errors. Screenshots were reviewed by eye for all HUD modules, the menu, the HUD editor, Fullbright at night, Zoom, Low Fire while burning and the Trajectories path for a snowball.
- Not tested: the launcher's authenticated PLAY flow end-to-end (blocked by the Minecraft Services 403), the official profile setup against a real `.minecraft` folder, multiplayer, real mouse dragging, real click counting and trajectory accuracy.

## 2026-11-04: official launcher as the only way to play
- NicoClient's own sign-in and game launch were removed, along with their tests; earlier results above for those parts no longer apply.
- `npm.cmd test`, the Electron smoke test and `gradle build` were rerun after the change. The tests use a temporary `.minecraft` folder and mocked services; the smoke test mocks opening the official launcher.
- Not tested: starting the game from the official launcher with the NicoClient profile.

## 2026-11-04: new in-game look
- The owner reported that playing through the official launcher works; this was not observed directly.
- `gradle runClientGameTest`: passed with the new menu. It now also checks with real input that Right Shift opens and closes the menu, a mouse click flips a switch, typing filters the list, and dragging moves a HUD module. Screenshots of the menu, search, settings, HUD and HUD editor were reviewed by eye.
- FPS in the test window stayed around the same level as before with all HUD modules on (about 70), in one short run; this is not a real benchmark.
- One of four runs failed before the first screenshot with "Network synchronizer in invalid state" from Fabric's game test tool, with no NicoClient code in the stack trace; the other three passed, including two reruns of the same code. The cause was not determined.
- Not tested: smooth scrolling and clicks on categories, favorites and settings controls, other GUI scales, and animations by eye (screenshots show single frames).

## 2026-11-04: Inter font
- Inter 4.1 (`Inter-Regular.ttf`) came from the official release at https://github.com/rsms/inter/releases/tag/v4.1 and is bundled under the SIL Open Font License.
- The search box is now custom: it supports typing and backspace, but not a movable cursor, text selection or pasting.

## 2026-11-04: right-click settings, new modules, installer 0.2.0
- `gradle runClientGameTest`: passed with real input for right click on a module row, Esc back to the menu and a module toggle key. Screenshots were reviewed by eye for the settings screens, Clear Weather in rain, Custom Time, Custom Crosshair, accent color, HUD size 125%, Held Item, Potion Effects, Server Info, Day Counter, Light Level, Memory Usage and Chat Timestamps. Toggle Sprint and Toggle Sneak change and restore Minecraft's own options.
- The first run failed its hotkey check because keys were only polled once per tick, which could also miss very short presses. Hotkeys now use the key press event.
- No Fog, No Vignette, No Hurt Camera and Dynamic FPS load without mixin errors, but their effects were not compared. Copy Coordinates, choosing a key in the menu, most individual settings, and HUD sizes other than 100% and 125% were not exercised.
- `npm.cmd test`: 11 tests passed. `NicoClient-Setup-0.2.0.exe` was installed silently on this PC: the app window opened, the bundled mod matched the build, and the desktop and Start menu shortcuts existed. Uninstalling removed the app and desktop shortcut and left `%APPDATA%\NicoClient\mods` and `launcher_profiles.json` unchanged.
- Not tested: the installer on another PC or Windows user, PLAY from the installed app, and SmartScreen behavior.

## 2026-11-04: version 0.3.0
- Found by this round's review and tests, and fixed: typed search text was invisible at normal window sizes, Inter was unreadable at GUI scale 1 (it now uses the Minecraft font there), and a status refresh overwrote the message after PLAY.
- `gradle runClientGameTest` now also checks with real input: clicking a category and a favorite star, choosing a toggle key, using Copy Coordinates, choosing a setting, and that the typed search text is displayed. Scrolling calls the screen directly. Screenshots at GUI scales 1, 2 and 3 and of No Fog were reviewed by eye.
- `npm.cmd test`: 16 tests passed, including launcher detection, profile state, profile icon and last-used date, the locked JAR message, and installed project lookup. The smoke test covers PLAY with a launcher that never ran and with no launcher; it uses mocked services and a temporary folder. A locked JAR is simulated with a read-only file.
- Not tested: the profile icon and last-used date in the real official launcher, the installer on another PC, a real locked JAR, PLAY from the installed app, the Microsoft Store and older launcher paths on a real PC other than this one, No Hurt Camera, No Vignette, and Dynamic FPS's effect.

## 2026-11-04: version 0.3.1
- The Cosmetics page and its code and test were removed. `npm.cmd test` and the smoke test passed afterward, and the installer was installed, started and uninstalled on this PC.

## 2026-11-04: version 0.3.2, automatic updates
- Real update test on this PC with a local server instead of GitHub: the installed 0.3.2 downloaded a 9.9.9 test build on its own, showed the banner on Home, and after clicking "Restart and update" was replaced by 9.9.9 and restarted. `latest.yml`, the blockmaps and the installer were served by a local server, and the test version was removed afterward.
- Not tested: downloading from a real GitHub release, an update on another PC, an update that installs on the next start without the button, and the check every four hours.

## 2026-11-04: first GitHub releases
- Real update test on this PC: an installed, locally built 0.3.2 found release 0.3.5 on GitHub, downloaded it on its own and, after clicking "Restart and update", ran as 0.3.5. The test installation was removed afterward; mods and launcher profiles were unchanged.
- GitHub Actions needs Java 25 for the mod build (it failed with Java 21; the exact reason was not read). Builds also hung while Gradle's output went through a pipe, which did not reproduce locally, so the cause is unconfirmed; writing to a file without a daemon took about 4 minutes. A build now stops only after 15 minutes without new output and reports the last lines.
- 0.3.4 was published as two broken releases because electron-builder uploaded files concurrently; 0.3.5 publishes with one command.
- Not tested: an update on another PC, an update that installs on quit without the button, and the check every four hours.

## 2026-11-05: five new modules
- `gradle runClientGameTest` passed in a real Minecraft 1.21.11 client, including the new checks: Item Pickups lists items given with /give (screenshot reviewed), Auto Sprint sprints while W is held, Tool Protection refuses to start mining with a pickaxe at 2 durability and allows it when off, Entity Distance hides a pig 25 blocks away at a 16-block range but not one 3 blocks away or your own player, and Death Position writes the position into chat and copies it (screenshot reviewed).
- Not tested: picking items up from the ground, Tool Protection while holding the mouse button, the FPS gain of Entity Distance, and multiplayer servers.
- Later the same day: Block Outline (red and thick compared with vanilla in screenshots), Chat History (150 messages kept with it on, vanilla's 100 with it off) and the Death Position way back (shown with arrow and distance after respawning, gone after walking to the death point) passed the client test.

## 2026-11-05: ten more modules
- `gradle runClientGameTest` passed with checks for Reach Display (2.65 m on a pig), Combo Counter (2 hits), Arrow Counter (20 arrows), Food & Saturation, Hit Color (overlay pixels green, vanilla red restored when off, green flash seen in a screenshot), Static FOV (flying FOV change removed), Freelook (orbit camera, player keeps facing ahead, first person restored), Quiet Mode (sounds at 25%, volume options untouched), Clear Water (fog end from about a quarter of the view distance to the full 128 blocks, compared in screenshots) and Auto Reconnect (button counts down from 10 s on a disconnect screen).
- A bug was found and fixed during testing: Clear Water first read a fog value that Minecraft only fills in later, so Full had no effect and More would have hidden everything under water.
- Not tested: Freelook with the real mouse (the test window does not capture it), a real reconnect to a server, Quiet Mode by ear, and combat on a multiplayer server.

## 2026-11-05: waypoints and four more modules (0.5.0)
- `gradle runClientGameTest` passed: a waypoint saved by typing a name and Enter, the looked-at chest marked with the Mark block button and the Add key, Delete removing a row by a real click, the chest marker projected within a few pixels of the crosshair, the colored box drawn around the chest (screenshot), Target HUD following a pig after a hit, Armor Warning naming a helmet at 3%, No Pumpkin compared in screenshots and Tab Ping drawn in a player list.
- Not tested: waypoints on a multiplayer server, the Armor Warning sound by ear, and Tab Ping with real server pings.

## 2026-11-06: cosmetics
- `gradle runClientGameTest` passed: the Cosmetics button in the menu opens the cosmetics screen, real clicks pick a cape, wings and halo, and screenshots of the preview and of third-person front and back views show Angel wings with a gold halo, Neon wings with the Nico cape, and Dragon wings with the Flame cape.
- Not tested: an elytra together with a cape, and cosmetics on a multiplayer server (other players cannot see them).
- More cosmetics: the cosmetics screen now uses arrow pickers (next and previous arrows clicked in the client test, including wrapping round), and screenshots show Aurora, Sakura, Rainbow, Gold and Lava capes with Butterfly, Phoenix, Crystal and Bat wings. The trail spawned particles while walking, but they were not visible in the underwater screenshot, so the look of trails is unchecked.

## 2026-11-06: launcher Cosmetics tab
- The real launcher was started with a debug port: the Cosmetics tab showed 31 cards, clicks selected Sakura, Phoenix, Gold and Hearts, and the settings file held the clicked values; screenshots reviewed. The owner's own settings file was backed up and restored afterward.
- A bug was found and fixed: two quick clicks could overwrite each other in the settings file. Writes now run one after another; a unit test covers four writes at once.
- `gradle runClientGameTest`: a cape written into the settings file from outside reached the running game within two seconds.
- Not tested: the installed launcher build and a game started from the official launcher while switching cosmetics.

## 2026-11-06: launcher redesign and Server-safe mode
- `gradle runClientGameTest` passed: with a public server simulated, Freelook stays switched on but the Freelook key does nothing, and the menu row says "Paused here by Server-safe mode" (screenshot reviewed); Freelook works again with Server-safe mode off and in singleplayer. A module switched on in the settings file from outside reached the running game. Menu screenshots show the new monogram tiles, a hotkey chip and the profile card with face and name.
- The launcher was started with a debug port at 1280x820 and 960x720: all four pages showed no script errors and no sideways scrolling. Real clicks switched Fullbright on and off, chose the pink accent (the whole launcher turned pink), switched Server-safe mode, set the HUD size to 125%, and the Performance filter on the Mods page listed Sodium, Iris, Entity Culling and FerriteCore. The owner's settings file was backed up and restored byte for byte.
- `npm test`: 18 tests pass, including a new one that only listed settings can be written and that other lines stay.
- Not tested: Server-safe mode on a real public server, and the installed launcher build.
- Later the same day: corner messages, a loading state on PLAY, switches for installed mods, placeholder cards while Modrinth loads, a What's new card and page keys. In the real launcher a quick module click showed "Keystrokes on", Ctrl+2 opened Mods, and the Entity Culling switch disabled the mod and enabled it again with the mods folder back as before; screenshots reviewed.
