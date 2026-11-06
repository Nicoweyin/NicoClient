package dev.nicoweyin.nicoclient.test;

import dev.nicoweyin.nicoclient.HudEditor;
import dev.nicoweyin.nicoclient.ModuleManager;
import dev.nicoweyin.nicoclient.ModuleSettingsScreen;
import dev.nicoweyin.nicoclient.NicoClientMod;
import dev.nicoweyin.nicoclient.NicoScreen;
import dev.nicoweyin.nicoclient.Pickups;
import dev.nicoweyin.nicoclient.Combat;
import dev.nicoweyin.nicoclient.Freelook;
import dev.nicoweyin.nicoclient.Reconnect;
import dev.nicoweyin.nicoclient.ArmorWarning;
import dev.nicoweyin.nicoclient.WaypointScreen;
import dev.nicoweyin.nicoclient.CosmeticsScreen;
import dev.nicoweyin.nicoclient.Trails;
import dev.nicoweyin.nicoclient.Waypoints;
import net.minecraft.client.gui.GuiGraphics;
import dev.nicoweyin.nicoclient.mixin.OverlayTextureAccessor;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
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
            context.runOnClient(client -> check(((NicoScreen) client.screen).toggleCenter(m.entityDistance) != null, "scrolling reaches the last module"));
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

            // Utility modules added in 0.4.0, checked in survival with real effects.
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("effect clear @a");
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
            world.getServer().runCommand("execute at @a run fill ~-2 ~-1 ~-2 ~2 ~-1 ~30 minecraft:stone");
            world.getServer().runCommand("execute at @a run fill ~-2 ~ ~-2 ~2 ~3 ~30 minecraft:air");
            context.waitTicks(10);

            // Item Pickups lists what arrives while no menu is open.
            context.runOnClient(client -> { m.pickups.enabled = true; m.save(); });
            context.waitTicks(2);
            world.getServer().runCommand("give @a minecraft:cobblestone 12");
            world.getServer().runCommand("give @a minecraft:oak_log 3");
            context.waitTicks(4);
            context.runOnClient(client -> check(Pickups.visible() && m.pickups.enabled, "picked up items are listed"));
            context.takeScreenshot("nicoclient-13a-pickups");

            // Auto Sprint starts sprinting while walking forward.
            context.runOnClient(client -> { m.autoSprint.enabled = true; client.player.setSprinting(false); });
            context.getInput().holdKey(GLFW.GLFW_KEY_W);
            context.waitTicks(8);
            context.runOnClient(client -> check(client.player.isSprinting(), "Auto Sprint sprints while walking forward"));
            context.getInput().releaseKey(GLFW.GLFW_KEY_W);
            context.waitTicks(10);
            context.runOnClient(client -> { m.autoSprint.enabled = false; client.player.setSprinting(false); });

            // Tool Protection refuses to mine with a nearly broken tool and allows it again when off.
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe[damage=1559]");
            world.getServer().runCommand("execute at @a run setblock ~ ~ ~2 minecraft:stone");
            context.waitTicks(5);
            context.runOnClient(client -> {
                m.toolProtect.enabled = true;
                BlockPos block = client.player.blockPosition().offset(0, 0, 2);
                check(client.level.getBlockState(block).is(Blocks.STONE), "stone placed for Tool Protection");
                check(!client.gameMode.startDestroyBlock(block, Direction.NORTH), "Tool Protection stops mining with 2 durability left");
                m.toolProtect.enabled = false;
                check(client.gameMode.startDestroyBlock(block, Direction.NORTH), "mining works again with Tool Protection off");
                client.gameMode.stopDestroyBlock();
            });
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");

            // Block Outline recolors the outline of the block you look at.
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 35");
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-13d-outline-vanilla");
            context.runOnClient(client -> { m.blockOutline.enabled = true; m.outlineColor.set(3); m.outlineWidth.set(300); });
            context.waitTicks(2);
            context.runOnClient(client -> check(NicoClientMod.outlineColor() == 0xCCFF6B6B, "Block Outline uses the chosen color"));
            context.takeScreenshot("nicoclient-13e-outline-red-thick");
            context.runOnClient(client -> { m.blockOutline.enabled = false; m.outlineColor.set(0); m.outlineWidth.set(200); });
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");

            // Chat History keeps more than vanilla's 100 messages.
            context.runOnClient(client -> {
                m.chatHistory.enabled = true; m.chatHistoryLimit.set(500);
                for (int i = 0; i < 150; i++) client.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("history " + i));
                check(chatSize(client.gui.getChat()) >= 150, "Chat History keeps 150 messages, has " + chatSize(client.gui.getChat()));
                m.chatHistory.enabled = false;
                for (int i = 0; i < 5; i++) client.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("vanilla " + i));
                check(chatSize(client.gui.getChat()) == 100, "without Chat History the vanilla limit of 100 applies");
            });

            // Entity Distance hides a far pig but never a near one.
            world.getServer().runCommand("execute at @a run summon minecraft:pig ~ ~ ~25 {NoAI:1b}");
            world.getServer().runCommand("execute at @a run summon minecraft:pig ~1 ~ ~3 {NoAI:1b}");
            context.waitTicks(10);
            context.runOnClient(client -> {
                m.entityDistance.enabled = true; m.entityRange.set(16);
                var camera = client.gameRenderer.getMainCamera().position();
                int hidden = 0, shown = 0;
                for (var entity : client.level.entitiesForRendering()) {
                    if (!(entity instanceof Pig)) continue;
                    if (NicoClientMod.hideEntity(entity, camera.x, camera.y, camera.z)) hidden++; else shown++;
                }
                check(hidden == 1 && shown == 1, "Entity Distance hides only the far pig (hidden " + hidden + ", shown " + shown + ")");
                check(!NicoClientMod.hideEntity(client.player, camera.x, camera.y, camera.z), "your own player is never hidden");
            });
            context.takeScreenshot("nicoclient-13b-entity-distance");
            context.runOnClient(client -> m.entityDistance.enabled = false);
            world.getServer().runCommand("kill @e[type=minecraft:pig]");

            // Death Position reports where you died.
            context.runOnClient(client -> { m.deathPos.enabled = true; m.deathCopy.set(1); client.keyboardHandler.setClipboard("before"); });
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~25");
            context.waitTicks(5);
            world.getServer().runCommand("kill @a");
            context.waitTicks(20);
            context.runOnClient(client -> {
                BlockPos pos = client.player.blockPosition();
                check(client.keyboardHandler.getClipboard().equals(pos.getX() + " " + pos.getY() + " " + pos.getZ()), "death position copied: " + client.keyboardHandler.getClipboard());
            });
            context.takeScreenshot("nicoclient-13c-death-position");
            context.runOnClient(client -> client.player.respawn());
            context.waitTicks(20);
            context.setScreen(() -> null);
            context.waitTicks(10);
            context.runOnClient(client -> check(NicoClientMod.deathPoint != null && client.player.blockPosition().distSqr(NicoClientMod.deathPoint) > 100, "the way back to the death point is shown after respawning"));
            context.takeScreenshot("nicoclient-13f-death-guide");
            BlockPos deathPoint = context.computeOnClient(client -> NicoClientMod.deathPoint);
            world.getServer().runCommand("tp @a " + deathPoint.getX() + " " + deathPoint.getY() + " " + deathPoint.getZ());
            context.waitTicks(10);
            context.runOnClient(client -> check(NicoClientMod.deathPoint == null, "the way back ends when you arrive, at " + client.player.blockPosition() + " death " + NicoClientMod.deathPoint));
            context.runOnClient(client -> { m.deathPos.enabled = false; m.deathCopy.set(0); m.pickups.enabled = false; m.save(); });
            world.getServer().runCommand("gamemode creative @a");

            // Modules added in 0.5.0.
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
            world.getServer().runCommand("execute at @a run fill ~-3 ~-1 ~-3 ~3 ~-1 ~8 minecraft:stone");
            world.getServer().runCommand("execute at @a run fill ~-3 ~ ~-3 ~3 ~3 ~8 minecraft:air");
            world.getServer().runCommand("execute at @a run summon minecraft:pig ~ ~ ~3 {NoAI:1b}");
            world.getServer().runCommand("give @a minecraft:arrow 20");
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:bow");
            context.waitTicks(10);

            // Reach Display and Combo Counter count real attacks; Food and Arrow Counter read the inventory.
            context.runOnClient(client -> {
                m.reach.enabled = true; m.combo.enabled = true; m.food.enabled = true; m.arrows.enabled = true;
                Pig pig = null;
                for (var entity : client.level.entitiesForRendering()) if (entity instanceof Pig found) pig = found;
                check(pig != null, "pig for the combat test");
                check(Combat.comboText().equals("No combo"), "no combo before hitting");
                client.gameMode.attack(client.player, pig);
                client.gameMode.attack(client.player, pig);
                check(Combat.comboText().equals("Combo 2"), "two hits make a combo of 2, got " + Combat.comboText());
                check(Combat.reachText().matches("Reach [0-9]\\.[0-9]{2} m"), "reach is shown, got " + Combat.reachText());
                double reach = Double.parseDouble(Combat.reachText().split(" ")[1]);
                check(reach > 1 && reach < 3.5, "reach is plausible: " + reach);
                check(hudText(m.arrows).equals("20 arrows"), "Arrow Counter counts 20, got " + hudText(m.arrows));
                check(hudText(m.food).startsWith("Food "), "Food shows hunger");
            });
            context.takeScreenshot("nicoclient-14a-combat-hud");

            // Hit Color repaints the hurt overlay and restores vanilla red when off.
            context.runOnClient(client -> { m.hitColor.enabled = true; m.hitColorValue.set(2); });
            context.waitTicks(2);
            context.runOnClient(client -> {
                var pixels = ((OverlayTextureAccessor) client.gameRenderer.overlayTexture()).nicoclient$texture().getPixels();
                check(pixels.getPixel(3, 3) == 0xB22EE86B, "Hit Color paints the overlay green, got " + Integer.toHexString(pixels.getPixel(3, 3)));
            });
            world.getServer().runCommand("damage @e[type=minecraft:pig,limit=1] 1");
            context.waitTicks(2);
            context.takeScreenshot("nicoclient-14b-hit-color");
            context.runOnClient(client -> m.hitColor.enabled = false);
            context.waitTicks(2);
            context.runOnClient(client -> {
                var pixels = ((OverlayTextureAccessor) client.gameRenderer.overlayTexture()).nicoclient$texture().getPixels();
                check(pixels.getPixel(3, 3) == 0xB2FF0000, "vanilla red is back without Hit Color");
                m.reach.enabled = false; m.combo.enabled = false; m.food.enabled = false; m.arrows.enabled = false;
            });
            world.getServer().runCommand("kill @e[type=minecraft:pig]");
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");

            // Static FOV keeps the field of view still while flying.
            context.runOnClient(client -> {
                client.player.getAbilities().flying = true;
                m.staticFov.enabled = false;
                float vanilla = client.player.getFieldOfViewModifier(true, 1f);
                m.staticFov.enabled = true;
                float fixed = client.player.getFieldOfViewModifier(true, 1f);
                check(vanilla != 1f && fixed == 1f, "Static FOV removes the flying FOV change (" + vanilla + " -> " + fixed + ")");
                m.staticFov.enabled = false;
                client.player.getAbilities().flying = false;
            });

            // Freelook turns the camera, not the player, and restores first person afterwards.
            context.runOnClient(client -> { m.freelook.enabled = true; client.options.setCameraType(CameraType.FIRST_PERSON); });
            float[] facing = new float[1];
            context.runOnClient(client -> facing[0] = client.player.getYRot());
            context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_ALT);
            context.waitTicks(3);
            // The test window does not capture the mouse, so this makes the same call Minecraft's mouse handler makes.
            context.runOnClient(client -> client.player.turn(300, -100));
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(Freelook.active && client.options.getCameraType() == CameraType.THIRD_PERSON_BACK, "holding the Freelook key switches to the orbit camera");
                check(client.player.getYRot() == facing[0], "the player keeps facing ahead during Freelook");
                check(Math.abs(Freelook.yaw - facing[0]) > 5, "the mouse turns the camera during Freelook, yaw " + Freelook.yaw);
            });
            context.takeScreenshot("nicoclient-14c-freelook");
            context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_ALT);
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(!Freelook.active && client.options.getCameraType() == CameraType.FIRST_PERSON, "releasing Freelook returns to first person");
                m.freelook.enabled = false;
            });

            // Quiet Mode scales every sound category without touching Minecraft's volume options.
            context.runOnClient(client -> {
                float loud = soundVolume(client, SoundSource.BLOCKS);
                double option = client.options.getSoundSourceVolume(SoundSource.MASTER);
                m.quietMode.enabled = true; m.quietVolume.set(25);
                float quiet = soundVolume(client, SoundSource.BLOCKS);
                check(loud > 0 && Math.abs(quiet - loud * 0.25f) < 0.001f, "Quiet Mode plays sounds at 25% (" + loud + " -> " + quiet + ")");
                check(client.options.getSoundSourceVolume(SoundSource.MASTER) == option, "Quiet Mode leaves the volume options alone");
                m.quietMode.enabled = false;
            });

            // Clear Water: compare the view under water.
            world.getServer().runCommand("execute at @a run fill ~-8 ~-1 ~-8 ~8 ~5 ~24 minecraft:water");
            world.getServer().runCommand("execute at @a run fill ~-8 ~-2 ~-8 ~8 ~-2 ~24 minecraft:sand");
            world.getServer().runCommand("execute at @a run fill ~-1 ~-1 ~12 ~1 ~3 ~14 minecraft:gold_block");
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~1 ~ 0 10");
            context.waitTicks(30);
            context.takeScreenshot("nicoclient-14d-water-normal");
            context.runOnClient(client -> {
                var camera = client.gameRenderer.getMainCamera();
                java.util.function.Supplier<net.minecraft.client.renderer.fog.FogData> fog = () -> {
                    var data = new net.minecraft.client.renderer.fog.FogData();
                    new net.minecraft.client.renderer.fog.environment.WaterFogEnvironment().setupFog(data, camera, client.level, 128, client.getDeltaTracker());
                    return data;
                };
                m.clearWater.enabled = false;
                float vanilla = fog.get().environmentalEnd;
                m.clearWater.enabled = true; m.waterClarity.set(1);
                float more = fog.get().environmentalEnd;
                m.waterClarity.set(2);
                var full = fog.get();
                check(vanilla > 0 && vanilla < 128, "vanilla water fog is short: " + vanilla);
                check(more > vanilla && more <= 128, "Clarity More sees further: " + vanilla + " -> " + more);
                check(full.environmentalEnd == 128 && full.skyEnd == 128, "Clarity Full reaches the view distance: " + full.environmentalEnd);
            });
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-14e-water-clear");
            context.runOnClient(client -> m.clearWater.enabled = false);
            world.getServer().runCommand("execute at @a run fill ~-8 ~-3 ~-8 ~8 ~5 ~24 minecraft:air replace minecraft:water");

            // Auto Reconnect adds a counting Reconnect button after a disconnect from a server.
            context.runOnClient(client -> {
                m.autoReconnect.enabled = true; m.reconnectDelay.set(10);
                Reconnect.lastServer = new ServerData("Test", "play.example.test", ServerData.Type.OTHER);
            });
            context.setScreen(() -> new DisconnectedScreen(new TitleScreen(), Component.literal("Connection Lost"), Component.literal("Test disconnect")));
            context.waitTicks(25);
            context.runOnClient(client -> {
                var labels = Screens.getButtons(client.screen).stream().map(widget -> widget.getMessage().getString()).toList();
                check(labels.stream().anyMatch(label -> label.equals("Reconnect in 9s") || label.equals("Reconnect in 8s")), "the Reconnect button counts down: " + labels);
            });
            context.takeScreenshot("nicoclient-14f-reconnect");
            context.setScreen(() -> null);
            context.runOnClient(client -> { m.autoReconnect.enabled = false; m.reconnectDelay.set(0); Reconnect.lastServer = null; m.save(); });

            // Waypoints, Target HUD, Armor Warning, Tab Ping and No Pumpkin.
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 20");
            world.getServer().runCommand("execute at @a run fill ~-4 ~-1 ~-4 ~4 ~-1 ~10 minecraft:stone");
            world.getServer().runCommand("execute at @a run fill ~-4 ~ ~-4 ~4 ~4 ~10 minecraft:air");
            world.getServer().runCommand("execute at @a run setblock ~ ~ ~3 minecraft:chest");
            world.getServer().runCommand("execute at @a run summon minecraft:pig ~2 ~ ~5 {NoAI:1b}");
            context.waitTicks(15);
            context.runOnClient(client -> {
                m.waypoints.enabled = true;
                Waypoints.here(client).forEach(Waypoints::remove);
                check(Waypoints.lookedAtBlock(client) != null && client.level.getBlockState(Waypoints.lookedAtBlock(client)).is(net.minecraft.world.level.block.Blocks.CHEST), "looking at the chest");
            });
            context.setScreen(() -> new WaypointScreen(null));
            context.waitTicks(15);
            context.getInput().typeChars("Base");
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            context.waitTicks(3);
            context.runOnClient(client -> check(Waypoints.here(client).stream().anyMatch(p -> p.name.equals("Base") && !p.block), "typing a name and Enter saves a waypoint"));
            int[] markButton = context.computeOnClient(client -> ((WaypointScreen) client.screen).markCenter());
            moveTo(context, markButton[0], markButton[1]);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(5);
            context.runOnClient(client -> check(Waypoints.here(client).stream().anyMatch(p -> p.name.equals("Chest") && p.block), "Mark block marks the chest: " + Waypoints.here(client).stream().map(p -> p.name).toList()));
            context.takeScreenshot("nicoclient-15a-waypoint-screen");
            int[] deleteButton = context.computeOnClient(client -> ((WaypointScreen) client.screen).deleteCenter(0));
            moveTo(context, deleteButton[0], deleteButton[1]);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(5);
            context.runOnClient(client -> check(Waypoints.here(client).size() == 1 && Waypoints.here(client).get(0).name.equals("Chest"), "Delete removes Base"));
            context.setScreen(() -> null);
            context.waitTicks(10);
            context.runOnClient(client -> {
                var chest = Waypoints.here(client).get(0);
                float[] spot = Waypoints.screenPosition(client, chest, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
                float cx = client.getWindow().getGuiScaledWidth() / 2f, cy = client.getWindow().getGuiScaledHeight() / 2f;
                check(spot != null && Math.abs(spot[0] - cx) < 30 && Math.abs(spot[1] - cy) < 45, "the chest marker sits near the crosshair: " + java.util.Arrays.toString(spot) + " center " + cx + "," + cy);
                m.waypoints.hotkey = GLFW.GLFW_KEY_N;
            });
            context.getInput().pressKey(GLFW.GLFW_KEY_N);
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(Waypoints.here(client).size() == 2 && Waypoints.here(client).get(1).block, "the Add key marks the looked-at block");
                Waypoints.remove(Waypoints.here(client).get(1));
                m.waypoints.hotkey = -1;
            });

            // Target HUD shows the pig just hit; Armor Warning reacts to a nearly broken helmet.
            world.getServer().runCommand("item replace entity @a armor.head with minecraft:iron_helmet[damage=160]");
            context.waitTicks(5);
            context.runOnClient(client -> {
                m.target.enabled = true; m.armorWarning.enabled = true;
                Pig pig = null;
                for (var entity : client.level.entitiesForRendering()) if (entity instanceof Pig found) pig = found;
                client.gameMode.attack(client.player, pig);
                check(dev.nicoweyin.nicoclient.Combat.target(client) == pig, "Target HUD follows the pig you hit");
            });
            context.waitTicks(3);
            context.runOnClient(client -> check(ArmorWarning.warning() != null && ArmorWarning.warning().startsWith("Helmet at 3%"), "Armor Warning names the helmet: " + ArmorWarning.warning()));
            context.takeScreenshot("nicoclient-15b-waypoint-world-target-armor");
            context.runOnClient(client -> { m.target.enabled = false; m.armorWarning.enabled = false; });
            world.getServer().runCommand("kill @e[type=minecraft:pig]");

            // No Pumpkin: compare the view with a carved pumpkin on the head.
            world.getServer().runCommand("item replace entity @a armor.head with minecraft:carved_pumpkin");
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-15c-pumpkin-normal");
            context.runOnClient(client -> m.noPumpkin.enabled = true);
            context.waitTicks(3);
            context.takeScreenshot("nicoclient-15d-no-pumpkin");
            context.runOnClient(client -> m.noPumpkin.enabled = false);
            world.getServer().runCommand("item replace entity @a armor.head with minecraft:air");

            // Tab Ping: the player list, drawn on a plain screen because single player hides it.
            context.runOnClient(client -> m.tabPing.enabled = true);
            context.setScreen(() -> new net.minecraft.client.gui.screens.Screen(Component.literal("Tab list")) {
                @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    mc.gui.getTabList().render(g, g.guiWidth(), mc.level.getScoreboard(), null);
                }
            });
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-15e-tab-ping");
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                m.tabPing.enabled = false; m.waypoints.enabled = false;
                Waypoints.here(client).forEach(Waypoints::remove);
                m.save();
            });
            world.getServer().runCommand("gamemode creative @a");

            // Cosmetics: opened from the menu button, chosen by real clicks, seen in the preview and in third person.
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
            context.setScreen(NicoScreen::new);
            context.waitTicks(15);
            int[] cosmeticsButton = context.computeOnClient(client -> ((NicoScreen) client.screen).cosmeticsCenter());
            moveTo(context, cosmeticsButton[0], cosmeticsButton[1]);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(20);
            context.runOnClient(client -> check(client.screen instanceof CosmeticsScreen, "the Cosmetics button opens the cosmetics screen"));
            for (int row = 0; row < 3; row++) {
                int pickRow = row;
                int[] arrow = context.computeOnClient(client -> ((CosmeticsScreen) client.screen).nextCenter(pickRow));
                moveTo(context, arrow[0], arrow[1]);
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                context.waitTicks(3);
            }
            int[] previous = context.computeOnClient(client -> ((CosmeticsScreen) client.screen).previousCenter(3));
            moveTo(context, previous[0], previous[1]);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            context.runOnClient(client -> check(m.trailStyle.get() == 5, "the previous arrow wraps from Off to the last trail, Snow"));
            context.runOnClient(client -> {
                check(m.capeStyle.get() == 1 && m.wingStyle.get() == 1 && m.haloStyle.get() == 1, "the next arrows pick Nebula cape, Angel wings and Gold halo");
                ((CosmeticsScreen) client.screen).setRotation(150);
            });
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-16a-cosmetics-screen");
            context.setScreen(() -> null);
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(10);
            context.takeScreenshot("nicoclient-16b-cosmetics-front");
            context.runOnClient(client -> { client.options.setCameraType(CameraType.THIRD_PERSON_BACK); m.capeStyle.set(4); m.wingStyle.set(3); m.haloStyle.set(2); });
            context.waitTicks(10);
            context.takeScreenshot("nicoclient-16c-cosmetics-back-neon");
            context.runOnClient(client -> { m.capeStyle.set(2); m.wingStyle.set(2); });
            context.waitTicks(10);
            context.takeScreenshot("nicoclient-16d-cosmetics-back-dragon");
            String[][] looks = {{"5", "4", "3"}, {"6", "5", "4"}, {"7", "6", "1"}, {"9", "7", "2"}, {"11", "5", "1"}};
            for (String[] look : looks) {
                context.runOnClient(client -> { m.capeStyle.set(Integer.parseInt(look[0])); m.wingStyle.set(Integer.parseInt(look[1])); m.haloStyle.set(Integer.parseInt(look[2])); });
                context.waitTicks(6);
                context.takeScreenshot("nicoclient-16e-look-cape" + look[0] + "-wings" + look[1]);
            }
            // The launcher's Cosmetics tab writes the settings file; the running game follows within two seconds.
            context.runOnClient(client -> {
                m.capeStyle.set(0); m.save();
                try {
                    Path file = FabricLoader.getInstance().getConfigDir().resolve("nicoclient.properties");
                    Thread.sleep(20);
                    String text = Files.readString(file).replaceAll("(?m)^cosmetics\\.cape=.*$", "cosmetics.cape=8");
                    Files.writeString(file, text);
                    Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 1000));
                } catch (Exception e) { throw new AssertionError(e); }
            });
            context.waitTicks(45);
            context.runOnClient(client -> check(m.capeStyle.get() == 8, "a cape chosen in the launcher reaches the running game, got " + m.capeStyle.get()));

            // Trail: walking forward leaves particles.
            context.runOnClient(client -> { m.trailStyle.set(1); Trails.spawned = 0; });
            context.getInput().holdKey(GLFW.GLFW_KEY_W);
            context.waitTicks(12);
            context.takeScreenshot("nicoclient-16f-trail-hearts");
            context.getInput().releaseKey(GLFW.GLFW_KEY_W);
            context.runOnClient(client -> { check(Trails.spawned > 2, "walking leaves trail particles: " + Trails.spawned); m.trailStyle.set(0); });
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                m.capeStyle.set(0); m.wingStyle.set(0); m.haloStyle.set(0); m.save();
            });

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

    private static String hudText(ModuleManager.HudModule module) { return dev.nicoweyin.nicoclient.HudRenderer.text(module); }

    /** Asks Minecraft's sound engine how loud a full-volume sound of this category plays right now. */
    private static float soundVolume(net.minecraft.client.Minecraft client, SoundSource source) {
        try {
            var engineField = net.minecraft.client.sounds.SoundManager.class.getDeclaredField("soundEngine");
            engineField.setAccessible(true);
            Object engine = engineField.get(client.getSoundManager());
            var method = net.minecraft.client.sounds.SoundEngine.class.getDeclaredMethod("calculateVolume", float.class, SoundSource.class);
            method.setAccessible(true);
            return (float) method.invoke(engine, 1f, source);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    /** Reads how many chat messages are stored; the field is private in Minecraft. */
    private static int chatSize(net.minecraft.client.gui.components.ChatComponent chat) {
        try {
            var field = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            return ((java.util.List<?>) field.get(chat)).size();
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("NicoClient game test failed: " + what);
    }
}
