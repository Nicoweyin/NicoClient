package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Quiet Mode: lowers every game sound without changing Minecraft's own volume settings. */
@Mixin(SoundEngine.class)
public abstract class SoundVolumeMixin {
    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void nicoclient$quietMode(float volume, SoundSource source, CallbackInfoReturnable<Float> result) {
        if (NicoClientMod.MODULES.quietMode.enabled) result.setReturnValue(result.getReturnValueF() * NicoClientMod.MODULES.quietVolume.get() / 100f);
    }
}
