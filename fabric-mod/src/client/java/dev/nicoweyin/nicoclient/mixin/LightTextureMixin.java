package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    // The first lightmap UBO float in 1.21.11 is the dimension ambient light.
    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 0, remap = false), index = 0)
    private float nicoclient$ambientLight(float ambient) {
        return NicoClientMod.MODULES.fullbright.enabled ? Math.max(ambient, NicoClientMod.MODULES.brightness.get() / 100.0f) : ambient;
    }
}
