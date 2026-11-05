package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.nicoweyin.nicoclient.Combat;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Tool Protection stops mining with a nearly broken tool; hits are counted for the combat HUD. */
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

    /** Reach Display and Combo Counter count your own hits. */
    @Inject(method = "attack", at = @At("HEAD"))
    private void nicoclient$countHit(Player player, Entity target, CallbackInfo ci) {
        if (player instanceof LocalPlayer local) Combat.onAttack(local, target);
    }
}
