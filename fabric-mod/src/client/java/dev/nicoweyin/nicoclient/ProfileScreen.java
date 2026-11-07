package dev.nicoweyin.nicoclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;
import java.util.List;

/** Save the current modules, settings and HUD layout under a name, and switch between them in one click. */
public final class ProfileScreen extends AnimatedScreen {
    private static final int ROW = 26, ROW_GAP = 3, TITLE = 34, INPUT = 22, MAX_NAME = 24, SAVE_WIDTH = 74, LOAD_WIDTH = 44, DELETE_WIDTH = 46;
    /** The profile loaded last, shown with a mark. */
    public static String current;
    private final Screen parent;
    private String name = "", message = "";
    private int scroll;
    private List<String> names = List.of();

    public ProfileScreen(Screen parent) {
        super("Profiles");
        this.parent = parent;
        refresh();
    }

    private void refresh() { names = Profiles.names(); }
    private int panelWidth() { return Math.min(width - 16, 360); }
    private int panelHeight() { return Math.min(height - 8, 270); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private float inputY() { return top() + TITLE; }
    private float listTop() { return inputY() + INPUT + 8; }
    private float backY() { return top() + panelHeight() - 26; }
    private int visibleRows() { return Math.max(1, (int) ((backY() - 6 - listTop()) / (ROW + ROW_GAP))); }
    private float rowY(int index) { return listTop() + (index - scroll) * (ROW + ROW_GAP); }
    private float saveX() { return left() + panelWidth() - 8 - SAVE_WIDTH; }
    private float deleteX() { return left() + panelWidth() - 14 - DELETE_WIDTH; }
    private float loadX() { return deleteX() - 4 - LOAD_WIDTH; }

    /** For the client test. */
    public List<String> shownNames() { return names; }
    private boolean shown(int index) { return index >= scroll && index < scroll + visibleRows(); }
    public int[] loadCenter(String profile) {
        int index = names.indexOf(profile);
        return index < 0 || !shown(index) ? null : new int[] {Math.round(loadX() + LOAD_WIDTH / 2f), Math.round(rowY(index) + ROW / 2f)};
    }
    public int[] deleteCenter(String profile) {
        int index = names.indexOf(profile);
        return index < 0 || !shown(index) ? null : new int[] {Math.round(deleteX() + DELETE_WIDTH / 2f), Math.round(rowY(index) + ROW / 2f)};
    }
    public int[] saveCenter() { return new int[] {Math.round(saveX() + SAVE_WIDTH / 2f), Math.round(inputY() + INPUT / 2f)}; }

    @Override protected void draw(GuiGraphics g, int mouseX, int mouseY) {
        int left = left(), top = top(), panelW = panelWidth();
        Ui.rounded(g, left - 1, top - 1, panelW + 2, panelHeight() + 2, 13, Ui.alpha(Ui.mix(Ui.HAIRLINE, Ui.accent(), 0.35f), alpha));
        Ui.rounded(g, left, top, panelW, panelHeight(), 12, Ui.alpha(Ui.PANEL, alpha));
        Ui.rounded(g, left + 14, top + 13, 6, 6, 3, Ui.alpha(Ui.accentLight(), alpha));
        Ui.text(g, "Profiles", left + 25, top + 12, Ui.alpha(Ui.TEXT, alpha));
        String info = message.isEmpty() ? "Modules, settings and HUD layout" : message;
        Ui.text(g, Ui.fit(info, panelW - 90), left + panelW - 14 - Ui.width(Ui.fit(info, panelW - 90)), top + 12, Ui.alpha(message.isEmpty() ? Ui.MUTED : Ui.accentLight(), alpha));

        float inputY = inputY(), inputW = saveX() - left - 12;
        Ui.rounded(g, left + 8, inputY, inputW, INPUT, 7, Ui.alpha(Ui.SURFACE, alpha));
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        if (name.isEmpty()) Ui.text(g, "Name your setup, then Enter", left + 16, inputY + 7, Ui.alpha(Ui.MUTED, alpha));
        else Ui.text(g, Ui.fit(name, (int) inputW - 20) + (caret ? "_" : ""), left + 16, inputY + 7, Ui.alpha(Ui.TEXT, alpha));
        float saveHover = animate("save", Ui.inside(mouseX, mouseY, saveX(), inputY, SAVE_WIDTH, INPUT) ? 1 : 0, 16);
        Ui.rounded(g, saveX(), inputY, SAVE_WIDTH, INPUT, 7, Ui.alpha(Ui.mix(Ui.accent(), Ui.accentLight(), saveHover * 0.5f), alpha));
        Ui.text(g, "Save current", saveX() + (SAVE_WIDTH - Ui.width("Save current")) / 2f, inputY + 7, Ui.alpha(Ui.TEXT, alpha));

        scroll = Math.max(0, Math.min(scroll, names.size() - visibleRows()));
        if (names.isEmpty()) {
            String empty = "No profiles yet. Name one above.";
            Ui.text(g, empty, left + (panelW - Ui.width(empty)) / 2f, listTop() + 20, Ui.alpha(Ui.MUTED, alpha));
        }
        for (int i = scroll; i < Math.min(names.size(), scroll + visibleRows()); i++) {
            String profile = names.get(i);
            float y = rowY(i);
            boolean active = profile.equals(current);
            float hover = animate("row:" + profile, Ui.inside(mouseX, mouseY, left + 8, y, panelW - 16, ROW) ? 1 : 0, 16);
            Ui.rounded(g, left + 8, y, panelW - 16, ROW, 7, Ui.alpha(Ui.mix(Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, hover), Ui.accent(), active ? 0.18f : 0), alpha));
            String mark = NicoScreen.monogram(profile);
            Ui.rounded(g, left + 13, y + 4, 18, 18, 5, Ui.alpha(Ui.mix(Ui.SURFACE, Ui.accent(), Profiles.isPreset(profile) ? 0.45f : 0.25f), alpha));
            Ui.text(g, mark, left + 22 - Ui.width(mark) / 2f, y + 9, Ui.alpha(Ui.TEXT, alpha));
            Ui.text(g, Ui.fit(profile, (int) (loadX() - left - 110)), left + 38, y + 9, Ui.alpha(Ui.TEXT, alpha));
            String tag = active ? "In use" : Profiles.isPreset(profile) ? "Preset" : "";
            if (!tag.isEmpty()) {
                float tagW = Ui.width(tag) + 10, tagX = loadX() - 8 - tagW;
                Ui.rounded(g, tagX, y + 7, tagW, 12, 4, Ui.alpha(active ? Ui.mix(Ui.accent(), Ui.PANEL, 0.3f) : Ui.OFF, alpha));
                Ui.text(g, tag, tagX + 5, y + 9, Ui.alpha(active ? Ui.TEXT : Ui.MUTED, alpha));
            }
            float loadHover = animate("load:" + profile, Ui.inside(mouseX, mouseY, loadX(), y + 4, LOAD_WIDTH, ROW - 8) ? 1 : 0, 16);
            Ui.rounded(g, loadX(), y + 4, LOAD_WIDTH, ROW - 8, 5, Ui.alpha(Ui.mix(Ui.mix(Ui.SIDEBAR, Ui.accent(), 0.5f), Ui.accent(), loadHover), alpha));
            Ui.text(g, "Load", loadX() + (LOAD_WIDTH - Ui.width("Load")) / 2f, y + 9, Ui.alpha(Ui.TEXT, alpha));
            float deleteHover = animate("delete:" + profile, Ui.inside(mouseX, mouseY, deleteX(), y + 4, DELETE_WIDTH, ROW - 8) ? 1 : 0, 16);
            Ui.rounded(g, deleteX(), y + 4, DELETE_WIDTH, ROW - 8, 5, Ui.alpha(Ui.mix(Ui.SIDEBAR, 0xFFB43C4C, deleteHover), alpha));
            Ui.text(g, "Delete", deleteX() + (DELETE_WIDTH - Ui.width("Delete")) / 2f, y + 9, Ui.alpha(Ui.mix(Ui.MUTED, Ui.TEXT, deleteHover), alpha));
        }
        button(g, "Back", left + 8, backY(), panelW - 16, 20, mouseX, mouseY);
    }

