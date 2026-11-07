package dev.nicoweyin.nicoclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Server-safe mode pauses modules that many public servers do not allow, such as Freelook and Trajectories.
 * They stay on in singleplayer and on LAN worlds, and come back on their own after leaving the server.
 */
public final class ServerSafe {
    /** Lets the game test act as if it played on a public server. Null means: look at the real connection. */
    public static Boolean simulateServer;
    private static boolean restricted, wasRestricted;

    private ServerSafe() {}

    /** True on a public server or Realm; false in singleplayer, on LAN and in menus. */
    public static boolean onPublicServer(Minecraft client) {
        if (simulateServer != null) return simulateServer;
        if (client.level == null || client.hasSingleplayerServer()) return false;
        ServerData server = client.getCurrentServer();
        return server != null && !server.isLan();
    }

    public static void tick(Minecraft client) {
        ModuleManager m = NicoClientMod.MODULES;
        restricted = m.serverSafe.on() && onPublicServer(client);
        if (restricted && !wasRestricted && client.player != null) {
            String paused = m.serverRestricted.stream().filter(module -> module.enabled).map(module -> module.name).collect(Collectors.joining(", "));
            if (!paused.isEmpty()) Notify.show("Server-safe mode", "Paused " + paused, Notify.WARN);
        }
        wasRestricted = restricted;
    }

    /** True while this module is switched on but held back by Server-safe mode. */
    public static boolean paused(ModuleManager.Module module) {
        return restricted && NicoClientMod.MODULES.serverRestricted.contains(module);
    }

    /** The modules Server-safe mode pauses, for the settings screen. */
    public static List<String> names() {
        return NicoClientMod.MODULES.serverRestricted.stream().map(module -> module.name).toList();
    }
}
