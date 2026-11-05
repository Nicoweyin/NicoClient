package dev.nicoweyin.nicoclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Armor Warning: a pulsing note and a sound when armor or the held tool is nearly broken. */
public final class ArmorWarning {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
    private static final String[] NAMES = {"Helmet", "Chestplate", "Leggings", "Boots", ""};
    private static String warning;
    /** Slots that already played the sound, so it rings once per item, not every tick. */
    private static final boolean[] warned = new boolean[SLOTS.length];

    private ArmorWarning() {}

    public static void tick(Minecraft client) {
        warning = null;
        if (!NicoClientMod.MODULES.armorWarning.enabled || client.player == null || client.player.isCreative()) {
            java.util.Arrays.fill(warned, false);
            return;
        }
        int lowest = 101;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == EquipmentSlot.MAINHAND && !NicoClientMod.MODULES.armorHeld.on()) { warned[i] = false; continue; }
            ItemStack stack = client.player.getItemBySlot(SLOTS[i]);
            if (stack.isEmpty() || !stack.isDamageableItem()) { warned[i] = false; continue; }
            int percent = (stack.getMaxDamage() - stack.getDamageValue()) * 100 / stack.getMaxDamage();
            if (percent >= NicoClientMod.MODULES.armorLimit.get()) { warned[i] = false; continue; }
            if (!warned[i]) {
                warned[i] = true;
                if (NicoClientMod.MODULES.armorSound.on()) client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 0.8f));
            }
            if (percent < lowest) {
                lowest = percent;
                String name = NAMES[i].isEmpty() ? stack.getHoverName().getString() : NAMES[i];
                warning = name + " at " + percent + "% - repair it soon";
            }
        }
    }

    public static String warning() { return warning; }

    public static void render(GuiGraphics g) {
        if (warning == null) return;
        float pulse = 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 180.0);
        int width = Ui.width(warning) + 16, x = (g.guiWidth() - width) / 2, y = g.guiHeight() - 92;
        Ui.rounded(g, x, y, width, 17, 6, Ui.alpha(0xE0B43C4C, pulse));
        Ui.text(g, warning, x + 8, y + 5, Ui.TEXT);
    }
}
