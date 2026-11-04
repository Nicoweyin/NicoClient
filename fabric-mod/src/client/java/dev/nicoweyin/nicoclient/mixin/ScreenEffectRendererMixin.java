package dev.nicoweyin.nicoclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void nicoclient$lowerFire(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        pose.pushPose();
        if (NicoClientMod.MODULES.lowFire.enabled) pose.translate(0.0f, -0.35f, 0.0f);
    }
    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void nicoclient$restoreFire(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        pose.popPose();
    }
}
