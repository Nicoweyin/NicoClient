package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: mouse movement turns the camera, not your player. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void nicoclient$freelook(double dx, double dy, CallbackInfo ci) {
        if (!Freelook.active || (Object) this != Minecraft.getInstance().player) return;
        Freelook.turn(dx, dy);
        ci.cancel();
    }
}
