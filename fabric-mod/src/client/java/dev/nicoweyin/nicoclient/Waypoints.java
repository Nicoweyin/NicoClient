package dev.nicoweyin.nicoclient;

import dev.nicoweyin.nicoclient.mixin.GameRendererAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Named places per world and server, shown as markers in the world. */
public final class Waypoints {
    public static final int[] COLORS = {0xFF8C85FF, 0xFF4C8DFF, 0xFF22B8CF, 0xFF34C77B, 0xFFFFC94D, 0xFFFF6B6B, 0xFFEC5FA6};
    private static final int MAX_NAME = 24;

    public static final class Waypoint {
        public String name;
        public final String world, dimension;
        public final int x, y, z;
        public int color;
        public boolean visible = true;
        /** True for a marked block, which also gets a colored box around it. */
        public boolean block;

        Waypoint(String name, String world, String dimension, int x, int y, int z, int color) {
            this.name = name; this.world = world; this.dimension = dimension; this.x = x; this.y = y; this.z = z; this.color = color;
        }

        public String coordinates() { return x + " " + y + " " + z; }

        public double distanceTo(Vec3 position) {
            double dx = x + 0.5 - position.x, dy = y + 0.5 - position.y, dz = z + 0.5 - position.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private static final List<Waypoint> ALL = new ArrayList<>();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nicoclient-waypoints.txt");
    private static boolean loaded;

    private Waypoints() {}

    /** The server address, or the single-player world name, so each place keeps its own waypoints. */
    public static String worldKey(Minecraft client) {
        if (client.getCurrentServer() != null) return "server:" + client.getCurrentServer().ip;
        if (client.getSingleplayerServer() != null) return "world:" + client.getSingleplayerServer().getWorldData().getLevelName();
        return "unknown";
    }

    private static String dimension(Minecraft client) { return client.level == null ? "" : client.level.dimension().identifier().toString(); }

    /** Waypoints of the world you are in, in every dimension. */
    public static List<Waypoint> here(Minecraft client) {
        load();
        String world = worldKey(client);
        return ALL.stream().filter(point -> point.world.equals(world)).toList();
    }

    public static Waypoint add(Minecraft client, String name) { return add(client, name, client.player.blockPosition(), false); }

    /** The block under the crosshair, or null when you are not looking at one. */
    public static BlockPos lookedAtBlock(Minecraft client) {
        return client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    /** Marks the block you look at, named after the block, like "Chest". */
    public static Waypoint markBlock(Minecraft client, String name) {
        BlockPos pos = lookedAtBlock(client);
        if (pos == null) return null;
        String fallback = client.level.getBlockState(pos).getBlock().getName().getString();
        return add(client, name == null || name.isBlank() ? fallback : name, pos, true);
    }

    public static Waypoint add(Minecraft client, String name, BlockPos pos, boolean block) {
        load();
        List<Waypoint> existing = here(client);
        String clean = name == null ? "" : name.replace('\t', ' ').replace('\n', ' ').strip();
        if (clean.isEmpty()) clean = "Waypoint " + (existing.size() + 1);
        if (clean.length() > MAX_NAME) clean = clean.substring(0, MAX_NAME);
        Waypoint point = new Waypoint(clean, worldKey(client), dimension(client), pos.getX(), pos.getY(), pos.getZ(), COLORS[existing.size() % COLORS.length]);
        point.block = block;
        ALL.add(point);
        save();
        return point;
    }

    public static void remove(Waypoint point) { ALL.remove(point); save(); }

    public static void cycleColor(Waypoint point) {
        int index = 0;
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == point.color) index = i;
        point.color = COLORS[(index + 1) % COLORS.length];
        save();
    }

    public static void toggleVisible(Waypoint point) { point.visible = !point.visible; save(); }

    public static String dimensionName(Waypoint point) {
        String path = point.dimension.substring(point.dimension.indexOf(':') + 1);
        return HudRenderer.title(path);
    }

    public static boolean inCurrentDimension(Minecraft client, Waypoint point) { return point.dimension.equals(dimension(client)); }

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(FILE)) return;
        try {
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t");
                if (parts.length < 8) continue;
                try {
                    Waypoint point = new Waypoint(parts[2], parts[0], parts[1], Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), (int) Long.parseLong(parts[6], 16));
                    point.visible = Boolean.parseBoolean(parts[7]);
                    point.block = parts.length > 8 && Boolean.parseBoolean(parts[8]);
                    ALL.add(point);
                } catch (NumberFormatException ignored) { /* Skip a damaged line, keep the rest. */ }
            }
        } catch (IOException e) {
            System.err.println("NicoClient: unable to read waypoints: " + e.getMessage());
        }
    }

    private static void save() {
        List<String> lines = new ArrayList<>();
        for (Waypoint p : ALL) {
            lines.add(String.join("\t", p.world, p.dimension, p.name, Integer.toString(p.x), Integer.toString(p.y), Integer.toString(p.z), Integer.toHexString(p.color), Boolean.toString(p.visible), Boolean.toString(p.block)));
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("NicoClient: unable to save waypoints: " + e.getMessage());
        }
    }

    /** Projects each visible waypoint of this dimension onto the screen and draws its marker. */
    public static void render(GuiGraphics g, Minecraft client) {
        if (!NicoClientMod.MODULES.waypoints.enabled || client.level == null) return;
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        Vector3fc forward = camera.forwardVector(), up = camera.upVector(), left = camera.leftVector();
        float fov = ((GameRendererAccessor) client.gameRenderer).nicoclient$getFov(camera, client.getDeltaTracker().getGameTimeDeltaPartialTick(true), true);
        double focal = 1 / Math.tan(Math.toRadians(fov) / 2);
        float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
        boolean distance = NicoClientMod.MODULES.waypointDistance.on();
        for (Waypoint point : here(client)) {
            if (!point.visible || !inCurrentDimension(client, point)) continue;
            double vx = point.x + 0.5 - eye.x, vy = point.y + 0.5 - eye.y, vz = point.z + 0.5 - eye.z;
            double depth = vx * forward.x() + vy * forward.y() + vz * forward.z();
            if (depth < 0.1) continue;
            double side = vx * left.x() + vy * left.y() + vz * left.z();
            double height = vx * up.x() + vy * up.y() + vz * up.z();
            float sx = (float) (cx - side / depth * focal * cy), sy = (float) (cy - height / depth * focal * cy);
            if (sx < -50 || sx > g.guiWidth() + 50 || sy < -20 || sy > g.guiHeight() + 20) continue;
            String label = point.name + (distance ? "  " + (int) point.distanceTo(client.player.position()) + " m" : "");
            int w = Ui.width(label) + 12;
            Ui.rounded(g, sx - 3, sy - 3, 6, 6, 3, point.color);
            Ui.rounded(g, sx - w / 2f, sy - 19, w, 13, 5, 0xB00D0F1E);
            Ui.text(g, label, sx - w / 2f + 6, sy - 16, Ui.mix(point.color, 0xFFFFFFFF, 0.55f));
        }
    }

    public static void register() { WorldRenderEvents.BEFORE_DEBUG_RENDER.register(Waypoints::renderBoxes); }

    /** A colored box around each marked block nearby, slightly larger than the block so it never flickers. */
    private static void renderBoxes(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!NicoClientMod.MODULES.waypoints.enabled || client.level == null || client.player == null || context.consumers() == null) return;
        Vec3 camera = context.worldState().cameraRenderState.pos;
        PoseStack.Pose pose = context.matrices().last();
        VertexConsumer lines = null;
        for (Waypoint point : here(client)) {
            if (!point.block || !point.visible || !inCurrentDimension(client, point) || point.distanceTo(client.player.position()) > 160) continue;
            if (lines == null) lines = context.consumers().getBuffer(RenderTypes.lines());
            double e = 0.004, x0 = point.x - e - camera.x, y0 = point.y - e - camera.y, z0 = point.z - e - camera.z;
            double x1 = point.x + 1 + e - camera.x, y1 = point.y + 1 + e - camera.y, z1 = point.z + 1 + e - camera.z;
            double[][] corners = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] edge : edges) edge(lines, pose, corners[edge[0]], corners[edge[1]], point.color);
        }
    }

    private static void edge(VertexConsumer out, PoseStack.Pose pose, double[] a, double[] b, int color) {
        float nx = (float) (b[0] - a[0]), ny = (float) (b[1] - a[1]), nz = (float) (b[2] - a[2]);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length; ny /= length; nz /= length;
        out.addVertex(pose, (float) a[0], (float) a[1], (float) a[2]).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(3.0f);
        out.addVertex(pose, (float) b[0], (float) b[1], (float) b[2]).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(3.0f);
    }

    /** For the client test: screen position of a waypoint, or null when it is not on screen. */
    public static float[] screenPosition(Minecraft client, Waypoint point, int guiWidth, int guiHeight) {
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        Vector3fc forward = camera.forwardVector(), up = camera.upVector(), left = camera.leftVector();
        float fov = ((GameRendererAccessor) client.gameRenderer).nicoclient$getFov(camera, 1, true);
        double focal = 1 / Math.tan(Math.toRadians(fov) / 2);
        double vx = point.x + 0.5 - eye.x, vy = point.y + 0.5 - eye.y, vz = point.z + 0.5 - eye.z;
        double depth = vx * forward.x() + vy * forward.y() + vz * forward.z();
        if (depth < 0.1) return null;
        double side = vx * left.x() + vy * left.y() + vz * left.z(), height = vx * up.x() + vy * up.y() + vz * up.z();
        return new float[] {(float) (guiWidth / 2f - side / depth * focal * guiHeight / 2f), (float) (guiHeight / 2f - height / depth * focal * guiHeight / 2f)};
    }
}
