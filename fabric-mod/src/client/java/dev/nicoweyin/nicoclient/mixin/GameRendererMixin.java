package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void nicoclient$zoom(Camera camera, float partialTick, boolean changingFov, CallbackInfoReturnable<Float> result) {
        Minecraft client = Minecraft.getInstance();
        if (changingFov && client.player != null && client.screen == null && NicoClientMod.MODULES.zoom.enabled
                && NicoClientMod.zoomKey != null && NicoClientMod.zoomKey.isDown()) {
            result.setReturnValue(result.getReturnValueF() / NicoClientMod.MODULES.zoomFactor);
        }
    }
}
