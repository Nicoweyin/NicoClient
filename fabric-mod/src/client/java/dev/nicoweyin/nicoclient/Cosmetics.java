package dev.nicoweyin.nicoclient;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Capes, wings and halos for your own player. The textures are painted by code, so every style stays sharp
 * and needs no image files. Only you see them: other players would need a shared NicoClient service.
 */
public final class Cosmetics {
    public static final String[] CAPES = {"Off", "Nebula", "Flame", "Ocean", "Nico", "Aurora", "Sakura", "Rainbow", "Ice", "Gold", "Carbon", "Lava"};
    public static final String[] WINGS = {"Off", "Angel", "Dragon", "Neon", "Butterfly", "Phoenix", "Crystal", "Bat"};
    public static final String[] HALOS = {"Off", "Gold", "Accent", "Rainbow", "Ice"};
    public static final String[] TRAILS = {"Off", "Hearts", "Sparkles", "Flames", "Notes", "Snow"};
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();

    private Cosmetics() {}

    public static int cape() { return NicoClientMod.MODULES.capeStyle.get(); }
    public static int wings() { return NicoClientMod.MODULES.wingStyle.get(); }
    public static int halo() { return NicoClientMod.MODULES.haloStyle.get(); }
    public static int trail() { return NicoClientMod.MODULES.trailStyle.get(); }

    /** Wings that glow in the dark. */
    public static boolean glowingWings(int style) { return style == 3 || style == 5 || style == 6; }

    private static int hue(float h, float saturation, float brightness) { return 0xFF000000 | java.awt.Color.HSBtoRGB(h, saturation, brightness); }

    private interface Painter { int color(int x, int y); }

    /** Paints a texture once per style and accent color, then reuses it. */
    private static Identifier texture(String key, int width, int height, Painter painter) {
        return TEXTURES.computeIfAbsent(key, k -> {
            NativeImage image = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setPixel(x, y, painter.color(x, y));
            DynamicTexture texture = new DynamicTexture(() -> "nicoclient " + k, image);
            texture.upload();
            Identifier id = Identifier.fromNamespaceAndPath("nicoclient", "dynamic/" + k.replaceAll("[^a-z0-9_]", "_"));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return id;
        });
    }

    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    // ---- Capes: vanilla's 64x32 cape layout, so Minecraft's own cape with its physics wears them.

    public static Identifier capeTexture(int style) {
        int accent = Ui.accent();
        return texture("cape_" + style + "_" + Integer.toHexString(accent), 64, 32, (x, y) -> capePixel(style, x, y, accent));
    }

