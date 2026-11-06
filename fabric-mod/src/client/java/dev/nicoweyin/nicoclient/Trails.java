package dev.nicoweyin.nicoclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/** Cosmetic particle trail behind your feet while you move. Client-side only, so only you see it. */
public final class Trails {
    private static final RandomSource RANDOM = RandomSource.create();
    private static int ticks;
    /** Particles spawned since start, for the client test. */
    public static int spawned;

    private Trails() {}

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        int style = Cosmetics.trail();
        if (style == 0 || player == null || client.level == null || player.isInvisible() || client.isPaused()) return;
        double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
        boolean moving = dx * dx + dz * dz > 0.0004 || player.getAbilities().flying && !player.onGround();
        if (!moving || ++ticks % 2 != 0) return;
        ParticleOptions particle = switch (style) {
            case 1 -> ParticleTypes.HEART;
            case 2 -> ParticleTypes.END_ROD;
            case 3 -> ParticleTypes.SMALL_FLAME;
            case 4 -> ParticleTypes.NOTE;
            default -> ParticleTypes.SNOWFLAKE;
        };
        double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 0.5, y = player.getY() + 0.15 + RANDOM.nextDouble() * 0.3;
        double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.5;
        // Notes take their color from the first speed value; the others drift gently.
        if (style == 4) client.level.addParticle(particle, x, y, z, RANDOM.nextDouble(), 0, 0);
        else client.level.addParticle(particle, x, y, z, 0, style == 1 ? 0.02 : 0.01, 0);
        spawned++;
    }
}
