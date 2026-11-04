package dev.nicoweyin.nicoclient;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import java.util.HashMap;
import java.util.Map;

/** Shared look of NicoClient: colors, smooth rounded shapes and animation helpers. */
public final class Ui {
    public static final int PANEL = 0xF60D0F1E, SIDEBAR = 0xFF14172A, SURFACE = 0xFF171A2E, SURFACE_HOVER = 0xFF232743;
    private static final int[] ACCENTS = {0xFF736AF5, 0xFF4C8DFF, 0xFF22B8CF, 0xFF34C77B, 0xFFEC5FA6};
    public static final int TEXT = 0xFFF3F3FF, MUTED = 0xFF8A90AE, OFF = 0xFF2E3252, HAIRLINE = 0xFF2A2E4A;
    private static final Map<Integer, Identifier> CIRCLES = new HashMap<>();
    private static final Style FONT = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("nicoclient", "inter")));

    private Ui() {}

    /** Draws in real screen pixels so corners stay smooth at every GUI scale. */
    public static void rounded(GuiGraphics g, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        int left = Math.round(x * scale), top = Math.round(y * scale), right = Math.round((x + w) * scale), bottom = Math.round((y + h) * scale);
        int r = Math.min(Math.round(radius * scale), Math.min(right - left, bottom - top) / 2);
        g.pose().pushMatrix();
        g.pose().scale(1f / scale, 1f / scale);
        if (r <= 0) {
            g.fill(left, top, right, bottom, color);
        } else {
            Identifier circle = circle(r);
            int d = r * 2;
            g.fill(left + r, top, right - r, bottom, color);
            g.fill(left, top + r, left + r, bottom - r, color);
            g.fill(right - r, top + r, right, bottom - r, color);
            g.blit(RenderPipelines.GUI_TEXTURED, circle, left, top, 0, 0, r, r, d, d, color);
            g.blit(RenderPipelines.GUI_TEXTURED, circle, right - r, top, r, 0, r, r, d, d, color);
            g.blit(RenderPipelines.GUI_TEXTURED, circle, left, bottom - r, 0, r, r, r, d, d, color);
            g.blit(RenderPipelines.GUI_TEXTURED, circle, right - r, bottom - r, r, r, r, r, d, d, color);
        }
        g.pose().popMatrix();
    }

    /** An anti-aliased white circle at exact pixel size, tinted when drawn. Made once per size. */
    private static Identifier circle(int radius) {
        return CIRCLES.computeIfAbsent(radius, r -> {
            int d = r * 2;
            NativeImage image = new NativeImage(d, d, false);
            for (int y = 0; y < d; y++) {
                for (int x = 0; x < d; x++) {
                    int inside = 0;
                    for (int sy = 0; sy < 4; sy++) {
                        for (int sx = 0; sx < 4; sx++) {
                            float dx = x + (sx + 0.5f) / 4 - r, dy = y + (sy + 0.5f) / 4 - r;
                            if (dx * dx + dy * dy <= r * r) inside++;
                        }
                    }
                    image.setPixel(x, y, (inside * 255 / 16) << 24 | 0xFFFFFF);
                }
            }
            DynamicTexture texture = new DynamicTexture(() -> "nicoclient circle " + r, image);
            texture.upload();
            Identifier id = Identifier.fromNamespaceAndPath("nicoclient", "dynamic/circle_" + r);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return id;
        });
    }

    public static void toggle(GuiGraphics g, float x, float y, float amount, float alpha) {
        rounded(g, x, y, 24, 12, 6, alpha(mix(OFF, accent(), amount), alpha));
        rounded(g, x + 2 + amount * 12, y + 2, 8, 8, 4, alpha(TEXT, alpha));
    }

    public static void text(GuiGraphics g, String text, float x, float y, int color) {
        if ((color >>> 24) < 8) return;
        g.drawString(Minecraft.getInstance().font, styled(text), Math.round(x), Math.round(y), color, false);
    }

    private static Component styled(String text) { return Component.literal(text).withStyle(FONT); }

    public static int width(String text) { return Minecraft.getInstance().font.width(styled(text)); }

    public static String fit(String text, int width) {
        if (width(text) <= width) return text;
        return Minecraft.getInstance().font.substrByWidth(styled(text), Math.max(0, width - width("..."))).getString() + "...";
    }

    public static int accent() { return ACCENTS[NicoClientMod.MODULES.accent.index()]; }

    public static int accentLight() { return mix(accent(), 0xFFFFFFFF, 0.4f); }

    public static int mix(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = from >>> shift & 255, b = to >>> shift & 255;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }

    public static int alpha(int color, float alpha) {
        int value = Math.round((color >>> 24) * Math.max(0, Math.min(1, alpha)));
        return value << 24 | color & 0xFFFFFF;
    }

    /** Moves towards a target at the same speed regardless of frame rate. */
    public static float approach(float current, float target, float seconds, float speed) {
        float next = current + (target - current) * (1 - (float) Math.exp(-speed * seconds));
        return Math.abs(target - next) < 0.001f ? target : next;
    }

    public static float easeOut(float t) {
        float inverse = 1 - t;
        return 1 - inverse * inverse * inverse;
    }

    public static boolean inside(double px, double py, float x, float y, float w, float h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public static void click() {
        if (!NicoClientMod.MODULES.clickSounds.on()) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.3f));
    }
}