    private static int capePixel(int style, int x, int y, int accent) {
        // The cape's two large faces are 10x16 at (1,1) and (12,1); the emblem goes on both.
        boolean face = y >= 1 && y < 17 && ((x >= 1 && x < 11) || (x >= 12 && x < 22));
        int fx = x >= 12 ? x - 12 : x - 1, fy = y - 1;
        float v = Math.max(0, Math.min(1, fy / 15f));
        return switch (style) {
            case 1 -> { // Nebula: deep violet with pink clouds and stars.
                float cloud = (float) (0.5 + 0.5 * Math.sin(fx * 0.9 + fy * 0.45) * Math.cos(fy * 0.3 - fx * 0.2));
                int base = Ui.mix(0xFF140F3A, 0xFF3B1A6E, v);
                int color = Ui.mix(base, 0xFFB04BD8, cloud * 0.35f);
                yield hash(x, y, 7) % 23 == 0 ? Ui.mix(color, 0xFFFFFFFF, 0.85f) : color;
            }
            case 2 -> { // Flame: embers at the top, bright fire rising from the bottom.
                float flame = v + 0.18f * (float) Math.sin(fx * 1.3 + fy * 0.2) + (hash(x, y, 3) % 100) / 900f;
                int color = flame < 0.45f ? Ui.mix(0xFF1E0804, 0xFF7A1A08, flame / 0.45f)
                        : flame < 0.8f ? Ui.mix(0xFF7A1A08, 0xFFFF6A14, (flame - 0.45f) / 0.35f)
                        : Ui.mix(0xFFFF6A14, 0xFFFFD25A, Math.min(1, (flame - 0.8f) / 0.3f));
                yield color;
            }
            case 3 -> { // Ocean: waves of teal over deep blue.
                float wave = (float) (0.5 + 0.5 * Math.sin(fy * 1.1 + Math.sin(fx * 0.8) * 1.6));
                int color = Ui.mix(Ui.mix(0xFF062A4A, 0xFF0B5C8A, v), 0xFF2CD3D6, wave * 0.45f);
                yield fy % 5 == 0 && wave > 0.8f ? Ui.mix(color, 0xFFFFFFFF, 0.4f) : color;
            }
            case 5 -> { // Aurora: green and violet lights waving over a night sky.
                float band = (float) (0.5 + 0.5 * Math.sin(fx * 0.7 + fy * 0.35 + Math.sin(fy * 0.5) * 1.2));
                int light = Ui.mix(0xFF2BE3A0, 0xFF9B5CF6, v);
                yield Ui.mix(Ui.mix(0xFF050B1E, 0xFF0B1A3A, v), light, band * 0.75f);
            }
            case 6 -> { // Sakura: soft pink with falling petals.
                int base = Ui.mix(0xFFFFD6E6, 0xFFF59AC0, v);
                int petal = hash(x / 2, y / 2, 11) % 9;
                yield petal == 0 ? 0xFFFFFFFF : petal == 1 ? 0xFFE0558F : base;
            }
            case 7 -> hue((fy / 16f + fx * 0.012f) % 1f, 0.75f, 1f); // Rainbow
            case 8 -> { // Ice: pale blue with crystal lines.
                boolean crack = (fx + fy * 2) % 7 == 0 || (fx * 3 - fy + 40) % 11 == 0;
                int base = Ui.mix(0xFFE4F6FF, 0xFF7CC8F0, v);
                yield crack ? Ui.mix(base, 0xFFFFFFFF, 0.8f) : base;
            }
            case 9 -> { // Gold: polished gold with a diagonal shine.
                float shine = (float) Math.max(0, 1 - Math.abs((fx + fy) - 12) / 3.0);
                int base = (fx + fy) % 4 == 0 ? 0xFFB8860B : Ui.mix(0xFFFFD45A, 0xFFE0A21C, v);
                yield Ui.mix(base, 0xFFFFFFFF, shine * 0.6f);
            }
            case 10 -> { // Carbon: woven dark fibre with an accent stripe.
                if (face && (fx == 4 || fx == 5)) yield accent;
                yield ((fx / 2 + fy / 2) % 2 == 0) ? 0xFF1A1C22 : 0xFF2A2D36;
            }
            case 11 -> { // Lava: black rock with glowing cracks.
                double n = Math.sin(fx * 1.7 + Math.sin(fy * 0.9) * 2) + Math.sin(fy * 1.3 - fx * 0.6);
                float crack = (float) Math.max(0, 1 - Math.abs(n) * 2.2);
                yield Ui.mix(0xFF1C1210, Ui.mix(0xFFFF4A0A, 0xFFFFC23A, crack), crack);
            }
            default -> { // Nico: dark panel, accent border and an N emblem.
                if (!face) yield 0xFF0D0F1E;
                boolean border = fx == 0 || fx == 9 || fy == 0 || fy == 15;
                if (border) yield accent;
                boolean emblem = fy >= 4 && fy <= 11 && (fx == 2 || fx == 7 || (fx >= 3 && fx <= 6 && fy - 4 >= (fx - 2) * 2 - 1 && fy - 4 <= (fx - 2) * 2));
                yield emblem ? Ui.mix(accent, 0xFFFFFFFF, 0.45f) : Ui.mix(0xFF0D0F1E, accent, v * 0.25f);
            }
        };
    }

    // ---- Wings: one 64x64 texture, drawn on a single plane per side. u runs from the back to the tip.

    public static Identifier wingTexture(int style) {
        int accent = Ui.accent();
        return texture("wing_" + style + "_" + Integer.toHexString(accent), 64, 64, (x, y) -> wingPixel(style, x / 63f, y / 63f, x, y, accent));
    }

    private static float wingTop(int style, float u) {
        if (style == 4) return 0.08f + 0.25f * (float) Math.pow(u - 0.45f, 2) * 4 - 0.1f; return 0.22f - 0.24f * (float) Math.sin(u * Math.PI * 0.75); }

    private static float wingBottom(int style, float u) {
        if (style == 4) return 0.92f - 0.3f * u - 0.18f * (float) Math.sin(u * Math.PI);
        float scallop = 0.05f * (float) Math.abs(Math.sin(u * Math.PI * 7));
        return 1.0f - 0.58f * u - scallop;
    }

