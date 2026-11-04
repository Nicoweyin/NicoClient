package dev.nicoweyin.nicoclient.bootstrap;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.InvocationTargetException;

/** Receives the game token from the launcher over stdin, rather than on the command line. */
public final class LaunchBootstrap {
    public static void main(String[] args) throws Throwable {
        String accessToken = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (accessToken == null || accessToken.isEmpty()) throw new IllegalStateException("Missing Minecraft session.");
        int substitutions = 0;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("__NICOCLIENT_SESSION_TOKEN__")) {
                args[i] = accessToken;
                substitutions++;
            }
        }
        if (substitutions != 1) throw new IllegalStateException("Invalid Minecraft session arguments.");
        try {
            Class.forName("net.fabricmc.loader.impl.launch.knot.KnotClient")
                    .getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
