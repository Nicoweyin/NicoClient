package dev.nicoweyin.nicoclient;

import dev.nicoweyin.nicoclient.mixin.InventoryScreenAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Pick a cape, wings, halo and trail, with a turning 3D preview of your own player. */
public final class CosmeticsScreen extends AnimatedScreen {
    private static final int PREVIEW = 128, ROW = 30, ROW_GAP = 4, ARROW = 20, FOOTER = 28;
    private final Screen parent;
    private float rotation = 150;
    private boolean dragging;
    private double lastMouseX;

    public CosmeticsScreen(Screen parent) {
        super("Cosmetics");
        this.parent = parent;
    }

    private record Row(String label, ModuleManager.Setting setting, String[] names) {}

    private Row[] rows() {
        ModuleManager m = NicoClientMod.MODULES;
        return new Row[] {
            new Row("Cape", m.capeStyle, Cosmetics.CAPES), new Row("Wings", m.wingStyle, Cosmetics.WINGS),
            new Row("Halo", m.haloStyle, Cosmetics.HALOS), new Row("Trail", m.trailStyle, Cosmetics.TRAILS)};
    }

    private int panelWidth() { return Math.min(width - 16, 420); }
    private int panelHeight() { return Math.min(height - 16, 236); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int optionsX() { return left() + PREVIEW + 18; }
    private int optionsWidth() { return panelWidth() - PREVIEW - 26; }
    private float rowY(int index) { return top() + 32 + index * (ROW + ROW_GAP); }
    private float flapY() { return rowY(4); }
    private float backY() { return top() + panelHeight() - 26; }
    private int pickerWidth() { return Math.min(150, optionsWidth() - 70); }
    private float pickerX() { return optionsX() + optionsWidth() - 6 - pickerWidth(); }
    private float nextX() { return pickerX() + pickerWidth() - ARROW; }

    /** For the client test: centers of a row's previous and next arrows. */
    public int[] previousCenter(int row) { return new int[] {Math.round(pickerX() + ARROW / 2f), Math.round(rowY(row) + ROW / 2f)}; }
    public int[] nextCenter(int row) { return new int[] {Math.round(nextX() + ARROW / 2f), Math.round(rowY(row) + ROW / 2f)}; }

    public void setRotation(float degrees) { rotation = degrees; }

    @Override protected void draw(GuiGraphics g, int mouseX, int mouseY) {
        int left = left(), top = top(), panelW = panelWidth(), panelH = panelHeight();
        Ui.rounded(g, left - 1, top - 1, panelW + 2, panelH + 2, 13, Ui.alpha(Ui.mix(Ui.HAIRLINE, Ui.accent(), 0.35f), alpha));
        Ui.rounded(g, left, top, panelW, panelH, 12, Ui.alpha(Ui.PANEL, alpha));
        Ui.rounded(g, left + 14, top + 13, 6, 6, 3, Ui.alpha(Ui.accentLight(), alpha));
        Ui.text(g, "Cosmetics", left + 25, top + 12, Ui.alpha(Ui.TEXT, alpha));
        String note = "Only you can see them";
        Ui.text(g, note, left + panelW - 14 - Ui.width(note), top + 12, Ui.alpha(Ui.MUTED, alpha));

        // Preview: a soft spotlight under the player, who slowly turns unless you drag.
        int px = left + 8, py = top + 30, ph = panelH - 30 - FOOTER - 6;
        Ui.rounded(g, px, py, PREVIEW, ph, 9, Ui.alpha(Ui.SIDEBAR, alpha));
        Ui.rounded(g, px + PREVIEW / 2f - 34, py + ph - 22, 68, 12, 6, Ui.alpha(Ui.mix(Ui.SIDEBAR, Ui.accent(), 0.35f), alpha));
        if (!dragging) rotation += seconds * 28;
        if (minecraft.player != null) preview(g, px, py, PREVIEW, ph);
        else Ui.text(g, "Join a world", px + (PREVIEW - Ui.width("Join a world")) / 2f, py + ph / 2f, Ui.alpha(Ui.MUTED, alpha));
        String hint = "Drag to turn";
        Ui.text(g, hint, px + (PREVIEW - Ui.width(hint)) / 2f, py + ph - 9, Ui.alpha(Ui.MUTED, alpha * 0.8f));

        Row[] rows = rows();
        int ox = optionsX(), ow = optionsWidth(), pw = pickerWidth();
        float pxl = pickerX(), nx = nextX();
        for (int r = 0; r < rows.length; r++) {
            Row row = rows[r];
            float y = rowY(r);
            int index = row.setting().index(), count = row.names().length;
            Ui.rounded(g, ox, y, ow, ROW, 8, Ui.alpha(Ui.SURFACE, alpha));
            Ui.text(g, row.label(), ox + 10, y + 11, Ui.alpha(Ui.TEXT, alpha));
            Ui.rounded(g, pxl, y + 4, pw, ROW - 8, 6, Ui.alpha(Ui.SIDEBAR, alpha));
            for (int side = 0; side < 2; side++) {
                float ax = side == 0 ? pxl : nx;
                float hover = animate("arrow:" + r + side, Ui.inside(mouseX, mouseY, ax, y + 4, ARROW, ROW - 8) ? 1 : 0, 18);
                Ui.rounded(g, ax + 2, y + 6, ARROW - 4, ROW - 12, 5, Ui.alpha(Ui.mix(Ui.SIDEBAR, Ui.accent(), 0.25f + hover * 0.6f), alpha));
                String arrow = side == 0 ? "‹" : "›";
                Ui.text(g, arrow, ax + (ARROW - Ui.width(arrow)) / 2f, y + 11, Ui.alpha(Ui.TEXT, alpha));
            }
            String name = row.names()[index];
            int nameColor = index == 0 ? Ui.MUTED : Ui.accentLight();
            Ui.text(g, Ui.fit(name, pw - ARROW * 2 - 6), pxl + (pw - Ui.width(Ui.fit(name, pw - ARROW * 2 - 6))) / 2f, y + 9, Ui.alpha(nameColor, alpha));
            // Small dots show where you are in the list.
            float dots = Math.min(count, 12) * 4f, dx = pxl + (pw - dots) / 2f + 1;
            for (int d = 0; d < Math.min(count, 12); d++) {
                Ui.rounded(g, dx + d * 4, y + ROW - 9, 2, 2, 1, Ui.alpha(d == index ? Ui.accentLight() : Ui.OFF, alpha));
            }
        }
        float fy = flapY();
        Ui.rounded(g, ox, fy, ow, 22, 8, Ui.alpha(Ui.SURFACE, alpha));
        Ui.text(g, "Animate wings", ox + 10, fy + 7, Ui.alpha(Ui.TEXT, alpha));
        Ui.toggle(g, ox + ow - 32, fy + 5, animate("flap", NicoClientMod.MODULES.wingFlap.on() ? 1 : 0, 16), alpha);

        button(g, "Back", left + 8, backY(), panelW - 16, 20, mouseX, mouseY);
    }

    /** Draws your player turned by the preview angle, like the inventory preview but all the way round. */
    private void preview(GuiGraphics g, int x, int y, int w, int h) {
        if (!(InventoryScreenAccessor.nicoclient$extractRenderState(minecraft.player) instanceof LivingEntityRenderState state)) return;
        state.bodyRot = 180 + rotation;
        state.yRot = 0;
        state.xRot = 0;
        float size = Math.min(h / 2.6f, 62);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(-0.12f);
        pose.mul(camera);
        g.submitEntityRenderState(state, size, new Vector3f(0, state.boundingBoxHeight / 2 + 0.0625f, 0), pose, camera, x, y + 4, x + w, y + h - 10);
    }

    private static void step(ModuleManager.Setting setting, int count, int by) {
        setting.selectIndex(Math.floorMod(setting.index() + by, count));
        NicoClientMod.MODULES.save();
        Ui.click();
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (isClosing()) return true;
        if (e.button() == 1) { onClose(); return true; }
        if (e.button() != 0) return false;
        if (Ui.inside(e.x(), e.y(), left() + 8, top() + 30, PREVIEW, panelHeight() - 64)) {
            dragging = true;
            lastMouseX = e.x();
            return true;
        }
        Row[] rows = rows();
        for (int r = 0; r < rows.length; r++) {
            float y = rowY(r);
            int count = rows[r].names().length;
            if (Ui.inside(e.x(), e.y(), pickerX(), y + 4, ARROW, ROW - 8)) { step(rows[r].setting(), count, -1); return true; }
            if (Ui.inside(e.x(), e.y(), nextX(), y + 4, ARROW, ROW - 8)) { step(rows[r].setting(), count, 1); return true; }
            // Clicking the name moves forward too, the quickest way to browse.
            if (Ui.inside(e.x(), e.y(), pickerX(), y + 4, pickerWidth(), ROW - 8)) { step(rows[r].setting(), count, 1); return true; }
        }
        if (Ui.inside(e.x(), e.y(), optionsX(), flapY(), optionsWidth(), 22)) {
            NicoClientMod.MODULES.wingFlap.flip();
            NicoClientMod.MODULES.save();
            Ui.click();
            return true;
        }
        if (Ui.inside(e.x(), e.y(), left() + 8, backY(), panelWidth() - 16, 20)) { Ui.click(); onClose(); return true; }
        return false;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        Row[] rows = rows();
        for (int r = 0; r < rows.length; r++) {
            if (Ui.inside(x, y, optionsX(), rowY(r), optionsWidth(), ROW)) { step(rows[r].setting(), rows[r].names().length, vertical < 0 ? 1 : -1); return true; }
        }
        return false;
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (!dragging) return super.mouseDragged(e, dx, dy);
        rotation += (float) (e.x() - lastMouseX) * 2.2f;
        lastMouseX = e.x();
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent e) {
        dragging = false;
        return super.mouseReleased(e);
    }

    @Override public void onClose() { closeTo(parent); }
}