    private static int wingPixel(int style, float u, float v, int x, int y, int accent) {
        float top = wingTop(style, u), bottom = wingBottom(style, u);
        if (u > 0.98f || v < top || v > bottom) return 0;
        float depth = (v - top) / Math.max(0.01f, bottom - top); // 0 at the upper edge, 1 at the feather tips
        float edge = Math.min(Math.min(v - top, bottom - v) * 63, (0.98f - u) * 63);
        return switch (style) {
            case 1 -> { // Angel: white feathers, soft blue at the tips, feather lines near the bottom.
                int color = Ui.mix(0xFFFFFFFF, 0xFFCFE3FF, u * 0.6f + depth * 0.3f);
                boolean feather = depth > 0.45f && (x + (int) (depth * 6)) % 5 == 0;
                yield feather ? Ui.mix(color, 0xFF9DB6D8, 0.5f) : edge < 1.2f ? Ui.mix(color, 0xFFAFC4E0, 0.6f) : color;
            }
            case 2 -> { // Dragon: dark membrane between bright bones.
                boolean bone = Math.abs(depth * 4 - Math.round(depth * 4)) < 0.06f && depth < 0.9f || v - top < 0.035f;
                int membrane = Ui.mix(0xFF2A0D3C, 0xFF8E1B2E, u * 0.7f + depth * 0.3f);
                yield bone ? 0xFF1A0A12 : edge < 1.2f ? Ui.mix(membrane, 0xFF140610, 0.6f) : Ui.alpha(membrane, 0.93f);
            }
            case 4 -> { // Butterfly: bright colors with eye spots and dark edges.
                if (edge < 1.8f) yield 0xFF1A1020;
                float spotX = u - 0.62f, spotY = depth - 0.45f;
                float spot = spotX * spotX * 4 + spotY * spotY;
                if (spot < 0.012f) yield 0xFF1A1020;
                if (spot < 0.03f) yield 0xFFFFFFFF;
                yield hue(0.55f + u * 0.3f + depth * 0.1f, 0.7f, 1f);
            }
            case 5 -> { // Phoenix: fire from gold at the root to red at the tips.
                int fire = u < 0.5f ? Ui.mix(0xFFFFE27A, 0xFFFF8A1E, u * 2) : Ui.mix(0xFFFF8A1E, 0xFFD3261A, (u - 0.5f) * 2);
                boolean feather = depth > 0.4f && (x + (int) (depth * 8)) % 4 == 0;
                yield feather ? Ui.mix(fire, 0xFF7A1408, 0.45f) : edge < 1.2f ? 0xFFFFF1B0 : fire;
            }
            case 6 -> { // Crystal: see-through ice with bright facets.
                boolean facet = (x + y) % 9 == 0 || (x - y + 64) % 13 == 0;
                if (edge < 1.4f) yield 0xFFFFFFFF;
                yield facet ? Ui.alpha(0xFFE8FBFF, 0.9f) : Ui.alpha(Ui.mix(0xFF9BE3FF, 0xFF4C8DFF, u), 0.55f);
            }
            case 7 -> { // Bat: grey membrane with dark finger bones.
                boolean bone = Math.abs(depth * 3 - Math.round(depth * 3)) < 0.05f && depth < 0.85f || v - top < 0.03f;
                yield bone ? 0xFF15151A : edge < 1.2f ? 0xFF202026 : Ui.mix(0xFF3A3A44, 0xFF55505E, u);
            }
            default -> { // Neon: dark glass with a glowing accent outline and stripes.
                int glow = Ui.mix(accent, 0xFF6BF2FF, u);
                if (edge < 1.6f) yield glow;
                boolean stripe = Math.abs(depth * 5 - Math.round(depth * 5)) < 0.05f;
                yield stripe ? Ui.alpha(glow, 0.85f) : Ui.alpha(Ui.mix(0xFF0B0A1C, accent, 0.18f), 0.82f);
            }
        };
    }

    // ---- Halo: a soft ring.

    public static Identifier haloTexture(int style) {
        int accent = Ui.accent();
        int color = style == 1 ? 0xFFFFD45A : style == 4 ? 0xFF9BE8FF : Ui.accentLight();
        return texture("halo_" + style + "_" + Integer.toHexString(accent), 32, 32, (x, y) -> {
            float dx = (x + 0.5f) / 16 - 1, dy = (y + 0.5f) / 16 - 1;
            float r = (float) Math.sqrt(dx * dx + dy * dy);
            float ring = 1 - Math.abs(r - 0.78f) / 0.14f;
            if (ring <= 0) return 0;
            int tint = style == 3 ? hue((float) ((Math.atan2(dy, dx) / (2 * Math.PI)) + 1) % 1f, 0.7f, 1f) : color;
            return Ui.alpha(Ui.mix(tint, 0xFFFFFFFF, ring * 0.5f), Math.min(1, ring * 1.6f));
        });
    }
}
