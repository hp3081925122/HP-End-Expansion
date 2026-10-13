package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.TidelampWyrmEntity;
import org.hp.hp_end_expansion.registry.ModEntities;
import org.hp.hp_end_expansion.client.render.HpGeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class TidelampWyrmRenderer extends HpGeoEntityRenderer<TidelampWyrmEntity> {
    private static final double LIFT = 0.45;
    private static float shake, shakeO;
    private static int ticks;

    public TidelampWyrmRenderer(EntityRendererProvider.Context context) {
        super(context, new TidelampWyrmModel());
        shadowRadius = 1.6F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TIDELAMP_WYRM.get(), TidelampWyrmRenderer::new);
        event.registerEntityRenderer(ModEntities.ABYSS_VFX.get(), AbyssVfxRenderer::new);
    }

    // 死亡由动画表现，不叠加原版侧翻
    @Override protected float getDeathMaxRotation(TidelampWyrmEntity animatable) { return 0; }
    @Override public boolean shouldShowName(TidelampWyrmEntity animatable) { return false; }

    @Override
    protected int getBlockLightLevel(TidelampWyrmEntity entity, BlockPos pos) {
        return Math.max(12, super.getBlockLightLevel(entity, pos));
    }

    @Override
    public void render(TidelampWyrmEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.translate(0.0, LIFT, 0.0);
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
        poseStack.popPose();
        if (!entity.isDeadOrDying()) skillFx(entity, partialTick, poseStack, buffers);
    }

    // ---------------- 跟随本体的技能特效 ----------------

    private void skillFx(TidelampWyrmEntity w, float pt, PoseStack ps, MultiBufferSource b) {
        byte skill = w.getSkill();
        if (skill == TidelampWyrmEntity.NONE) return;
        float age = w.getSkillAge(pt);
        float yaw = Mth.rotLerp(pt, w.yBodyRotO, w.yBodyRot);
        Vec3 cam = entityRenderDispatcher.camera.position().subtract(w.getPosition(pt));
        PoseStack.Pose p = ps.last();
        switch (skill) {
            case TidelampWyrmEntity.BITE -> bite(age, yaw, cam, p, b);
            case TidelampWyrmEntity.SWEEP -> sweep(age, yaw, w.groundOffset(pt), cam, p, b);
            case TidelampWyrmEntity.VOLLEY -> volley(age, yaw, cam, p, b);
            case TidelampWyrmEntity.GAZE -> gaze(w, age, yaw, pt, cam, p, b);
            case TidelampWyrmEntity.ROAR -> roar(age, yaw, p, b);
            case TidelampWyrmEntity.MAELSTROM -> maelstrom(age, yaw, cam, p, b);
            default -> {}
        }
    }

    private static Vec3 local(float yaw, double right, double up, double fwd) {
        double y = Math.toRadians(yaw), s = Math.sin(y), c = Math.cos(y);
        return new Vec3(-s * fwd - c * right, up, c * fwd - s * right);
    }

    private static Vec3 fwd(float yaw) { return local(yaw, 0, 0, 1); }

    private static void eyes(float yaw, Vec3 cam, PoseStack.Pose p, MultiBufferSource b, double size, float k) {
        VertexConsumer g = AbyssFx.glow(b, AbyssFx.FLARE);
        for (int side = -1; side <= 1; side += 2) {
            Vec3 e = local(yaw, side * 0.72, 3.6, 2.75);
            AbyssFx.billboard(p, g, e, cam.subtract(e), size, side * 0.4, k);
        }
    }

    // 冲咬：眼睛逐渐亮起成星芒（读招），冲刺时嘴前拖出两道弧光，合嘴瞬间由服务端放爆闪
    private static void bite(float age, float yaw, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        if (age < TidelampWyrmEntity.BITE_DASH) {
            float t = age / TidelampWyrmEntity.BITE_DASH;
            eyes(yaw, cam, p, b, 0.25 + 0.55 * t, 0.4F + 0.6F * t);
        } else if (age < TidelampWyrmEntity.BITE_SNAP + 2) {
            eyes(yaw, cam, p, b, 0.8, 1);
            float k = Mth.clamp((TidelampWyrmEntity.BITE_SNAP + 2 - age) / 3, 0, 1);
            VertexConsumer a = AbyssFx.glow(b, AbyssFx.ARC);
            double head = Math.toRadians(yaw + 90);
            Vec3 c = TidelampWyrmEntity.mouthOffset(yaw).subtract(fwd(yaw).scale(3.7));
            AbyssFx.arcBlade(p, a, c.add(0, 0.9, 0), head + 0.35, 0.7, 3.2, 4.2, 0.0, 10, k);
            AbyssFx.arcBlade(p, a, c.add(0, -0.4, 0), head - 0.35 + 0.7, 0.7, 3.2, 4.2, 0.0, 10, k);
        }
    }

    // 鳍刃回旋：脚下法阵随蓄力扩到攻击半径；回旋时两片弧刃绕身体扫一圈
    private static void sweep(float age, float yaw, double groundY, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        double R = 5.5;
        if (age < TidelampWyrmEntity.SWEEP_START) {
            float t = age / TidelampWyrmEntity.SWEEP_START;
            Vec3 ground = new Vec3(0, groundY, 0);
            AbyssFx.decal(p, AbyssFx.glow(b, AbyssFx.GLYPH), ground, R * (0.35 + 0.65 * t), age * 0.05, 0.35F + 0.4F * t);
            AbyssFx.flatRing(p, AbyssFx.glow(b, AbyssFx.RING), ground.add(0, 0.02, 0), R - 0.4, R, 40, 10, 0, t);
            return;
        }
        float t = (age - TidelampWyrmEntity.SWEEP_START) / (TidelampWyrmEntity.SWEEP_END - TidelampWyrmEntity.SWEEP_START);
        if (t > 1.5F) return;
        float k = t <= 1 ? 1 : 1 - (t - 1) * 2;
        // 动画里第 18-26 tick 身体从 -35° 转到 360°
        double spin = Math.toRadians(yaw + 90 - 35 + 395 * Math.min(t, 1));
        VertexConsumer a = AbyssFx.glow(b, AbyssFx.ARC);
        for (int i = 0; i < 2; i++) {
            double head = spin + i * Math.PI + Math.PI / 2;
            AbyssFx.arcBlade(p, a, new Vec3(0, 1.6, 0), head, 1.9, 2.0, R, -0.06, 16, k);
            AbyssFx.arcBlade(p, a, new Vec3(0, 1.2, 0), head - 0.15, 1.4, 3.0, R - 0.3, -0.04, 12, k * 0.5F);
        }
        AbyssFx.flatRing(p, AbyssFx.glow(b, AbyssFx.RING), new Vec3(0, groundY, 0), R * 0.7, R, 40, 10, (float) spin, k * 0.6F);
    }

    // 晶脊齐射：背上一串晶簇逐个亮起，放出晶核时爆闪
    private static void volley(float age, float yaw, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        VertexConsumer g = AbyssFx.glow(b, AbyssFx.FLARE);
        for (int i = 0; i < 3; i++) {
          for (int side = -1; side <= 1; side += 2) {
            Vec3 at = TidelampWyrmEntity.spineOffset(yaw, i, side);
            float lit = Mth.clamp((age - i * 3) / 10, 0, 1);
            float shot = 0;
            int m = 20 + i * 4;
            if (age >= m && age < m + 3) shot = 1 - (age - m) / 3;
            float k = age < 32 ? lit * (0.55F + 0.3F * Mth.sin(age * 0.9F + i)) + shot * 0.5F : Mth.clamp((40 - age) / 8, 0, 1) * 0.5F;
            AbyssFx.billboard(p, g, at, cam.subtract(at), 0.5 + 0.5 * lit + shot * 0.8, age * 0.2 + i, k);
          }
        }
    }

    // 深渊凝视：嘴前聚光（星芒变大 + 圆环往里收），瞄准细线；发射后是自绘光束，末端打出星芒和地面冲击环
    private static void gaze(TidelampWyrmEntity w, float age, float yaw, float pt, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        Vec3 mouth = TidelampWyrmEntity.mouthOffset(yaw);
        Vec3 dir = w.beamDirection();
        double len = TidelampWyrmEntity.beamLength(w.level(), w.getPosition(pt).add(mouth), dir);
        VertexConsumer flare = AbyssFx.glow(b, AbyssFx.FLARE);
        if (age < TidelampWyrmEntity.GAZE_FIRE) {
            float t = age / TidelampWyrmEntity.GAZE_FIRE;
            AbyssFx.billboard(p, flare, mouth, cam.subtract(mouth), 0.3 + 1.1 * t, age * 0.3, 0.5F + 0.5F * t);
            double rr = 2.6 * (1 - t) + 0.5;
            AbyssFx.axialRing(p, AbyssFx.glow(b, AbyssFx.RING), mouth, dir, rr, rr + 0.35, 24, t);
            AbyssFx.ribbon(p, AbyssFx.glow(b, AbyssFx.BEAM), mouth, dir, cam.subtract(mouth), len, 0.05 + 0.04 * t, 0, (float) len, 0.25F + 0.2F * Mth.sin(age));
            eyes(yaw, cam, p, b, 0.3 + 0.4 * t, t);
            return;
        }
        if (age > TidelampWyrmEntity.GAZE_END + 2) return;
        float fade = Mth.clamp(Math.min((age - TidelampWyrmEntity.GAZE_FIRE) / 3, (TidelampWyrmEntity.GAZE_END + 2 - age) / 3), 0, 1);
        double width = TidelampWyrmEntity.BEAM_RADIUS;
        VertexConsumer beam = AbyssFx.glow(b, AbyssFx.BEAM);
        float v0 = -age * 0.6F, v1 = v0 + (float) (len / (4 * Math.max(width, 0.05)));
        AbyssFx.ribbon(p, beam, mouth, dir, cam.subtract(mouth), len, width, v0, v1, fade);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        AbyssFx.ribbon(p, beam, mouth, dir, side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize(), len, width * 0.8, v0 + 0.4F, v1 + 0.4F, fade * 0.7F);
        // 光束外层每 6 tick 沿光束推出一个螺旋环
        for (int i = 0; i < 3; i++) {
            double d = ((age * 0.8 + i * len / 3) % Math.max(len, 1.0E-6));
            AbyssFx.axialRing(p, AbyssFx.glow(b, AbyssFx.RING), mouth.add(dir.scale(d)), dir, width * 1.4, width * 2.2, 16, fade * 0.6F);
        }
        AbyssFx.billboard(p, flare, mouth, cam.subtract(mouth), 1.3 * fade, age * 0.4, fade);
        Vec3 end = mouth.add(dir.scale(len));
        AbyssFx.billboard(p, flare, end, cam.subtract(end), 1.6 * fade * (1 + 0.15 * Mth.sin(age * 3)), -age * 0.5, fade);
        if (len < TidelampWyrmEntity.GAZE_LENGTH - 0.1)
            AbyssFx.flatRing(p, AbyssFx.glow(b, AbyssFx.RING), end.add(0, 0.05, 0), 0.4, 1.4 + 0.3 * Mth.sin(age * 2), 20, 5, age * 0.3F, fade);
        eyes(yaw, cam, p, b, 0.7, fade);
    }

    // 咆哮：嘴前连续推出三道音波环
    private static void roar(float age, float yaw, PoseStack.Pose p, MultiBufferSource b) {
        VertexConsumer g = AbyssFx.glow(b, AbyssFx.RING);
        float a = age - TidelampWyrmEntity.ROAR_BLAST;
        if (a < 0 || a > 14) return;
        float k = 1 - a / 14;
        AbyssFx.wallRing(p, g, new Vec3(0, -1, 0), 7, 5, 0, 40, 10, k);
    }

    // 渊潮漩涡：盘旋时眼睛常亮；俯冲时嘴前聚成一颗星芒，身后两道弧光
    private static void maelstrom(float age, float yaw, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        if (age >= TidelampWyrmEntity.MAEL_RISE && age < TidelampWyrmEntity.MAEL_DIVE) eyes(yaw, cam, p, b, 0.6, 0.9F);
        if (age >= TidelampWyrmEntity.MAEL_DIVE && age < TidelampWyrmEntity.MAEL_SLAM) {
            float t = (age - TidelampWyrmEntity.MAEL_DIVE) / (TidelampWyrmEntity.MAEL_SLAM - TidelampWyrmEntity.MAEL_DIVE);
            Vec3 m = TidelampWyrmEntity.mouthOffset(yaw);
            AbyssFx.billboard(p, AbyssFx.glow(b, AbyssFx.FLARE), m, cam.subtract(m), 0.8 + 1.4 * t, age * 0.5, 1);
            VertexConsumer a = AbyssFx.glow(b, AbyssFx.ARC);
            double head = Math.toRadians(yaw + 90);
            AbyssFx.arcBlade(p, a, Vec3.ZERO.add(0, 2, 0), head + 1.2, 1.0, 3.0, 4.5, 0.2, 10, 0.8F);
            AbyssFx.arcBlade(p, a, Vec3.ZERO.add(0, 2, 0), head - 1.2 + 1.0, 1.0, 3.0, 4.5, 0.2, 10, 0.8F);
        }
    }

    // ---------------- 震屏 ----------------

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        shakeO = shake;
        shake *= 0.6F;
        Player player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;
        for (TidelampWyrmEntity w : mc.level.getEntitiesOfClass(TidelampWyrmEntity.class, player.getBoundingBox().inflate(32))) {
            if (!w.isAlive()) continue;
            float near = (float) Mth.clamp(1 - w.distanceTo(player) / 24, 0, 1);
            if (near <= 0) continue;
            int age = (int) w.getSkillAge(0);
            byte s = w.getSkill();
            float k = 0;
            if (s == TidelampWyrmEntity.BITE && age >= TidelampWyrmEntity.BITE_SNAP && age < TidelampWyrmEntity.BITE_SNAP + 3) k = 0.4F;
            if (s == TidelampWyrmEntity.SWEEP && age >= TidelampWyrmEntity.SWEEP_START && age < TidelampWyrmEntity.SWEEP_END) k = 0.25F;
            if (s == TidelampWyrmEntity.GAZE && age >= TidelampWyrmEntity.GAZE_FIRE && age < TidelampWyrmEntity.GAZE_END) k = 0.15F;
            if (s == TidelampWyrmEntity.MAELSTROM) {
                if (age >= TidelampWyrmEntity.MAEL_RISE && age < TidelampWyrmEntity.MAEL_DIVE) k = 0.12F;
                if (age >= TidelampWyrmEntity.MAEL_SLAM && age < TidelampWyrmEntity.MAEL_SLAM + 5) k = 0.9F;
            }
            if (s == TidelampWyrmEntity.ROAR && age >= TidelampWyrmEntity.ROAR_BLAST && age < TidelampWyrmEntity.ROAR_BLAST + 12) k = 0.5F;
            shake = Math.max(shake, k * near);
        }
    }

    // 跟随"画面扭曲效果"设置
    @SubscribeEvent public static void cameraShake(ViewportEvent.ComputeCameraAngles e) {
        float s0 = Mth.lerp((float) e.getPartialTick(), shakeO, shake);
        if (s0 < 0.01F) return;
        float scale = Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        if (scale <= 0) return;
        float t = ticks + (float) e.getPartialTick();
        float s = s0 * s0 * 2.2F * scale;
        e.setPitch(e.getPitch() + Mth.sin(t * 2.5F) * s);
        e.setYaw(e.getYaw() + Mth.sin(t * 1.9F + 1.3F) * s * 0.7F);
        e.setRoll(e.getRoll() + Mth.sin(t * 3.1F + 0.5F) * s * 0.6F);
    }
}
