package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "calculateParticleLevel", at = @At("RETURN"), cancellable = true)
    private void nicoclient$reduceParticles(boolean alwaysVisible, CallbackInfoReturnable<ParticleStatus> result) {
        if (!alwaysVisible && NicoClientMod.MODULES.particles.enabled) result.setReturnValue(ParticleStatus.MINIMAL);
    }
}
