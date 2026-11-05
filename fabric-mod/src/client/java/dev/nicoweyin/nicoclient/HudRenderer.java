package dev.nicoweyin.nicoclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws every enabled HUD module and provides the values they display. */
public final class HudRenderer {
    private static final int[] BACKGROUND_ALPHA = {0, 0x70, 0xB0};
    private static final float[] keyAmount = new float[7];
    private static long lastKeyFrame;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final long STARTED = System.currentTimeMillis();
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>(), RIGHT = new ArrayDeque<>();
    private static boolean leftDown, rightDown;
    /** Lets the day counter read the real time while Custom Time changes what the world shows. */
    public static boolean readingRealTime;
    private static final float WELCOME_SECONDS = 10;
    private static long welcomeStarted;

    private HudRenderer() {}

    public static float scale() { return NicoClientMod.MODULES.hudScale.get() / 100f; }

    private static int background() { return BACKGROUND_ALPHA[NicoClientMod.MODULES.hudBackground.index()] << 24 | 0x0D0F1E; }

    private static int textColor() {
        return switch (NicoClientMod.MODULES.hudText.index()) {
            case 1 -> Ui.accentLight();
            case 2 -> 0xFF8BE9F5;
            case 3 -> 0xFFFFE27A;
            default -> Ui.TEXT;
        };
    }

