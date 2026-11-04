package dev.nicoweyin.nicoclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Locale;

/** Draws every enabled HUD module and provides the values they display. */
public final class HudRenderer {
    private static final int BACKGROUND = 0xB0181732, TEXT = 0xFFB2F4F8, PRESSED = 0xD05BBDD3;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final long STARTED = System.currentTimeMillis();
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>(), RIGHT = new ArrayDeque<>();
    private static boolean leftDown, rightDown;

    private HudRenderer() {}

    public static void render(GuiGraphics g) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        ModuleManager modules = NicoClientMod.MODULES;
        if (modules.cps.enabled) sampleClicks(client);
        for (ModuleManager.HudModule module : modules.hudModules) {
            if (!module.enabled) continue;
            int width = width(module), height = module.height;
            int x = Math.max(0, Math.min(g.guiWidth() - width, module.x));
            int y = Math.max(0, Math.min(g.guiHeight() - height, module.y));
            if (module == modules.keys) keys(g, client, x, y);
            else if (module == modules.armor) armor(g, client, x, y);
            else label(g, client, text(module), x, y);
        }
    }

    public static int width(ModuleManager.HudModule module) {
        ModuleManager modules = NicoClientMod.MODULES;
        if (module == modules.keys) return 52;
        if (module == modules.armor) return 72;
        return Minecraft.getInstance().font.width(text(module)) + 8;
    }

    public static String text(ModuleManager.HudModule module) {
        Minecraft client = Minecraft.getInstance();
        ModuleManager m = NicoClientMod.MODULES;
        if (module == m.fps) return client.getFps() + " FPS";
        if (module == m.clock) return LocalTime.now().format(CLOCK);
        if (module == m.session) return session(Duration.ofMillis(System.currentTimeMillis() - STARTED));
        if (module == m.cps) return count(LEFT) + " | " + count(RIGHT) + " CPS";
        if (client.player == null) return module.name;
        if (module == m.coords) {
            BlockPos pos = client.player.blockPosition();
            return pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }
        if (module == m.direction) return direction(client.player.getDirection().getName());
        if (module == m.speed) {
            double dx = client.player.getX() - client.player.xo, dz = client.player.getZ() - client.player.zo;
            return String.format(Locale.ROOT, "%.1f m/s", Math.sqrt(dx * dx + dz * dz) * 20);
        }
        if (module == m.ping) {
            var info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(client.player.getUUID());
            return info == null ? "- ms" : info.getLatency() + " ms";
        }
        if (module == m.biome && client.level != null) {
            return client.level.getBiome(client.player.blockPosition()).unwrapKey().map(key -> title(key.identifier().getPath())).orElse("Unknown");
        }
        return module.name;
    }

    static String session(Duration elapsed) {
        long seconds = elapsed.toSeconds();
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private static String direction(String name) {
        return switch (name) {
            case "north" -> "North (-Z)";
            case "south" -> "South (+Z)";
            case "east" -> "East (+X)";
            case "west" -> "West (-X)";
            default -> title(name);
        };
    }

    private static String title(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static void sampleClicks(Minecraft client) {
        long now = System.currentTimeMillis();
        boolean inGame = client.screen == null;
        boolean left = inGame && client.options.keyAttack.isDown(), right = inGame && client.options.keyUse.isDown();
        if (left && !leftDown) LEFT.addLast(now);
        if (right && !rightDown) RIGHT.addLast(now);
        leftDown = left; rightDown = right;
    }

    private static int count(ArrayDeque<Long> clicks) {
        long cutoff = System.currentTimeMillis() - 1000;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) clicks.pollFirst();
        return clicks.size();
    }

    private static void label(GuiGraphics g, Minecraft client, String text, int x, int y) {
        g.fill(x, y, x + client.font.width(text) + 8, y + 15, BACKGROUND);
        g.drawString(client.font, text, x + 4, y + 4, TEXT, false);
    }

    private static void keys(GuiGraphics g, Minecraft client, int x, int y) {
        key(g, client, "W", x + 18, y, client.options.keyUp.isDown());
        key(g, client, "A", x, y + 18, client.options.keyLeft.isDown());
        key(g, client, "S", x + 18, y + 18, client.options.keyDown.isDown());
        key(g, client, "D", x + 36, y + 18, client.options.keyRight.isDown());
    }

    private static void key(GuiGraphics g, Minecraft client, String label, int x, int y, boolean down) {
        g.fill(x, y, x + 16, y + 16, down ? PRESSED : BACKGROUND);
        g.drawString(client.font, label, x + 5, y + 4, 0xFFFFFFFF, false);
    }

    private static void armor(GuiGraphics g, Minecraft client, int x, int y) {
        int offset = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = client.player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + offset, y);
                g.renderItemDecorations(client.font, stack, x + offset, y);
            }
            offset += 18;
        }
    }
}
