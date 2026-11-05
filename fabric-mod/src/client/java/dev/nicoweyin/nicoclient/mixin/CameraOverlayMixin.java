package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Pumpkin: hides the carved pumpkin view and, if chosen, the powder snow frost. */
@Mixin(Gui.class)
public abstract class CameraOverlayMixin {
    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void nicoclient$noPumpkin(GuiGraphics g, Identifier texture, float alpha, CallbackInfo ci) {
        if (!NicoClientMod.MODULES.noPumpkin.enabled) return;
        String path = texture.getPath();
        if (path.contains("pumpkinblur") || (NicoClientMod.MODULES.noPowderSnow.on() && path.contains("powder_snow_outline"))) ci.cancel();
    }
}
