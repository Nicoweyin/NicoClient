package dev.nicoweyin.nicoclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;

public final class HudEditor extends Screen {
    private static final int RESET_WIDTH = 78, RESET_HEIGHT = 20;
    private final Map<ModuleManager.HudModule, Float> highlight = new HashMap<>();
    private ModuleManager.HudModule dragging;
    private int offsetX, offsetY;
    private long lastFrame;
    private float resetHover;

    public HudEditor() { super(Component.literal("NicoClient HUD Editor")); }

    private float resetX() { return width - RESET_WIDTH - 8; }
    private float resetY() { return height - RESET_HEIGHT - 8; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float seconds = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        g.fill(0, 0, width, height, 0x3005060F);
        boolean any = false;
        float scale = HudRenderer.scale(), scaledX = mouseX / scale, scaledY = mouseY / scale;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        for (ModuleManager.HudModule module : NicoClientMod.MODULES.hudModules) {
            if (!module.enabled) continue;
            any = true;
            int w = HudRenderer.width(module), h = HudRenderer.height(module), x = clampX(module.x, w), y = clampY(module.y, h);
            boolean active = module == dragging || (dragging == null && Ui.inside(scaledX, scaledY, x, y, w, h));
            float amount = Ui.approach(highlight.getOrDefault(module, 0f), active ? 1 : 0, seconds, 18);
            highlight.put(module, amount);
            Ui.rounded(g, x - 1, y - 1, w + 2, h + 2, 5, Ui.alpha(Ui.accentLight(), 0.25f + 0.75f * amount));
            Ui.rounded(g, x, y, w, h, 4, Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, amount));
            String label = module == NicoClientMod.MODULES.keys ? "WASD" : module == NicoClientMod.MODULES.armor ? "Armor" : HudRenderer.hasContent(module) && module != NicoClientMod.MODULES.potions ? HudRenderer.text(module) : module.name;
            Ui.text(g, Ui.fit(label, w - 6), x + 4, y + Math.min(4, (h - 8) / 2f), Ui.TEXT);
        }
        g.pose().popMatrix();
        String help = any ? "Drag to move   •   Right click to configure   •   Esc to return" : "Enable a HUD module first   •   Esc to return";
        float helpWidth = Ui.width(help) + 20, helpY = resetY();
        Ui.rounded(g, 8, helpY, helpWidth, RESET_HEIGHT, 7, Ui.PANEL);
        Ui.text(g, help, 18, helpY + 6, Ui.MUTED);
        resetHover = Ui.approach(resetHover, Ui.inside(mouseX, mouseY, resetX(), resetY(), RESET_WIDTH, RESET_HEIGHT) ? 1 : 0, seconds, 16);
        Ui.rounded(g, resetX(), resetY(), RESET_WIDTH, RESET_HEIGHT, 7, Ui.mix(Ui.PANEL, Ui.SURFACE_HOVER, resetHover));
        String reset = "Reset layout";
        Ui.text(g, reset, resetX() + (RESET_WIDTH - Ui.width(reset)) / 2f, resetY() + 6, Ui.mix(Ui.MUTED, Ui.TEXT, resetHover));
        super.render(g, mouseX, mouseY, delta);
    }

    // Keep the world sharp: positions are judged against the real game view.
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {}

    private int clampX(int x, int w) { return Math.max(0, Math.min((int) (width / HudRenderer.scale()) - w, x)); }
    private int clampY(int y, int h) { return Math.max(0, Math.min((int) (height / HudRenderer.scale()) - h, y)); }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (super.mouseClicked(e, doubled)) return true;
        if (e.button() != 0 && e.button() != 1) return false;
        if (e.button() == 0 && Ui.inside(e.x(), e.y(), resetX(), resetY(), RESET_WIDTH, RESET_HEIGHT)) {
            NicoClientMod.MODULES.hudModules.forEach(ModuleManager.HudModule::resetPosition);
            NicoClientMod.MODULES.save();
            Ui.click();
            return true;
        }
        // Search from the top: later modules are drawn over earlier ones.
        var modules = NicoClientMod.MODULES.hudModules;
        float scale = HudRenderer.scale();
        double px = e.x() / scale, py = e.y() / scale;
        for (int i = modules.size() - 1; i >= 0; i--) {
            ModuleManager.HudModule module = modules.get(i);
            if (!module.enabled) continue;
            int w = HudRenderer.width(module), h = HudRenderer.height(module), x = clampX(module.x, w), y = clampY(module.y, h);
            if (Ui.inside(px, py, x, y, w, h)) {
                if (e.button() == 1) {
                    Ui.click();
                    minecraft.setScreen(new ModuleSettingsScreen(module, this));
                } else {
                    dragging = module; offsetX = (int) px - x; offsetY = (int) py - y;
                }
                return true;
            }
        }
        return false;
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(e, dx, dy);
        float scale = HudRenderer.scale();
        dragging.x = clampX((int) (e.x() / scale) - offsetX, HudRenderer.width(dragging));
        dragging.y = clampY((int) (e.y() / scale) - offsetY, HudRenderer.height(dragging));
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging != null) { dragging = null; NicoClientMod.MODULES.save(); return true; }
        return super.mouseReleased(e);
    }

    @Override public void onClose() { NicoClientMod.MODULES.save(); minecraft.setScreen(new NicoScreen()); }
    @Override public boolean isPauseScreen() { return false; }
}
