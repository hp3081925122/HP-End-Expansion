package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
import org.hp.hp_end_expansion.entity.tidelight.AbyssWatcherEntity;
import org.hp.hp_end_expansion.registry.ModEntities;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class AbyssWatcherRenderer extends GeoEntityRenderer<AbyssWatcherEntity> {
    private static final double LIFT = 0.45;
    private static float shake, shakeO;
    private static int ticks;

    public AbyssWatcherRenderer(EntityRendererProvider.Context context) {
        super(context, new AbyssWatcherModel());
        shadowRadius = 1.6F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ABYSS_WATCHER.get(), AbyssWatcherRenderer::new);
        event.registerEntityRenderer(ModEntities.ABYSS_VFX.get(), AbyssVfxRenderer::new);
    }

    // 死亡由动画表现，不叠加原版侧翻
    @Override protected float getDeathMaxRotation(AbyssWatcherEntity animatable) { return 0; }
    @Override public boolean shouldShowName(AbyssWatcherEntity animatable) { return false; }

    @Override
    public void render(AbyssWatcherEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.translate(0.0, LIFT, 0.0);
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
        poseStack.popPose();
        if (!entity.isDeadOrDying()) skillFx(entity, partialTick, poseStack, buffers);
    }

    // ---------------- 跟随本体的技能特效 ----------------

    private void skillFx(AbyssWatcherEntity w, float pt, PoseStack ps, MultiBufferSource b) {
        byte skill = w.getSkill();
        if (skill == AbyssWatcherEntity.NONE) return;
        float age = w.getSkillAge(pt);
        float yaw = Mth.rotLerp(pt, w.yBodyRotO, w.yBodyRot);
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(w.getPosition(pt));
        PoseStack.Pose p = ps.last();
        switch (skill) {
            case AbyssWatcherEntity.BITE -> bite(age, yaw, cam, p, b);
            case AbyssWatcherEntity.SWEEP -> sweep(age, yaw, cam, p, b);
            case AbyssWatcherEntity.VOLLEY -> volley(age, yaw, cam, p, b);
            case AbyssWatcherEntity.GAZE -> gaze(w, age, yaw, cam, p, b);
            case AbyssWatcherEntity.ROAR -> roar(age, yaw, p, b);
            case AbyssWatcherEntity.MAELSTROM -> maelstrom(age, yaw, cam, p, b);
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
        if (age < AbyssWatcherEntity.BITE_DASH) {
            float t = age / AbyssWatcherEntity.BITE_DASH;
            eyes(yaw, cam, p, b, 0.25 + 0.55 * t, 0.4F + 0.6F * t);
        } else if (age < AbyssWatcherEntity.BITE_SNAP + 2) {
            eyes(yaw, cam, p, b, 0.8, 1);
            float k = Mth.clamp((AbyssWatcherEntity.BITE_SNAP + 2 - age) / 3, 0, 1);
            VertexConsumer a = AbyssFx.glow(b, AbyssFx.ARC);
            double head = Math.toRadians(yaw + 90);
            Vec3 c = local(yaw, 0, 2.6, -1.0);
            AbyssFx.arcBlade(p, a, c.add(0, 0.9, 0), head + 0.35, 0.7, 3.2, 4.2, 0.0, 10, k);
            AbyssFx.arcBlade(p, a, c.add(0, -0.4, 0), head - 0.35 + 0.7, 0.7, 3.2, 4.2, 0.0, 10, k);
        }
    }

    // 鳍刃回旋：脚下法阵随蓄力扩到攻击半径；回旋时两片弧刃绕身体扫一圈
    private static void sweep(float age, float yaw, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        double R = 5.5;
        if (age < AbyssWatcherEntity.SWEEP_START) {
            float t = age / AbyssWatcherEntity.SWEEP_START;
            Vec3 ground = new Vec3(0, -2.55, 0);
            AbyssFx.decal(p, AbyssFx.glow(b, AbyssFx.GLYPH), ground, R * (0.35 + 0.65 * t), age * 0.05, 0.35F + 0.4F * t);
            AbyssFx.flatRing(p, AbyssFx.glow(b, AbyssFx.RING), ground.add(0, 0.02, 0), R - 0.4, R, 40, 10, 0, t);
            return;
        }
        float t = (age - AbyssWatcherEntity.SWEEP_START) / (AbyssWatcherEntity.SWEEP_END - AbyssWatcherEntity.SWEEP_START);
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
        AbyssFx.flatRing(p, AbyssFx.glow(b, AbyssFx.RING), new Vec3(0, -2.5, 0), R * 0.7, R, 40, 10, (float) spin, k * 0.6F);
    }

    // 晶脊齐射：背上一串晶簇逐个亮起，放出晶核时爆闪
    private static void volley(float age, float yaw, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        VertexConsumer g = AbyssFx.glow(b, AbyssFx.FLARE);
        for (int i = 0; i < 4; i++) {
            Vec3 at = local(yaw, 0, 4.3 - i * 0.25, 1.2 - i * 1.4);
            float lit = Mth.clamp((age - i * 3) / 10, 0, 1);
            float shot = 0;
            for (int m : new int[]{20, 24, 28}) if (age >= m && age < m + 3) shot = 1 - (age - m) / 3;
            float k = age < 32 ? lit * (0.55F + 0.3F * Mth.sin(age * 0.9F + i)) + shot * 0.5F : Mth.clamp((40 - age) / 8, 0, 1) * 0.5F;
            AbyssFx.billboard(p, g, at, cam.subtract(at), 0.5 + 0.5 * lit + shot * 0.8, age * 0.2 + i, k);
        }
    }

    // 深渊凝视：嘴前聚光（星芒变大 + 圆环往里收），瞄准细线；发射后是自绘光束，末端打出星芒和地面冲击环
    private static void gaze(AbyssWatcherEntity w, float age, float yaw, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        Vec3 mouth = w.mouth().subtract(w.position());
        Vec3 dir = w.beamDirection();
        double len = AbyssWatcherEntity.beamLength(w.level(), w.mouth(), dir);
        VertexConsumer flare = AbyssFx.glow(b, AbyssFx.FLARE);
        if (age < AbyssWatcherEntity.GAZE_FIRE) {
            float t = age / AbyssWatcherEntity.GAZE_FIRE;
            AbyssFx.billboard(p, flare, mouth, cam.subtract(mouth), 0.3 + 1.1 * t, age * 0.3, 0.5F + 0.5F * t);
            double rr = 2.6 * (1 - t) + 0.5;
            AbyssFx.axialRing(p, AbyssFx.glow(b, AbyssFx.RING), mouth, dir, rr, rr + 0.35, 24, t);
            AbyssFx.ribbon(p, AbyssFx.glow(b, AbyssFx.BEAM), mouth, dir, cam.subtract(mouth), len, 0.05 + 0.04 * t, 0, (float) len, 0.25F + 0.2F * Mth.sin(age));
            eyes(yaw, cam, p, b, 0.3 + 0.4 * t, t);
            return;
        }
        if (age > AbyssWatcherEntity.GAZE_END + 2) return;
        float fade = Mth.clamp(Math.min((age - AbyssWatcherEntity.GAZE_FIRE) / 3, (AbyssWatcherEntity.GAZE_END + 2 - age) / 3), 0, 1);
        double width = 0.55 * fade * (1 + 0.1 * Mth.sin(age * 2.3F));
        VertexConsumer beam = AbyssFx.glow(b, AbyssFx.BEAM);
        float v0 = -age * 0.6F, v1 = v0 + (float) (len / (4 * Math.max(width, 0.05)));
        AbyssFx.ribbon(p, beam, mouth, dir, cam.subtract(mouth), len, width, v0, v1, fade);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        AbyssFx.ribbon(p, beam, mouth, dir, side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize(), len, width * 0.8, v0 + 0.4F, v1 + 0.4F, fade * 0.7F);
        // 光束外层每 6 tick 沿光束推出一个螺旋环
        for (int i = 0; i < 3; i++) {
            double d = ((age * 0.8 + i * len / 3) % len);
            AbyssFx.axialRing(p, AbyssFx.glow(b, AbyssFx.RING), mouth.add(dir.scale(d)), dir, width * 1.4, width * 2.2, 16, fade * 0.6F);
        }
        AbyssFx.billboard(p, flare, mouth, cam.subtract(mouth), 1.3 * fade, age * 0.4, fade);
        Vec3 end = mouth.add(dir.scale(len));
        AbyssFx.billboard(p, flare, end, cam.subtract(end), 1.6 * fade * (1 + 0.15 * Mth.sin(age * 3)), -age * 0.5, fade);
        if (len < AbyssWatcherEntity.GAZE_LENGTH - 0.1)
            AbyssFx.flatRing(p, AbyssFx.glow(b, AbyssFx.RING), end.add(0, 0.05, 0), 0.4, 1.4 + 0.3 * Mth.sin(age * 2), 20, 5, age * 0.3F, fade);
        eyes(yaw, cam, p, b, 0.7, fade);
    }

    // 咆哮：嘴前连续推出三道音波环
    private static void roar(float age, float yaw, PoseStack.Pose p, MultiBufferSource b) {
        Vec3 mouth = local(yaw, 0, 3.5, 3.1);
        Vec3 f = fwd(yaw);
        VertexConsumer g = AbyssFx.glow(b, AbyssFx.RING);
        for (int i = 0; i < 3; i++) {
            float a = age - AbyssWatcherEntity.ROAR_BLAST - i * 4;
            if (a < 0 || a > 14) continue;
            float k = 1 - a / 14;
            AbyssFx.axialRing(p, g, mouth.add(f.scale(a * 0.45)), f, 0.6 + a * 0.45, 1.2 + a * 0.55, 32, k);
        }
    }

    // 渊潮漩涡：盘旋时眼睛常亮；俯冲时嘴前聚成一颗星芒，身后两道弧光
    private static void maelstrom(float age, float yaw, Vec3 cam, PoseStack.Pose p, MultiBufferSource b) {
        if (age >= AbyssWatcherEntity.MAEL_RISE && age < AbyssWatcherEntity.MAEL_DIVE) eyes(yaw, cam, p, b, 0.6, 0.9F);
        if (age >= AbyssWatcherEntity.MAEL_DIVE && age < AbyssWatcherEntity.MAEL_SLAM) {
            float t = (age - AbyssWatcherEntity.MAEL_DIVE) / (AbyssWatcherEntity.MAEL_SLAM - AbyssWatcherEntity.MAEL_DIVE);
            Vec3 m = local(yaw, 0, 2.0, 3.0);
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
        for (AbyssWatcherEntity w : mc.level.getEntitiesOfClass(AbyssWatcherEntity.class, player.getBoundingBox().inflate(32))) {
            if (!w.isAlive()) continue;
            float near = (float) Mth.clamp(1 - w.distanceTo(player) / 24, 0, 1);
            if (near <= 0) continue;
            int age = (int) w.getSkillAge(0);
            byte s = w.getSkill();
            float k = 0;
            if (s == AbyssWatcherEntity.BITE && age >= AbyssWatcherEntity.BITE_SNAP && age < AbyssWatcherEntity.BITE_SNAP + 3) k = 0.4F;
            if (s == AbyssWatcherEntity.SWEEP && age >= AbyssWatcherEntity.SWEEP_START && age < AbyssWatcherEntity.SWEEP_END) k = 0.25F;
            if (s == AbyssWatcherEntity.GAZE && age >= AbyssWatcherEntity.GAZE_FIRE && age < AbyssWatcherEntity.GAZE_END) k = 0.15F;
            if (s == AbyssWatcherEntity.MAELSTROM) {
                if (age >= AbyssWatcherEntity.MAEL_RISE && age < AbyssWatcherEntity.MAEL_DIVE) k = 0.12F;
                if (age >= AbyssWatcherEntity.MAEL_SLAM && age < AbyssWatcherEntity.MAEL_SLAM + 5) k = 0.9F;
            }
            if (s == AbyssWatcherEntity.ROAR && age >= AbyssWatcherEntity.ROAR_BLAST && age < AbyssWatcherEntity.ROAR_BLAST + 12) k = 0.5F;
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
