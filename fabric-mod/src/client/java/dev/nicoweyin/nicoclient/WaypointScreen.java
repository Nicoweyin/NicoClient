package dev.nicoweyin.nicoclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import java.util.List;

/**
 * The waypoints of this world, opened with its own key (M by default) or from the module settings.
 * Add one where you stand or on the block you look at, click one to rename it, recolor, hide or delete it,
 * and switch all markers in the world on or off at the top.
 */
public final class WaypointScreen extends AnimatedScreen {
    private static final int ROW = 24, ROW_GAP = 2, TITLE = 34, INPUT = 22, MAX_NAME = 24, ADD_WIDTH = 64, MARK_WIDTH = 72, DELETE_WIDTH = 46;
    private final Screen parent;
    private String name = "";
    /** The waypoint being renamed, or null while the input adds new ones. */
    private Waypoints.Waypoint editing;
    private int scroll;

    public WaypointScreen(Screen parent) {
        super("Waypoints");
        this.parent = parent;
    }

    private List<Waypoints.Waypoint> points() { return minecraft.player == null ? List.of() : Waypoints.here(minecraft); }
    private int panelWidth() { return Math.min(width - 16, 400); }
    private int panelHeight() { return Math.min(height - 8, 290); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private float inputY() { return top() + TITLE; }
    private float listTop() { return inputY() + INPUT + 8; }
    private float backY() { return top() + panelHeight() - 26; }
    private int visibleRows() { return Math.max(1, (int) ((backY() - 6 - listTop()) / (ROW + ROW_GAP))); }
    private float rowY(int index) { return listTop() + (index - scroll) * (ROW + ROW_GAP); }
    private float markX() { return left() + panelWidth() - 8 - MARK_WIDTH; }
    private float addX() { return markX() - 4 - ADD_WIDTH; }
    private float deleteX() { return left() + panelWidth() - 16 - DELETE_WIDTH; }
    private float eyeX() { return deleteX() - 32; }
    private float masterX() { return left() + panelWidth() - 14 - 24; }
    private float masterY() { return top() + 10; }
    private boolean shown() { return NicoClientMod.MODULES.waypoints.enabled; }

    /** For the client test. */
    public int[] markCenter() { return new int[] {Math.round(markX() + MARK_WIDTH / 2f), Math.round(inputY() + INPUT / 2f)}; }
    public int[] addCenter() { return new int[] {Math.round(addX() + ADD_WIDTH / 2f), Math.round(inputY() + INPUT / 2f)}; }
    public int[] deleteCenter(int index) { return new int[] {Math.round(deleteX() + DELETE_WIDTH / 2f), Math.round(rowY(index) + ROW / 2f)}; }
    public int[] visibleCenter(int index) { return new int[] {Math.round(eyeX() + 12), Math.round(rowY(index) + ROW / 2f)}; }
    public int[] nameCenter(int index) { return new int[] {left() + 60, Math.round(rowY(index) + ROW / 2f)}; }
    public int[] masterCenter() { return new int[] {Math.round(masterX() + 12), Math.round(masterY() + 6)}; }
    public String typedName() { return name; }
    public boolean renaming() { return editing != null; }

    @Override protected void draw(GuiGraphics g, int mouseX, int mouseY) {
        int left = left(), top = top(), panelW = panelWidth();
        Ui.rounded(g, left, top, panelW, panelHeight(), 12, Ui.alpha(Ui.PANEL, alpha));
        Ui.rounded(g, left + 14, top + 13, 6, 6, 3, Ui.alpha(Ui.accentLight(), alpha));
        Ui.text(g, "Waypoints", left + 25, top + 12, Ui.alpha(Ui.TEXT, alpha));

        // Show in world: the master switch for every marker and block box.
        String master = shown() ? "Shown in world" : "Hidden in world";
        float masterLabelX = masterX() - 6 - Ui.width(master);
        Ui.text(g, master, masterLabelX, top + 12, Ui.alpha(shown() ? Ui.TEXT : Ui.MUTED, alpha));
        Ui.toggle(g, masterX(), masterY(), animate("master", shown() ? 1 : 0, 16), alpha);
        String world = minecraft.player == null ? "" : Waypoints.worldKey(minecraft).replaceFirst("^(server|world):", "");
        float worldX = left + 33 + Ui.width("Waypoints");
        Ui.text(g, Ui.fit(world, (int) (masterLabelX - worldX - 10)), worldX, top + 12, Ui.alpha(Ui.MUTED, alpha));

        // The input adds a waypoint, or renames the one picked from the list.
        float inputY = inputY(), inputW = addX() - left - 14;
        Ui.rounded(g, left + 8, inputY, inputW, INPUT, 7, Ui.alpha(editing == null ? Ui.SURFACE : Ui.mix(Ui.SURFACE, Ui.accent(), 0.25f), alpha));
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        String placeholder = editing == null ? "Type a name, then Enter" : "New name, then Enter";
        if (name.isEmpty()) Ui.text(g, placeholder, left + 16, inputY + 7, Ui.alpha(Ui.MUTED, alpha));
        else Ui.text(g, Ui.fit(name, (int) inputW - 20) + (caret ? "_" : ""), left + 16, inputY + 7, Ui.alpha(Ui.TEXT, alpha));
        String primary = editing == null ? "Add here" : "Rename";
        float addHover = animate("add", Ui.inside(mouseX, mouseY, addX(), inputY, ADD_WIDTH, INPUT) ? 1 : 0, 16);
        Ui.rounded(g, addX(), inputY, ADD_WIDTH, INPUT, 7, Ui.alpha(Ui.mix(Ui.accent(), Ui.accentLight(), addHover * 0.5f), alpha));
        Ui.text(g, primary, addX() + (ADD_WIDTH - Ui.width(primary)) / 2f, inputY + 7, Ui.alpha(Ui.TEXT, alpha));
        boolean canMark = editing != null || minecraft.player != null && Waypoints.lookedAtBlock(minecraft) != null;
        String secondary = editing == null ? "Mark block" : "Cancel";
        float markHover = animate("mark", canMark && Ui.inside(mouseX, mouseY, markX(), inputY, MARK_WIDTH, INPUT) ? 1 : 0, 16);
        int markColor = editing != null ? Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, markHover) : canMark ? Ui.mix(Ui.SURFACE_HOVER, Ui.accent(), 0.35f + markHover * 0.4f) : Ui.SURFACE;
        Ui.rounded(g, markX(), inputY, MARK_WIDTH, INPUT, 7, Ui.alpha(markColor, alpha));
        Ui.text(g, secondary, markX() + (MARK_WIDTH - Ui.width(secondary)) / 2f, inputY + 7, Ui.alpha(canMark ? Ui.TEXT : Ui.MUTED, alpha));

        List<Waypoints.Waypoint> points = points();
        scroll = Math.max(0, Math.min(scroll, points.size() - visibleRows()));
        if (points.isEmpty()) {
            String empty = minecraft.player == null ? "Join a world to add waypoints." : "No waypoints in this world yet.";
            Ui.text(g, empty, left + (panelW - Ui.width(empty)) / 2f, listTop() + 20, Ui.alpha(Ui.MUTED, alpha));
            if (minecraft.player != null) {
                String hint = "Add one where you stand, or look at a block and Mark it.";
                Ui.text(g, hint, left + (panelW - Ui.width(hint)) / 2f, listTop() + 34, Ui.alpha(Ui.MUTED, alpha * 0.7f));
            }
        }
        for (int i = scroll; i < Math.min(points.size(), scroll + visibleRows()); i++) {
            Waypoints.Waypoint point = points.get(i);
            float y = rowY(i);
            float hover = animate(point, Ui.inside(mouseX, mouseY, left + 8, y, panelW - 16, ROW) ? 1 : 0, 16);
            int rowColor = point == editing ? Ui.mix(Ui.SURFACE_HOVER, Ui.accent(), 0.3f) : Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, hover);
            Ui.rounded(g, left + 8, y, panelW - 16, ROW, 7, Ui.alpha(rowColor, alpha));
            int dot = point.visible && shown() ? point.color : Ui.mix(point.color, Ui.SURFACE, 0.6f);
            Ui.rounded(g, left + 15, y + 7, 10, 10, point.block ? 2 : 5, Ui.alpha(dot, alpha));
            String detail = Waypoints.inCurrentDimension(minecraft, point)
                    ? point.coordinates() + "   " + (int) point.distanceTo(minecraft.player.position()) + " m"
                    : point.coordinates() + "   " + Waypoints.dimensionName(point);
            int nameSpace = (int) (eyeX() - left - 40 - Ui.width(detail) - 10);
            Ui.text(g, Ui.fit(point.name, Math.max(30, nameSpace)), left + 32, y + 8, Ui.alpha(point.visible ? Ui.TEXT : Ui.MUTED, alpha));
            Ui.text(g, detail, eyeX() - 8 - Ui.width(detail), y + 8, Ui.alpha(Ui.MUTED, alpha));
            Ui.toggle(g, eyeX(), y + 6, animate("visible:" + System.identityHashCode(point), point.visible ? 1 : 0, 16), alpha);
            float deleteHover = animate("delete:" + System.identityHashCode(point), Ui.inside(mouseX, mouseY, deleteX(), y + 3, DELETE_WIDTH, ROW - 6) ? 1 : 0, 16);
            Ui.rounded(g, deleteX(), y + 3, DELETE_WIDTH, ROW - 6, 5, Ui.alpha(Ui.mix(Ui.SIDEBAR, 0xFFB43C4C, deleteHover), alpha));
            Ui.text(g, "Delete", deleteX() + (DELETE_WIDTH - Ui.width("Delete")) / 2f, y + 8, Ui.alpha(Ui.mix(Ui.MUTED, Ui.TEXT, deleteHover), alpha));
        }
        if (points.size() > visibleRows()) {
            float trackTop = listTop(), trackHeight = visibleRows() * (ROW + ROW_GAP) - ROW_GAP;
            float thumb = Math.max(16, trackHeight * visibleRows() / points.size());
            float thumbY = trackTop + (trackHeight - thumb) * scroll / Math.max(1, points.size() - visibleRows());
            Ui.rounded(g, left + panelW - 6, thumbY, 3, thumb, 1.5f, Ui.alpha(Ui.MUTED, alpha * 0.6f));
        }
        String help = "Click a name to rename  •  dot to recolor  •  square = block";
        help = Ui.fit(help, panelW - 110);
        button(g, parent == null ? "Close" : "Back", left + 8, backY(), panelW - 24 - Ui.width(help), 20, mouseX, mouseY);
        Ui.text(g, help, left + panelW - 10 - Ui.width(help), backY() + 6, Ui.alpha(Ui.MUTED, alpha));
    }

    private void submit() {
        if (minecraft.player == null) return;
        if (editing != null) {
            if (!name.isBlank()) {
                Waypoints.rename(editing, name);
                Notify.show("Renamed to " + editing.name, editing.coordinates(), Ui.accentLight());
            }
            editing = null;
            name = "";
            Ui.click();
            return;
        }
        Waypoints.add(minecraft, name);
        name = "";
        scroll = Math.max(0, points().size() - visibleRows());
        Ui.click();
    }

    private void cancelRename() {
        editing = null;
        name = "";
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (isClosing()) return true;
        if (e.button() == 1) { onClose(); return true; }
        if (e.button() != 0) return false;
        if (Ui.inside(e.x(), e.y(), masterX() - 4, masterY() - 4, 32, 20)) {
            NicoClientMod.MODULES.waypoints.enabled = !NicoClientMod.MODULES.waypoints.enabled;
            NicoClientMod.MODULES.save();
            Ui.click();
            return true;
        }
        if (Ui.inside(e.x(), e.y(), addX(), inputY(), ADD_WIDTH, INPUT)) { submit(); return true; }
        if (Ui.inside(e.x(), e.y(), markX(), inputY(), MARK_WIDTH, INPUT)) {
            if (editing != null) { cancelRename(); Ui.click(); }
            else if (minecraft.player != null && Waypoints.markBlock(minecraft, name) != null) {
                name = "";
                scroll = Math.max(0, points().size() - visibleRows());
                Ui.click();
            }
            return true;
        }
        List<Waypoints.Waypoint> points = points();
        for (int i = scroll; i < Math.min(points.size(), scroll + visibleRows()); i++) {
            Waypoints.Waypoint point = points.get(i);
            float y = rowY(i);
            if (!Ui.inside(e.x(), e.y(), left() + 8, y, panelWidth() - 16, ROW)) continue;
            if (Ui.inside(e.x(), e.y(), deleteX(), y, DELETE_WIDTH, ROW)) {
                if (point == editing) cancelRename();
                Waypoints.remove(point);
                Notify.show("Deleted " + point.name, point.coordinates(), Notify.OFF);
            }
            else if (Ui.inside(e.x(), e.y(), eyeX() - 4, y, 32, ROW)) Waypoints.toggleVisible(point);
            else if (Ui.inside(e.x(), e.y(), left() + 8, y, 26, ROW)) Waypoints.cycleColor(point);
            else if (point == editing) cancelRename();
            else { editing = point; name = point.name; }
            Ui.click();
            return true;
        }
        if (Ui.inside(e.x(), e.y(), left() + 8, backY(), panelWidth() - 16, 20)) { Ui.click(); onClose(); return true; }
        return false;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(vertical), points().size() - visibleRows()));
        return true;
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) { submit(); return true; }
        if (e.key() == GLFW.GLFW_KEY_ESCAPE && editing != null) { cancelRename(); return true; }
        if (e.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (!name.isEmpty()) name = e.hasControlDown() ? "" : name.substring(0, name.offsetByCodePoints(name.length(), -1));
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
