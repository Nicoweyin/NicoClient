package dev.nicoweyin.nicoclient.test;

import dev.nicoweyin.nicoclient.HudEditor;
import dev.nicoweyin.nicoclient.ModuleManager;
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
                    check(text != null && !text.isBlank(), "text for " + module.id);
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
            context.runOnClient(client -> { m.zoom.enabled = true; m.zoomFactor = 8; m.save(); });
            context.getInput().holdKey(NicoClientMod.zoomKey);
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-5-zoom");
            context.getInput().releaseKey(NicoClientMod.zoomKey);

            // The menu must open with its real key, then the HUD editor.
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            context.waitTicks(5);
            context.runOnClient(client -> check(client.screen instanceof NicoScreen, "Right Shift opens the menu"));
            context.takeScreenshot("nicoclient-6-menu");
            context.setScreen(HudEditor::new);
            context.waitTicks(5);
            context.takeScreenshot("nicoclient-7-hud-editor");
            context.setScreen(() -> null);

            // Settings must reach the disk and load back.
            context.runOnClient(client -> {
                m.coords.x = 123; m.coords.y = 45; m.save();
                Path file = FabricLoader.getInstance().getConfigDir().resolve("nicoclient.properties");
                check(Files.isRegularFile(file), "configuration file written");
                m.coords.x = 0; m.coords.y = 0; m.load();
                check(m.coords.x == 123 && m.coords.y == 45 && m.coords.enabled, "configuration loads back");
                m.modules.forEach(module -> module.enabled = false);
                m.fps.enabled = true;
                m.hudModules.forEach(ModuleManager.HudModule::resetPosition);
                m.save();
            });
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("NicoClient game test failed: " + what);
    }
}
