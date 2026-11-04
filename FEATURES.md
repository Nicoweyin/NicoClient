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
28. [ ] Auto sprint
29. [x] Toggle sneak, through Minecraft's own option (option change checked in client test)
30. [ ] Tool protection
31. [x] Toggle sprint, through Minecraft's own option (option change checked in client test)
32. [ ] Screenshot shortcut
33. [ ] Screenshot notification
34. [x] Chat timestamps (client test)
35. [ ] Chat history extension
36. [ ] Waypoint management
37. [x] Copy Coordinates (key press and clipboard checked in client test)
38. [ ] Inventory search
39. [ ] Item pickup notifications
40. [ ] Sound volume shortcuts

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

- Visuals: Trajectories for bow, snowballs, eggs, ender pearls and splash potions with optional impact marker (path draws in client test for a snowball; accuracy not compared with real throws); Hit Color, Block Outline, Clear Water and FOV Changer remain planned.
- HUD: Server Info (client test in single-player), Day Counter, Light Level and Memory Usage (client test).
- Performance: Dynamic FPS is added (loads in client test; lowering the frame rate while unfocused was not measured). Smart Animations, Entity Render Distance and Performance Presets remain planned.
- Utility: Inventory Profiles, Screenshot Manager, Auto Reconnect.
- Cosmetics: Capes, Wings, Particle Cosmetics and HUD or GUI Themes are not implemented; the launcher page that only stored preferences was removed.
