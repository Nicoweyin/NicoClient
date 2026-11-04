package dev.nicoweyin.nicoclient;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

public final class ModuleManager {
    public static class Module {
        public final String id, name, category;
        final boolean enabledByDefault;
        public boolean enabled, favorite;
        Module(String id, String name, String category) { this(id, name, category, false); }
        Module(String id, String name, String category, boolean enabledByDefault) {
            this.id = id; this.name = name; this.category = category; this.enabledByDefault = enabledByDefault;
            this.enabled = enabledByDefault;
        }
    }

    /** A module with a position on the screen that can be moved in the HUD editor. */
    public static final class HudModule extends Module {
        public int x, y;
        public final int height;
        final int defaultX, defaultY;
        HudModule(String id, String name, boolean enabledByDefault, int x, int y, int height) {
            super(id, name, "HUD", enabledByDefault);
            this.x = defaultX = x; this.y = defaultY = y; this.height = height;
        }
        public void resetPosition() { x = defaultX; y = defaultY; }
    }

    // Two columns by default, so enabling everything stays clear of the chat at small window sizes.
    private static final int ROW = 17, FIRST_ROW = 62, SECOND_COLUMN = 120;
    public final HudModule fps = new HudModule("fps", "FPS Counter", true, 5, 5, 15);
    public final HudModule keys = new HudModule("keys", "Keystrokes", false, 5, 25, 34);
    public final HudModule cps = new HudModule("cps", "CPS Counter", false, 5, FIRST_ROW, 15);
    public final HudModule coords = new HudModule("coords", "Coordinates", false, 5, FIRST_ROW + ROW, 15);
    public final HudModule direction = new HudModule("direction", "Direction", false, 5, FIRST_ROW + ROW * 2, 15);
    public final HudModule speed = new HudModule("speed", "Speed", false, 5, FIRST_ROW + ROW * 3, 15);
    public final HudModule biome = new HudModule("biome", "Biome", false, 5, FIRST_ROW + ROW * 4, 15);
    public final HudModule ping = new HudModule("ping", "Ping", false, SECOND_COLUMN, 5, 15);
    public final HudModule clock = new HudModule("clock", "Clock", false, SECOND_COLUMN, 5 + ROW, 15);
    public final HudModule session = new HudModule("session", "Session Timer", false, SECOND_COLUMN, 5 + ROW * 2, 15);
    public final HudModule armor = new HudModule("armor", "Armor HUD", false, SECOND_COLUMN, 5 + ROW * 3, 16);
    public final Module zoom = new Module("zoom", "Zoom", "Visuals");
    public final Module lowFire = new Module("lowfire", "Low Fire", "Visuals");
    public final Module fullbright = new Module("fullbright", "Fullbright", "Visuals");
    public final Module trajectories = new Module("trajectories", "Trajectories", "Visuals");
    public final Module particles = new Module("particles", "Reduced Particles", "Performance");
    public final List<HudModule> hudModules = List.of(fps, keys, cps, coords, direction, speed, biome, ping, clock, session, armor);
    public final List<Module> modules = Stream.concat(hudModules.stream(), Stream.<Module>of(zoom, lowFire, fullbright, trajectories, particles)).toList();
    public int zoomFactor = 4;
    public int brightness = 100;
    public boolean trajectoryMarker = true;
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("nicoclient.properties");
    private final Properties properties = new Properties();

    public void load() {
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            for (Module m : modules) {
                m.enabled = Boolean.parseBoolean(properties.getProperty(m.id + ".enabled", Boolean.toString(m.enabledByDefault)));
                m.favorite = Boolean.parseBoolean(properties.getProperty(m.id + ".favorite", "false"));
            }
            for (HudModule m : hudModules) {
                m.x = number(m.id + ".x", m.defaultX);
                m.y = number(m.id + ".y", m.defaultY);
            }
            zoomFactor = number("zoom.factor", 4);
            if (zoomFactor != 2 && zoomFactor != 4 && zoomFactor != 8) zoomFactor = 4;
            brightness = number("fullbright.brightness", 100);
            if (brightness != 60 && brightness != 80 && brightness != 100) brightness = 100;
            trajectoryMarker = Boolean.parseBoolean(properties.getProperty("trajectories.marker", "true"));
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("NicoClient: unable to read configuration: " + e.getMessage());
        }
    }

    private int number(String key, int fallback) {
        try { return Math.max(0, Math.min(10000, Integer.parseInt(properties.getProperty(key, Integer.toString(fallback))))); }
        catch (NumberFormatException e) { return fallback; }
    }

    public void save() {
        for (Module m : modules) {
            properties.setProperty(m.id + ".enabled", Boolean.toString(m.enabled));
            properties.setProperty(m.id + ".favorite", Boolean.toString(m.favorite));
        }
        for (HudModule m : hudModules) {
            properties.setProperty(m.id + ".x", Integer.toString(m.x));
            properties.setProperty(m.id + ".y", Integer.toString(m.y));
        }
        properties.setProperty("zoom.factor", Integer.toString(zoomFactor));
        properties.setProperty("fullbright.brightness", Integer.toString(brightness));
        properties.setProperty("trajectories.marker", Boolean.toString(trajectoryMarker));
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) { properties.store(writer, "NicoClient settings"); }
        } catch (IOException e) { System.err.println("NicoClient: unable to save configuration: " + e.getMessage()); }
    }
}
