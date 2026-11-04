package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mixin(ChatComponent.class)
public abstract class ChatTimestampMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component nicoclient$timestamp(Component message) {
        if (!NicoClientMod.MODULES.chatTime.enabled) return message;
        String pattern = NicoClientMod.MODULES.chat24.on() ? "HH:mm:ss" : "HH:mm";
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern));
        return Component.literal("[" + time + "] ").withStyle(ChatFormatting.DARK_GRAY).append(message);
    }
}
