package dev.nicoweyin.nicoclient.mixin;

import dev.nicoweyin.nicoclient.NicoClientMod;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Chat History: keeps more than vanilla's 100 chat messages. */
@Mixin(ChatComponent.class)
public abstract class ChatHistoryMixin {
    @ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
    private int nicoclient$chatLimit(int limit) {
        return NicoClientMod.MODULES.chatHistory.enabled ? NicoClientMod.MODULES.chatHistoryLimit.get() : limit;
    }
}
