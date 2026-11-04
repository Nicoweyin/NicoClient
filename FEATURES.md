# Feature roadmap

PLAY sets up a NicoClient profile in the official Minecraft Launcher and opens it; the official launcher handles sign-in and starting the game. See `BUILD_STATUS.md` and `TEST_RESULTS.md`.

The separate Fabric project has the HUD modules marked below, Zoom, Low Fire, Fullbright, Reduced Particles, Trajectories, a Right Shift module screen and a draggable HUD editor. "Client test" means it ran in a real Minecraft 1.21.11 client through `gradle runClientGameTest` in a temporary single-player world, with screenshots reviewed by eye. The owner has also played through the official launcher and reported that it works. Multiplayer and long sessions have not been tested systematically. Zoom uses a configurable 2x/4x/8x factor and a rebindable hold key (default C). Fullbright uses configurable ambient-light levels; Trajectories has an optional impact marker. FPS retains its initially unbound separate toggle. Module state, favorites, settings and HUD positions are stored in `config/nicoclient.properties`. Other in-game items below remain planned. The launcher cosmetic controls do not render in Fabric. See `fabric-mod/LOCAL_TESTING.md` for testing and prediction limitations.

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
12. [ ] Potion effects display
13. [ ] Held item display
14. [ ] Durability display
15. [x] Session timer (client test)

## Visuals (12)
16. [x] Zoom (client test)
17. [x] Low fire (client test, compared while burning)
18. [ ] No fog
19. [x] Reduced particles (ordinary particles use Minecraft's minimal setting; loads in client test, effect not compared)
20. [ ] Custom crosshair
21. [x] Fullbright (configurable ambient-light minimum; client test, compared at night)
22. [ ] Weather display controls
23. [ ] Time display controls
24. [ ] Custom hotbar styling
25. [ ] Custom GUI scale
26. [ ] Damage tint controls
27. [ ] Screen vignette controls

## Utilities (13)
28. [ ] Auto sprint
29. [ ] Toggle sneak
30. [ ] Tool protection
31. [ ] Toggle sprint
32. [ ] Screenshot shortcut
33. [ ] Screenshot notification
34. [ ] Chat timestamps
35. [ ] Chat history extension
36. [ ] Waypoint management
37. [ ] Coordinate copy shortcut
38. [ ] Inventory search
39. [ ] Item pickup notifications
40. [ ] Sound volume shortcuts

## Client Settings (10)
41. [x] Rounded, animated module menu with a sidebar and the Inter font (opens and closes with Right Shift in client test)
42. [x] Searchable, smoothly scrolling module list (typing checked in client test; scrolling not exercised)
43. [x] Module categories and filtering (displays in client test; clicking a category not exercised)
44. [x] Animated module switches (real mouse click checked in client test)
45. [ ] Module-specific settings (zoom, Fullbright and trajectory marker settings compiled; broader settings pending)
46. [x] Persistent configuration (save and reload checked in client test)
47. [ ] Keybind customization
48. [ ] Theme accent selection
49. [ ] HUD scale settings
50. [ ] Configuration profiles

## Additional requested modules (planned)

- Visuals: Trajectories for bow, snowballs, eggs, ender pearls and splash potions with optional impact marker (path draws in client test for a snowball; accuracy not compared with real throws); Hit Color, Block Outline, Clear Water and FOV Changer remain planned.
- HUD: Server Info.
- Performance: Smart Animations, Entity Render Distance, Dynamic FPS, Performance Presets.
- Utility: Inventory Profiles, Screenshot Manager, Auto Reconnect.
- Cosmetics: actual Fabric-side Capes, Wings, Particle Cosmetics, HUD Themes and GUI Themes. Launcher cosmetic controls are preferences, not in-game rendering.