    public static void render(GuiGraphics g) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        ModuleManager modules = NicoClientMod.MODULES;
        welcome(g);
        if (modules.cps.enabled) sampleClicks(client);
        float scale = scale();
        float screenW = g.guiWidth() / scale, screenH = g.guiHeight() / scale;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        for (ModuleManager.HudModule module : modules.hudModules) {
            if (!module.enabled || !hasContent(module)) continue;
            int width = width(module), height = height(module);
            int x = (int) Math.max(0, Math.min(screenW - width, module.x));
            int y = (int) Math.max(0, Math.min(screenH - height, module.y));
            if (module == modules.keys) keys(g, client, x, y);
            else if (module == modules.armor) armor(g, client, x, y);
            else if (module == modules.potions) potions(g, x, y, width);
            else if (module == modules.pickups) Pickups.render(g, x, y, width);
            else label(g, text(module), x, y);
        }
        g.pose().popMatrix();
        deathGuide(g, client);
    }

    /** Tells new players once how to find the menu, then fades away. */
    private static void welcome(GuiGraphics g) {
        ModuleManager m = NicoClientMod.MODULES;
        if (welcomeStarted == 0) {
            if (m.welcomed) return;
            welcomeStarted = System.currentTimeMillis();
            m.welcomed = true;
            m.save();
        }
        float elapsed = (System.currentTimeMillis() - welcomeStarted) / 1000f;
        if (elapsed > WELCOME_SECONDS) return;
        float fade = Math.min(1, Math.min(elapsed * 3, (WELCOME_SECONDS - elapsed) * 2));
        String text = "Press " + NicoClientMod.menuKey.getTranslatedKeyMessage().getString() + " to open NicoClient";
        int width = Ui.width(text) + 24, x = (g.guiWidth() - width) / 2, y = g.guiHeight() / 4;
        Ui.rounded(g, x, y, width, 22, 8, Ui.alpha(Ui.PANEL, fade));
        Ui.text(g, text, x + 12, y + 8, Ui.alpha(Ui.TEXT, fade));
    }

    private static final String[] ARROWS = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};

    /** Points the way back to where you died, with the distance. */
    private static void deathGuide(GuiGraphics g, Minecraft client) {
        BlockPos point = NicoClientMod.deathPoint;
        if (point == null || client.player.isDeadOrDying()) return;
        String text;
        if (client.level.dimension() != NicoClientMod.deathWorld) text = "Death point in " + title(NicoClientMod.deathWorld.identifier().getPath());
        else {
            double dx = point.getX() + 0.5 - client.player.getX(), dz = point.getZ() + 0.5 - client.player.getZ();
            double target = Math.toDegrees(Math.atan2(-dx, dz));
            double turn = ((target - client.player.getYRot()) % 360 + 540) % 360 - 180;
            text = ARROWS[Math.floorMod((int) Math.round(turn / 45), 8)] + "  Death point  " + (int) Math.sqrt(dx * dx + dz * dz) + " m";
        }
        // Just below the crosshair, where it is seen without covering HUD modules.
        int width = Ui.width(text) + 16, x = (g.guiWidth() - width) / 2, y = g.guiHeight() / 2 + 22;
        Ui.rounded(g, x, y, width, 17, 6, 0xB00D0F1E);
        Ui.text(g, text, x + 8, y + 5, Ui.accentLight());
    }

    /** Modules without anything to show stay hidden instead of drawing an empty box. */
    public static boolean hasContent(ModuleManager.HudModule module) {
        Minecraft client = Minecraft.getInstance();
        ModuleManager m = NicoClientMod.MODULES;
        if (client.player == null) return false;
        if (module == m.potions) return !client.player.getActiveEffects().isEmpty();
        if (module == m.pickups) return Pickups.visible();
        if (module == m.held) return !client.player.getMainHandItem().isEmpty();
        if (module == m.armor) {
            for (EquipmentSlot slot : ARMOR) if (!client.player.getItemBySlot(slot).isEmpty()) return true;
            return false;
        }
        return true;
    }

    public static int width(ModuleManager.HudModule module) {
        ModuleManager modules = NicoClientMod.MODULES;
        if (module == modules.keys) return 52;
        if (module == modules.armor) return 72;
        if (module == modules.pickups && hasContent(module)) return Pickups.width();
        if (module == modules.potions) {
            int widest = Ui.width(module.name);
            if (hasContent(module)) { widest = 0; for (String line : potionLines()) widest = Math.max(widest, Ui.width(line)); }
            return widest + 8;
        }
        return Ui.width(hasContent(module) ? text(module) : module.name) + 8;
    }

    public static int height(ModuleManager.HudModule module) {
        ModuleManager modules = NicoClientMod.MODULES;
        if (module == modules.keys) return 34 + (modules.keysSpace.on() ? 12 : 0) + (modules.keysMouse.on() ? 18 : 0);
        if (module == modules.potions && hasContent(module)) return potionLines().size() * 11 + 4;
        if (module == modules.pickups && hasContent(module)) return Pickups.height();
        return module.height;
    }

    public static String text(ModuleManager.HudModule module) {
        Minecraft client = Minecraft.getInstance();
        ModuleManager m = NicoClientMod.MODULES;
        if (module == m.fps) return client.getFps() + " FPS";
        if (module == m.clock) return LocalTime.now().format(DateTimeFormatter.ofPattern((m.clock24.on() ? "HH" : "h") + ":mm" + (m.clockSeconds.on() ? ":ss" : "") + (m.clock24.on() ? "" : " a"), Locale.ENGLISH));
        if (module == m.session) return session(Duration.ofMillis(System.currentTimeMillis() - STARTED));
        if (module == m.cps) return count(LEFT) + (m.cpsRight.on() ? " | " + count(RIGHT) : "") + " CPS";
        if (module == m.memory) {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory(), max = runtime.maxMemory();
            return m.memoryPercent.on() ? "RAM " + used * 100 / max + "%" : "RAM " + used / 1048576 + " / " + max / 1048576 + " MB";
        }
        if (client.player == null) return module.name;
        if (module == m.coords) {
            BlockPos pos = client.player.blockPosition();
            return pos.getX() + (m.coordsY.on() ? " " + pos.getY() : "") + " " + pos.getZ();
        }
        if (module == m.direction) return direction(client.player.getDirection().getName(), m.directionShort.on());
        if (module == m.speed) {
            double dx = client.player.getX() - client.player.xo, dz = client.player.getZ() - client.player.zo;
            double perSecond = Math.sqrt(dx * dx + dz * dz) * 20;
            return m.speedUnit.get() == 1 ? String.format(Locale.ROOT, "%.1f km/h", perSecond * 3.6) : String.format(Locale.ROOT, "%.1f m/s", perSecond);
        }
        if (module == m.ping) {
            var info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(client.player.getUUID());
            return info == null ? "- ms" : info.getLatency() + " ms";
        }
        if (module == m.held) {
            ItemStack stack = client.player.getMainHandItem();
            if (stack.isEmpty()) return "";
            String name = stack.getHoverName().getString();
            if (!m.heldCount.on()) return name;
            if (stack.isDamageableItem()) return name + "  " + (stack.getMaxDamage() - stack.getDamageValue()) + "/" + stack.getMaxDamage();
            return stack.getCount() > 1 ? name + "  x" + stack.getCount() : name;
        }
        if (module == m.server) {
            var info = client.getCurrentServer();
            String name = info == null ? "Singleplayer" : info.ip;
            int players = client.getConnection() == null ? 0 : client.getConnection().getOnlinePlayers().size();
            return m.serverPlayers.on() ? name + "  " + players + (players == 1 ? " player" : " players") : name;
        }
        if (client.level == null) return module.name;
        if (module == m.day) {
            readingRealTime = true;
            long time = client.level.getLevelData().getDayTime();
            readingRealTime = false;
            return "Day " + time / 24000L;
        }
        if (module == m.light) return "Light " + client.level.getMaxLocalRawBrightness(client.player.blockPosition());
        if (module == m.biome) {
            return client.level.getBiome(client.player.blockPosition()).unwrapKey().map(key -> title(key.identifier().getPath())).orElse("Unknown");
        }
        return module.name;
    }

    private static List<String> potionLines() {
        List<String> lines = new ArrayList<>();
        for (MobEffectInstance effect : Minecraft.getInstance().player.getActiveEffects()) {
            String time = effect.isInfiniteDuration() ? "∞" : session(Duration.ofSeconds(effect.getDuration() / 20)).replaceFirst("^0:", "");
            String level = effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : "";
            lines.add(effect.getEffect().value().getDisplayName().getString() + level + "  " + time);
        }
        return lines;
    }

    static String session(Duration elapsed) {
        long seconds = elapsed.toSeconds();
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private static String direction(String name, boolean shortForm) {
        if (shortForm) return name.isEmpty() ? "" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        return switch (name) {
            case "north" -> "North (-Z)";
            case "south" -> "South (+Z)";
            case "east" -> "East (+X)";
            case "west" -> "West (-X)";
            default -> title(name);
        };
    }

    public static String title(String id) {
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

    private static void label(GuiGraphics g, String text, int x, int y) {
        Ui.rounded(g, x, y, Ui.width(text) + 8, 15, 4, background());
        Ui.text(g, text, x + 4, y + 4, textColor());
    }

    private static void potions(GuiGraphics g, int x, int y, int width) {
        List<String> lines = potionLines();
        Ui.rounded(g, x, y, width, lines.size() * 11 + 4, 4, background());
        for (int i = 0; i < lines.size(); i++) Ui.text(g, lines.get(i), x + 4, y + 3 + i * 11, textColor());
    }

    private static void keys(GuiGraphics g, Minecraft client, int x, int y) {
        long now = System.nanoTime();
        float seconds = lastKeyFrame == 0 ? 0 : Math.min(0.1f, (now - lastKeyFrame) / 1e9f);
        lastKeyFrame = now;
        ModuleManager m = NicoClientMod.MODULES;
        key(g, 0, "W", x + 18, y, 16, 16, client.options.keyUp.isDown(), seconds);
        key(g, 1, "A", x, y + 18, 16, 16, client.options.keyLeft.isDown(), seconds);
        key(g, 2, "S", x + 18, y + 18, 16, 16, client.options.keyDown.isDown(), seconds);
        key(g, 3, "D", x + 36, y + 18, 16, 16, client.options.keyRight.isDown(), seconds);
        int next = y + 36;
        if (m.keysSpace.on()) { key(g, 4, "", x, next, 52, 10, client.options.keyJump.isDown(), seconds); next += 12; }
        if (m.keysMouse.on()) {
            key(g, 5, "LMB", x, next, 25, 16, client.options.keyAttack.isDown(), seconds);
            key(g, 6, "RMB", x + 27, next, 25, 16, client.options.keyUse.isDown(), seconds);
        }
    }

    private static void key(GuiGraphics g, int index, String label, int x, int y, int w, int h, boolean down, float seconds) {
        keyAmount[index] = Ui.approach(keyAmount[index], down ? 1 : 0, seconds, 24);
        int idle = NicoClientMod.MODULES.hudBackground.index() == 0 ? 0x700D0F1E : background();
        Ui.rounded(g, x, y, w, h, 4, Ui.mix(idle, Ui.alpha(Ui.accent(), 0.88f), keyAmount[index]));
        Ui.text(g, label, x + (w - Ui.width(label)) / 2f, y + (h - 8) / 2f, textColor());
    }

    private static void armor(GuiGraphics g, Minecraft client, int x, int y) {
        Ui.rounded(g, x - 1, y - 1, 74, 18, 4, background());
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

    /** Replaces the vanilla crosshair while Custom Crosshair is on. */
    public static void crosshair(GuiGraphics g) {
        ModuleManager m = NicoClientMod.MODULES;
        int color = switch (m.crosshairColor.index()) {
            case 1 -> Ui.accentLight();
            case 2 -> 0xFF6BF28C;
            case 3 -> 0xFFFF6B6B;
            default -> 0xFFFFFFFF;
        };
        float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
        int size = m.crosshairSize.get(), gap = m.crosshairGap.get(), style = m.crosshairStyle.get();
        if (style != 0) Ui.rounded(g, cx - 1, cy - 1, 2, 2, 1, color);
        if (style != 1) {
            Ui.rounded(g, cx - gap - size, cy - 0.5f, size, 1, 0, color);
            Ui.rounded(g, cx + gap, cy - 0.5f, size, 1, 0, color);
            Ui.rounded(g, cx - 0.5f, cy - gap - size, 1, size, 0, color);
            Ui.rounded(g, cx - 0.5f, cy + gap, 1, size, 0, color);
        }
    }
}
