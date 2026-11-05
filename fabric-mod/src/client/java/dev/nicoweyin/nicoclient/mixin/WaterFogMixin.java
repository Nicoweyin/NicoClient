package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Water: pushes the underwater fog further away. */
@Mixin(WaterFogEnvironment.class)
public abstract class WaterFogMixin {
    /**
     * renderDistance is the view distance in blocks. FogData's own render distance fields are only filled in
     * after this method, so they cannot be used here.
     */
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void nicoclient$clearWater(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker delta, CallbackInfo ci) {
        if (!NicoClientMod.MODULES.clearWater.enabled) return;
        float end = NicoClientMod.MODULES.waterClarity.get() == 2 ? renderDistance : Math.min(renderDistance, fog.environmentalEnd * 3);
        fog.environmentalStart = -8;
        fog.environmentalEnd = Math.max(fog.environmentalEnd, end);
        fog.skyEnd = fog.environmentalEnd;
        fog.cloudEnd = fog.environmentalEnd;
    }
}
