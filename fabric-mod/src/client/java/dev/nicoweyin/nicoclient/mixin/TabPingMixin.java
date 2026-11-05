package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tab Ping: the player list shows each ping in milliseconds instead of signal bars. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabPingMixin {
    @Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
    private void nicoclient$numericPing(GuiGraphics g, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
        if (!NicoClientMod.MODULES.tabPing.enabled) return;
        int ping = info.getLatency();
        String text = Integer.toString(ping);
        int color = ping < 0 ? 0xFF8A90AE : ping < 80 ? 0xFF6BF28C : ping < 150 ? 0xFFFFE27A : ping < 300 ? 0xFFFFA24D : 0xFFFF6B6B;
        var font = Minecraft.getInstance().font;
        // Three-quarter size fits the space of the vanilla icon.
        g.pose().pushMatrix();
        g.pose().translate(x + width - 1, y + 1);
        g.pose().scale(0.75f, 0.75f);
        g.drawString(font, text, -font.width(text), 0, color, true);
        g.pose().popMatrix();
        ci.cancel();
    }
}
