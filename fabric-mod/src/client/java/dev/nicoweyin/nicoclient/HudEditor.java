package dev.nicoweyin.nicoclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudEditor extends Screen {
    private ModuleManager.HudModule dragging;
    private int offsetX, offsetY;
    public HudEditor() { super(Component.literal("NicoClient HUD Editor")); }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Reset layout"), b -> {
            NicoClientMod.MODULES.hudModules.forEach(ModuleManager.HudModule::resetPosition);
            NicoClientMod.MODULES.save();
        }).bounds(width - 88, height - 24, 80, 20).build());
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x66070817);
        boolean any = false;
        for (ModuleManager.HudModule module : NicoClientMod.MODULES.hudModules) {
            if (!module.enabled) continue;
            any = true;
            int w = HudRenderer.width(module), x = clampX(module.x, w), y = clampY(module.y, module.height);
            boolean hovered = module == dragging || inside(mouseX, mouseY, x, y, w, module.height);
            g.fill(x, y, x + w, y + module.height, hovered ? 0xE04A4580 : 0xD0272546);
            g.drawString(font, module == NicoClientMod.MODULES.keys ? "WASD" : module == NicoClientMod.MODULES.armor ? "Armor" : HudRenderer.text(module), x + 4, y + 4, 0xFFB2F4F8, false);
        }
        String help = any ? "HUD EDITOR  |  Drag to move  |  Esc to return" : "Enable a HUD module first  |  Esc to return";
        g.drawString(font, help, 8, height - 16, 0xFFB2F4F8, false);
        super.render(g, mouseX, mouseY, delta);
    }
    // Keep the world sharp: positions are judged against the real game view.
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {}
    private int clampX(int x, int w) { return Math.max(0, Math.min(width - w, x)); }
    private int clampY(int y, int h) { return Math.max(0, Math.min(height - h, y)); }
    private static boolean inside(double px, double py, int x, int y, int w, int h) { return px >= x && px < x + w && py >= y && py < y + h; }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (super.mouseClicked(e, doubled)) return true;
        if (e.button() != 0) return false;
        // Search from the top: later modules are drawn over earlier ones.
        var modules = NicoClientMod.MODULES.hudModules;
        for (int i = modules.size() - 1; i >= 0; i--) {
            ModuleManager.HudModule module = modules.get(i);
            if (!module.enabled) continue;
            int w = HudRenderer.width(module), x = clampX(module.x, w), y = clampY(module.y, module.height);
            if (inside(e.x(), e.y(), x, y, w, module.height)) {
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
