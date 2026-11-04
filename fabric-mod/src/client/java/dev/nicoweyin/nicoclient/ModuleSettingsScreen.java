package dev.nicoweyin.nicoclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class ModuleSettingsScreen extends AnimatedScreen {
    private static final int WIDTH = 270, HEIGHT = 170, ROW = 30, SEGMENT = 36;
    private final ModuleManager m = NicoClientMod.MODULES;

    private record Choice(String label, String[] options, IntSupplier selected, IntConsumer select) {}

    private final Choice[] choices = {
        new Choice("Zoom factor", new String[] {"2x", "4x", "8x"},
            () -> m.zoomFactor == 2 ? 0 : m.zoomFactor == 4 ? 1 : 2, i -> m.zoomFactor = 2 << i),
        new Choice("Fullbright level", new String[] {"60%", "80%", "100%"},
            () -> m.brightness == 60 ? 0 : m.brightness == 80 ? 1 : 2, i -> m.brightness = 60 + i * 20),
    };

    public ModuleSettingsScreen() { super("NicoClient settings"); }

    private int left() { return (width - WIDTH) / 2; }
    private int top() { return (height - HEIGHT) / 2; }
    private float rowY(int index) { return top() + 36 + index * (ROW + 4); }
    private float segmentX(int option) { return left() + WIDTH - 14 - (3 - option) * SEGMENT; }
    private float backY() { return top() + HEIGHT - 28; }

    @Override protected void draw(GuiGraphics g, int mouseX, int mouseY) {
        int left = left(), top = top();
        Ui.rounded(g, left, top, WIDTH, HEIGHT, 12, Ui.alpha(Ui.PANEL, alpha));
        Ui.rounded(g, left + 14, top + 15, 6, 6, 3, Ui.alpha(Ui.ACCENT_LIGHT, alpha));
        Ui.text(g, "Module settings", left + 25, top + 14, Ui.alpha(Ui.TEXT, alpha));
        for (int i = 0; i < choices.length; i++) {
            Choice choice = choices[i];
            float y = rowY(i);
            Ui.rounded(g, left + 8, y, WIDTH - 16, ROW, 8, Ui.alpha(Ui.SURFACE, alpha));
            Ui.text(g, choice.label, left + 18, y + 11, Ui.alpha(Ui.TEXT, alpha));
            Ui.rounded(g, segmentX(0) - 2, y + 5, SEGMENT * 3 + 4, 20, 6, Ui.alpha(Ui.SIDEBAR, alpha));
            float position = animate(choice, choice.selected.getAsInt(), 20);
            Ui.rounded(g, segmentX(0) + position * SEGMENT, y + 7, SEGMENT, 16, 5, Ui.alpha(Ui.ACCENT, alpha));
            for (int o = 0; o < choice.options.length; o++) {
                String option = choice.options[o];
                boolean selected = o == choice.selected.getAsInt();
                Ui.text(g, option, segmentX(o) + (SEGMENT - Ui.width(option)) / 2f, y + 11, Ui.alpha(selected ? Ui.TEXT : Ui.MUTED, alpha));
            }
        }
        float y = rowY(choices.length);
        Ui.rounded(g, left + 8, y, WIDTH - 16, ROW, 8, Ui.alpha(Ui.SURFACE, alpha));
        Ui.text(g, "Trajectory impact marker", left + 18, y + 11, Ui.alpha(Ui.TEXT, alpha));
        Ui.toggle(g, left + WIDTH - 40, y + 9, animate("marker", m.trajectoryMarker ? 1 : 0, 16), alpha);
        button(g, "Back", left + 8, backY(), WIDTH - 16, 20, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (isClosing()) return true;
        if (e.button() != 0) return false;
        for (int i = 0; i < choices.length; i++) {
            for (int o = 0; o < choices[i].options.length; o++) {
                if (Ui.inside(e.x(), e.y(), segmentX(o), rowY(i) + 5, SEGMENT, 20)) {
                    choices[i].select.accept(o);
                    m.save(); Ui.click();
                    return true;
                }
            }
        }
        if (Ui.inside(e.x(), e.y(), left() + 8, rowY(choices.length), WIDTH - 16, ROW)) {
            m.trajectoryMarker = !m.trajectoryMarker;
            m.save(); Ui.click();
            return true;
        }
        if (Ui.inside(e.x(), e.y(), left() + 8, backY(), WIDTH - 16, 20)) { Ui.click(); onClose(); return true; }
        return false;
    }

    @Override public void onClose() { closeTo(new NicoScreen()); }
}
