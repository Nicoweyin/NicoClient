# Feature roadmap

PLAY sets up a NicoClient profile in the official Minecraft Launcher and opens it; the official launcher handles sign-in and starting the game. See `BUILD_STATUS.md` and `TEST_RESULTS.md`.

The separate Fabric project has the HUD modules marked below, Zoom, Low Fire, Fullbright, Reduced Particles, Trajectories, a Right Shift module screen and a draggable HUD editor. "Client test" means it ran in a real Minecraft 1.21.11 client through `gradle runClientGameTest` in a temporary single-player world, with screenshots reviewed by eye. The owner has also played through the official launcher and reported that it works. Multiplayer and long sessions have not been tested systematically. Zoom uses a configurable 2x/4x/8x factor and a rebindable hold key (default C). Fullbright uses configurable ambient-light levels; Trajectories has an optional impact marker. FPS retains its initially unbound separate toggle. Module state, favorites, settings and HUD positions are stored in `config/nicoclient.properties`. Other in-game items below remain planned. See `fabric-mod/LOCAL_TESTING.md` for testing and prediction limitations.

## HUD (15)
1. [x] FPS counter (client test; on by default)
2. [x] CPS counter (displays in client test; real click counting not exercised)
3. [x] Keystrokes (W/A/S/D; client test)
4. [x] Coordinates (client test)
5. [x] Armor HUD (client test)
6. [x] Ping HUD (client test in single-player only)
7. [x] HUD editor for all HUD modules, with layout reset (mouse dragging checked in client test)
8. [x] Clock (client test)
9. [x] Direction indicator (client test)
10. [x] Speed display (client test while standing still only)
11. [x] Biome display (client test)
12. [x] Potion effects display (client test)
13. [x] Held item display (client test)
14. [x] Durability display, as part of Held Item and Armor HUD (client test)
15. [x] Session timer (client test)

## Visuals (12)
16. [x] Zoom (client test)
17. [x] Low fire (client test, compared while burning)
18. [x] No fog (client test, compared on and off)
19. [x] Reduced particles (ordinary particles use Minecraft's minimal setting; loads in client test, effect not compared)
20. [x] Custom crosshair with style, size, gap and color (client test)
21. [x] Fullbright (configurable ambient-light minimum; client test, compared at night)
22. [x] Clear Weather, client-side only (client test, compared in rain)
23. [x] Custom Time, client-side only (client test)
24. [ ] Custom hotbar styling
25. [ ] Custom GUI scale
26. [x] No Hurt Camera (loads in client test; effect not compared)
27. [x] No Vignette (loads in client test; effect not compared)

## Utilities (13)
28. [x] Auto sprint (sprinting while walking forward checked in client test)
29. [x] Toggle sneak, through Minecraft's own option (option change checked in client test)
30. [x] Tool protection, stops mining at a chosen durability left (refusal and release checked in client test)
31. [x] Toggle sprint, through Minecraft's own option (option change checked in client test)
32. [ ] Screenshot shortcut
33. [ ] Screenshot notification
34. [x] Chat timestamps (client test)
35. [x] Chat history extension, 500 to 5000 messages (stored count checked in client test)
36. [x] Waypoint management: save where you stand or mark the looked-at block (colored box), name, recolor, hide and delete in a waypoint screen; markers with distance in the world (client test with real typing and clicks)
37. [x] Copy Coordinates (key press and clipboard checked in client test)
38. [ ] Inventory search
39. [x] Item pickup notifications as a movable HUD module (client test with /give; picking items off the ground not exercised)
40. [x] Sound volume shortcut: Quiet Mode turns all game sounds down to 0 to 50% and can have a toggle key (volume math checked in client test; not listened to)

## Client Settings (10)
41. [x] Rounded, animated module menu with a sidebar and the Inter font (opens and closes with Right Shift in client test)
42. [x] Searchable, smoothly scrolling module list with descriptions (typing and scrolling checked in client test)
43. [x] Module categories and filtering (clicking a category checked in client test)
44. [x] Animated module switches (real mouse click checked in client test)
45. [x] Right click any module to configure it (real right click checked in client test; not every setting was exercised)
46. [x] Persistent configuration (save and reload checked in client test)
47. [x] Per-module toggle keys (choosing and pressing a key checked in client test)
48. [x] Theme accent selection (client test)
49. [x] HUD scale settings (client test at 125%)
50. [ ] Configuration profiles

## Additional requested modules (planned)

- Visuals: Trajectories for bow, snowballs, eggs, ender pearls and splash potions with optional impact marker (path draws in client test for a snowball; accuracy not compared with real throws); Block Outline with color and thickness (client test, compared with the vanilla outline). Hit Color (green hurt flash on a pig checked in client test), Clear Water (fog distances and screenshots compared under water), Static FOV (no FOV change while flying, checked in client test) and Freelook on a hold key, default Left Alt (camera switch and turning checked in client test; the test window does not capture the real mouse, so the mouse path was exercised through the same call Minecraft makes). Some servers do not allow Freelook.
- HUD: Server Info (client test in single-player), Day Counter, Light Level, Memory Usage, Reach Display and Combo Counter (real attacks on a pig in client test), Food & Saturation, and Arrow Counter while holding a bow (client test).
- Performance: Dynamic FPS is added (loads in client test; lowering the frame rate while unfocused was not measured). Entity Distance hides entities beyond 16 to 64 blocks, players only when chosen (which entities are hidden checked in client test; the FPS gain was not measured). Smart Animations and Performance Presets remain planned.
- Utility: Death Position writes where you died into chat, can copy it, and after respawning points the way back with an arrow and the distance until you arrive (client test). Auto Reconnect adds a Reconnect button after a disconnect, with an optional countdown (button and countdown checked in client test; a real reconnect to a server was not tested). Inventory Profiles and Screenshot Manager remain planned.
- Cosmetics (only visible to you): 11 capes (Nebula, Flame, Ocean, Nico, Aurora, Sakura, Rainbow, Ice, Gold, Carbon, Lava) on Minecraft's own cape, 7 wings (Angel, Dragon, Neon, Butterfly, Phoenix, Crystal, Bat) with flapping, 4 halos (Gold, Accent, Rainbow, Ice) and 5 particle trails (Hearts, Sparkles, Flames, Notes, Snow), chosen in a Cosmetics screen with a turning 3D preview (clicks, preview and third-person views checked in client test). Particle cosmetics and sharing cosmetics with other players are not implemented.
- Added in 0.5.0 as well: Target HUD, Armor Warning, Tab Ping (milliseconds in the player list) and No Pumpkin (client test; Tab Ping seen in single player only).
