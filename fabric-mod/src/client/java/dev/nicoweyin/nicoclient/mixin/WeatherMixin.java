package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only what this client shows changes; the world on the server keeps its weather. */
@Mixin(Level.class)
public abstract class WeatherMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void nicoclient$noRain(float partialTick, CallbackInfoReturnable<Float> result) {
        if (NicoClientMod.MODULES.noWeather.enabled && (Object) this instanceof ClientLevel) result.setReturnValue(0f);
    }
    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void nicoclient$noThunder(float partialTick, CallbackInfoReturnable<Float> result) {
        if (NicoClientMod.MODULES.noWeather.enabled && (Object) this instanceof ClientLevel) result.setReturnValue(0f);
    }
}
