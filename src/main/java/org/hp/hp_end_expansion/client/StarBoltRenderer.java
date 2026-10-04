package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.starwreck.StarBoltEntity;
import org.hp.hp_end_expansion.item.StarBowItem;
import org.hp.hp_end_expansion.registry.ModStarwreck;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 星晶矢：原版箭那样两片十字交叉的面片（长 1 格、高 0.25 格，贴图 32x8 按这个比例画），
 * 本体用全亮的镂空层，同形状再叠一层加法发光；矢尖一颗闪烁的四芒星，身后一条朝镜头的拖尾。
 * 贴图由 art/star_bow_fx.py 生成。另外给群星之弓注册拉弓的 pull/pulling 属性，原版只给自己的弓注册了。
 */
@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class StarBoltRenderer extends EntityRenderer<StarBoltEntity> {
    private static final ResourceLocation BODY = tex("entity/star_bolt");
    private static final ResourceLocation GLOW = tex("entity/star_bolt_glow");
    private static final ResourceLocation TRAIL = tex("effect/star_bolt_trail");
    private static final ResourceLocation FLARE = tex("effect/star_bolt_flare");
    private static final float LEN = 1.0F, HALF_H = 0.125F;
    /** 拉满弓一拍飞 3 格，拖尾最长画 14 格，再长就成激光了。 */
    private static final float MAX_TRAIL = 14;

    StarBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/" + path + ".png");
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StarwreckEntities.STAR_BOLT.get(), StarBoltRenderer::new);
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModStarwreck.STAR_BOW.get(), ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) ->
                entity == null ? 0 : StarBowItem.getPullProgress(stack, entity));
            ItemProperties.register(ModStarwreck.STAR_BOW.get(), ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0);
        });
    }

    @Override public ResourceLocation getTextureLocation(StarBoltEntity e) { return BODY; }

    @Override public void render(StarBoltEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float yRot = Mth.lerp(pt, e.yRotO, e.getYRot()), xRot = Mth.lerp(pt, e.xRotO, e.getXRot());
        boolean stuck = e.stuck(), crit = e.isCritArrow();
        float pulse = stuck ? 0.55F + 0.25F * Mth.sin(t * 0.15F) : 1;

        // 矢身：两片十字面片，矢头朝 +x
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(yRot - 90));
        ps.mulPose(Axis.ZP.rotationDegrees(xRot));
        if (!stuck) ps.mulPose(Axis.XP.rotationDegrees(t * 18));   // 飞行时绕自身慢慢转，晶面会闪
        PoseStack.Pose pose = ps.last();
        float h = LEN / 2;
        VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(BODY));
        for (int i = 0; i < 2; i++) {
            Vec3 up = i == 0 ? new Vec3(0, HALF_H, 0) : new Vec3(0, 0, HALF_H);
            Vec3 a = new Vec3(-h, 0, 0).add(up), b = new Vec3(h, 0, 0).add(up), c = new Vec3(h, 0, 0).subtract(up), d = new Vec3(-h, 0, 0).subtract(up);
            StarfallDraw.quad(pose, body, a, b, c, d, 0, 0, 1, 1, 1, 1, 1);
        }
        VertexConsumer glow = buffers.getBuffer(StarfallDraw.additive(GLOW));
        for (int i = 0; i < 2; i++) {
            Vec3 up = i == 0 ? new Vec3(0, HALF_H, 0) : new Vec3(0, 0, HALF_H);
            Vec3 a = new Vec3(-h, 0, 0).add(up), b = new Vec3(h, 0, 0).add(up), c = new Vec3(h, 0, 0).subtract(up), d = new Vec3(-h, 0, 0).subtract(up);
            StarfallDraw.quad(pose, glow, a, b, c, d, 0, 0, 1, 1, pulse, pulse, pulse);
        }
        ps.popPose();

        Quaternionf cam = entityRenderDispatcher.cameraOrientation();
        Vec3 right = rotate(cam, 1, 0, 0), up = rotate(cam, 0, 1, 0);
        double yr = Math.toRadians(yRot), xr = Math.toRadians(xRot);
        Vec3 dir = new Vec3(Math.sin(yr) * Math.cos(xr), Math.sin(xr), Math.cos(yr) * Math.cos(xr));

        // 矢尖四芒星：闪烁，暴击时更大更亮
        PoseStack.Pose world = ps.last();
        float tw = 0.75F + 0.25F * Mth.sin(t * 1.3F) * Mth.sin(t * 0.7F + 1);
        float fs = (crit ? 0.5F : 0.38F) * tw * (stuck ? 0.7F : 1);
        float fb = tw * pulse * (crit ? 1.2F : 1);
        Vec3 tip = dir.scale(h * 0.9);
        VertexConsumer fv = buffers.getBuffer(StarfallDraw.additive(FLARE));
        StarfallDraw.quad(world, fv, tip.add(right.scale(-fs)).add(up.scale(fs)), tip.add(right.scale(fs)).add(up.scale(fs)),
            tip.add(right.scale(fs)).add(up.scale(-fs)), tip.add(right.scale(-fs)).add(up.scale(-fs)), 0, 0, 1, 1, fb, fb, fb);

        renderTrail(e, world, buffers, pt, crit);
        super.render(e, yaw, pt, ps, buffers, light);
    }

    /** 拖尾：沿最近几拍的位置连成一条朝镜头的飘带，头宽尾细，从矢尾开始。 */
    private void renderTrail(StarBoltEntity e, PoseStack.Pose pose, MultiBufferSource buffers, float pt, boolean crit) {
        int n = e.trailCount;
        if (n < 2) return;
        Vec3 here = e.getPosition(pt);
        Vec3 eye = entityRenderDispatcher.camera.getPosition().subtract(here);
        Vec3[] pts = new Vec3[n + 1];
        pts[0] = Vec3.ZERO;
        for (int i = 0; i < n; i++) pts[i + 1] = e.trail[i].subtract(here);
        float total = 0;
        for (int i = 0; i < n; i++) total += (float) pts[i + 1].distanceTo(pts[i]);
        if (total < 0.05F) return;
        total = Math.min(total, MAX_TRAIL);
        VertexConsumer vc = buffers.getBuffer(StarfallDraw.additive(TRAIL));
        float w0 = crit ? 0.2F : 0.15F, b = crit ? 1.15F : 0.95F;
        float done = 0;
        for (int i = 0; i < n && done < total; i++) {
            Vec3 p0 = pts[i], p1 = pts[i + 1];
            Vec3 seg = p1.subtract(p0);
            float len = (float) seg.length();
            if (len < 1.0E-4F) continue;
            if (done + len > total) {
                p1 = p0.add(seg.scale((total - done) / len));
                len = total - done;
            }
            Vec3 side = seg.cross(eye.subtract(p0));
            if (side.lengthSqr() < 1.0E-8) continue;
            side = side.normalize();
            float v0 = done / total, v1 = (done + len) / total;
            float wa = w0 * (1 - v0 * 0.85F), wb = w0 * (1 - v1 * 0.85F);
            StarfallDraw.quad(pose, vc, p0.add(side.scale(wa)), p0.subtract(side.scale(wa)), p1.subtract(side.scale(wb)), p1.add(side.scale(wb)),
                0, v0, 1, v1, b, b, b);
            done += len;
        }
    }

    private static Vec3 rotate(Quaternionf q, float x, float y, float z) {
        Vector3f v = q.transform(new Vector3f(x, y, z));
        return new Vec3(v.x, v.y, v.z);
    }
}
