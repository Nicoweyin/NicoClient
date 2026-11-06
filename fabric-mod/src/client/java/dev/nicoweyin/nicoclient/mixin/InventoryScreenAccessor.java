package dev.nicoweyin.nicoclient.mixin;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The Cosmetics preview turns the player all the way around, which the vanilla preview cannot. */
@Mixin(InventoryScreen.class)
public interface InventoryScreenAccessor {
    @Invoker("extractRenderState")
    static EntityRenderState nicoclient$extractRenderState(LivingEntity entity) { throw new AssertionError(); }
}
