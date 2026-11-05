package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tool Protection: stops mining while the held tool is about to break. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Shadow public abstract boolean isDestroying();
    @Shadow public abstract void stopDestroyBlock();

    @Inject(method = {"startDestroyBlock", "continueDestroyBlock"}, at = @At("HEAD"), cancellable = true)
    private void nicoclient$toolProtection(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> result) {
        if (!NicoClientMod.protectTool()) return;
        if (isDestroying()) stopDestroyBlock();
        result.setReturnValue(false);
    }
}
