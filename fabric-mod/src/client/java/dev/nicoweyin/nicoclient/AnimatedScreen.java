package dev.nicoweyin.nicoclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;

/** A screen that fades and scales in and out, with frame-rate independent hover animations. */
abstract class AnimatedScreen extends Screen {
    private static final float OPEN_SECONDS = 0.18f, CLOSE_SECONDS = 0.11f;
    private final Map<Object, Float> animations = new HashMap<>();
    private float open;
    private long lastFrame;
    private boolean closing;
    private Screen next;
    protected float seconds, alpha;

    protected AnimatedScreen(String title) { super(Component.literal(title)); }

    protected abstract void draw(GuiGraphics g, int mouseX, int mouseY);

    @Override public final void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        seconds = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        open = closing ? Math.max(0, open - seconds / CLOSE_SECONDS) : Math.min(1, open + seconds / OPEN_SECONDS);
        if (closing && open <= 0) { minecraft.setScreen(next); return; }
        alpha = Ui.easeOut(open);
        float scale = 0.965f + 0.035f * alpha, cx = width / 2f, cy = height / 2f;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx, -cy);
        draw(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
        g.pose().popMatrix();
    }

    /** A soft fade instead of the vanilla background, so opening never pops. */
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, Ui.alpha(0x8A05060F, alpha));
    }

    protected final void closeTo(Screen target) {
        if (closing) return;
        next = target;
        closing = true;
    }

    protected final boolean isClosing() { return closing; }

    public final boolean isFullyOpen() { return open >= 1; }

    protected final float animate(Object key, float target, float speed) {
        float value = Ui.approach(animations.getOrDefault(key, target), target, seconds, speed);
        animations.put(key, value);
        return value;
    }

    /** A rounded button whose color follows the mouse smoothly. */
    protected final void button(GuiGraphics g, String label, float x, float y, float w, float h, int mouseX, int mouseY) {
        float hover = animate("button:" + label, !closing && Ui.inside(mouseX, mouseY, x, y, w, h) ? 1 : 0, 16);
        Ui.rounded(g, x, y, w, h, 6, Ui.alpha(Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, hover), alpha));
        Ui.text(g, label, x + (w - Ui.width(label)) / 2, y + (h - 8) / 2, Ui.alpha(Ui.mix(Ui.MUTED, Ui.TEXT, hover), alpha));
    }

    @Override public boolean isPauseScreen() { return false; }
}
