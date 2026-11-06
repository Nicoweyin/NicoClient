package dev.nicoweyin.nicoclient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Items;

/** Draws wings on the back and a halo above the head of your own player. */
public final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final float SPAN = 1.05f, TOP = -0.5f, BOTTOM = 0.62f, HALO = 0.27f;

    public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || state.id != client.player.getId() || state.isInvisible) return;
        int wings = Cosmetics.wings();
        if (wings > 0 && !state.chestEquipment.is(Items.ELYTRA)) {
            boolean flying = state.fallFlyingTimeInTicks > 0 || client.player.getAbilities().flying;
            float speed = flying ? 0.45f : 0.12f, range = flying ? 0.35f : 0.12f;
            float flap = NicoClientMod.MODULES.wingFlap.on() ? (float) Math.sin(state.ageInTicks * speed) * range : 0;
            int wingLight = wings == 3 ? LightTexture.FULL_BRIGHT : light;
            var type = RenderTypes.entityCutoutNoCull(Cosmetics.wingTexture(wings));
            for (int side = -1; side <= 1; side += 2) {
                pose.pushPose();
                getParentModel().body.translateAndRotate(pose);
                pose.translate(side * 0.06f, 0.12f, 0.15f);
                pose.mulPose(Axis.YP.rotation(-side * (0.6f + flap)));
                pose.mulPose(Axis.ZP.rotation(-side * 0.22f));
                float s = side;
                collector.submitCustomGeometry(pose, type, (p, out) -> {
                    vertex(out, p, 0, TOP, 0, 0, 0, wingLight);
                    vertex(out, p, s * SPAN, TOP, 0, 1, 0, wingLight);
                    vertex(out, p, s * SPAN, BOTTOM, 0, 1, 1, wingLight);
                    vertex(out, p, 0, BOTTOM, 0, 0, 1, wingLight);
                });
                pose.popPose();
            }
        }
        int halo = Cosmetics.halo();
        if (halo > 0) {
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            pose.translate(0, -0.7f + (float) Math.sin(state.ageInTicks * 0.08f) * 0.02f, 0);
            pose.mulPose(Axis.YP.rotation(state.ageInTicks * 0.03f));
            collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(Cosmetics.haloTexture(halo)), (p, out) -> {
                haloVertex(out, p, -HALO, -HALO, 0, 0);
                haloVertex(out, p, HALO, -HALO, 1, 0);
                haloVertex(out, p, HALO, HALO, 1, 1);
                haloVertex(out, p, -HALO, HALO, 0, 1);
            });
            pose.popPose();
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, float u, float v, int light) {
        out.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
    }

    private static void haloVertex(VertexConsumer out, PoseStack.Pose p, float x, float z, float u, float v) {
        out.addVertex(p, x, 0, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, -1, 0);
    }
}
