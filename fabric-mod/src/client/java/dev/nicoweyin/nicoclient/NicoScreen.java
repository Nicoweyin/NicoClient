package dev.nicoweyin.nicoclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Locale;

public final class NicoScreen extends AnimatedScreen {
    private static final String[] CATEGORIES = {"All", "HUD", "Visuals", "Utility", "Performance", "Favorites"};
    private static final int SIDEBAR = 104, ROW = 30, ROW_GAP = 4, ITEM = 18, ITEM_GAP = 2, ITEMS_TOP = 36, FAVORITES = 5;
    private static final int MAX_QUERY = 40, COSMETICS = 66, PROFILES = 54;
    private String query = "";
    private int category;
    private float scroll, scrollTarget;

    public NicoScreen() { super("NicoClient"); }

    private int panelWidth() { return Math.min(width - 16, 430); }
    private int panelHeight() { return Math.min(height - 16, 280); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int contentX() { return left() + SIDEBAR + 8; }
    private int contentWidth() { return panelWidth() - SIDEBAR - 16; }
    private int listTop() { return top() + 36; }
    private int listHeight() { return panelHeight() - 44; }

    public List<ModuleManager.Module> visibleModules() {
        String needle = query.toLowerCase(Locale.ROOT);
        return NicoClientMod.MODULES.modules.stream().filter(m ->
            (category == 0 || (category == FAVORITES ? m.favorite : m.category.equals(CATEGORIES[category])))
            && (m.name + " " + m.category + " " + m.description()).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    private float maxScroll(int count) { return Math.max(0, count * (ROW + ROW_GAP) - ROW_GAP - listHeight()); }
    private float rowY(int index) { return listTop() - scroll + index * (ROW + ROW_GAP); }
    private float editHudY() { return top() + panelHeight() - 54; }
    private float settingsY() { return top() + panelHeight() - 30; }

    /** Center of a module's switch in GUI coordinates, or null if it is scrolled out of view. */
    public int[] toggleCenter(ModuleManager.Module module) {
        int index = visibleModules().indexOf(module);
        if (index < 0) return null;
        float y = rowY(index);
        if (y < listTop() || y + ROW > listTop() + listHeight()) return null;
        return new int[] {contentX() + contentWidth() - 32 + 12, Math.round(y) + ROW / 2};
    }

    @Override protected void draw(GuiGraphics g, int mouseX, int mouseY) {
        int left = left(), top = top(), panelW = panelWidth(), panelH = panelHeight();
        // A thin accent-tinted rim gives the panel depth.
        Ui.rounded(g, left - 1, top - 1, panelW + 2, panelH + 2, 13, Ui.alpha(Ui.mix(Ui.HAIRLINE, Ui.accent(), 0.35f), alpha));
        Ui.rounded(g, left, top, panelW, panelH, 12, Ui.alpha(Ui.PANEL, alpha));
        Ui.rounded(g, left + 4, top + 4, SIDEBAR - 4, panelH - 8, 9, Ui.alpha(Ui.SIDEBAR, alpha));

        Ui.rounded(g, left + 12, top + 13, 10, 10, 5, Ui.alpha(Ui.mix(Ui.accent(), Ui.SIDEBAR, 0.55f), alpha));
        Ui.rounded(g, left + 14, top + 15, 6, 6, 3, Ui.alpha(Ui.accentLight(), alpha));
        Ui.text(g, "NicoClient", left + 27, top + 14, Ui.alpha(Ui.TEXT, alpha));

        float indicator = animate("category", ITEMS_TOP + category * (ITEM + ITEM_GAP), 20);
        Ui.rounded(g, left + 9, top + indicator, SIDEBAR - 14, ITEM, 6, Ui.alpha(Ui.accent(), alpha));
        for (int i = 0; i < CATEGORIES.length; i++) {
            float y = top + ITEMS_TOP + i * (ITEM + ITEM_GAP);
            boolean hovered = !isClosing() && Ui.inside(mouseX, mouseY, left + 9, y, SIDEBAR - 14, ITEM);
            float hover = animate("sidebar:" + i, hovered ? 1 : 0, 16);
            float selected = animate("selected:" + i, i == category ? 1 : 0, 16);
            Ui.text(g, CATEGORIES[i], left + 18 + hover * 2, y + 5, Ui.alpha(Ui.mix(Ui.mix(Ui.MUTED, Ui.TEXT, hover), Ui.TEXT, selected), alpha));
        }
        profile(g, left, top);
        button(g, "Edit HUD", left + 9, editHudY(), SIDEBAR - 14, 20, mouseX, mouseY);
        button(g, "Settings", left + 9, settingsY(), SIDEBAR - 14, 20, mouseX, mouseY);

        int x = contentX(), w = contentWidth();
        // Typing always goes to the search box; the vanilla box only works with the vanilla font.
        float cosmeticsHover = animate("cosmetics", !isClosing() && Ui.inside(mouseX, mouseY, cosmeticsX(), top + 8, COSMETICS, 22) ? 1 : 0, 16);
        Ui.rounded(g, cosmeticsX(), top + 8, COSMETICS, 22, 7, Ui.alpha(Ui.mix(Ui.accent(), Ui.accentLight(), cosmeticsHover * 0.5f), alpha));
        Ui.text(g, "Cosmetics", cosmeticsX() + (COSMETICS - Ui.width("Cosmetics")) / 2f, top + 15, Ui.alpha(Ui.TEXT, alpha));
        float profilesHover = animate("profiles", !isClosing() && Ui.inside(mouseX, mouseY, profilesX(), top + 8, PROFILES, 22) ? 1 : 0, 16);
        Ui.rounded(g, profilesX(), top + 8, PROFILES, 22, 7, Ui.alpha(Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, profilesHover), alpha));
        Ui.text(g, "Profiles", profilesX() + (PROFILES - Ui.width("Profiles")) / 2f, top + 15, Ui.alpha(Ui.mix(Ui.MUTED, Ui.TEXT, 0.6f + profilesHover * 0.4f), alpha));
        if (waypointsWidth() > 0) {
        float waypointsHover = animate("waypoints", !isClosing() && Ui.inside(mouseX, mouseY, waypointsX(), top + 8, waypointsWidth(), 22) ? 1 : 0, 16);
        Ui.rounded(g, waypointsX(), top + 8, waypointsWidth(), 22, 7, Ui.alpha(Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, waypointsHover), alpha));
        Ui.text(g, "Waypoints", waypointsX() + (waypointsWidth() - Ui.width("Waypoints")) / 2f, top + 15, Ui.alpha(Ui.mix(Ui.MUTED, Ui.TEXT, 0.6f + waypointsHover * 0.4f), alpha));
        }
        w -= COSMETICS + PROFILES + 12 + (waypointsWidth() > 0 ? waypointsWidth() + 6 : 0);
        Ui.rounded(g, x, top + 8, w, 22, 7, Ui.alpha(Ui.SURFACE, alpha));
        if (query.isEmpty()) {
            Ui.text(g, "Search modules", x + 11, top + 15, Ui.alpha(Ui.MUTED, alpha));
            String hint = "Right click to configure";
            if (w > Ui.width(hint) + 110) Ui.text(g, hint, x + w - Ui.width(hint) - 11, top + 15, Ui.alpha(Ui.mix(Ui.MUTED, Ui.SURFACE, 0.35f), alpha));
        } else {
            Ui.text(g, displayedQuery(), x + 11, top + 15, Ui.alpha(Ui.TEXT, alpha));
        }
        if (System.currentTimeMillis() % 1000 < 550) {
            float caret = query.isEmpty() ? 0 : Ui.width(displayedQuery()) + 1;
            Ui.rounded(g, x + 10 + caret, top + 14, 1, 10, 0, Ui.alpha(Ui.accentLight(), alpha));
        }

        w = contentWidth();
        List<ModuleManager.Module> modules = visibleModules();
        float max = maxScroll(modules.size());
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        scroll = Ui.approach(scroll, scrollTarget, seconds, 18);

        int listTop = listTop(), listH = listHeight();
        g.enableScissor(x, listTop, x + w, listTop + listH);
        boolean inList = !isClosing() && Ui.inside(mouseX, mouseY, x, listTop, w, listH);
        for (int i = 0; i < modules.size(); i++) {
            ModuleManager.Module module = modules.get(i);
            float y = rowY(i);
            float on = animate(module, module.enabled ? 1 : 0, 16);
            if (y + ROW < listTop || y > listTop + listH) continue;
            float hover = animate("row:" + module.id, inList && Ui.inside(mouseX, mouseY, x, y, w, ROW) ? 1 : 0, 16);
            int surface = Ui.mix(Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, hover), Ui.accent(), on * 0.12f);
            Ui.rounded(g, x, y, w, ROW, 8, Ui.alpha(surface, alpha));
            if (on > 0.01f) Ui.rounded(g, x + 3, y + 8, 3, ROW - 16, 1.5f, Ui.alpha(Ui.accentLight(), alpha * on));
            // A small monogram tile tinted by category makes the long list easy to scan.
            int tint = tint(module.category);
            Ui.rounded(g, x + 10, y + 5, 20, 20, 6, Ui.alpha(Ui.mix(Ui.SURFACE, tint, 0.28f + on * 0.12f), alpha));
            String mark = monogram(module.name);
            Ui.text(g, mark, x + 20 - Ui.width(mark) / 2f, y + 11, Ui.alpha(Ui.mix(tint, 0xFFFFFFFF, 0.45f), alpha));
            int reserved = 58;
            if (module.hotkey >= 0) {
                String key = keyName(module.hotkey);
                int chip = Ui.width(key) + 10;
                reserved += chip + 4;
                Ui.rounded(g, starX(x, w) - chip - 4, y + 9, chip, 12, 4, Ui.alpha(Ui.OFF, alpha));
                Ui.text(g, key, starX(x, w) - chip + 1, y + 11, Ui.alpha(Ui.MUTED, alpha));
            }
            Ui.text(g, Ui.fit(module.name, w - 36 - reserved), x + 36, y + 6, Ui.alpha(Ui.TEXT, alpha));
            boolean paused = module.enabled && ServerSafe.paused(module);
            String detail = paused ? "Paused here by Server-safe mode" : module.description();
            Ui.text(g, Ui.fit(detail, w - 36 - reserved + 10), x + 36, y + 17, Ui.alpha(paused ? 0xFFF2B84B : Ui.MUTED, alpha));
            boolean overStar = inList && Ui.inside(mouseX, mouseY, starX(x, w), y, 16, ROW);
            int star = module.favorite ? Ui.accentLight() : overStar ? Ui.TEXT : Ui.OFF;
            Ui.text(g, "★", starX(x, w) + 3, y + 11, Ui.alpha(star, alpha));
            Ui.toggle(g, x + w - 32, y + 9, on, alpha);
        }
        if (modules.isEmpty()) {
            String empty = category == FAVORITES && query.isEmpty() ? "Star a module to find it here" : "No modules found";
            Ui.text(g, empty, x + (w - Ui.width(empty)) / 2f, listTop + listH / 2f - 4, Ui.alpha(Ui.MUTED, alpha));
        }
        g.disableScissor();
        if (max > 0) {
            float contentHeight = max + listH;
            float barHeight = Math.max(16, listH * listH / contentHeight);
            float barY = listTop + (listH - barHeight) * (scroll / max);
            Ui.rounded(g, left + panelW - 5, barY, 2, barHeight, 1, Ui.alpha(Ui.HAIRLINE, alpha));
        }
    }

