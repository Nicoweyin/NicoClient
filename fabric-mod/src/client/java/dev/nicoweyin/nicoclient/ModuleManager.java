package dev.nicoweyin.nicoclient;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

public final class ModuleManager {
    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
        Map.entry("fps", "Frames per second"),
        Map.entry("keys", "Shows the movement keys you press"),
        Map.entry("cps", "Clicks per second"),
        Map.entry("coords", "Your position in the world"),
        Map.entry("direction", "The direction you are facing"),
        Map.entry("speed", "How fast you are moving"),
        Map.entry("biome", "The biome you are standing in"),
        Map.entry("ping", "Your connection delay to the server"),
        Map.entry("clock", "The time on your computer"),
        Map.entry("session", "How long you have been playing"),
        Map.entry("armor", "Your armor and its durability"),
        Map.entry("held", "The item in your hand"),
        Map.entry("day", "How many days the world has run"),
        Map.entry("light", "The light level at your position"),
        Map.entry("memory", "How much RAM Minecraft uses"),
        Map.entry("server", "The server and its player count"),
        Map.entry("potions", "Active effects and time left"),
        Map.entry("zoom", "Hold the zoom key to look closer"),
        Map.entry("crosshair", "Your own crosshair style and color"),
        Map.entry("lowfire", "Lowers the fire on your screen"),
        Map.entry("fullbright", "See clearly in the dark"),
        Map.entry("trajectories", "Shows where throwables will fly"),
        Map.entry("nofog", "Removes distance fog"),
        Map.entry("noweather", "Hides rain and storms for you"),
        Map.entry("time", "Pick a time of day for you"),
        Map.entry("novignette", "Removes dark screen corners"),
        Map.entry("nohurtcam", "Stops the camera shake when hit"),
        Map.entry("togglesprint", "Press sprint once to keep sprinting"),
        Map.entry("togglesneak", "Press sneak once to keep sneaking"),
        Map.entry("copycoords", "Set a key to copy your position"),
        Map.entry("chattime", "Adds the time to chat messages"),
        Map.entry("particles", "Fewer particles for more FPS"),
        Map.entry("dynamicfps", "Saves power while in the background"),
        Map.entry("pickups", "Shows the items you pick up"),
        Map.entry("autosprint", "Sprints whenever you walk forward"),
        Map.entry("toolprotect", "Stops mining before a tool breaks"),
        Map.entry("deathpos", "Tells you where you died"),
        Map.entry("entitydistance", "Hides far away mobs for more FPS"),
        Map.entry("outline", "Color and size of the block outline"),
        Map.entry("chathistory", "Keeps more chat messages to scroll back"),
        Map.entry("reach", "How far away your last hit landed"),
        Map.entry("combo", "Hits in a row without being hit"),
        Map.entry("food", "Your hunger and hidden saturation"),
        Map.entry("arrows", "Arrows left while holding a bow"),
        Map.entry("staticfov", "No FOV change when sprinting or flying"),
        Map.entry("clearwater", "See further underwater"),
        Map.entry("hitcolor", "Color of mobs and players when hit"),
        Map.entry("freelook", "Hold a key to look around you"),
        Map.entry("quiet", "Turns all game sounds down at once"),
        Map.entry("reconnect", "Rejoin a server after a disconnect"),
        Map.entry("waypoints", "Save places and see them in the world"),
        Map.entry("target", "Health and distance of who you fight"),
        Map.entry("armorwarn", "Warns before armor or tools break"),
        Map.entry("tabping", "Ping in milliseconds in the player list"),
        Map.entry("nopumpkin", "Clear view with a pumpkin on your head"));

    /** One configurable value of a module, shown as switch or segmented choice. */
    public static final class Setting {
        public final String id, label;
        public final String[] labels;
        private final int[] values;
        private int index;

        private Setting(String id, String label, String[] labels, int[] values, int fallback) {
            this.id = id; this.label = label; this.labels = labels; this.values = values;
            set(fallback);
        }
        public int get() { return values[index]; }
        public boolean on() { return get() != 0; }
        public int index() { return index; }
        public boolean isSwitch() { return labels.length == 0; }
        public void selectIndex(int value) { if (value >= 0 && value < values.length) index = value; }
        public void set(int value) { for (int i = 0; i < values.length; i++) if (values[i] == value) index = i; }
        public void flip() { index = 1 - index; }
    }

    public static class Module {
        public final String id, name, category;
        final boolean enabledByDefault;
        public boolean enabled, favorite;
        /** GLFW key that toggles this module during play, or -1 when unset. */
        public int hotkey = -1;
        public final List<Setting> settings = new ArrayList<>();
        Module(String id, String name, String category) { this(id, name, category, false); }
        Module(String id, String name, String category, boolean enabledByDefault) {
            this.id = id; this.name = name; this.category = category; this.enabledByDefault = enabledByDefault;
            this.enabled = enabledByDefault;
        }
        public String description() { return DESCRIPTIONS.getOrDefault(id, ""); }
        Setting toggle(String id, String label, boolean fallback) {
            Setting setting = new Setting(id, label, new String[0], new int[] {0, 1}, fallback ? 1 : 0);
            settings.add(setting);
            return setting;
        }
        Setting choice(String id, String label, int fallback, String[] labels, int... values) {
            Setting setting = new Setting(id, label, labels, values, fallback);
            settings.add(setting);
            return setting;
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

    // Three columns by default, so enabling everything stays clear of the chat at small window sizes.
    private static final int ROW = 17, FIRST_ROW = 62, SECOND_COLUMN = 120, THIRD_COLUMN = 210;
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
    public final HudModule held = new HudModule("held", "Held Item", false, SECOND_COLUMN, 5 + ROW * 4, 15);
    public final HudModule day = new HudModule("day", "Day Counter", false, SECOND_COLUMN, 5 + ROW * 5, 15);
    public final HudModule light = new HudModule("light", "Light Level", false, SECOND_COLUMN, 5 + ROW * 6, 15);
    public final HudModule memory = new HudModule("memory", "Memory Usage", false, THIRD_COLUMN, 5, 15);
    public final HudModule server = new HudModule("server", "Server Info", false, THIRD_COLUMN, 5 + ROW, 15);
    public final HudModule potions = new HudModule("potions", "Potion Effects", false, THIRD_COLUMN, 5 + ROW * 2, 15);
    public final HudModule pickups = new HudModule("pickups", "Item Pickups", false, THIRD_COLUMN, 5 + ROW * 5, 18);
    public final HudModule reach = new HudModule("reach", "Reach Display", false, SECOND_COLUMN, 5 + ROW * 7, 15);
    public final HudModule combo = new HudModule("combo", "Combo Counter", false, SECOND_COLUMN, 5 + ROW * 8, 15);
    public final HudModule food = new HudModule("food", "Food & Saturation", false, 5, FIRST_ROW + ROW * 5, 15);
    public final HudModule arrows = new HudModule("arrows", "Arrow Counter", false, 5, FIRST_ROW + ROW * 6, 15);
    public final HudModule target = new HudModule("target", "Target HUD", false, THIRD_COLUMN, 5 + ROW * 11, 32);
    public final Module zoom = new Module("zoom", "Zoom", "Visuals", true);
    public final Module crosshair = new Module("crosshair", "Custom Crosshair", "Visuals");
    public final Module lowFire = new Module("lowfire", "Low Fire", "Visuals");
    public final Module fullbright = new Module("fullbright", "Fullbright", "Visuals");
    public final Module trajectories = new Module("trajectories", "Trajectories", "Visuals");
    public final Module noFog = new Module("nofog", "No Fog", "Visuals");
    public final Module noWeather = new Module("noweather", "Clear Weather", "Visuals");
    public final Module customTime = new Module("time", "Custom Time", "Visuals");
    public final Module noVignette = new Module("novignette", "No Vignette", "Visuals");
    public final Module noHurtCam = new Module("nohurtcam", "No Hurt Camera", "Visuals");
    public final Module blockOutline = new Module("outline", "Block Outline", "Visuals");
    public final Module staticFov = new Module("staticfov", "Static FOV", "Visuals");
    public final Module clearWater = new Module("clearwater", "Clear Water", "Visuals");
    public final Module hitColor = new Module("hitcolor", "Hit Color", "Visuals");
    public final Module freelook = new Module("freelook", "Freelook", "Visuals");
    public final Module noPumpkin = new Module("nopumpkin", "No Pumpkin", "Visuals");
    public final Module tabPing = new Module("tabping", "Tab Ping", "Visuals");
    public final Module chatHistory = new Module("chathistory", "Chat History", "Utility");
    public final Module quietMode = new Module("quiet", "Quiet Mode", "Utility");
    public final Module autoReconnect = new Module("reconnect", "Auto Reconnect", "Utility");
    public final Module waypoints = new Module("waypoints", "Waypoints", "Utility");
    public final Module armorWarning = new Module("armorwarn", "Armor Warning", "Utility");
    public final Module toggleSprint = new Module("togglesprint", "Toggle Sprint", "Utility");
    public final Module toggleSneak = new Module("togglesneak", "Toggle Sneak", "Utility");
    public final Module copyCoords = new Module("copycoords", "Copy Coordinates", "Utility");
    public final Module autoSprint = new Module("autosprint", "Auto Sprint", "Utility");
    public final Module toolProtect = new Module("toolprotect", "Tool Protection", "Utility");
    public final Module deathPos = new Module("deathpos", "Death Position", "Utility");
    public final Module entityDistance = new Module("entitydistance", "Entity Distance", "Performance");
    public final Module chatTime = new Module("chattime", "Chat Timestamps", "Utility");
    public final Module particles = new Module("particles", "Reduced Particles", "Performance");
    public final Module dynamicFps = new Module("dynamicfps", "Dynamic FPS", "Performance");

    public final Setting zoomFactor = zoom.choice("factor", "Zoom factor", 4, new String[] {"2x", "4x", "8x"}, 2, 4, 8);
    public final Setting zoomSmooth = zoom.toggle("smooth", "Smooth zoom", true);
    public final Setting brightness = fullbright.choice("brightness", "Brightness", 100, new String[] {"60%", "80%", "100%"}, 60, 80, 100);
    public final Setting trajectoryMarker = trajectories.toggle("marker", "Impact marker", true);
    public final Setting clock24 = clock.toggle("24h", "24-hour clock", true);
    public final Setting clockSeconds = clock.toggle("seconds", "Show seconds", false);
    public final Setting directionShort = direction.toggle("short", "Short form", false);
    public final Setting speedUnit = speed.choice("unit", "Unit", 0, new String[] {"m/s", "km/h"}, 0, 1);
    public final Setting cpsRight = cps.toggle("right", "Show right clicks", true);
    public final Setting coordsY = coords.toggle("y", "Show height (Y)", true);
    public final Setting keysSpace = keys.toggle("space", "Show space bar", false);
    public final Setting keysMouse = keys.toggle("mouse", "Show mouse buttons", false);
    public final Setting heldCount = held.toggle("count", "Show count and durability", true);
    public final Setting memoryPercent = memory.toggle("percent", "Show as percent", false);
    public final Setting serverPlayers = server.toggle("players", "Show player count", true);
    public final Setting crosshairStyle = crosshair.choice("style", "Style", 0, new String[] {"Cross", "Dot", "Both"}, 0, 1, 2);
    public final Setting crosshairSize = crosshair.choice("size", "Size", 4, new String[] {"S", "M", "L"}, 3, 4, 6);
    public final Setting crosshairGap = crosshair.choice("gap", "Gap", 2, new String[] {"0", "2", "4"}, 0, 2, 4);
    public final Setting crosshairColor = crosshair.choice("color", "Color", 0, new String[] {"White", "Accent", "Green", "Red"}, 0, 1, 2, 3);
    public final Setting timeOfDay = customTime.choice("value", "Time", 6000, new String[] {"Morning", "Noon", "Sunset", "Night"}, 1000, 6000, 12500, 18000);
    public final Setting chat24 = chatTime.toggle("seconds", "Show seconds", false);
    public final Setting pickupSeconds = pickups.choice("seconds", "Show for", 3, new String[] {"2s", "3s", "5s"}, 2, 3, 5);
    public final Setting toolLimit = toolProtect.choice("limit", "Stop at durability", 5, new String[] {"2", "5", "10", "25"}, 2, 5, 10, 25);
    public final Setting deathCopy = deathPos.toggle("copy", "Copy to clipboard", false);
    public final Setting deathGuide = deathPos.toggle("guide", "Show the way back", true);
    public final Setting outlineColor = blockOutline.choice("color", "Color", 0, new String[] {"Accent", "White", "Cyan", "Red", "Green"}, 0, 1, 2, 3, 4);
    public final Setting outlineWidth = blockOutline.choice("width", "Thickness", 200, new String[] {"Thin", "Normal", "Thick"}, 100, 200, 300);
    public final Setting chatHistoryLimit = chatHistory.choice("limit", "Messages kept", 1000, new String[] {"500", "1000", "5000"}, 500, 1000, 5000);
    public final Setting entityRange = entityDistance.choice("range", "Distance", 48, new String[] {"16", "32", "48", "64"}, 16, 32, 48, 64);
    public final Setting entityPlayers = entityDistance.toggle("players", "Also hide players", false);
    public final Setting foodSaturation = food.toggle("saturation", "Show saturation", true);
    public final Setting arrowsAlways = arrows.toggle("always", "Always show", false);
    public final Setting waterClarity = clearWater.choice("clarity", "Clarity", 1, new String[] {"More", "Full"}, 1, 2);
    public final Setting hitColorValue = hitColor.choice("color", "Color", 0, new String[] {"Accent", "Blue", "Green", "White"}, 0, 1, 2, 3);
    public final Setting quietVolume = quietMode.choice("volume", "Volume", 25, new String[] {"0%", "10%", "25%", "50%"}, 0, 10, 25, 50);
    public final Setting reconnectDelay = autoReconnect.choice("delay", "Reconnect on its own", 0, new String[] {"Off", "5s", "10s", "30s"}, 0, 5, 10, 30);
    public final Setting waypointDistance = waypoints.toggle("distance", "Show distance", true);
    public final Setting waypointMarkBlock = waypoints.toggle("markblock", "Add key marks the looked-at block", true);
    public final Setting targetDistance = target.toggle("distance", "Show distance", true);
    public final Setting armorLimit = armorWarning.choice("limit", "Warn below", 10, new String[] {"5%", "10%", "20%"}, 5, 10, 20);
    public final Setting armorSound = armorWarning.toggle("sound", "Play a sound", true);
    public final Setting armorHeld = armorWarning.toggle("held", "Include held tool", true);
    public final Setting noPowderSnow = noPumpkin.toggle("snow", "Also powder snow frost", true);
    public final Setting dynamicFpsLimit = dynamicFps.choice("limit", "Unfocused limit", 15, new String[] {"5", "15", "30"}, 5, 15, 30);

    /** Settings for the whole client, opened from the sidebar. Not shown in the module list. */
    public final Module client = new Module("client", "Client Settings", "Client");
    public final Setting accent = client.choice("accent", "Accent color", 0, new String[] {"Violet", "Blue", "Cyan", "Green", "Pink"}, 0, 1, 2, 3, 4);
    public final Setting hudScale = client.choice("hudscale", "HUD size", 100, new String[] {"75%", "100%", "125%", "150%"}, 75, 100, 125, 150);
    public final Setting hudBackground = client.choice("hudbackground", "HUD background", 2, new String[] {"Off", "Soft", "Strong"}, 0, 1, 2);
    public final Setting hudText = client.choice("hudtext", "HUD text color", 0, new String[] {"White", "Accent", "Cyan", "Yellow"}, 0, 1, 2, 3);
    public final Setting clickSounds = client.toggle("sounds", "Menu click sounds", true);

    /** False until the first-run hint about the menu key has been shown. */
    public boolean welcomed;
    public final List<HudModule> hudModules = List.of(fps, keys, cps, coords, direction, speed, biome, ping, clock, session, armor, held, day, light, memory, server, potions, pickups, reach, combo, food, arrows, target);
    public final List<Module> modules = Stream.concat(hudModules.stream(), Stream.<Module>of(zoom, crosshair, lowFire, fullbright, trajectories, noFog, noWeather, customTime, noVignette, noHurtCam, blockOutline, staticFov, clearWater, hitColor, freelook, noPumpkin, tabPing, toggleSprint, toggleSneak, autoSprint, copyCoords, toolProtect, deathPos, chatTime, chatHistory, quietMode, autoReconnect, waypoints, armorWarning, particles, dynamicFps, entityDistance)).toList();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("nicoclient.properties");
    private final Properties properties = new Properties();

    private List<Module> withClient() { return Stream.concat(modules.stream(), Stream.of(client)).toList(); }

    public void load() {
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            welcomed = Boolean.parseBoolean(properties.getProperty("client.welcomed", "true"));
            for (Module m : withClient()) {
                m.enabled = Boolean.parseBoolean(properties.getProperty(m.id + ".enabled", Boolean.toString(m.enabledByDefault)));
                m.favorite = Boolean.parseBoolean(properties.getProperty(m.id + ".favorite", "false"));
                m.hotkey = number(m.id + ".key", -1);
                for (Setting setting : m.settings) loadSetting(m.id + "." + setting.id, setting);
            }
            for (HudModule m : hudModules) {
                m.x = number(m.id + ".x", m.defaultX);
                m.y = number(m.id + ".y", m.defaultY);
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("NicoClient: unable to read configuration: " + e.getMessage());
        }
    }

    private void loadSetting(String key, Setting setting) {
        String value = properties.getProperty(key);
        if (value == null) return;
        if (value.equals("true") || value.equals("false")) { setting.set(value.equals("true") ? 1 : 0); return; }
        try { setting.set(Integer.parseInt(value)); } catch (NumberFormatException ignored) { /* Keep the default. */ }
    }

    private int number(String key, int fallback) {
        try { return Math.max(-1, Math.min(10000, Integer.parseInt(properties.getProperty(key, Integer.toString(fallback))))); }
        catch (NumberFormatException e) { return fallback; }
    }

    public void save() {
        for (Module m : withClient()) {
            properties.setProperty(m.id + ".enabled", Boolean.toString(m.enabled));
            properties.setProperty(m.id + ".favorite", Boolean.toString(m.favorite));
            properties.setProperty(m.id + ".key", Integer.toString(m.hotkey));
            for (Setting setting : m.settings) properties.setProperty(m.id + "." + setting.id, setting.isSwitch() ? Boolean.toString(setting.on()) : Integer.toString(setting.get()));
        }
        for (HudModule m : hudModules) {
            properties.setProperty(m.id + ".x", Integer.toString(m.x));
            properties.setProperty(m.id + ".y", Integer.toString(m.y));
        }
        properties.setProperty("client.welcomed", Boolean.toString(welcomed));
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) { properties.store(writer, "NicoClient settings"); }
        } catch (IOException e) { System.err.println("NicoClient: unable to save configuration: " + e.getMessage()); }
    }
}
