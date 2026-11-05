package dev.nicoweyin.nicoclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import dev.nicoweyin.nicoclient.mixin.OverlayTextureAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class NicoClientMod implements ClientModInitializer {
    public static final ModuleManager MODULES = new ModuleManager();
    public static KeyMapping zoomKey, menuKey, freelookKey;
    private static boolean sprintApplied, sneakApplied, fogApplied, wasDead;
    private static long lastToolWarning;
    private static int hitColorApplied = -2;
    private static boolean quietApplied;
    private static int quietVolumeApplied = -1;
    /** Where the player last died while Show the way back is on; null when there is nothing to show. */
    public static BlockPos deathPoint;
    public static ResourceKey<Level> deathWorld;
    private static float zoomAmount = 1;
    private static long lastZoomFrame;

    @Override
    public void onInitializeClient() {
        MODULES.load();
        TrajectoryRenderer.register();
        Waypoints.register();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nicoclient", "controls"));
        KeyMapping toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nicoclient.fps", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        KeyMapping gui = menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nicoclient.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nicoclient.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category));
        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nicoclient.freelook", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, category));
        Reconnect.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.consumeClick()) { MODULES.fps.enabled = !MODULES.fps.enabled; MODULES.save(); }
            while (gui.consumeClick()) if (client.screen == null) client.setScreen(new NicoScreen());
            applyOptions(client);
            Pickups.tick(client);
            Combat.tick(client);
            ArmorWarning.tick(client);
            Freelook.tick(client);
            applyHitColor(client);
            applyQuietMode(client);
            autoSprint(client);
            deathPosition(client);
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("nicoclient", "hud"), (graphics, delta) -> HudRenderer.render(graphics));
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (MODULES.crosshair.enabled && client.options.getCameraType().isFirstPerson() && !client.options.hideGui) HudRenderer.crosshair(graphics);
            else vanilla.render(graphics, delta);
        });
    }

    /** Called for every real key press. Hotkeys work during play only, so typing in chat or menus never toggles a module. */
    public static void onKeyPressed(int key) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null || client.player == null) return;
        for (ModuleManager.Module module : MODULES.modules) {
            if (module.hotkey != key || module == MODULES.zoom) continue;
            if (module == MODULES.copyCoords) {
                if (module.enabled) copyCoordinates(client);
                continue;
            }
            if (module == MODULES.waypoints) {
                if (module.enabled) {
                    Waypoints.Waypoint point = MODULES.waypointMarkBlock.on() ? Waypoints.markBlock(client, null) : null;
                    if (point == null) point = Waypoints.add(client, null);
                    client.player.displayClientMessage(Component.literal((point.block ? "Marked " : "Saved ") + point.name + " at " + point.coordinates()), true);
                }
                continue;
            }
            module.enabled = !module.enabled;
            MODULES.save();
            client.player.displayClientMessage(Component.literal(module.name + (module.enabled ? " on" : " off")), true);
        }
    }

    private static void copyCoordinates(Minecraft client) {
        BlockPos pos = client.player.blockPosition();
        String text = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        client.keyboardHandler.setClipboard(text);
        client.player.displayClientMessage(Component.literal("Copied " + text), true);
    }

    /** Starts sprinting whenever vanilla would allow it, so holding the sprint key is never needed. */
    private static void autoSprint(Minecraft client) {
        LocalPlayer p = client.player;
        if (!MODULES.autoSprint.enabled || p == null || p.isSprinting() || !p.input.hasForwardImpulse()) return;
        if (p.isShiftKeyDown() || p.isUsingItem() || p.isInWater() || p.horizontalCollision || p.hasEffect(MobEffects.BLINDNESS)) return;
        if (p.getFoodData().getFoodLevel() <= 6 && !p.getAbilities().mayfly) return;
        p.setSprinting(true);
    }

    /** Writes the death position into chat once per death, so it can be found again. */
    private static void deathPosition(Minecraft client) {
        boolean dead = client.player != null && client.player.isDeadOrDying();
        if (dead && !wasDead && MODULES.deathPos.enabled && client.level != null) {
            BlockPos pos = client.player.blockPosition();
            String text = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            String world = HudRenderer.title(client.level.dimension().identifier().getPath());
            client.player.displayClientMessage(Component.literal("You died at " + text + " (" + world + ")"), false);
            if (MODULES.deathCopy.on()) client.keyboardHandler.setClipboard(text);
            deathPoint = pos;
            deathWorld = client.level.dimension();
        }
        wasDead = dead;
        // The guide ends when you get back, leave the world or turn it off.
        if (deathPoint != null && (client.level == null || !MODULES.deathPos.enabled || !MODULES.deathGuide.on()
                || (!dead && client.player != null && client.level.dimension() == deathWorld && client.player.blockPosition().distSqr(deathPoint) < 16))) {
            deathPoint = null;
        }
    }

    /** True when the held tool is nearly broken and Tool Protection should stop mining. */
    public static boolean protectTool() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer p = client.player;
        if (!MODULES.toolProtect.enabled || p == null || p.isCreative()) return false;
        ItemStack stack = p.getMainHandItem();
        if (!stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > MODULES.toolLimit.get()) return false;
        long now = System.currentTimeMillis();
        if (now - lastToolWarning > 1500) {
            lastToolWarning = now;
            p.displayClientMessage(Component.literal(stack.getHoverName().getString() + " is almost broken. Tool Protection stopped mining."), true);
        }
        return true;
    }

    /** True when Entity Distance should skip drawing this entity. Your own player and vehicle always stay visible. */
    public static boolean hideEntity(Entity entity, double x, double y, double z) {
        if (!MODULES.entityDistance.enabled) return false;
        Minecraft client = Minecraft.getInstance();
        if (entity == client.player || entity == client.getCameraEntity() || (client.player != null && entity == client.player.getVehicle())) return false;
        if (entity instanceof Player && !MODULES.entityPlayers.on()) return false;
        double range = MODULES.entityRange.get();
        return entity.distanceToSqr(x, y, z) > range * range;
    }

    /** The hurt tint of mobs and players. Vanilla's red is restored when Hit Color is off. */
    public static int hitColor() {
        if (!MODULES.hitColor.enabled) return 0xB2FF0000;
        int rgb = switch (MODULES.hitColorValue.index()) {
            case 1 -> 0x3D7BFF;
            case 2 -> 0x2EE86B;
            case 3 -> 0xFFFFFF;
            default -> Ui.accentLight() & 0xFFFFFF;
        };
        return 0xB2000000 | rgb;
    }

    /** Repaints the hurt rows of Minecraft's overlay texture only when the color changes. */
    private static void applyHitColor(Minecraft client) {
        int color = hitColor();
        if (color == hitColorApplied || client.gameRenderer == null) return;
        var texture = ((OverlayTextureAccessor) client.gameRenderer.overlayTexture()).nicoclient$texture();
        var pixels = texture.getPixels();
        if (pixels == null) return;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) pixels.setPixel(x, y, color);
        texture.upload();
        hitColorApplied = color;
    }

    /** Sounds already playing follow Quiet Mode right away. */
    private static void applyQuietMode(Minecraft client) {
        int volume = MODULES.quietVolume.get();
        if (MODULES.quietMode.enabled == quietApplied && volume == quietVolumeApplied) return;
        quietApplied = MODULES.quietMode.enabled;
        quietVolumeApplied = volume;
        for (SoundSource source : SoundSource.values()) client.getSoundManager().refreshCategoryVolume(source);
    }

    /** The Block Outline color, slightly see-through like the vanilla outline. */
    public static int outlineColor() {
        int rgb = switch (MODULES.outlineColor.index()) {
            case 1 -> 0xFFFFFF;
            case 2 -> 0x8BE9F5;
            case 3 -> 0xFF6B6B;
            case 4 -> 0x6BF28C;
            default -> Ui.accentLight() & 0xFFFFFF;
        };
        return 0xCC000000 | rgb;
    }

    /** Uses Minecraft's own options, and only touches them when a module changes state. */
    private static void applyOptions(Minecraft client) {
        if (MODULES.toggleSprint.enabled != sprintApplied) {
            sprintApplied = MODULES.toggleSprint.enabled;
            client.options.toggleSprint().set(sprintApplied);
        }
        if (MODULES.toggleSneak.enabled != sneakApplied) {
            sneakApplied = MODULES.toggleSneak.enabled;
            client.options.toggleCrouch().set(sneakApplied);
        }
        if (MODULES.noFog.enabled != fogApplied) {
            fogApplied = MODULES.noFog.enabled;
            if (FogRenderer.toggleFog() == fogApplied) FogRenderer.toggleFog();
        }
    }

    /** How far the view is zoomed in right now; moves gently unless Smooth zoom is off. */
    public static float zoomAmount(boolean held) {
        float target = held ? MODULES.zoomFactor.get() : 1;
        long now = System.nanoTime();
        float seconds = lastZoomFrame == 0 ? 0 : Math.min(0.1f, (now - lastZoomFrame) / 1e9f);
        lastZoomFrame = now;
        zoomAmount = MODULES.zoomSmooth.on() ? Ui.approach(zoomAmount, target, seconds, 14) : target;
        return zoomAmount;
    }
}
