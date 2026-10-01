package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.starwreck.StarChaserEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * 逐星兽的胸核光晕与攻击特效，模型画完后按贴图分三遍画（光晕 → 笔触 → 热浪环），
 * 同一遍内只写一个缓冲。坐标都相对实体渲染原点，骨骼位置取自本帧的骨骼矩阵。
 * 笔触用 meteor_tail 顶部一行：横向从黑到白热再到黑，天然是一条带软边的光线。
 */
public final class StarChaserVfx {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float COLUMN = 0.25F, HOT = 0.03F;
    // 吻部下沿前端（相对 head 枢轴）和下颚上沿前端（相对 jaw 枢轴），单位像素，取自 geo
    private static final float SNOUT_Y = -2, SNOUT_Z = -10, LOWER_Y = 0.3F, LOWER_Z = -7;

    private final StarChaserEntity e;
    private final PoseStack.Pose pose;
    private final MultiBufferSource buffers;
    private final Vec3 origin, cam, f, side, core, jaw, tail;
    @Nullable private final Matrix4f headBone, jawBone;
    private final byte state;
    private final float age, time, fade;

    private StarChaserVfx(StarChaserEntity e, BakedGeoModel model, PoseStack poseStack, MultiBufferSource buffers, float pt) {
        this.e = e;
        this.pose = poseStack.last();
        this.buffers = buffers;
        origin = e.getPosition(pt);
        cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(origin);
        float yaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        side = new Vec3(-f.z, 0, f.x);
        core = anchor(model, "core", 0, 1 / 16F, -1 / 16F, f.scale(1.2).add(0, 1.35, 0));
        headBone = model.getBone("head").map(b -> new Matrix4f(b.getLocalSpaceMatrix())).orElse(null);
        jawBone = model.getBone("jaw").map(b -> new Matrix4f(b.getLocalSpaceMatrix())).orElse(null);
        // 嘴缝中点：吻部下沿前端和下颚上沿前端的中间
        jaw = headBone != null && jawBone != null
            ? at(headBone, 0, SNOUT_Y, SNOUT_Z).add(at(jawBone, 0, LOWER_Y, LOWER_Z)).scale(0.5)
            : f.scale(2.4).add(0, 1.9, 0);
        tail = anchor(model, "comet_3", 0, 0, 8 / 16F, f.scale(-3.6).add(0, 1.6, 0));
        Vec3 paw = anchor(model, "leg_front_right_paw", 0, 0, 0, null);
        e.vfxCore = origin.add(core);
        e.vfxJaw = origin.add(jaw);
        e.vfxTail = origin.add(tail);
        e.vfxPaw = paw != null ? origin.add(paw) : null;
        state = e.getState();
        age = e.getClientAge() + pt;
        time = e.tickCount + pt;
        fade = e.isDeadOrDying() ? Mth.clamp(1 - (e.deathTime + pt) / 24, 0, 1) : 1;
    }

    public static void render(StarChaserEntity entity, BakedGeoModel model, PoseStack poseStack, MultiBufferSource buffers, float partialTick) {
        StarChaserVfx vfx = new StarChaserVfx(entity, model, poseStack, buffers, partialTick);
        if (vfx.fade <= 0) return;
        vfx.glowPass();
        if (entity.isDeadOrDying()) return;
        vfx.strokePass();
        vfx.ringPass();
    }

    /** 骨骼本地坐标（像素，相对枢轴）→ 渲染原点坐标，已含模型缩放。 */
    private static Vec3 at(Matrix4f bone, float x, float y, float z) {
        Vector3f p = bone.transformPosition(new Vector3f(x / 16, y / 16, z / 16));
        return new Vec3(p.x, p.y, p.z);
    }

