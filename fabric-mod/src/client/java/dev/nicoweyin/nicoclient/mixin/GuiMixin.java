package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void nicoclient$noVignette(CallbackInfo ci) {
        if (NicoClientMod.MODULES.noVignette.enabled) ci.cancel();
    }
}
