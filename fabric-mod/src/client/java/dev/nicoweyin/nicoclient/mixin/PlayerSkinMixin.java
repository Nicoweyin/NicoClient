package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cosmetics: your own player wears the chosen NicoClient cape, which also colors an elytra like vanilla capes do. */
@Mixin(AbstractClientPlayer.class)
public abstract class PlayerSkinMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void nicoclient$cape(CallbackInfoReturnable<PlayerSkin> result) {
        int cape = Cosmetics.cape();
        if (cape == 0 || (Object) this != Minecraft.getInstance().player) return;
        PlayerSkin skin = result.getReturnValue();
        var id = Cosmetics.capeTexture(cape);
        var texture = new ClientAsset.ResourceTexture(id, id);
        result.setReturnValue(new PlayerSkin(skin.body(), texture, texture, skin.model(), skin.secure()));
    }
}
