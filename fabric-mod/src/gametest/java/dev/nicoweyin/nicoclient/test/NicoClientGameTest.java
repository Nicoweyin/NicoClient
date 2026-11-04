package dev.nicoweyin.nicoclient.test;

import dev.nicoweyin.nicoclient.HudEditor;
import dev.nicoweyin.nicoclient.ModuleManager;
import dev.nicoweyin.nicoclient.ModuleSettingsScreen;
import dev.nicoweyin.nicoclient.NicoClientMod;
import dev.nicoweyin.nicoclient.NicoScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs the mod in a real Minecraft client with a temporary world. Start with: gradle runClientGameTest */
public final class NicoClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        ModuleManager m = NicoClientMod.MODULES;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runCommand("time set midnight");
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate");
            context.waitTicks(20);

            // Only the default module is on at first.
            context.runOnClient(client -> check(m.fps.enabled && !m.coords.enabled, "default module state"));
            context.takeScreenshot("nicoclient-1-defaults");
            context.runOnClient(client -> check(m.zoom.enabled && m.welcomed, "Zoom is on by default and the first-run hint was shown"));

            // Every HUD module together, plus each mixin-backed module.
            context.runOnClient(client -> {
                m.hudModules.forEach(module -> module.enabled = true);
                m.lowFire.enabled = true;
                m.particles.enabled = true;
                m.trajectories.enabled = true;
                m.save();
            });
            context.waitTicks(10);
            context.takeScreenshot("nicoclient-2-all-hud");
            context.runOnClient(client -> {
                for (ModuleManager.HudModule module : m.hudModules) {
                    String text = dev.nicoweyin.nicoclient.HudRenderer.text(module);
                    check(text != null && (!text.isBlank() || !dev.nicoweyin.nicoclient.HudRenderer.hasContent(module)), "text for " + module.id);
                }
            });

            // Actually burn the player, with Low Fire off and on, in survival so fire applies.
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("execute at @a run setblock ~ ~ ~ minecraft:fire");
            context.runOnClient(client -> { m.lowFire.enabled = false; m.save(); });
            context.waitTicks(15);
            context.runOnClient(client -> check(client.player.isOnFire(), "player is burning for the fire test"));
            context.takeScreenshot("nicoclient-fire-normal");
            context.runOnClient(client -> { m.lowFire.enabled = true; m.save(); });
            context.waitTicks(3);
            context.takeScreenshot("nicoclient-fire-low");
            world.getServer().runCommand("execute at @a run fill ~-1 ~ ~-1 ~1 ~1 ~1 minecraft:air replace minecraft:fire");
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("effect give @a minecraft:fire_resistance 2 0 true");
            context.waitTicks(40);

            // Hold a throwable so the trajectory path has something to predict; look slightly up.
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:snowball");
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ -15");
            context.waitTicks(10);
            context.takeScreenshot("nicoclient-trajectory");
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ 0");
            context.waitTicks(5);

            // Fullbright at night, off and on.
            context.runOnClient(client -> { m.fullbright.enabled = false; m.save(); });
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-3-night-normal");
            context.runOnClient(client -> { m.fullbright.enabled = true; m.save(); });
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-4-night-fullbright");

            // Zoom through its real key.
            context.runOnClient(client -> { m.zoom.enabled = true; m.zoomFactor.set(8); m.zoomSmooth.set(0); m.save(); });
            context.getInput().holdKey(NicoClientMod.zoomKey);
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-5-zoom");
            context.getInput().releaseKey(NicoClientMod.zoomKey);

            // The menu must open with its real key and fully finish its animation.
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            context.waitTicks(15);
            context.runOnClient(client -> check(client.screen instanceof NicoScreen screen && screen.isFullyOpen(), "Right Shift opens the menu"));
            context.takeScreenshot("nicoclient-6-menu");

            // A real mouse click on a switch flips the module, and the mouse hovers a row.
            int[] cpsSwitch = context.computeOnClient(client -> ((NicoScreen) client.screen).toggleCenter(m.cps));
            check(cpsSwitch != null, "CPS switch is visible");
            moveTo(context, cpsSwitch[0], cpsSwitch[1]);
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-6b-menu-hover");
            boolean before = context.computeOnClient(client -> m.cps.enabled);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(10);
            context.runOnClient(client -> check(m.cps.enabled != before, "clicking the switch toggles the module"));

            // Typing filters the list.
            context.getInput().typeChars("zoom");
            context.waitTicks(5);
            context.runOnClient(client -> {
                NicoScreen screen = (NicoScreen) client.screen;
                check(screen.visibleModules().contains(m.zoom), "search finds Zoom");
                check(screen.displayedQuery().equals("zoom"), "the typed search text is displayed");
            });
            context.takeScreenshot("nicoclient-6c-menu-search");
            context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
            context.waitTicks(2);
            context.runOnClient(client -> check(((NicoScreen) client.screen).visibleModules().contains(m.zoom), "backspace edits the search"));

            // The same key closes the menu again, through the closing animation.
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            context.waitTicks(10);
            context.runOnClient(client -> check(client.screen == null, "Right Shift closes the menu"));

            context.setScreen(() -> new ModuleSettingsScreen(m.client, null));
            context.waitTicks(15);
            context.takeScreenshot("nicoclient-6d-settings");

            // Dragging with the real mouse moves a HUD module.
            context.setScreen(HudEditor::new);
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-7-hud-editor");
            int[] coordsStart = context.computeOnClient(client -> new int[] {m.coords.x, m.coords.y});
            moveTo(context, coordsStart[0] + 6, coordsStart[1] + 6);
            context.waitTick();
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTick();
            moveTo(context, coordsStart[0] + 106, coordsStart[1] + 36);
            context.waitTicks(3);
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.runOnClient(client -> check(m.coords.x == coordsStart[0] + 100 && m.coords.y == coordsStart[1] + 30, "dragging moved Coordinates to " + m.coords.x + "," + m.coords.y));
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                m.coords.resetPosition();
                m.hudModules.forEach(module -> module.enabled = false);
                m.save();
            });

            // A real right click on a row opens that module's own settings.
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            context.waitTicks(15);
            int[] keysSwitch = context.computeOnClient(client -> ((NicoScreen) client.screen).toggleCenter(m.keys));
            check(keysSwitch != null, "Keystrokes row is visible");
            moveTo(context, keysSwitch[0] - 60, keysSwitch[1]);
            context.waitTick();
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(25);
            context.runOnClient(client -> check(client.screen instanceof ModuleSettingsScreen, "right click opens module settings"));
            context.takeScreenshot("nicoclient-8-right-click-keystrokes");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(10);
            context.runOnClient(client -> check(client.screen instanceof NicoScreen, "Esc returns to the menu"));
            context.setScreen(() -> new ModuleSettingsScreen(m.crosshair, null));
            context.waitTicks(15);
            context.takeScreenshot("nicoclient-8b-crosshair-settings");
            context.setScreen(() -> null);

            // Hotkeys toggle a module during play, and saved values load back.
            context.runOnClient(client -> { m.cps.enabled = false; m.cps.hotkey = GLFW.GLFW_KEY_J; });
            context.getInput().pressKey(GLFW.GLFW_KEY_J);
            context.waitTicks(3);
            context.runOnClient(client -> { check(m.cps.enabled, "hotkey toggles the module"); m.cps.hotkey = -1; });

            // New modules, each seen working in the world.
            world.getServer().runCommand("weather rain");
            context.runOnClient(client -> { m.fullbright.enabled = false; m.noWeather.enabled = false; m.customTime.enabled = false; m.save(); });
            context.waitTicks(40);
            context.takeScreenshot("nicoclient-9a-rain");
            context.runOnClient(client -> { m.noWeather.enabled = true; });
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-9b-clear-weather");
            context.runOnClient(client -> {
                m.noWeather.enabled = false;
                m.customTime.enabled = true; m.timeOfDay.set(6000);
                m.crosshair.enabled = true; m.crosshairStyle.set(2); m.crosshairColor.set(3);
                m.accent.set(3); m.hudScale.set(125);
                m.hudModules.forEach(module -> module.enabled = true);
                m.fps.enabled = true;
                m.chatTime.enabled = true; m.chat24.set(1);
                m.toggleSprint.enabled = true; m.toggleSneak.enabled = true; m.noFog.enabled = true;
                m.noVignette.enabled = true; m.noHurtCam.enabled = true; m.dynamicFps.enabled = true;
                m.save();
            });
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("effect give @a minecraft:speed 60 1 true");
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe");
            world.getServer().runCommand("damage @a 1");
            world.getServer().runCommand("say hello");
            context.waitTicks(20);
            context.takeScreenshot("nicoclient-9c-new-modules");
            context.runOnClient(client -> {
                check(client.options.toggleSprint().get() && client.options.toggleCrouch().get(), "toggle sprint and sneak apply");
                check(client.gui.getChat() != null, "chat exists");
                m.toggleSprint.enabled = false; m.toggleSneak.enabled = false; m.noFog.enabled = false;
            });
            context.waitTicks(2);
            context.runOnClient(client -> check(!client.options.toggleSprint().get() && !client.options.toggleCrouch().get(), "toggle sprint and sneak restore"));
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            context.waitTicks(15);
            context.takeScreenshot("nicoclient-9d-menu-green");
            context.setScreen(() -> null);
            context.runOnClient(client -> { m.accent.set(0); m.hudScale.set(100); m.crosshair.enabled = false; m.customTime.enabled = false; m.chatTime.enabled = false; });

            // Controls that had not been exercised yet, all with real input.
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            context.waitTicks(15);
            int[] visuals = context.computeOnClient(client -> ((NicoScreen) client.screen).categoryCenter(2));
            moveTo(context, visuals[0], visuals[1]);
            context.waitTick();
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(8);
            context.runOnClient(client -> check(((NicoScreen) client.screen).visibleModules().stream().allMatch(module -> module.category.equals("Visuals")), "clicking Visuals filters the list"));
            context.takeScreenshot("nicoclient-10a-category");
            int[] star = context.computeOnClient(client -> ((NicoScreen) client.screen).starCenter(m.zoom));
            check(star != null, "Zoom star is visible");
            boolean favoriteBefore = context.computeOnClient(client -> m.zoom.favorite);
            moveTo(context, star[0], star[1]);
            context.waitTick();
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            context.runOnClient(client -> check(m.zoom.favorite != favoriteBefore, "clicking the star changes the favorite"));
            int[] all = context.computeOnClient(client -> ((NicoScreen) client.screen).categoryCenter(0));
            moveTo(context, all[0], all[1]);
            context.waitTick();
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(5);
            context.runOnClient(client -> {
                NicoScreen screen = (NicoScreen) client.screen;
                check(screen.toggleCenter(m.particles) == null, "the last module starts out of view");
                screen.mouseScrolled(200, 200, 0, -100);
            });
            context.waitTicks(25);
            context.runOnClient(client -> check(((NicoScreen) client.screen).toggleCenter(m.dynamicFps) != null, "scrolling reaches the last module"));
            context.takeScreenshot("nicoclient-10b-scrolled");
            context.setScreen(() -> null);

            // Choosing a segment and a toggle key with real input, then using that key.
            context.setScreen(() -> new ModuleSettingsScreen(m.copyCoords, null));
            context.waitTicks(15);
            int[] keyRow = context.computeOnClient(client -> ((ModuleSettingsScreen) client.screen).rowCenter(ModuleSettingsScreen.HOTKEY_ROW));
            moveTo(context, keyRow[0], keyRow[1]);
            context.waitTick();
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            context.takeScreenshot("nicoclient-10c-choose-key");
            context.getInput().pressKey(GLFW.GLFW_KEY_K);
            context.waitTicks(3);
            context.runOnClient(client -> check(m.copyCoords.hotkey == GLFW.GLFW_KEY_K, "the chosen key is stored"));
            context.setScreen(() -> null);
            context.runOnClient(client -> { m.copyCoords.enabled = true; client.keyboardHandler.setClipboard("before"); });
            context.getInput().pressKey(GLFW.GLFW_KEY_K);
            context.waitTicks(3);
            context.runOnClient(client -> {
                String clipboard = client.keyboardHandler.getClipboard();
                check(clipboard.matches("-?\\d+ -?\\d+ -?\\d+"), "Copy Coordinates copied " + clipboard);
                m.copyCoords.hotkey = -1;
            });
            context.setScreen(() -> new ModuleSettingsScreen(m.crosshair, null));
            context.waitTicks(15);
            int[] large = context.computeOnClient(client -> ((ModuleSettingsScreen) client.screen).segmentCenter(m.crosshairSize, 2));
            moveTo(context, large[0], large[1]);
            context.waitTick();
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            context.runOnClient(client -> check(m.crosshairSize.get() == 6, "clicking a segment chooses it"));
            context.setScreen(() -> null);

            // Fog, off and on, against the far horizon.
            context.runOnClient(client -> { client.options.renderDistance().set(4); m.noFog.enabled = false; });
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-11a-fog");
            context.runOnClient(client -> m.noFog.enabled = true);
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-11b-no-fog");
            context.runOnClient(client -> m.noFog.enabled = false);
            context.waitTick();

            // The menu at the other GUI scales players really use.
            for (int scale : new int[] {1, 3}) {
                context.runOnClient(client -> {
                    client.getWindow().setWindowed(1920, 1080);
                    client.options.guiScale().set(scale);
                    client.resizeDisplay();
                });
                context.waitTicks(5);
                context.setScreen(NicoScreen::new);
                context.waitTicks(15);
                context.takeScreenshot("nicoclient-12-menu-scale-" + scale);
                context.setScreen(() -> new ModuleSettingsScreen(m.crosshair, null));
                context.waitTicks(15);
                context.takeScreenshot("nicoclient-12-settings-scale-" + scale);
                context.setScreen(() -> null);
            }

            // Settings must reach the disk and load back.
            context.runOnClient(client -> {
                m.coords.enabled = true; m.coords.x = 123; m.coords.y = 45; m.zoomFactor.set(2); m.save();
                Path file = FabricLoader.getInstance().getConfigDir().resolve("nicoclient.properties");
                check(Files.isRegularFile(file), "configuration file written");
                m.coords.x = 0; m.coords.y = 0; m.load();
                m.zoomFactor.set(8);
                m.load();
                check(m.coords.x == 123 && m.coords.y == 45 && m.coords.enabled && m.zoomFactor.get() == 2, "configuration loads back");
                m.modules.forEach(module -> module.enabled = false);
                m.fps.enabled = true;
                m.hudModules.forEach(ModuleManager.HudModule::resetPosition);
                m.save();
            });
        }
    }

    /** The test cursor works in window pixels; the GUI works in scaled units. */
    private static void moveTo(ClientGameTestContext context, int guiX, int guiY) {
        double scale = context.computeOnClient(client -> (double) client.getWindow().getGuiScale());
        context.getInput().setCursorPos((guiX + 0.5) * scale, (guiY + 0.5) * scale);
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("NicoClient game test failed: " + what);
    }
}
