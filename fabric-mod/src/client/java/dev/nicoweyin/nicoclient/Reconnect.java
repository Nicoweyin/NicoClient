package dev.nicoweyin.nicoclient;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Auto Reconnect: adds a Reconnect button, with an optional countdown, when a server disconnects you. */
public final class Reconnect {
    /** The last multiplayer server joined; single-player worlds are never stored. */
    public static ServerData lastServer;
    private static Screen countdownScreen;
    private static int ticksLeft = -1;

    private Reconnect() {}

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerData server = client.getCurrentServer();
            if (server != null) lastServer = server;
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof DisconnectedScreen) || !NicoClientMod.MODULES.autoReconnect.enabled || lastServer == null) return;
            List<AbstractWidget> buttons = Screens.getButtons(screen);
            int bottom = buttons.stream().mapToInt(widget -> widget.getY() + widget.getHeight()).max().orElse(height / 2);
            // Resizing the window runs init again; the countdown keeps going instead of starting over.
            if (countdownScreen != screen) {
                countdownScreen = screen;
                int seconds = NicoClientMod.MODULES.reconnectDelay.get();
                ticksLeft = seconds > 0 ? seconds * 20 : -1;
            }
            Button button = Button.builder(label(), pressed -> connect(client)).bounds(width / 2 - 100, bottom + 4, 200, 20).build();
            buttons.add(button);
            ScreenEvents.afterTick(screen).register(ticked -> {
                if (ticksLeft <= 0 || client.screen != ticked) return;
                ticksLeft--;
                button.setMessage(label());
                if (ticksLeft == 0) connect(client);
            });
        });
    }

    private static Component label() {
        return Component.literal(ticksLeft > 0 ? "Reconnect in " + (ticksLeft + 19) / 20 + "s" : "Reconnect to " + lastServer.ip);
    }

    private static void connect(Minecraft client) {
        ticksLeft = -1;
        countdownScreen = null;
        ServerData server = lastServer;
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client, ServerAddress.parseString(server.ip), server, false, null);
    }
}
