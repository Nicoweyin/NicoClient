package dev.nicoweyin.nicoclient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public final class TrajectoryRenderer {
    /** The flight path; hitBlock is the block it lands on, or null. */
    private record Prediction(List<Vec3> points, boolean impact, boolean marker, BlockPos hitBlock) {
        Prediction(List<Vec3> points, boolean impact, boolean marker) { this(points, impact, marker, null); }
    }
    private static volatile Prediction prediction = new Prediction(List.of(), false, false);
    private static final int GLOW = 0xFFFF3B3B;

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(context -> extract());
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(TrajectoryRenderer::render);
    }

    private static void extract() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (!NicoClientMod.MODULES.trajectories.active() || player == null || client.level == null) {
            prediction = new Prediction(List.of(), false, false); return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!supported(stack)) stack = player.getOffhandItem();
        if (!supported(stack)) { prediction = new Prediction(List.of(), false, false); return; }
        boolean bow = stack.is(Items.BOW), potion = stack.is(Items.SPLASH_POTION);
        float speed = potion ? 0.5f : 1.5f;
        if (bow) {
            if (!player.isUsingItem() || !player.getUseItem().is(Items.BOW)) { prediction = new Prediction(List.of(), false, false); return; }
            speed = BowItem.getPowerForTime(player.getTicksUsingItem()) * 3.0f;
            if (speed < 0.3f) { prediction = new Prediction(List.of(), false, false); return; }
        }
        Vec3 pos = player.getEyePosition().add(0, -0.1, 0);
        Vec3 direction = Vec3.directionFromRotation(player.getXRot() + (potion ? -20.0f : 0), player.getYRot());
        Vec3 motion = player.getDeltaMovement();
        Vec3 velocity = direction.scale(speed).add(motion.x, player.onGround() ? 0 : motion.y, motion.z);
        List<Vec3> points = new ArrayList<>(); points.add(pos);
        boolean hit = false;
        BlockPos hitBlock = null;
        for (int i = 0; i < 160; i++) {
            Vec3 end = pos.add(velocity);
            HitResult block = client.level.clip(new ClipContext(pos, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            AABB box = new AABB(pos, limit).inflate(1.0);
            EntityHitResult entity = ProjectileUtil.getEntityHitResult(client.level, player, pos, limit, box,
                    e -> e != player && e.isAlive() && e.isPickable(), 0.3f);
            hit = entity != null || block.getType() != HitResult.Type.MISS;
            if (entity == null && block instanceof BlockHitResult blockHit && block.getType() == HitResult.Type.BLOCK) hitBlock = blockHit.getBlockPos();
            pos = entity == null ? limit : entity.getLocation();
            points.add(pos);
            if (hit) break;
            velocity = velocity.scale(0.99).add(0, bow || potion ? -0.05 : -0.03, 0);
        }
        prediction = new Prediction(List.copyOf(points), hit, NicoClientMod.MODULES.trajectoryMarker.on(), NicoClientMod.MODULES.trajectoryBlock.on() ? hitBlock : null);
    }

    private static boolean supported(ItemStack stack) {
        return stack.is(Items.BOW) || stack.is(Items.SNOWBALL) || stack.is(Items.EGG)
                || stack.is(Items.BLUE_EGG) || stack.is(Items.BROWN_EGG)
                || stack.is(Items.ENDER_PEARL) || stack.is(Items.SPLASH_POTION);
    }

    private static void render(WorldRenderContext context) {
        Prediction p = prediction;
        if (p.points().size() < 2 || context.consumers() == null) return;
        Vec3 camera = context.worldState().cameraRenderState.pos;
        PoseStack.Pose pose = context.matrices().last();
        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
        for (int i = 1; i < p.points().size(); i++) line(lines, pose, camera, p.points().get(i - 1), p.points().get(i), 0xFF74E4F2);
        if (p.hitBlock() != null) glow(lines, pose, camera, p.hitBlock());
        if (p.impact() && p.marker()) {
            Vec3 end = p.points().get(p.points().size() - 1);
            line(lines, pose, camera, end.add(-0.15, 0, 0), end.add(0.15, 0, 0), 0xFFFFC46B);
            line(lines, pose, camera, end.add(0, -0.15, 0), end.add(0, 0.15, 0), 0xFFFFC46B);
            line(lines, pose, camera, end.add(0, 0, -0.15), end.add(0, 0, 0.15), 0xFFFFC46B);
        }
    }
    /** A red, softly pulsing box around the block the shot lands on, with a fainter halo so it reads as a glow. */
    private static void glow(VertexConsumer out, PoseStack.Pose pose, Vec3 camera, BlockPos pos) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0);
        box(out, pose, camera, pos, 0.004, GLOW);
        int halo = ((int) (0x50 + 0x70 * pulse) << 24) | (GLOW & 0xFFFFFF);
        box(out, pose, camera, pos, 0.03 + 0.03 * pulse, halo);
        box(out, pose, camera, pos, 0.08 + 0.04 * pulse, ((int) (0x20 + 0x30 * pulse) << 24) | (GLOW & 0xFFFFFF));
    }

    private static void box(VertexConsumer out, PoseStack.Pose pose, Vec3 camera, BlockPos pos, double grow, int color) {
        double x0 = pos.getX() - grow, y0 = pos.getY() - grow, z0 = pos.getZ() - grow;
        double x1 = pos.getX() + 1 + grow, y1 = pos.getY() + 1 + grow, z1 = pos.getZ() + 1 + grow;
        Vec3[] c = {new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1),
                new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1)};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) line(out, pose, camera, c[e[0]], c[e[1]], color);
    }

    private static void line(VertexConsumer out, PoseStack.Pose pose, Vec3 camera, Vec3 a, Vec3 b, int color) {
        Vec3 normal = b.subtract(a).normalize();
        vertex(out, pose, a.subtract(camera), normal, color); vertex(out, pose, b.subtract(camera), normal, color);
    }
    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 point, Vec3 normal, int color) {
        out.addVertex(pose, (float)point.x, (float)point.y, (float)point.z).setColor(color)
                .setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z).setLineWidth(2.0f);
    }
}
