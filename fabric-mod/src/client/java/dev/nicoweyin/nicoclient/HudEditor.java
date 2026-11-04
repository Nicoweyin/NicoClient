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
        for (ModuleManager.HudModule module : NicoClientMod.MODULES.hudModules) {
            if (!module.enabled) continue;
            any = true;
            int w = HudRenderer.width(module), x = clampX(module.x, w), y = clampY(module.y, module.height);
            boolean active = module == dragging || (dragging == null && Ui.inside(mouseX, mouseY, x, y, w, module.height));
            float amount = Ui.approach(highlight.getOrDefault(module, 0f), active ? 1 : 0, seconds, 18);
            highlight.put(module, amount);
            Ui.rounded(g, x - 1, y - 1, w + 2, module.height + 2, 5, Ui.alpha(Ui.ACCENT_LIGHT, 0.25f + 0.75f * amount));
            Ui.rounded(g, x, y, w, module.height, 4, Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, amount));
            String label = module == NicoClientMod.MODULES.keys ? "WASD" : module == NicoClientMod.MODULES.armor ? "Armor" : HudRenderer.text(module);
            Ui.text(g, label, x + 4, y + (module.height - 8) / 2f, Ui.TEXT);
        }
        String help = any ? "Drag to move   •   Esc to return" : "Enable a HUD module first   •   Esc to return";
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

    private int clampX(int x, int w) { return Math.max(0, Math.min(width - w, x)); }
    private int clampY(int y, int h) { return Math.max(0, Math.min(height - h, y)); }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (super.mouseClicked(e, doubled)) return true;
        if (e.button() != 0) return false;
        if (Ui.inside(e.x(), e.y(), resetX(), resetY(), RESET_WIDTH, RESET_HEIGHT)) {
            NicoClientMod.MODULES.hudModules.forEach(ModuleManager.HudModule::resetPosition);
            NicoClientMod.MODULES.save();
            Ui.click();
            return true;
        }
        // Search from the top: later modules are drawn over earlier ones.
        var modules = NicoClientMod.MODULES.hudModules;
        for (int i = modules.size() - 1; i >= 0; i--) {
            ModuleManager.HudModule module = modules.get(i);
            if (!module.enabled) continue;
            int w = HudRenderer.width(module), x = clampX(module.x, w), y = clampY(module.y, module.height);
            if (Ui.inside(e.x(), e.y(), x, y, w, module.height)) {
                dragging = module; offsetX = (int) e.x() - x; offsetY = (int) e.y() - y;
                return true;
            }
        }
        return false;
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(e, dx, dy);
        dragging.x = clampX((int) e.x() - offsetX, HudRenderer.width(dragging));
        dragging.y = clampY((int) e.y() - offsetY, dragging.height);
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging != null) { dragging = null; NicoClientMod.MODULES.save(); return true; }
        return super.mouseReleased(e);
    }

    @Override public void onClose() { NicoClientMod.MODULES.save(); minecraft.setScreen(new NicoScreen()); }
    @Override public boolean isPauseScreen() { return false; }
}
