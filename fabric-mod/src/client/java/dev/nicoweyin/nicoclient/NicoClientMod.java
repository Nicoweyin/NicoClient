package dev.nicoweyin.nicoclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class NicoClientMod implements ClientModInitializer {
    public static final ModuleManager MODULES = new ModuleManager();
    public static KeyMapping zoomKey, menuKey;
    @Override
    public void onInitializeClient() {
        MODULES.load();
        TrajectoryRenderer.register();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nicoclient", "controls"));
        KeyMapping toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nicoclient.fps", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        KeyMapping gui = menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nicoclient.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nicoclient.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.consumeClick()) { MODULES.fps.enabled = !MODULES.fps.enabled; MODULES.save(); }
            while (gui.consumeClick()) if (client.screen == null) client.setScreen(new NicoScreen());
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("nicoclient", "hud"), (graphics, delta) -> HudRenderer.render(graphics));
    }
}
