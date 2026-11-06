package dev.nicoweyin.nicoclient;

import dev.nicoweyin.nicoclient.mixin.InventoryScreenAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Pick a cape, wings and a halo, with a turning 3D preview of your own player. */
public final class CosmeticsScreen extends AnimatedScreen {
    private static final int PREVIEW = 128, ROW = 36, CHIP = 16, FOOTER = 28;
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
        return new Row[] {new Row("Cape", m.capeStyle, Cosmetics.CAPES), new Row("Wings", m.wingStyle, Cosmetics.WINGS), new Row("Halo", m.haloStyle, Cosmetics.HALOS)};
    }

    private int panelWidth() { return Math.min(width - 16, 420); }
    private int panelHeight() { return Math.min(height - 16, 236); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int optionsX() { return left() + PREVIEW + 18; }
    private int optionsWidth() { return panelWidth() - PREVIEW - 26; }
    private float rowY(int index) { return top() + 32 + index * (ROW + 4); }
    private float flapY() { return rowY(3); }
    private float backY() { return top() + panelHeight() - 26; }

    private float chipX(int option, int count) { return optionsX() + 8 + option * chipWidth(count); }
    private float chipWidth(int count) { return (optionsWidth() - 16) / (float) count; }

    /** For the client test. */
    public int[] chipCenter(int row, int option) {
        int count = rows()[row].names().length;
        return new int[] {Math.round(chipX(option, count) + chipWidth(count) / 2), Math.round(rowY(row) + 15 + CHIP / 2f)};
    }

    public void setRotation(float degrees) { rotation = degrees; }

    @Override protected void draw(GuiGraphics g, int mouseX, int mouseY) {
        int left = left(), top = top(), panelW = panelWidth(), panelH = panelHeight();
        Ui.rounded(g, left - 1, top - 1, panelW + 2, panelH + 2, 13, Ui.alpha(Ui.mix(Ui.HAIRLINE, Ui.accent(), 0.35f), alpha));
        Ui.rounded(g, left, top, panelW, panelH, 12, Ui.alpha(Ui.PANEL, alpha));
        Ui.rounded(g, left + 14, top + 13, 6, 6, 3, Ui.alpha(Ui.accentLight(), alpha));
        Ui.text(g, "Cosmetics", left + 25, top + 12, Ui.alpha(Ui.TEXT, alpha));
        String note = "Only you can see them";
        Ui.text(g, note, left + panelW - 14 - Ui.width(note), top + 12, Ui.alpha(Ui.MUTED, alpha));

        // Preview: a soft spotlight behind the player, who slowly turns unless you drag.
        int px = left + 8, py = top + 30, ph = panelH - 30 - FOOTER - 6;
        Ui.rounded(g, px, py, PREVIEW, ph, 9, Ui.alpha(Ui.SIDEBAR, alpha));
        Ui.rounded(g, px + PREVIEW / 2f - 34, py + ph - 22, 68, 12, 6, Ui.alpha(Ui.mix(Ui.SIDEBAR, Ui.accent(), 0.35f), alpha));
        if (!dragging) rotation += seconds * 28;
        if (minecraft.player != null) preview(g, px, py, PREVIEW, ph);
        else Ui.text(g, "Join a world", px + (PREVIEW - Ui.width("Join a world")) / 2f, py + ph / 2f, Ui.alpha(Ui.MUTED, alpha));
        String hint = "Drag to turn";
        Ui.text(g, hint, px + (PREVIEW - Ui.width(hint)) / 2f, py + ph - 9, Ui.alpha(Ui.MUTED, alpha * 0.8f));

        Row[] rows = rows();
        int ox = optionsX(), ow = optionsWidth();
        for (int r = 0; r < rows.length; r++) {
            Row row = rows[r];
            float y = rowY(r);
            Ui.rounded(g, ox, y, ow, ROW, 8, Ui.alpha(Ui.SURFACE, alpha));
            Ui.text(g, row.label(), ox + 9, y + 5, Ui.alpha(Ui.TEXT, alpha));
            int count = row.names().length;
            float cw = chipWidth(count);
            Ui.rounded(g, ox + 7, y + 14, ow - 14, CHIP + 2, 6, Ui.alpha(Ui.SIDEBAR, alpha));
            float position = animate("chip:" + r, chipX(row.setting().index(), count), 20);
            Ui.rounded(g, position + 1, y + 15, cw - 2, CHIP, 5, Ui.alpha(Ui.accent(), alpha));
            for (int o = 0; o < count; o++) {
                String name = Ui.fit(row.names()[o], (int) cw - 4);
                boolean hover = Ui.inside(mouseX, mouseY, chipX(o, count), y + 14, cw, CHIP + 2);
                int color = o == row.setting().index() ? Ui.TEXT : hover ? Ui.mix(Ui.MUTED, Ui.TEXT, 0.6f) : Ui.MUTED;
                Ui.text(g, name, chipX(o, count) + (cw - Ui.width(name)) / 2, y + 19, Ui.alpha(color, alpha));
            }
        }
        float fy = flapY();
        Ui.rounded(g, ox, fy, ow, 22, 8, Ui.alpha(Ui.SURFACE, alpha));
        Ui.text(g, "Animate wings", ox + 9, fy + 7, Ui.alpha(Ui.TEXT, alpha));
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
            int count = rows[r].names().length;
            for (int o = 0; o < count; o++) {
                if (Ui.inside(e.x(), e.y(), chipX(o, count), rowY(r) + 14, chipWidth(count), CHIP + 2)) {
                    rows[r].setting().selectIndex(o);
                    NicoClientMod.MODULES.save();
                    Ui.click();
                    return true;
                }
            }
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
