package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.HudRenderer;
import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only what this client shows changes; the time on the server stays the same. */
@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientTimeMixin {
    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void nicoclient$customTime(CallbackInfoReturnable<Long> result) {
        if (NicoClientMod.MODULES.customTime.enabled && !HudRenderer.readingRealTime) result.setReturnValue((long) NicoClientMod.MODULES.timeOfDay.get());
    }
}
