package dev.nicoweyin.nicoclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Notices when items arrive in the inventory during play and lists them for a few seconds. */
public final class Pickups {
    private static final int MAX_LINES = 5, LINE = 18;
    private static final float FADE_SECONDS = 0.5f;

    private static final class Note {
        final Item item;
        final ItemStack icon;
        int count;
        long time;
        Note(Item item, ItemStack icon, int count, long time) { this.item = item; this.icon = icon; this.count = count; this.time = time; }
        String text() { return "+" + count + " " + icon.getHoverName().getString(); }
    }

    private static final List<Note> NOTES = new ArrayList<>();
    private static Map<Item, Integer> last = Map.of();
    private static LocalPlayer lastPlayer;

    private Pickups() {}

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (!NicoClientMod.MODULES.pickups.enabled || player == null) {
            NOTES.clear();
            lastPlayer = null;
            return;
        }
        Map<Item, Integer> counts = new HashMap<>();
        Map<Item, ItemStack> stacks = new HashMap<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            stacks.putIfAbsent(stack.getItem(), stack);
        }
        // A new player (joining, respawning, changing dimension) starts a fresh baseline.
        // Menus are skipped, so moving items out of chests or crafting does not count as a pickup.
        if (player == lastPlayer && client.screen == null && !player.isCreative()) {
            for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
                int gained = entry.getValue() - last.getOrDefault(entry.getKey(), 0);
                if (gained > 0) add(entry.getKey(), stacks.get(entry.getKey()), gained);
            }
        }
        last = counts;
        lastPlayer = player;
        long cutoff = System.currentTimeMillis() - (long) (seconds() * 1000);
        NOTES.removeIf(note -> note.time < cutoff);
    }

    private static void add(Item item, ItemStack stack, int gained) {
        long now = System.currentTimeMillis();
        for (Note note : NOTES) {
            if (note.item == item) {
                note.count += gained;
                note.time = now;
                NOTES.remove(note);
                NOTES.add(0, note);
                return;
            }
        }
        NOTES.add(0, new Note(item, stack.copyWithCount(1), gained, now));
        while (NOTES.size() > MAX_LINES) NOTES.remove(NOTES.size() - 1);
    }

    private static float seconds() { return NicoClientMod.MODULES.pickupSeconds.get(); }

    public static boolean visible() { return !NOTES.isEmpty(); }

    public static int width() {
        int widest = 0;
        for (Note note : NOTES) widest = Math.max(widest, Ui.width(note.text()));
        return widest + 26;
    }

    public static int height() { return NOTES.size() * LINE; }

    public static void render(GuiGraphics g, int x, int y, int width) {
        Minecraft client = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        for (int i = 0; i < NOTES.size(); i++) {
            Note note = NOTES.get(i);
            float left = seconds() - (now - note.time) / 1000f;
            float fade = Math.max(0, Math.min(1, left / FADE_SECONDS));
            int top = y + i * LINE;
            Ui.rounded(g, x, top, width, LINE - 2, 4, Ui.alpha(0xB00D0F1E, fade));
            if (fade > 0.3f) g.renderItem(note.icon, x, top);
            Ui.text(g, note.text(), x + 20, top + 4, Ui.alpha(Ui.TEXT, fade));
        }
    }

    /** For the client test: pretends an item arrived. */
    public static void testAdd(ItemStack stack) { add(stack.getItem(), stack, stack.getCount()); }
}
