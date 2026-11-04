package dev.nicoweyin.nicoclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import java.util.List;

public final class NicoScreen extends Screen {
    private EditBox search;
    private String query = "";
    private boolean favorites;
    private int categoryIndex, page;
    private static final int HEIGHT = 234, ROWS = 5;
    private static final String[] CATEGORIES = {"All", "HUD", "Visuals", "Performance"};
    public NicoScreen() { super(Component.literal("NicoClient")); }
    @Override protected void init() {
        int left = (width - 260) / 2, top = (height - HEIGHT) / 2;
        search = addRenderableWidget(new EditBox(font, left + 12, top + 34, 154, 20, Component.literal("Search modules")));
        search.setHint(Component.literal("Search modules"));
        search.setValue(query);
        search.setResponder(value -> { query = value; page = 0; rebuildWidgets(); setFocused(search); search.moveCursorToEnd(false); });
        addRenderableWidget(Button.builder(Component.literal(favorites ? "Favorites *" : "Favorites"), b -> { favorites = !favorites; page = 0; rebuildWidgets(); }).bounds(left + 170, top + 34, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal(CATEGORIES[categoryIndex]), b -> { categoryIndex = (categoryIndex + 1) % CATEGORIES.length; page = 0; rebuildWidgets(); }).bounds(left + 12, top + 58, 100, 20).build());
        List<ModuleManager.Module> visible = NicoClientMod.MODULES.modules.stream().filter(m ->
            (!favorites || m.favorite) && (categoryIndex == 0 || m.category.equals(CATEGORIES[categoryIndex]))
            && (m.name + " " + m.category).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int pages = Math.max(1, (visible.size() + ROWS - 1) / ROWS);
        page = Math.min(page, pages - 1);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = (page + pages - 1) % pages; rebuildWidgets(); }).bounds(left + 120, top + 58, 32, 20).build());
        addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages + " >"), b -> { page = (page + 1) % pages; rebuildWidgets(); }).bounds(left + 156, top + 58, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Edit HUD"), b -> minecraft.setScreen(new HudEditor())).bounds(left + 12, top + 206, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Module settings"), b -> minecraft.setScreen(new ModuleSettingsScreen())).bounds(left + 120, top + 206, 128, 20).build());
        int y = top + 82;
        for (ModuleManager.Module module : visible.subList(page * ROWS, Math.min(visible.size(), page * ROWS + ROWS))) {
            int row = y;
            addRenderableWidget(Button.builder(Component.literal(module.category + " | " + module.name + (module.enabled ? "  ON" : "  OFF")), b -> {
                module.enabled = !module.enabled; NicoClientMod.MODULES.save(); rebuildWidgets();
            }).bounds(left + 12, row, 194, 20).build());
            addRenderableWidget(Button.builder(Component.literal(module.favorite ? "*" : "+"), b -> {
                module.favorite = !module.favorite; NicoClientMod.MODULES.save(); rebuildWidgets();
            }).bounds(left + 210, row, 38, 20).build());
            y += 24;
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int left = (width - 260) / 2, top = (height - HEIGHT) / 2;
        g.fill(0, 0, width, height, 0x99070817);
        g.fill(left, top, left + 260, top + HEIGHT, 0xF0161630);
        g.fill(left, top, left + 260, top + 2, 0xFF82DCE9);
        g.drawString(font, "NICOCLIENT", left + 12, top + 11, 0xFFBAA2F1, false);
        super.render(g, mouseX, mouseY, delta);
    }
    @Override public boolean isPauseScreen() { return false; }
}
