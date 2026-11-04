package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Saves power and heat while the game window is not focused. */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void nicoclient$dynamicFps(CallbackInfoReturnable<Integer> result) {
        if (NicoClientMod.MODULES.dynamicFps.enabled && !Minecraft.getInstance().isWindowActive()) {
            result.setReturnValue(Math.min(result.getReturnValue(), NicoClientMod.MODULES.dynamicFpsLimit.get()));
        }
    }
}
