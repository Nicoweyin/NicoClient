package dev.nicoweyin.nicoclient;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Saved sets of modules, settings and HUD positions, one file each in config/nicoclient-profiles.
 * Cosmetics are not part of a profile. Four presets are written the first time the folder is made.
 */
public final class Profiles {
    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _-]{1,24}");
    private static final String SUFFIX = ".properties";
    static final Map<String, Set<String>> PRESETS = Map.of(
        "PvP", Set.of("fps", "keys", "cps", "ping", "armor", "potions", "reach", "combo", "target", "zoom", "crosshair", "lowfire", "nohurtcam", "hitcolor", "staticfov", "togglesprint", "armorwarn"),
        "Survival", Set.of("fps", "coords", "direction", "biome", "clock", "armor", "held", "potions", "food", "pickups", "zoom", "waypoints", "deathpos", "toolprotect", "armorwarn", "chathistory"),
        "Performance", Set.of("fps", "memory", "zoom", "particles", "dynamicfps", "entitydistance"),
        "Minimal", Set.of("fps", "zoom"));

    private Profiles() {}

    public static Path folder() { return FabricLoader.getInstance().getConfigDir().resolve("nicoclient-profiles"); }

    private static Path file(String name) {
        if (name == null || !NAME.matcher(name).matches() || name.isBlank()) throw new IllegalArgumentException("Use 1 to 24 letters, digits, spaces, - or _.");
        return folder().resolve(name.trim() + SUFFIX);
    }

    /** Writes the presets once, so they can be loaded, changed or deleted like any other profile. */
    public static void ensurePresets() {
        Path folder = folder();
        if (Files.isDirectory(folder)) return;
        try {
            Files.createDirectories(folder);
            for (var preset : PRESETS.entrySet()) {
                Properties values = new Properties();
                for (ModuleManager.Module m : NicoClientMod.MODULES.modules) values.setProperty(m.id + ".enabled", Boolean.toString(preset.getValue().contains(m.id)));
                write(file(preset.getKey()), values, "NicoClient preset " + preset.getKey());
            }
        } catch (IOException e) {
            System.err.println("NicoClient: unable to write profile presets: " + e.getMessage());
        }
    }

    /** Profile names, presets first, then the rest alphabetically. */
    public static List<String> names() {
        ensurePresets();
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder())) {
            files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(SUFFIX))
                .map(name -> name.substring(0, name.length() - SUFFIX.length())).filter(name -> NAME.matcher(name).matches()).forEach(names::add);
        } catch (IOException e) {
            return names;
        }
        List<String> order = List.of("PvP", "Survival", "Performance", "Minimal");
        names.sort((a, b) -> {
            int ia = order.indexOf(a), ib = order.indexOf(b);
            if (ia >= 0 || ib >= 0) return Integer.compare(ia < 0 ? 99 : ia, ib < 0 ? 99 : ib);
            return a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT));
        });
        return names;
    }

    public static boolean isPreset(String name) { return PRESETS.containsKey(name); }

    /** Saves everything except cosmetics under this name, replacing a profile with the same name. */
    public static void save(String name) throws IOException {
        Path path = file(name);
        Files.createDirectories(folder());
        write(path, NicoClientMod.MODULES.snapshot(), "NicoClient profile " + name.trim());
    }

    public static void load(String name) throws IOException {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(file(name))) { values.load(reader); }
        NicoClientMod.MODULES.applyProfile(values);
    }

    public static void delete(String name) throws IOException { Files.deleteIfExists(file(name)); }

    private static void write(Path path, Properties values, String comment) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary)) { values.store(writer, comment); }
        Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
