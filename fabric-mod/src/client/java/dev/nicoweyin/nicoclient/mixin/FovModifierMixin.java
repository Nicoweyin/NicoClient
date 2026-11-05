package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Static FOV: no FOV change from sprinting, flying or speed. Drawing a bow and the spyglass still zoom. */
@Mixin(AbstractClientPlayer.class)
public abstract class FovModifierMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void nicoclient$staticFov(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> result) {
        if (NicoClientMod.MODULES.staticFov.enabled && !((AbstractClientPlayer) (Object) this).isUsingItem()) result.setReturnValue(1.0f);
    }
}
