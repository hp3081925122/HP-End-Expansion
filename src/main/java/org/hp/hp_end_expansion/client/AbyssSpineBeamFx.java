package org.hp.hp_end_expansion.client;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.ModParticles;

/** 深渊晶刺激光：命中点长出晶簇，光束从方块射向敌人，双螺旋潮光缠绕光束，两端炸开光环与晶屑。 */
@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class AbyssSpineBeamFx {
    private static final int LIFE = 14;
    private static final int CRYSTALS = 5;
    private static final List<Beam> BEAMS = new ArrayList<>();

    private AbyssSpineBeamFx() {}

    private static final class Beam {
        final Vec3 from, to, normal, axis, side, up;
        final double length;
        final Vec3[] crystalDirs = new Vec3[CRYSTALS];
        final double[] crystalLen = new double[CRYSTALS];
        int age;

        Beam(Vec3 from, Vec3 to, Vec3 normal, RandomSource random) {
            this.from = from;
            this.to = to;
            this.normal = normal;
            this.length = Math.max(0.01D, to.distanceTo(from));
            this.axis = to.subtract(from).scale(1.0D / length);
            // 光束横截面的两根基轴，用来绕螺旋
            Vec3 s = axis.cross(new Vec3(0, 1, 0));
            if (s.lengthSqr() < 1.0E-4) s = axis.cross(new Vec3(1, 0, 0));
            this.side = s.normalize();
            this.up = side.cross(axis).normalize();
            // 晶簇方向：受击面法线加随机倾斜
            for (int i = 0; i < CRYSTALS; i++) {
                Vec3 tilt = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.45D);
                crystalDirs[i] = normal.add(tilt).normalize();
                crystalLen[i] = 0.22D + random.nextDouble() * 0.2D;
            }
        }
    }

    public static void spawn(Vec3 from, Vec3 to, int face) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource random = level.random;
        Vec3 normal = Vec3.atLowerCornerOf(Direction.from3DDataValue(face).getNormal());
        BEAMS.add(new Beam(from, to, normal, random));

        // 沿光束撒出潮光火花
        Vec3 path = to.subtract(from);
        int sparks = Mth.ceil(path.length() * 3);
        for (int i = 0; i < sparks; i++) {
            Vec3 p = from.add(path.scale(random.nextDouble()));
            level.addParticle(ModParticles.TIDE_SPARK.get(), p.x, p.y, p.z,
                random.nextGaussian() * 0.03D, random.nextGaussian() * 0.03D, random.nextGaussian() * 0.03D);
        }
        // 命中敌人处崩出晶屑
        for (int i = 0; i < 12; i++) {
            level.addParticle(ModParticles.TIDE_SHARD.get(), to.x, to.y, to.z,
                random.nextGaussian() * 0.15D, random.nextGaussian() * 0.15D + 0.05D, random.nextGaussian() * 0.15D);
        }
        // 发射点沿受击面喷出火花
        for (int i = 0; i < 8; i++) {
            Vec3 v = normal.scale(0.12D).add(random.nextGaussian() * 0.06D, random.nextGaussian() * 0.06D, random.nextGaussian() * 0.06D);
            level.addParticle(ModParticles.TIDE_SPARK.get(), from.x, from.y, from.z, v.x, v.y, v.z);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // 离开世界时清空
        if (mc.level == null) {
            BEAMS.clear();
            return;
        }
        if (mc.isPaused()) return;
        // 推进寿命并移除过期光束
        for (Beam beam : BEAMS) beam.age++;
        BEAMS.removeIf(beam -> beam.age >= LIFE);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BEAMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (mc.isPaused()) pt = 0;

        // 模型视图已含镜头旋转，这里只需要平移到世界坐标
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        stack.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = stack.last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // 同一贴图的面一起画，切换贴图会结束上一批
        VertexConsumer beamVc = AbyssFx.glow(buffers, AbyssFx.BEAM);
        for (Beam beam : BEAMS) drawBeam(pose, beamVc, beam, beam.age + pt, cam);
        VertexConsumer flareVc = AbyssFx.glow(buffers, AbyssFx.FLARE);
        for (Beam beam : BEAMS) drawFlares(pose, flareVc, beam, beam.age + pt, cam);
        VertexConsumer ringVc = AbyssFx.glow(buffers, AbyssFx.RING);
        for (Beam beam : BEAMS) drawRings(pose, ringVc, beam, beam.age + pt);
        VertexConsumer crystalVc = AbyssFx.glow(buffers, AbyssFx.CRYSTAL);
        for (Beam beam : BEAMS) drawCrystals(pose, crystalVc, beam, beam.age + pt);

        buffers.endBatch();
        stack.popPose();
    }

    /** 主光束、外层辉光和缠绕光束的双螺旋。 */
    private static void drawBeam(PoseStack.Pose pose, VertexConsumer vc, Beam beam, float f, Vec3 cam) {
        float t = f / LIFE;
        // 头两 tick 光束从方块伸向敌人，之后逐渐熄灭
        double reach = beam.length * Mth.clamp(f / 2.0F, 0.15F, 1.0F);
        float core = Mth.clamp(1.0F - (f - 2.0F) / 8.0F, 0.0F, 1.0F);
        float flicker = 1.0F + 0.15F * Mth.sin(f * 9.0F);
        Vec3 toCam = cam.subtract(beam.from.add(beam.axis.scale(reach * 0.5D)));

        // 白热内芯
        AbyssFx.ribbon(pose, vc, beam.from, beam.axis, toCam, reach, 0.06D * (0.4F + 0.6F * core) * flicker, 0, 1, core);
        // 外层辉光，比内芯熄得慢
        float halo = (1.0F - t) * (1.0F - t);
        AbyssFx.ribbon(pose, vc, beam.from, beam.axis, toCam, reach, 0.22D + 0.18D * t, 0, 1, 0.55F * halo);

        // 双螺旋：随时间旋转并向外扩散
        double radius = 0.1D + 0.3D * t;
        int seg = Math.max(6, Mth.ceil(reach * 6));
        float helixK = 0.8F * (1.0F - t);
        for (int strand = 0; strand < 2; strand++) {
            Vec3 prev = helixPoint(beam, 0, radius, f, strand);
            for (int i = 1; i <= seg; i++) {
                Vec3 next = helixPoint(beam, reach * i / seg, radius, f, strand);
                Vec3 dir = next.subtract(prev);
                double len = dir.length();
                if (len > 1.0E-4) AbyssFx.ribbon(pose, vc, prev, dir.scale(1.0D / len), cam.subtract(prev), len, 0.025D, 0, 1, helixK);
                prev = next;
            }
        }
    }

    private static Vec3 helixPoint(Beam beam, double along, double radius, float f, int strand) {
        double angle = along * 2.4D + f * 0.5D + strand * Math.PI;
        return beam.from.add(beam.axis.scale(along))
            .add(beam.side.scale(Math.cos(angle) * radius))
            .add(beam.up.scale(Math.sin(angle) * radius));
    }

    /** 两端的闪光：发射点一团，命中点更大的一团。 */
    private static void drawFlares(PoseStack.Pose pose, VertexConsumer vc, Beam beam, float f, Vec3 cam) {
        float t = f / LIFE;
        float core = Mth.clamp(1.0F - (f - 2.0F) / 8.0F, 0.0F, 1.0F);
        float pulse = 1.0F + 0.25F * Mth.sin(f * 7.0F);
        // 发射点
        AbyssFx.billboard(pose, vc, beam.from, cam.subtract(beam.from), (0.2D + 0.35D * core) * pulse, f * 0.2D, 0.3F + 0.7F * core);
        // 命中点：光束到达后才炸开
        if (f < 1.5F) return;
        float burst = Mth.clamp(1.0F - (f - 1.5F) / 9.0F, 0.0F, 1.0F);
        AbyssFx.billboard(pose, vc, beam.to, cam.subtract(beam.to), 0.3D + 0.5D * burst,-f * 0.3D, burst);
        AbyssFx.billboard(pose, vc, beam.to, cam.subtract(beam.to), 0.9D * t + 0.2D, f * 0.15D, 0.5F * burst);
    }

    /** 冲击环：命中点沿光束方向扩散，发射点贴着受击面扩散。 */
    private static void drawRings(PoseStack.Pose pose, VertexConsumer vc, Beam beam, float f) {
        float t = f / LIFE;
        float fade = 1.0F - t;
        // 发射点贴面光环
        double r0 = 0.05D + t * 0.9D;
        AbyssFx.axialRing(pose, vc, beam.from.subtract(beam.normal.scale(0.1D)), beam.normal, r0, r0 + 0.18D * fade, 20, 0.8F * fade);
        // 命中点两道一前一后的冲击环
        if (f < 1.5F) return;
        float hit = Mth.clamp((f - 1.5F) / (LIFE - 1.5F), 0.0F, 1.0F);
        double h0 = 0.1D + hit * 1.3D;
        AbyssFx.axialRing(pose, vc, beam.to, beam.axis, h0, h0 + 0.3D * (1.0F - hit), 24, 1.0F - hit);
        double h1 = 0.05D + hit * 0.7D;
        AbyssFx.axialRing(pose, vc, beam.to.subtract(beam.axis.scale(0.4D * hit)), beam.axis, h1, h1 + 0.15D, 20, 0.6F * (1.0F - hit));
    }

    /** 发射点长出的晶簇：两 tick 内刺出，最后几 tick 缩回。 */
    private static void drawCrystals(PoseStack.Pose pose, VertexConsumer vc, Beam beam, float f) {
        float grow = Mth.clamp(f / 2.0F, 0.0F, 1.0F) * Mth.clamp((LIFE - f) / 5.0F, 0.0F, 1.0F);
        if (grow <= 0) return;
        Vec3 base = beam.from.subtract(beam.normal.scale(0.12D));
        for (int i = 0; i < CRYSTALS; i++) {
            double len = beam.crystalLen[i] * grow;
            AbyssFx.crystal(pose, vc, base, beam.crystalDirs[i], len, 0.045D + len * 0.12D, i * 1.3D + f * 0.05D, 0.4F + 0.6F * grow);
        }
        // 中央指向敌人的主晶刺
        AbyssFx.crystal(pose, vc, base, beam.axis, 0.45D * grow, 0.07D, f * 0.1D, grow);
    }
}
