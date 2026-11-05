package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Block Outline: recolors and thickens the outline around the block you look at. */
@Mixin(LevelRenderer.class)
public abstract class BlockOutlineMixin {
    /** Vanilla's dark back outline for high contrast mode stays as it is. */
    private static final int HIGH_CONTRAST_BACK = 0xFF000000;

    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int nicoclient$outlineColor(int color) {
        return NicoClientMod.MODULES.blockOutline.enabled && color != HIGH_CONTRAST_BACK ? NicoClientMod.outlineColor() : color;
    }

    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float nicoclient$outlineWidth(float width) {
        return NicoClientMod.MODULES.blockOutline.enabled ? width * NicoClientMod.MODULES.outlineWidth.get() / 100f : width;
    }
}
