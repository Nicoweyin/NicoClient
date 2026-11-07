package dev.nicoweyin.nicoclient;

import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.List;

/** Small animated cards in the bottom right corner, above the hotbar: modules switched by key, profiles loaded, waypoints saved. */
public final class Notify {
    public static final int GOOD = 0xFF35C9A0, WARN = 0xFFF2B84B, OFF = 0xFF8A90AE;
    private static final long SHOW = 2600, FADE = 250;
    private static final int WIDTH = 150, HEIGHT = 26, GAP = 4, MAX = 4;

    /** One card. Public so the game test can read what is shown. */
    public record Card(String title, String detail, int color, long start) {}

    private static final List<Card> CARDS = new ArrayList<>();

    private Notify() {}

    public static void show(String title, String detail, int color) {
        CARDS.removeIf(card -> card.title.equals(title));
        CARDS.add(new Card(title, detail, color, System.currentTimeMillis()));
        while (CARDS.size() > MAX) CARDS.remove(0);
    }

    /** A module switched on or off, with a green or grey dot. */
    public static void module(ModuleManager.Module module) {
        boolean paused = module.enabled && ServerSafe.paused(module);
        show(module.name, paused ? "On, paused by Server-safe mode" : module.enabled ? "Enabled" : "Disabled", paused ? WARN : module.enabled ? GOOD : OFF);
    }

    public static List<Card> visible() {
        long now = System.currentTimeMillis();
        CARDS.removeIf(card -> now - card.start > SHOW + FADE);
        return List.copyOf(CARDS);
    }

    public static void render(GuiGraphics g) {
        List<Card> cards = visible();
        if (cards.isEmpty()) return;
        long now = System.currentTimeMillis();
        // Newest at the bottom, older ones stacked above it.
        float y = g.guiHeight() - 48 - HEIGHT;
        for (int i = cards.size() - 1; i >= 0; i--) {
            Card card = cards.get(i);
            long age = now - card.start;
            // Slides in from the right, then fades out.
            float in = Ui.easeOut(Math.min(1, age / 220f));
            float out = age > SHOW ? 1 - Math.min(1, (age - SHOW) / (float) FADE) : 1;
            float alpha = Math.min(in, out);
            int width = Math.max(WIDTH, Math.max(Ui.width(card.title), Ui.width(card.detail)) + 30);
            float x = g.guiWidth() - width - 6 + (1 - in) * (width + 10);
            Ui.rounded(g, x, y, width, HEIGHT, 7, Ui.alpha(Ui.PANEL, alpha));
            Ui.rounded(g, x + 1, y + 1, 3, HEIGHT - 2, 1.5f, Ui.alpha(card.color, alpha));
            Ui.rounded(g, x + 10, y + 10, 6, 6, 3, Ui.alpha(card.color, alpha));
            Ui.text(g, card.title, x + 22, y + 4, Ui.alpha(Ui.TEXT, alpha));
            Ui.text(g, card.detail, x + 22, y + 14, Ui.alpha(Ui.MUTED, alpha));
            // The card's time left, as a thin line along the bottom.
            float left = 1 - Math.min(1, age / (float) SHOW);
            Ui.rounded(g, x + 6, y + HEIGHT - 2, (width - 12) * left, 1, 0, Ui.alpha(Ui.mix(card.color, Ui.PANEL, 0.4f), alpha));
            y -= (HEIGHT + GAP) * Math.min(1, alpha * 2);
        }
    }
}
