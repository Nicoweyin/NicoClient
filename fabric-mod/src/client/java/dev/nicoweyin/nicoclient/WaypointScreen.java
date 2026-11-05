package dev.nicoweyin.nicoclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import java.util.List;

/** Lists the waypoints of this world: add one where you stand, rename by typing, recolor, hide or delete. */
public final class WaypointScreen extends AnimatedScreen {
    private static final int ROW = 24, ROW_GAP = 2, TITLE = 34, INPUT = 22, FOOTER = 28, MAX_NAME = 24, ADD_WIDTH = 64, MARK_WIDTH = 72, DELETE_WIDTH = 46;
    private final Screen parent;
    private String name = "";
    private int scroll;

    public WaypointScreen(Screen parent) {
        super("Waypoints");
        this.parent = parent;
    }

    private List<Waypoints.Waypoint> points() { return minecraft.player == null ? List.of() : Waypoints.here(minecraft); }
    private int panelWidth() { return Math.min(width - 16, 380); }
    private int panelHeight() { return Math.min(height - 8, 280); }
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

    /** For the client test. */
    public int[] markCenter() { return new int[] {Math.round(markX() + MARK_WIDTH / 2f), Math.round(inputY() + INPUT / 2f)}; }
    public int[] addCenter() { return new int[] {Math.round(addX() + ADD_WIDTH / 2f), Math.round(inputY() + INPUT / 2f)}; }
    public int[] deleteCenter(int index) { return new int[] {Math.round(deleteX() + DELETE_WIDTH / 2f), Math.round(rowY(index) + ROW / 2f)}; }
    public String typedName() { return name; }

    @Override protected void draw(GuiGraphics g, int mouseX, int mouseY) {
        int left = left(), top = top(), panelW = panelWidth();
        Ui.rounded(g, left, top, panelW, panelHeight(), 12, Ui.alpha(Ui.PANEL, alpha));
        Ui.rounded(g, left + 14, top + 13, 6, 6, 3, Ui.alpha(Ui.accentLight(), alpha));
        Ui.text(g, "Waypoints", left + 25, top + 12, Ui.alpha(Ui.TEXT, alpha));
        String world = minecraft.player == null ? "" : Waypoints.worldKey(minecraft).replaceFirst("^(server|world):", "");
        Ui.text(g, Ui.fit(world, panelW / 2), left + panelW - 14 - Ui.width(Ui.fit(world, panelW / 2)), top + 12, Ui.alpha(Ui.MUTED, alpha));

        float inputY = inputY(), inputW = addX() - left - 14;
        Ui.rounded(g, left + 8, inputY, inputW, INPUT, 7, Ui.alpha(Ui.SURFACE, alpha));
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        if (name.isEmpty()) Ui.text(g, "Type a name, then Enter", left + 16, inputY + 7, Ui.alpha(Ui.MUTED, alpha));
        else Ui.text(g, Ui.fit(name, (int) inputW - 20) + (caret ? "_" : ""), left + 16, inputY + 7, Ui.alpha(Ui.TEXT, alpha));
        float addHover = animate("add", Ui.inside(mouseX, mouseY, addX(), inputY, ADD_WIDTH, INPUT) ? 1 : 0, 16);
        Ui.rounded(g, addX(), inputY, ADD_WIDTH, INPUT, 7, Ui.alpha(Ui.mix(Ui.accent(), Ui.accentLight(), addHover * 0.5f), alpha));
        Ui.text(g, "Add here", addX() + (ADD_WIDTH - Ui.width("Add here")) / 2f, inputY + 7, Ui.alpha(Ui.TEXT, alpha));
        boolean canMark = minecraft.player != null && Waypoints.lookedAtBlock(minecraft) != null;
        float markHover = animate("mark", canMark && Ui.inside(mouseX, mouseY, markX(), inputY, MARK_WIDTH, INPUT) ? 1 : 0, 16);
        Ui.rounded(g, markX(), inputY, MARK_WIDTH, INPUT, 7, Ui.alpha(canMark ? Ui.mix(Ui.SURFACE_HOVER, Ui.accent(), 0.35f + markHover * 0.4f) : Ui.SURFACE, alpha));
        Ui.text(g, "Mark block", markX() + (MARK_WIDTH - Ui.width("Mark block")) / 2f, inputY + 7, Ui.alpha(canMark ? Ui.TEXT : Ui.MUTED, alpha));

        List<Waypoints.Waypoint> points = points();
        scroll = Math.max(0, Math.min(scroll, points.size() - visibleRows()));
        if (points.isEmpty()) {
            String empty = minecraft.player == null ? "Join a world to add waypoints." : "No waypoints in this world yet.";
            Ui.text(g, empty, left + (panelW - Ui.width(empty)) / 2f, listTop() + 20, Ui.alpha(Ui.MUTED, alpha));
        }
        for (int i = scroll; i < Math.min(points.size(), scroll + visibleRows()); i++) {
            Waypoints.Waypoint point = points.get(i);
            float y = rowY(i);
            float hover = animate(point, Ui.inside(mouseX, mouseY, left + 8, y, panelW - 16, ROW) ? 1 : 0, 16);
            Ui.rounded(g, left + 8, y, panelW - 16, ROW, 7, Ui.alpha(Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, hover), alpha));
            if (point.block) Ui.rounded(g, left + 15, y + 7, 10, 10, 2, Ui.alpha(point.color, alpha));
            else Ui.rounded(g, left + 15, y + 7, 10, 10, 5, Ui.alpha(point.color, alpha));
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
        String help = "Click the dot to recolor  \u2022  square = marked block";
        button(g, "Back", left + 8, backY(), panelW - 24 - Ui.width(help), 20, mouseX, mouseY);
        Ui.text(g, help, left + panelW - 10 - Ui.width(help), backY() + 6, Ui.alpha(Ui.MUTED, alpha));
    }

    private void add() {
        if (minecraft.player == null) return;
        Waypoints.add(minecraft, name);
        name = "";
        scroll = Math.max(0, points().size() - visibleRows());
        Ui.click();
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (isClosing()) return true;
        if (e.button() == 1) { onClose(); return true; }
        if (e.button() != 0) return false;
        if (Ui.inside(e.x(), e.y(), addX(), inputY(), ADD_WIDTH, INPUT)) { add(); return true; }
        if (Ui.inside(e.x(), e.y(), markX(), inputY(), MARK_WIDTH, INPUT)) {
            if (minecraft.player != null && Waypoints.markBlock(minecraft, name) != null) {
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
            if (Ui.inside(e.x(), e.y(), deleteX(), y, DELETE_WIDTH, ROW)) Waypoints.remove(point);
            else if (Ui.inside(e.x(), e.y(), eyeX() - 4, y, 32, ROW)) Waypoints.toggleVisible(point);
            else if (Ui.inside(e.x(), e.y(), left() + 8, y, 26, ROW)) Waypoints.cycleColor(point);
            else return true;
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
        if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) { add(); return true; }
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
