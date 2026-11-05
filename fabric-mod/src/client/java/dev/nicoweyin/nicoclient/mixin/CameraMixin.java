package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.Freelook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freelook: the camera uses its own angles while the key is held. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void nicoclient$freelook(Camera camera, float yRot, float xRot) {
        if (Freelook.active) setRotation(Freelook.yaw, Freelook.pitch);
        else setRotation(yRot, xRot);
    }
}
