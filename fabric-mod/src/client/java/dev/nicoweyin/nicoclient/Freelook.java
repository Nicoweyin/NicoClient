package dev.nicoweyin.nicoclient;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/** Freelook: while the key is held, the camera orbits the player and the player keeps facing ahead. */
public final class Freelook {
    public static boolean active;
    public static float yaw, pitch;
    private static CameraType previous;

    private Freelook() {}

    public static void tick(Minecraft client) {
        boolean held = NicoClientMod.MODULES.freelook.enabled && client.player != null && client.screen == null
                && NicoClientMod.freelookKey != null && NicoClientMod.freelookKey.isDown();
        if (held && !active) {
            active = true;
            yaw = client.player.getYRot();
            pitch = client.player.getXRot();
            previous = client.options.getCameraType();
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!held && active) {
            active = false;
            if (previous != null) client.options.setCameraType(previous);
        }
    }

    /** Mouse movement turns the camera instead of the player, with the same sensitivity as vanilla. */
    public static void turn(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = Math.max(-90, Math.min(90, pitch + (float) dy * 0.15f));
    }
}
