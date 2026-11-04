package dev.nicoweyin.nicoclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ModuleSettingsScreen extends Screen {
    public ModuleSettingsScreen() { super(Component.literal("NicoClient settings")); }
    @Override protected void init() {
        int x = (width - 210) / 2, y = (height - 140) / 2;
        ModuleManager m = NicoClientMod.MODULES;
        addRenderableWidget(Button.builder(Component.literal("Zoom factor: " + m.zoomFactor + "x"), b -> {
            m.zoomFactor = m.zoomFactor == 2 ? 4 : m.zoomFactor == 4 ? 8 : 2; m.save(); rebuildWidgets();
        }).bounds(x + 10, y + 32, 190, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fullbright ambient: " + m.brightness + "%"), b -> {
            m.brightness = m.brightness == 60 ? 80 : m.brightness == 80 ? 100 : 60; m.save(); rebuildWidgets();
        }).bounds(x + 10, y + 56, 190, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Impact marker: " + (m.trajectoryMarker ? "ON" : "OFF")), b -> {
            m.trajectoryMarker = !m.trajectoryMarker; m.save(); rebuildWidgets();
        }).bounds(x + 10, y + 80, 190, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(x + 10, y + 106, 190, 20).build());
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = (width - 210) / 2, y = (height - 140) / 2;
        g.fill(0, 0, width, height, 0x99070817);
        g.fill(x, y, x + 210, y + 140, 0xF0161630);
        g.fill(x, y, x + 210, y + 2, 0xFF82DCE9);
        g.drawString(font, "MODULE SETTINGS", x + 10, y + 12, 0xFFBAA2F1, false);
        super.render(g, mouseX, mouseY, delta);
    }
    @Override public void onClose() { minecraft.setScreen(new NicoScreen()); }
    @Override public boolean isPauseScreen() { return false; }
}
