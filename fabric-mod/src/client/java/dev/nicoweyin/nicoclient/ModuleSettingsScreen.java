package dev.nicoweyin.nicoclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/** Everything configurable about one module: switch, hotkey and its own settings. */
public final class ModuleSettingsScreen extends AnimatedScreen {
    private static final int ROW = 24, ROW_GAP = 2, TITLE = 34, FOOTER = 28;
    private final ModuleManager.Module module;
    private final Screen parent;
    private final List<Object> rows = new ArrayList<>();
    private boolean capturing;
    private static final Object ENABLED = "enabled", HOTKEY = "hotkey";

    public ModuleSettingsScreen(ModuleManager.Module module, Screen parent) {
        super(module.name);
        this.module = module;
        this.parent = parent;
        if (module != NicoClientMod.MODULES.client) { rows.add(ENABLED); rows.add(HOTKEY); }
        rows.addAll(module.settings);
    }

    private int panelWidth() { return Math.min(width - 16, 340); }
    private int panelHeight() { return Math.min(height - 8, TITLE + rows.size() * (ROW + ROW_GAP) + FOOTER + 4); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private float rowY(int index) { return top() + TITLE + index * (ROW + ROW_GAP); }
    private float backY() { return top() + panelHeight() - 26; }
    private int rowRight() { return left() + panelWidth() - 8; }

    private boolean isZoom() { return module == NicoClientMod.MODULES.zoom; }
    private String hotkeyLabel() { return module == NicoClientMod.MODULES.copyCoords ? "Copy key" : isZoom() ? "Hold key" : "Toggle key"; }

    private String hotkeyName() {
        if (capturing) return "Press a key...";
        if (isZoom()) return NicoClientMod.zoomKey.getTranslatedKeyMessage().getString();
        return module.hotkey < 0 ? "Not set" : InputConstants.Type.KEYSYM.getOrCreate(module.hotkey).getDisplayName().getString();
    }

    /** Screen position of a row and of one choice in it, for the game test. */
    public int[] rowCenter(Object row) { return new int[] {left() + 60, Math.round(rowY(rows.indexOf(row))) + ROW / 2}; }

    public int[] segmentCenter(ModuleManager.Setting setting, int option) {
        return new int[] {Math.round(segmentX(setting, option)) + segmentWidth(setting.labels[option]) / 2, Math.round(rowY(rows.indexOf(setting))) + ROW / 2};
    }

    public static final Object HOTKEY_ROW = HOTKEY;

    private static int segmentWidth(String label) { return Math.max(26, Ui.width(label) + 12); }

    private int segmentsWidth(ModuleManager.Setting setting) {
        int total = 0;
        for (String label : setting.labels) total += segmentWidth(label);
        return total;
    }

    /** The left edge of one option in a segmented choice. */
    private float segmentX(ModuleManager.Setting setting, int option) {
        float x = rowRight() - 8 - segmentsWidth(setting);
        for (int i = 0; i < option; i++) x += segmentWidth(setting.labels[i]);
        return x;
    }

    @Override protected void draw(GuiGraphics g, int mouseX, int mouseY) {
        int left = left(), top = top(), panelW = panelWidth();
        Ui.rounded(g, left, top, panelW, panelHeight(), 12, Ui.alpha(Ui.PANEL, alpha));
        Ui.rounded(g, left + 14, top + 13, 6, 6, 3, Ui.alpha(Ui.accentLight(), alpha));
        Ui.text(g, module.name, left + 25, top + 12, Ui.alpha(Ui.TEXT, alpha));
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            float y = rowY(i);
            boolean clickable = row == ENABLED || row == HOTKEY || (row instanceof ModuleManager.Setting setting && setting.isSwitch());
            float hover = animate("row:" + i, clickable && !isClosing() && Ui.inside(mouseX, mouseY, left + 8, y, panelW - 16, ROW) ? 1 : 0, 16);
            Ui.rounded(g, left + 8, y, panelW - 16, ROW, 7, Ui.alpha(Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, hover), alpha));
            if (row == ENABLED) {
                Ui.text(g, "Enabled", left + 18, y + 8, Ui.alpha(Ui.TEXT, alpha));
                Ui.toggle(g, rowRight() - 32, y + 6, animate("enabled", module.enabled ? 1 : 0, 16), alpha);
            } else if (row == HOTKEY) {
                Ui.text(g, hotkeyLabel(), left + 18, y + 8, Ui.alpha(Ui.TEXT, alpha));
                String name = hotkeyName();
                float w = Ui.width(name) + 14;
                float pulse = animate("capture", capturing ? 1 : 0, 16);
                Ui.rounded(g, rowRight() - 8 - w, y + 4, w, 16, 5, Ui.alpha(Ui.mix(Ui.SIDEBAR, Ui.accent(), pulse), alpha));
                Ui.text(g, name, rowRight() - 8 - w + 7, y + 8, Ui.alpha(Ui.mix(Ui.MUTED, Ui.TEXT, Math.max(pulse, hover)), alpha));
            } else if (row instanceof ModuleManager.Setting setting) {
                Ui.text(g, setting.label, left + 18, y + 8, Ui.alpha(Ui.TEXT, alpha));
                if (setting.isSwitch()) {
                    Ui.toggle(g, rowRight() - 32, y + 6, animate(setting, setting.on() ? 1 : 0, 16), alpha);
                } else {
                    Ui.rounded(g, segmentX(setting, 0) - 2, y + 3, segmentsWidth(setting) + 4, 18, 6, Ui.alpha(Ui.SIDEBAR, alpha));
                    float position = animate(setting, segmentX(setting, setting.index()), 20);
                    float selectedWidth = animate(setting.label, segmentWidth(setting.labels[setting.index()]), 20);
                    Ui.rounded(g, position, y + 5, selectedWidth, 14, 5, Ui.alpha(Ui.accent(), alpha));
                    for (int o = 0; o < setting.labels.length; o++) {
                        String label = setting.labels[o];
                        Ui.text(g, label, segmentX(setting, o) + (segmentWidth(label) - Ui.width(label)) / 2f, y + 8, Ui.alpha(o == setting.index() ? Ui.TEXT : Ui.MUTED, alpha));
                    }
                }
            }
        }
        if (capturing) {
            String help = "Esc or Backspace removes the key";
            Ui.text(g, help, left + 12, backY() + 6, Ui.alpha(Ui.MUTED, alpha));
        } else {
            button(g, "Back", left + 8, backY(), panelW - 16, 20, mouseX, mouseY);
        }
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (isClosing()) return true;
        if (capturing) { capturing = false; return true; }
        if (e.button() == 1) { onClose(); return true; }
        if (e.button() != 0) return false;
        ModuleManager manager = NicoClientMod.MODULES;
        for (int i = 0; i < rows.size(); i++) {
            float y = rowY(i);
            if (!Ui.inside(e.x(), e.y(), left() + 8, y, panelWidth() - 16, ROW)) continue;
            Object row = rows.get(i);
            if (row == ENABLED) module.enabled = !module.enabled;
            else if (row == HOTKEY) capturing = true;
            else if (row instanceof ModuleManager.Setting setting) {
                if (setting.isSwitch()) setting.flip();
                else {
                    int picked = -1;
                    for (int o = 0; o < setting.labels.length; o++) {
                        if (Ui.inside(e.x(), e.y(), segmentX(setting, o), y, segmentWidth(setting.labels[o]), ROW)) picked = o;
                    }
                    if (picked < 0) return true;
                    setting.selectIndex(picked);
                }
            }
            manager.save();
            Ui.click();
            return true;
        }
        if (Ui.inside(e.x(), e.y(), left() + 8, backY(), panelWidth() - 16, 20)) { Ui.click(); onClose(); return true; }
        return false;
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (!capturing) return super.keyPressed(e);
        capturing = false;
        boolean clear = e.key() == GLFW.GLFW_KEY_ESCAPE || e.key() == GLFW.GLFW_KEY_BACKSPACE;
        if (isZoom()) {
            KeyMapping key = NicoClientMod.zoomKey;
            key.setKey(clear ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(e.key()));
            KeyMapping.resetMapping();
            minecraft.options.save();
        } else {
            module.hotkey = clear ? -1 : e.key();
            NicoClientMod.MODULES.save();
        }
        return true;
    }

    @Override public void onClose() { closeTo(parent); }
}