    private static int starX(int x, int w) { return x + w - 52; }

    private static int tint(String category) {
        return switch (category) {
            case "HUD" -> 0xFF6C7BFF;
            case "Visuals" -> 0xFFD06BFF;
            case "Utility" -> 0xFF35C9A0;
            case "Performance" -> 0xFFF2A93B;
            default -> Ui.accent();
        };
    }

    /** "FPS Counter" becomes "FC", "Zoom" becomes "Zo". */
    static String monogram(String name) {
        String[] words = name.replace("&", "").trim().split("\\s+");
        if (words.length > 1) return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(Locale.ROOT);
        return words[0].length() > 1 ? words[0].substring(0, 1).toUpperCase(Locale.ROOT) + words[0].substring(1, 2).toLowerCase(Locale.ROOT) : words[0];
    }

    static String keyName(int key) {
        String name = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        return name.length() > 8 ? name.substring(0, 8) : name;
    }

    /** Your face and name above the sidebar buttons, when the window is tall enough. */
    private void profile(GuiGraphics g, int left, int top) {
        Minecraft client = Minecraft.getInstance();
        float y = editHudY() - 30;
        if (client.player == null || y < top + ITEMS_TOP + CATEGORIES.length * (ITEM + ITEM_GAP) + 4) return;
        Ui.rounded(g, left + 9, y, SIDEBAR - 14, 24, 7, Ui.alpha(Ui.mix(Ui.SIDEBAR, Ui.accent(), 0.10f), alpha));
        if (alpha > 0.5f) PlayerFaceRenderer.draw(g, client.player.getSkin(), left + 14, Math.round(y) + 4, 16);
        Ui.text(g, Ui.fit(client.getUser().getName(), SIDEBAR - 44), left + 35, y + 4, Ui.alpha(Ui.TEXT, alpha));
        boolean safe = NicoClientMod.MODULES.serverSafe.on() && ServerSafe.onPublicServer(client);
        Ui.text(g, safe ? "Server-safe" : "Online", left + 35, y + 14, Ui.alpha(safe ? 0xFFF2B84B : Ui.mix(Ui.MUTED, 0xFF35C9A0, 0.6f), alpha));
    }
    private int cosmeticsX() { return contentX() + contentWidth() - COSMETICS; }
    private int profilesX() { return cosmeticsX() - PROFILES - 6; }
    private int waypointsWidth() { return contentWidth() >= 270 ? 66 : 0; }
    private int waypointsX() { return profilesX() - waypointsWidth() - 6; }
    public int[] waypointsCenter() { return new int[] {waypointsX() + waypointsWidth() / 2, top() + 19}; }
    public int[] profilesCenter() { return new int[] {profilesX() + PROFILES / 2, top() + 19}; }