    private static Vec3 anchor(BakedGeoModel model, String bone, float x, float y, float z, @Nullable Vec3 fallback) {
        GeoBone b = model.getBone(bone).orElse(null);
        if (b == null) return fallback;
        // 骨骼的本地矩阵相对实体渲染原点，模型画完后才可用
        Vector3f p = b.getLocalSpaceMatrix().transformPosition(new Vector3f(x, y, z));
        return new Vec3(p.x, p.y, p.z);
    }

    // ---- 第一遍：光晕（胸核、闪光） ----

    private void glowPass() {
        VertexConsumer buf = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.HALO));
        float pulse = Mth.sin(time * Mth.TWO_PI / 60);
        if (e.isOverloaded()) {
            // 过载：胸核裸露，白热、跳得更快
            float q = Mth.sin(time * 0.7F);
            float b = (0.9F + 0.1F * q) * fade;
            glow(buf, core, 0.46F + 0.06F * q, b, b * 0.93F, b * 0.78F);
            glow(buf, core, 1.1F + 0.1F * q, 0.45F * b, 0.3F * b, 0.12F * b);
        } else {
            float size = 0.32F + 0.04F * pulse, b = (0.55F + 0.25F * pulse) * fade;
            if (state == StarChaserEntity.WINDUP || state == StarChaserEntity.CHARGE) {
                size *= 1.3F;
                b = Math.min(1, b * 1.4F);
            }
            glow(buf, core, size, b, b * 0.8F, b * 0.45F);
        }
        if (e.isDeadOrDying()) return;

        if (state == StarChaserEntity.SUMMON) {
            float cast = StarChaserEntity.SUMMON_CAST;
            float env = age < cast ? Mth.clamp((age - 4) / (cast - 4), 0, 1) : Mth.clamp(1 - (age - cast) / 8, 0, 1);
            env *= env;
            for (int i = 0; i < 3; i++) glow(buf, core, 0.4F + 2.2F * env, env, 0.85F * env, 0.55F * env);
        }
        if (state == StarChaserEntity.BITE) {
            float t = (age - StarChaserEntity.BITE_HIT + 1) / 5;
            if (t >= 0 && t < 1) {
                float b = (1 - t) * (1 - t);
                Vec3 mouth = jaw.add(f.scale(0.15));
                for (int i = 0; i < 2; i++) glow(buf, mouth, 0.2F + 0.7F * t, b, 0.85F * b, 0.5F * b);
            }
        }
        if (state == StarChaserEntity.LEAVE) {
            // 化成陨星：胸核越来越亮，腾空后变成一团白热星光
            float env = Mth.clamp(age / StarChaserEntity.LEAVE_LIFT, 0, 1);
            float size = 0.4F + 0.8F * env + (age > StarChaserEntity.LEAVE_LIFT ? 0.8F : 0);
            for (int i = 0; i < 3; i++) glow(buf, core, size, env, 0.9F * env, 0.7F * env);
        }
        if (state == StarChaserEntity.STOMP) {
            // 砸地一瞬爪下闪白，随后压成一团贴地余光
            float t = (age - StarChaserEntity.STOMP_HIT) / 8;
            if (t >= 0 && t < 1) {
                float b = (1 - t) * (1 - t);
                Vec3 at = stompCenter().add(0, 0.35, 0);
                for (int i = 0; i < 3; i++) glow(buf, at, 0.6F + 2.2F * t, b, 0.85F * b, 0.5F * b);
            }
        }
        if (state == StarChaserEntity.STAGGER && age < 8) {
            float t = age / 8, b = (1 - t) * (1 - t);
            for (int i = 0; i < 3; i++) glow(buf, jaw.add(f.scale(0.4)), 0.5F + 2.4F * t, b, 0.8F * b, 0.45F * b);
        }
        float oa = overloadAge();
        if (oa >= 0 && oa < 16) {
            float t = oa / 16, b = (1 - t) * (1 - t);
            for (int i = 0; i < 3; i++) glow(buf, core, 0.6F + 4.5F * t, b, 0.95F * b, 0.85F * b);
        }
    }

    // ---- 第二遍：笔触（獠牙、预警线、冲锋拖尾、召星光柱） ----

    private void strokePass() {
        VertexConsumer buf = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.TAIL));
        switch (state) {
            case StarChaserEntity.BITE -> fangs(buf);
            case StarChaserEntity.WINDUP -> chargeLine(buf);
            case StarChaserEntity.CHARGE -> chargeTrail(buf, e.vfxOrigin.subtract(origin), Vec3.ZERO, core.add(f.scale(0.3)), 1);
            case StarChaserEntity.SUMMON -> summonBeam(buf);
            case StarChaserEntity.STOMP -> stompCracks(buf);
            case StarChaserEntity.LEAVE -> {
                if (age > StarChaserEntity.LEAVE_LIFT) {
                    Vec3 ground = e.vfxOrigin.subtract(origin).add(0, 0.5, 0);
                    stroke(buf, core, ground, 1.2F, 0.3F, 0, 0.95F, COLUMN, 0.9F, 0.45F, 0.15F);
                    stroke(buf, core, ground, 0.5F, 0.08F, 0, 0.8F, COLUMN * 2, 1, 0.8F, 0.45F);
                    stroke(buf, core, core.add(ground.subtract(core).scale(0.5)), 0.2F, 0.03F, 0, 0.4F, COLUMN * 3, 1, 0.97F, 0.9F);
                }
            }
            default -> {}
        }
        if (e.trailAge >= 0 && state != StarChaserEntity.CHARGE) {
            float k = 1 - Mth.clamp((e.trailAge + (age - (int) age)) / 10, 0, 1);
            Vec3 end = e.trailEnd.subtract(origin);
            chargeTrail(buf, e.vfxOrigin.subtract(origin), end, end.add(0, core.y, 0), k * k);
        }
    }

    /**
     * 光獠牙：上排沿吻部下沿、下排沿下颚上沿，挂在 head / jaw 骨骼上跟着张嘴合嘴走；
     * 比真嘴略宽、额外多张开一点，咬合那一拍合拢并闪白，然后散掉。坐标是骨骼本地像素。
     */
    private void fangs(VertexConsumer buf) {
        if (headBone == null || jawBone == null) return;
        float hit = StarChaserEntity.BITE_HIT;
        if (age < 1 || age > hit + 5) return;
        float open = age < hit - 3 ? Mth.clamp((age - 1) / 3, 0, 1) : 1 - smooth(Mth.clamp((age - hit + 3) / 3, 0, 1));
        float b = Mth.clamp((age - 1) / 3, 0, 1) * Mth.clamp(1 - (age - hit) / 5, 0, 1) + 0.6F * Math.max(0, 1 - Math.abs(age - hit) / 1.5F);
        b = Math.min(1, b);
        int n = 8;
        for (int upper = 0; upper < 2; upper++) {
            Matrix4f bone = upper == 1 ? headBone : jawBone;
            float y = upper == 1 ? SNOUT_Y + 1.5F * open : LOWER_Y - 1.5F * open;
            float z = upper == 1 ? SNOUT_Z : LOWER_Z;
            float half = upper == 1 ? 2.6F : 2.3F, toothDir = upper == 1 ? -1 : 1;
            Vec3[] rim = new Vec3[n + 1];
            for (int k = 0; k <= n; k++) {
                float th = -1 + 2F * k / n;
                // 前端最突出，两侧往后收，贴着吻部 / 下颚轮廓
                rim[k] = at(bone, half * th, y, z - 0.8F + 4 * th * th);
            }
            for (int k = 0; k < n; k++) {
                float t0 = -1 + 2F * k / n, t1 = -1 + 2F * (k + 1) / n;
                float w0 = 0.018F + 0.022F * (1 - t0 * t0), w1 = 0.018F + 0.022F * (1 - t1 * t1);
                stroke(buf, rim[k], rim[k + 1], w0 * 2.8F, w1 * 2.8F, HOT, HOT, 0, 0.9F * b, 0.42F * b, 0.12F * b);
                stroke(buf, rim[k], rim[k + 1], w0, w1, HOT, HOT, 0, b, 0.95F * b, 0.82F * b);
            }
            for (float th : new float[]{-0.75F, -0.3F, 0.3F, 0.75F}) {
                float zz = z - 0.8F + 4 * th * th, len = (upper == 1 ? 2.4F : 1.8F) * (1 - 0.3F * Math.abs(th));
                Vec3 root = at(bone, half * th, y, zz), tip = at(bone, half * th * 0.9F, y + toothDir * len, zz - 0.2F);
                stroke(buf, root, tip, 0.045F, 0.004F, HOT, 0.3F, COLUMN, b, 0.9F * b, 0.7F * b);
            }
        }
    }

    /** 蓄力时沿锁定方向铺一条贴地的彗光线，脉冲往前跑，末端一个落点圈（圈在第三遍画）。 */
    private void chargeLine(VertexConsumer buf) {
        float grow = 1 - (1 - Mth.clamp(age / 9, 0, 1)) * (1 - Mth.clamp(age / 9, 0, 1));
        float length = (float) StarChaserEntity.CHARGE_RANGE * grow;
        if (length < 0.3F) return;
        boolean locked = age > StarChaserEntity.WINDUP_TRACK + 2;
        float flick = (0.75F + 0.25F * Mth.sin(age * 2.3F)) * (locked ? 1 : 0.7F);
        Vec3 aim = e.getAim();
        Vec3 flatSide = new Vec3(-aim.z, 0, aim.x);
        float step = 0.5F;
        int n = Mth.ceil(length / step);
        Vec3 prev = null;
        double lastY = 0.07;
        for (int k = 0; k <= n; k++) {
            float d = Math.min(length, k * step) + 1.4F;
            double x = aim.x * d, z = aim.z * d;
            double y = StarChaserEntity.groundY(e.level(), origin.x + x, origin.z + z, origin.y + 1);
            lastY = Double.isNaN(y) ? lastY : y - origin.y + 0.07;
            Vec3 p = new Vec3(x, lastY, z);
            if (prev != null) {
                float wave = (float) Math.pow(Math.max(0, Mth.cos(k * step * 0.9F - age * 0.55F)), 4);
                float tip = 1 - 0.6F * (k * step / length);
                float b = flick * tip * (0.45F + 0.55F * wave);
                flat(buf, prev, p, flatSide, 0.55F, HOT, 0.8F * b, 0.3F * b, 0.08F * b);
                flat(buf, prev, p, flatSide, 0.16F + 0.06F * wave, HOT, b, 0.88F * b, 0.6F * b);
            }
            prev = p;
        }
    }

    /** 冲锋拖尾：从起点到胸口三层彗光，外层宽而暗、内层细而白；地上再拖一道焦痕光。 */
    private void chargeTrail(VertexConsumer buf, Vec3 start, Vec3 end, Vec3 head, float k) {
        Vec3 flat = new Vec3(end.x - start.x, 0, end.z - start.z);
        if (flat.lengthSqr() < 0.25 || k <= 0.01F) return;
        Vec3 back = start.add(0, (head.y - end.y) * 0.85, 0);
        stroke(buf, head, back, 1.15F * k, 0.15F, 0, 0.95F, COLUMN, 0.9F * k, 0.45F * k, 0.15F * k);
        stroke(buf, head, back, 0.55F * k, 0.05F, 0, 0.8F, COLUMN * 2, k, 0.8F * k, 0.45F * k);
        stroke(buf, head, head.add(back.subtract(head).scale(0.6)), 0.2F * k, 0.02F, 0, 0.4F, COLUMN * 3, k, 0.97F * k, 0.9F * k);
        Vec3 dir = flat.normalize();
        Vec3 flatSide = new Vec3(-dir.z, 0, dir.x);
        Vec3 g0 = new Vec3(start.x, start.y + 0.06, start.z), g1 = new Vec3(end.x, end.y + 0.06, end.z);
        StarfallDraw.quad(pose, buf, g0.add(flatSide.scale(0.45)), g0.subtract(flatSide.scale(0.45)),
            g1.subtract(flatSide.scale(0.85)), g1.add(flatSide.scale(0.85)), 0, 0.9F, COLUMN, 0.1F, 0.7F * k, 0.3F * k, 0.08F * k);
    }

    private Vec3 stompCenter() {
        return age >= StarChaserEntity.STOMP_HIT ? e.vfxOrigin.subtract(origin) : f.scale(StarChaserEntity.STOMP_REACH);
    }

    /** 践踏地裂：爪下向外裂出几道折线亮缝，先随震荡波一起伸长，再慢慢熄灭。每次践踏按动作计数定形。 */
    private void stompCracks(VertexConsumer buf) {
        float wave = age - StarChaserEntity.STOMP_HIT;
        if (wave < 0) return;
        float reach = (float) e.waveRadius(wave) * 0.75F;
        float b = Mth.clamp(1 - (wave - StarChaserEntity.STOMP_WAVE_TICKS * 0.5F) / 10, 0, 1);
        if (b <= 0.01F) return;
        Vec3 c = stompCenter().add(0, 0.06, 0);
        Random rng = new Random(e.getId() * 31L + e.getActionCount());
        int n = 7;
        for (int i = 0; i < n; i++) {
            float a = (i + rng.nextFloat() * 0.6F) * Mth.TWO_PI / n;
            float len = reach * (0.6F + 0.4F * rng.nextFloat());
            Vec3 prev = c;
            int segs = 4;
            for (int s = 1; s <= segs; s++) {
                float k = (float) s / segs;
                float bend = a + (rng.nextFloat() - 0.5F) * 0.5F;
                Vec3 next = c.add(Mth.cos(bend) * len * k, 0, Mth.sin(bend) * len * k);
                Vec3 dir = next.subtract(prev);
                Vec3 across = new Vec3(-dir.z, 0, dir.x);
                if (across.lengthSqr() > 1.0E-6) {
                    across = across.normalize();
                    float w = 0.16F * (1 - 0.6F * k);
                    flat(buf, prev, next, across, w * 2.2F, 0.5F, 0.9F * b, 0.45F * b, 0.12F * b);
                    flat(buf, prev, next, across, w, 0.5F, b, 0.85F * b, 0.5F * b);
                }
                prev = next;
            }
        }
    }

    /** 召星：胸核往天上打一道光柱。 */
    private void summonBeam(VertexConsumer buf) {
        float t = (age - (StarChaserEntity.SUMMON_CAST - 2)) / 12;
        if (t < 0 || t >= 1) return;
        float b = t < 0.15F ? t / 0.15F : (1 - t) / 0.85F;
        Vec3 top = core.add(0, 28, 0);
        stroke(buf, core, top, 0.15F + 0.6F * b, 0.25F, 0, 0.9F, COLUMN, 0.9F * b, 0.55F * b, 0.2F * b);
        stroke(buf, core, top, 0.08F + 0.15F * b, 0.05F, 0, 0.5F, COLUMN * 2, b, 0.95F * b, 0.85F * b);
    }

    // ---- 第三遍：热浪环（落点圈、冲击环、音速锥、甩尾弧、嚎叫、过载） ----

    private void ringPass() {
        VertexConsumer buf = buffers.getBuffer(StarfallDraw.additive(StarfallDraw.RING));
        Vec3 flatX = new Vec3(1, 0, 0), flatZ = new Vec3(0, 0, 1);
        switch (state) {
            case StarChaserEntity.WINDUP -> {
                float grow = Mth.clamp((age - 6) / 6, 0, 1);
                if (grow > 0) {
                    Vec3 aim = e.getAim();
                    double d = StarChaserEntity.CHARGE_RANGE + 1.4;
                    double y = StarChaserEntity.groundY(e.level(), origin.x + aim.x * d, origin.z + aim.z * d, origin.y + 1);
                    Vec3 end = new Vec3(aim.x * d, Double.isNaN(y) ? 0.07 : y - origin.y + 0.07, aim.z * d);
                    float r = 1.1F + 0.15F * Mth.sin(age * 0.9F), b = grow * (0.7F + 0.3F * Mth.sin(age * 2.3F));
                    ring(buf, end, flatX, flatZ, r * 0.55F, r, 20, b, 0.85F * b, 0.6F * b);
                }
            }
            case StarChaserEntity.CHARGE -> {
                if (age < 10) {
                    float t = age / 10;
                    Vec3 at = e.vfxOrigin.subtract(origin).add(0, 0.08, 0);
                    float r = 0.8F + 3.5F * t, b = 1 - t;
                    ring(buf, at, flatX, flatZ, r - 0.9F * (1 - 0.5F * t), r, 24, b, 0.9F * b, 0.75F * b);
                    wall(buf, at, r, 1.4F * (1 - t), 24, 0.6F * b);
                }
                // 音速锥：胸前一圈圈往后退的热浪
                for (int j = 0; j < 2; j++) {
                    float p = (age * 0.3F + j * 0.5F) % 1;
                    float r = 0.8F + 1.3F * p, b = (1 - p) * 0.8F;
                    ring(buf, core.add(f.scale(1.6 - 2.8 * p)), side, UP, r * 0.72F, r, 20, b, 0.85F * b, 0.65F * b);
                }
            }
            case StarChaserEntity.STAGGER -> {
                if (age < 10) {
                    float t = age / 10, r = 0.4F + 2.6F * t, b = (1 - t) * (1 - t);
                    ring(buf, jaw.add(f.scale(0.3)), side, UP, r * 0.6F, r, 20, b, 0.85F * b, 0.6F * b);
                }
            }
            case StarChaserEntity.SWEEP -> sweepArc(buf);
            case StarChaserEntity.STOMP -> {
                float wave = age - StarChaserEntity.STOMP_HIT;
                // 人立时爪下先亮一圈落点提示，砸地后换成往外推的震荡波
                if (wave < 0 && age >= 4) {
                    float k = Mth.clamp((age - 4) / 6, 0, 1), b = k * (0.45F + 0.2F * Mth.sin(age * 2.1F));
                    float r = (float) StarChaserEntity.STOMP_RADIUS;
                    ring(buf, f.scale(StarChaserEntity.STOMP_REACH).add(0, 0.07, 0), flatX, flatZ, r - 0.35F, r, 24, b, 0.8F * b, 0.45F * b);
                }
                if (wave >= 0 && wave <= StarChaserEntity.STOMP_WAVE_TICKS + 4) {
                    Vec3 c = stompCenter().add(0, 0.08, 0);
                    float t = Mth.clamp(wave / (StarChaserEntity.STOMP_WAVE_TICKS + 4), 0, 1);
                    float r = (float) e.waveRadius(wave), b = (1 - t) * (1 - t * 0.5F);
                    ring(buf, c, flatX, flatZ, Math.max(0, r - 1.1F), r, 36, b, 0.85F * b, 0.55F * b);
                    wall(buf, c, r, 1.1F * (1 - t), 36, 0.75F * b);
                    // 内圈一道淡的余波，拉开层次
                    float r2 = Math.max(0, r * 0.55F);
                    ring(buf, c, flatX, flatZ, Math.max(0, r2 - 0.5F), r2, 28, 0.4F * b, 0.3F * b, 0.15F * b);
                }
            }
            case StarChaserEntity.LEAVE -> {
                float t = (age - StarChaserEntity.LEAVE_LIFT) / 12;
                if (t >= 0 && t < 1) {
                    Vec3 at = e.vfxOrigin.subtract(origin).add(0, 0.08, 0);
                    float r = 1 + 5 * (1 - (1 - t) * (1 - t)), b = 1 - t;
                    ring(buf, at, flatX, flatZ, Math.max(0, r - 1), r, 28, b, 0.9F * b, 0.75F * b);
                    wall(buf, at, r, 1.6F * (1 - t), 28, 0.6F * b);
                }
            }
            case StarChaserEntity.HOWL -> {
                for (int k = 0; k < 4; k++) {
                    float ra = age - StarChaserEntity.HOWL_ROAR - 7 * k;
                    if (ra < 0 || ra >= 16) continue;
                    float t = ra / 16, r = 1.6F + 7 * (1 - (1 - t) * (1 - t)), b = (1 - t) * 0.9F;
                    Vec3 at = new Vec3(0, 0.08, 0);
                    ring(buf, at, flatX, flatZ, Math.max(0, r - 0.8F), r, 32, b, 0.85F * b, 0.6F * b);
                    wall(buf, at, r, 1.3F * (1 - t), 32, 0.6F * b);
                }
            }
            default -> {}
        }
        float oa = overloadAge();
        if (oa >= 0 && oa < 16) {
            float t = oa / 16, r = 1 + 8 * (1 - (1 - t) * (1 - t)), b = 1 - t;
            Vec3 at = new Vec3(0, 0.08, 0);
            ring(buf, at, flatX, flatZ, Math.max(0, r - 1.2F), r, 32, b, 0.95F * b, 0.85F * b);
            wall(buf, at, r, 2 * (1 - t), 32, 0.7F * b);
        }
    }

    /** 甩尾弧：前沿贴着彗尾尖，往回拖一段渐隐的新月带，方向跟着尾尖的实际转向。 */
    private void sweepArc(VertexConsumer buf) {
        double dist = Math.sqrt(tail.x * tail.x + tail.z * tail.z);
        float angle = (float) Math.atan2(tail.z, tail.x);
        float delta = Mth.wrapDegrees((angle - e.sweepAngle) * Mth.RAD_TO_DEG);
        if (Math.abs(delta) > 0.5F) e.sweepSign = delta > 0 ? 1 : -1;
        e.sweepAngle = angle;
        float hit = StarChaserEntity.SWEEP_HIT;
        if (age < hit - 5 || age > hit + 5 || dist < 1) return;
        float env = Mth.clamp((age - hit + 5) / 4, 0, 1) * Mth.clamp((hit + 5 - age) / 4, 0, 1);
        float arc = 1.9F * Mth.clamp((age - hit + 5) / 4, 0, 1);
        int n = 16;
        float outer = (float) dist + 0.5F, inner = 1.0F;
        for (int layer = 0; layer < 2; layer++) {
            float r0 = layer == 0 ? inner : outer - 0.7F, r1 = layer == 0 ? outer : outer + 0.5F;
            float y0 = (float) tail.y + (layer == 0 ? 0.4F : 0.1F), y1 = (float) tail.y - 0.1F;
            for (int i = 0; i < n; i++) {
                float a0 = angle - e.sweepSign * arc * i / n, a1 = angle - e.sweepSign * arc * (i + 1) / n;
                float b = env * (1 - (float) i / n) * (layer == 0 ? 0.7F : 1);
                Vec3 i0 = new Vec3(Mth.cos(a0) * r0, y0, Mth.sin(a0) * r0), i1 = new Vec3(Mth.cos(a1) * r0, y0, Mth.sin(a1) * r0);
                Vec3 o0 = new Vec3(Mth.cos(a0) * r1, y1, Mth.sin(a0) * r1), o1 = new Vec3(Mth.cos(a1) * r1, y1, Mth.sin(a1) * r1);
                StarfallDraw.quad(pose, buf, i0, i1, o1, o0, i / (float) n, 0, (i + 1) / (float) n, 1, b, 0.78F * b, 0.4F * b);
            }
        }
    }

    // ---- 画图工具 ----

    private float overloadAge() {
        return e.overloadAge < 0 ? -1 : e.overloadAge + (age - (int) age);
    }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }

    private void glow(VertexConsumer buf, Vec3 center, float size, float r, float g, float b) {
        Vec3 view = cam.subtract(center).normalize();
        Vec3 right = view.cross(UP);
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(view).normalize();
        Vec3 p = center.add(view.scale(0.05));
        Vec3 rr = right.scale(size), uu = up.scale(size);
        StarfallDraw.quad(pose, buf, p.subtract(rr).add(uu), p.add(rr).add(uu), p.add(rr).subtract(uu), p.subtract(rr).subtract(uu), 0, 0, 1, 1, r, g, b);
    }

    /** 朝向镜头的一段光线，u 横跨贴图一列（软边），v 沿长度。 */
    private void stroke(VertexConsumer buf, Vec3 a, Vec3 b, float wa, float wb, float va, float vb, float u0, float r, float g, float bl) {
        Vec3 s = b.subtract(a).cross(cam.subtract(a.add(b).scale(0.5)));
        if (s.lengthSqr() < 1.0E-8) return;
        s = s.normalize();
        StarfallDraw.quad(pose, buf, a.add(s.scale(wa)), a.subtract(s.scale(wa)), b.subtract(s.scale(wb)), b.add(s.scale(wb)),
            u0, va, u0 + COLUMN, vb, r, g, bl);
    }

    /** 贴地的一段光线。 */
    private void flat(VertexConsumer buf, Vec3 a, Vec3 b, Vec3 s, float w, float v, float r, float g, float bl) {
        StarfallDraw.quad(pose, buf, a.add(s.scale(w)), a.subtract(s.scale(w)), b.subtract(s.scale(w)), b.add(s.scale(w)), 0, v, COLUMN, v, r, g, bl);
    }

    /** ax1、ax2 张成的平面上一圈环带，v=1 在外缘（热浪贴图亮边）。 */
    private void ring(VertexConsumer buf, Vec3 c, Vec3 ax1, Vec3 ax2, float inner, float outer, int segments, float r, float g, float b) {
        for (int i = 0; i < segments; i++) {
            float a0 = i * Mth.TWO_PI / segments, a1 = (i + 1) * Mth.TWO_PI / segments;
            Vec3 d0 = ax1.scale(Mth.cos(a0)).add(ax2.scale(Mth.sin(a0))), d1 = ax1.scale(Mth.cos(a1)).add(ax2.scale(Mth.sin(a1)));
            float u0 = i * 4F / segments, u1 = (i + 1) * 4F / segments;
            StarfallDraw.quad(pose, buf, c.add(d0.scale(inner)), c.add(d1.scale(inner)), c.add(d1.scale(outer)), c.add(d0.scale(outer)), u0, 0, u1, 1, r, g, b);
        }
    }

    /** 地面环外缘竖起的一圈火墙，底部亮。 */
    private void wall(VertexConsumer buf, Vec3 c, float radius, float height, int segments, float b) {
        if (height <= 0.02F || b <= 0.01F) return;
        for (int i = 0; i < segments; i++) {
            float a0 = i * Mth.TWO_PI / segments, a1 = (i + 1) * Mth.TWO_PI / segments;
            float c0 = Mth.cos(a0) * radius, s0 = Mth.sin(a0) * radius, c1 = Mth.cos(a1) * radius, s1 = Mth.sin(a1) * radius;
            float u0 = i * 4F / segments, u1 = (i + 1) * 4F / segments;
            StarfallDraw.quad(pose, buf, c.add(c0, height, s0), c.add(c1, height, s1), c.add(c1, -0.03, s1), c.add(c0, -0.03, s0),
                u0, 0, u1, 1, b, 0.85F * b, 0.7F * b);
        }
    }
}
