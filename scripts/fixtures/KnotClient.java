package net.fabricmc.loader.impl.launch.knot;

public final class KnotClient {
    public static void main(String[] args) {
        if (args.length != 2 || !args[0].equals("--accessToken") || !args[1].equals("test-session-value")) {
            throw new IllegalStateException("Session was not delivered to the Fabric entry point.");
        }
        System.out.println("BOOTSTRAP_OK");
    }
}