    private void save() {
        String trimmed = name.trim();
        try {
            Profiles.save(trimmed);
            current = trimmed;
            message = "Saved " + trimmed;
            Notify.show("Profile saved", trimmed, Notify.GOOD);
            name = "";
            refresh();
            // Scroll so the saved profile is in view.
            int index = names.indexOf(trimmed);
            if (index >= 0 && !shown(index)) scroll = Math.max(0, index - visibleRows() + 1);
            Ui.click();
        } catch (IOException | IllegalArgumentException e) {
            message = trimmed.isEmpty() ? "Type a name first" : "Use letters, digits, spaces, - or _";
        }
    }

    public void load(String profile) {
        try {
            Profiles.load(profile);
            current = profile;
            message = "Loaded " + profile;
            Notify.show("Profile loaded", profile, Ui.accentLight());
        } catch (IOException | IllegalArgumentException e) {
            message = "Could not load " + profile;
        }
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (isClosing()) return true;
        if (e.button() == 1) { onClose(); return true; }
        if (e.button() != 0) return false;
        if (Ui.inside(e.x(), e.y(), saveX(), inputY(), SAVE_WIDTH, INPUT)) { save(); return true; }
        for (int i = scroll; i < Math.min(names.size(), scroll + visibleRows()); i++) {
            String profile = names.get(i);
            float y = rowY(i);
            if (Ui.inside(e.x(), e.y(), loadX(), y, LOAD_WIDTH, ROW)) { Ui.click(); load(profile); return true; }
            if (Ui.inside(e.x(), e.y(), deleteX(), y, DELETE_WIDTH, ROW)) {
                Ui.click();
                try { Profiles.delete(profile); message = "Deleted " + profile; if (profile.equals(current)) current = null; }
                catch (IOException | IllegalArgumentException ex) { message = "Could not delete " + profile; }
                refresh();
                return true;
            }
        }
        if (Ui.inside(e.x(), e.y(), left() + 8, backY(), panelWidth() - 16, 20)) { Ui.click(); onClose(); return true; }
        return false;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(vertical), names.size() - visibleRows()));
        return true;
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        if (e.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (!name.isEmpty()) name = name.substring(0, name.offsetByCodePoints(name.length(), -1));
            return true;
        }
        return super.keyPressed(e);
    }

    @Override public boolean charTyped(CharacterEvent e) {
        int codepoint = e.codepoint();
        if (Character.isISOControl(codepoint) || name.length() >= MAX_NAME) return false;
        name += Character.toString(codepoint);
        return true;
    }

    @Override public void onClose() { closeTo(parent); }
}
