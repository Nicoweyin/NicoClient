package dev.nicoweyin.nicoclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Locale;

/** Tracks your hits for the Reach Display and Combo Counter HUD modules. */
public final class Combat {
    private static final long COMBO_TIMEOUT = 2000, REACH_SHOWN = 3000;
    private static double reach;
    private static long lastHit;
    private static int combo, lastHurtTime;
    private static LivingEntity lastTarget;

    private Combat() {}

    /** Called when you attack an entity. Reach is measured from your eyes to the closest point of its hitbox. */
    public static void onAttack(LocalPlayer player, Entity target) {
        if (!(target instanceof LivingEntity living)) return;
        lastTarget = living;
        long now = System.currentTimeMillis();
        reach = Math.sqrt(target.getBoundingBox().distanceToSqr(player.getEyePosition()));
        combo = now - lastHit > COMBO_TIMEOUT ? 1 : combo + 1;
        lastHit = now;
    }

    /** Taking damage ends a combo, like in PvP. */
    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) { combo = 0; lastHurtTime = 0; return; }
        if (player.hurtTime > lastHurtTime) combo = 0;
        lastHurtTime = player.hurtTime;
    }

    /** The entity under the crosshair, or the last one you hit in the past few seconds. */
    public static LivingEntity target(Minecraft client) {
        if (client.player == null) return null;
        if (client.crosshairPickEntity instanceof LivingEntity looked && looked.isAlive()) return looked;
        if (lastTarget != null && lastTarget.isAlive() && lastTarget.level() == client.level
                && System.currentTimeMillis() - lastHit < REACH_SHOWN && lastTarget.distanceTo(client.player) < 64) return lastTarget;
        return null;
    }

    public static String reachText() {
        if (lastHit == 0 || System.currentTimeMillis() - lastHit > REACH_SHOWN) return "Reach -";
        return String.format(Locale.ROOT, "Reach %.2f m", reach);
    }

    public static String comboText() {
        int current = System.currentTimeMillis() - lastHit > COMBO_TIMEOUT ? 0 : combo;
        return current == 0 ? "No combo" : "Combo " + current;
    }
}