    public int[] cosmeticsCenter() { return new int[] {cosmeticsX() + COSMETICS / 2, top() + 19}; }

    /** The search text as it is drawn; public so the game test can check what the player sees. */
    public String displayedQuery() { return Ui.fit(query, contentWidth() - COSMETICS - PROFILES - 36 - (waypointsWidth() > 0 ? waypointsWidth() + 6 : 0)); }

    public int[] categoryCenter(int index) { return new int[] {left() + 40, top() + ITEMS_TOP + index * (ITEM + ITEM_GAP) + ITEM / 2}; }

    public int[] starCenter(ModuleManager.Module module) {
        int[] center = toggleCenter(module);
        return center == null ? null : new int[] {starX(contentX(), contentWidth()) + 8, center[1]};
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (isClosing()) return true;
        if (super.mouseClicked(e, doubled)) return true;
        if (e.button() == 1) return rightClick(e);
        if (e.button() != 0) return false;
        int left = left(), top = top();
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (Ui.inside(e.x(), e.y(), left + 9, top + ITEMS_TOP + i * (ITEM + ITEM_GAP), SIDEBAR - 14, ITEM)) {
                if (category != i) Ui.click();
                category = i; scrollTarget = 0;
                return true;
            }
        }
        if (Ui.inside(e.x(), e.y(), left + 9, editHudY(), SIDEBAR - 14, 20)) { Ui.click(); closeTo(new HudEditor()); return true; }
        if (waypointsWidth() > 0 && Ui.inside(e.x(), e.y(), waypointsX(), top + 8, waypointsWidth(), 22)) { Ui.click(); closeTo(new WaypointScreen(this)); return true; }
        if (Ui.inside(e.x(), e.y(), profilesX(), top + 8, PROFILES, 22)) { Ui.click(); closeTo(new ProfileScreen(this)); return true; }
        if (Ui.inside(e.x(), e.y(), cosmeticsX(), top + 8, COSMETICS, 22)) { Ui.click(); closeTo(new CosmeticsScreen(this)); return true; }
        if (Ui.inside(e.x(), e.y(), left + 9, settingsY(), SIDEBAR - 14, 20)) { Ui.click(); closeTo(new ModuleSettingsScreen(NicoClientMod.MODULES.client, this)); return true; }
        int x = contentX(), w = contentWidth();
        if (!Ui.inside(e.x(), e.y(), x, listTop(), w, listHeight())) return false;
        List<ModuleManager.Module> modules = visibleModules();
        for (int i = 0; i < modules.size(); i++) {
            float y = rowY(i);
            if (!Ui.inside(e.x(), e.y(), x, y, w, ROW)) continue;
            ModuleManager.Module module = modules.get(i);
            if (Ui.inside(e.x(), e.y(), starX(x, w), y, 16, ROW)) module.favorite = !module.favorite;
            else module.enabled = !module.enabled;
            Ui.click();
            NicoClientMod.MODULES.save();
            return true;
        }
        return false;
    }

    private boolean rightClick(MouseButtonEvent e) {
        int x = contentX(), w = contentWidth();
        if (!Ui.inside(e.x(), e.y(), x, listTop(), w, listHeight())) return false;
        List<ModuleManager.Module> modules = visibleModules();
        for (int i = 0; i < modules.size(); i++) {
            if (Ui.inside(e.x(), e.y(), x, rowY(i), w, ROW)) {
                Ui.click();
                closeTo(new ModuleSettingsScreen(modules.get(i), this));
                return true;
            }
        }
        return false;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * 28;
        return true;
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (NicoClientMod.menuKey.matches(e)) { onClose(); return true; }
        if (e.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            setQuery(query.substring(0, query.offsetByCodePoints(query.length(), -1)));
            return true;
        }
        return super.keyPressed(e);
    }

    @Override public boolean charTyped(CharacterEvent e) {
        int codepoint = e.codepoint();
        if (Character.isISOControl(codepoint) || query.length() >= MAX_QUERY) return false;
        setQuery(query + Character.toString(codepoint));
        return true;
    }

    private void setQuery(String value) { query = value; scrollTarget = 0; }

    @Override public void onClose() { closeTo(null); }
}
