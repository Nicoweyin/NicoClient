package dev.nicoweyin.nicoclient.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Waypoints need the real field of view, including zoom, to place markers on screen. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("getFov")
    float nicoclient$getFov(Camera camera, float partialTick, boolean changingFov);
}
