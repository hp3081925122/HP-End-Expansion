package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.TidebreakerWarlordEntity;
import org.hp.hp_end_expansion.registry.ModEntities;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TidebreakerWarlordRenderer extends GeoEntityRenderer<TidebreakerWarlordEntity> {
    private static final ResourceLocation GEO = id("geo/tidebreaker_warlord.geo.json");
    private static final ResourceLocation TEXTURE = id("textures/entity/tidebreaker_warlord.png");
    private static final ResourceLocation ANIMATION = id("animations/tidebreaker_warlord.animation.json");

    // 特效贴图：自绘路线与裂地，其余沿用潮光系列
    private static final ResourceLocation LANE = id("textures/entity/tidebreaker_warlord_fx_lane.png");
    private static final ResourceLocation CRACK = id("textures/entity/tidebreaker_warlord_fx_crack.png");
    private static final ResourceLocation SEAL = id("textures/entity/tidecrown_fx_seal.png");
    private static final ResourceLocation FOAM = id("textures/entity/tidecrown_fx_foam.png");
    private static final ResourceLocation WAVE = id("textures/entity/tide_wave.png");
    private static final ResourceLocation IMPACT = id("textures/entity/tidebreaker_impact.png");
    private static final ResourceLocation GUARD = id("textures/entity/tidebreaker_guard.png");
    private static final ResourceLocation STREAK = id("textures/entity/tidebreaker_streak.png");
    private static final ResourceLocation DISC = id("textures/entity/tidebreaker_arc.png");

    // 锤头相对身体的角度表（0 为正前，90 为左侧），来自动画实测
    private static final float[][] SWEEP = {{8, -76}, {10, -54}, {11, -12}, {12, 23}, {13, 36}, {14, 45}};
    private static final float[][] HOOK = {{16, 40}, {17, 25}, {18, -4}, {19, -31}, {20, -43}};
    private static final float[][] SPIN = {{12, -90}, {14, -27}, {16, 101}, {18, 180}, {20, 277}, {22, 330}};
    private static final float[][] COUNTER = {{21, 40}, {23, 0}, {25, -35}};

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, path);
    }

    public TidebreakerWarlordRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        shadowRadius = 1.2F;
    }

    // 注册碎潮锤将渲染器
    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TIDEBREAKER_WARLORD.get(), TidebreakerWarlordRenderer::new);
    }

    @Override
    protected float getDeathMaxRotation(TidebreakerWarlordEntity e) {
        return 0;
    }

    // 技能特效范围较大，放宽视锥剔除
    @Override
    public boolean shouldRender(TidebreakerWarlordEntity e, Frustum f, double x, double y, double z) {
        double grow = 2;
        if (e.skill() != TidebreakerWarlordEntity.NONE) grow = 14;
        return e.shouldRender(x, y, z) && f.isVisible(e.getBoundingBox().inflate(grow));
    }

    @Override
    public void render(TidebreakerWarlordEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource b, int light) {
        super.render(e, yaw, pt, ps, b, light);
        if (e.isDeadOrDying()) return;
        Vec3 pos = e.getPosition(pt);
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(pos);
        float age = e.skillAge(pt);
        float bodyYaw = e.aimYaw();
        PoseStack.Pose p = ps.last();
        int s = e.skill();
        Vec3 ground = new Vec3(0, 0.06, 0);
        Vec3 hammer = e.hammerPoint(ground);

        if (s == TidebreakerWarlordEntity.COMBO) {
            // 前扫与回勾的弧光
            if (age >= 9 && age <= 16) swing(p, b, bodyYaw, SWEEP, age, 3, 1.3, 3.0, 1.0, window(age, 9, 11, 13, 16));
            if (age >= 16 && age <= 23) swing(p, b, bodyYaw, HOOK, age, 2.5F, 1.1, 2.7, 1.1, window(age, 16, 17, 20, 23));
            // 下砸前落点预警，落地后裂地与水花
            if (age >= 22 && age < TidebreakerWarlordEntity.COMBO_SMASH) warning(p, b, hammer, TidebreakerWarlordEntity.SMASH_RADIUS, (age - 22) / 10F);
            if (age >= TidebreakerWarlordEntity.COMBO_SMASH) impact(p, b, hammer, cam, age - TidebreakerWarlordEntity.COMBO_SMASH, TidebreakerWarlordEntity.SMASH_RADIUS);
        } else if (s == TidebreakerWarlordEntity.SLAM) {
            // 重砸预警圈从举锤开始
            if (age >= 4 && age < TidebreakerWarlordEntity.SLAM_IMPACT) warning(p, b, hammer, TidebreakerWarlordEntity.SLAM_RADIUS, (age - 4) / 18F);
            if (age >= TidebreakerWarlordEntity.SLAM_IMPACT) impact(p, b, hammer, cam, age - TidebreakerWarlordEntity.SLAM_IMPACT, TidebreakerWarlordEntity.SLAM_RADIUS);
            // 地面震波：低矮水墙向外推，提示跳跃
            if (age >= TidebreakerWarlordEntity.WAVE_START - 1 && age <= TidebreakerWarlordEntity.WAVE_END + 3) {
                double radius = TidebreakerWarlordEntity.waveRadius(age);
                float fade = Mth.clamp((TidebreakerWarlordEntity.WAVE_END + 3 - age) / 4F, 0, 1);
                AbyssFx.wallRing(p, AbyssFx.glow(b, WAVE), hammer, radius, 0.7, 0.15, 48, (float) Math.max(4, Math.round(radius * 2)), fade);
                AbyssFx.flatRing(p, AbyssFx.glow(b, AbyssFx.RING), hammer.add(0, 0.02, 0), Math.max(0, radius - 0.8), radius, 48, 8, age * 0.05F, fade * 0.8F);
            }
        } else if (s == TidebreakerWarlordEntity.SPIN) {
            // 蓄力时显示回旋范围
            if (age >= 2 && age < TidebreakerWarlordEntity.SPIN_START) warning(p, b, ground, TidebreakerWarlordEntity.SPIN_RADIUS, (age - 2) / 12F);
            if (age >= 13 && age <= 22) swing(p, b, bodyYaw, SPIN, age, 2.5F, 1.5, 3.4, 1.2, window(age, 13, 14, 20, 22));
        } else if (s == TidebreakerWarlordEntity.RAM) {
            // 冲撞路线：蓄力时逐渐变亮，冲锋时淡出
            if (age >= 2 && age < TidebreakerWarlordEntity.RAM_START + 6) {
                float k = Mth.clamp((age - 2) / 10F, 0, 1) * Mth.clamp((TidebreakerWarlordEntity.RAM_START + 6 - age) / 6F, 0, 1);
                lane(p, b, e, k, age);
            }
            // 冲锋时身前的破水弧与两侧拖尾
            if (age >= TidebreakerWarlordEntity.RAM_START && age <= TidebreakerWarlordEntity.RAM_END) {
                Vec3 front = e.forward().scale(1.4).add(0, 1.3, 0);
                disc(p, b, DISC, front, e.forward(), 1.3, 0.6F);
                Vec3 side = e.right().scale(0.9);
                for (int i = -1; i <= 1; i += 2) {
                    Vec3 start = front.add(side.scale(i)).add(0, -0.6, 0);
                    AbyssFx.ribbon(p, AbyssFx.glow(b, STREAK), start, e.forward().scale(-1), cam.subtract(start), 3.2, 0.25, 0, 1, 0.6F);
                }
            }
        } else if (s == TidebreakerWarlordEntity.STAGGER) {
            // 撞墙火花与晕眩水沫
            if (age < 8) {
                Vec3 c = e.forward().scale(1.3).add(0, 1.6, 0);
                AbyssFx.billboard(p, AbyssFx.glow(b, IMPACT), c, cam.subtract(c), 0.9 + age * 0.12, age * 0.05, 1 - age / 8F);
            }
            for (int i = 0; i < 3; i++) {
                double a = age * 0.25 + i * Math.PI * 2 / 3;
                Vec3 c = new Vec3(Math.cos(a) * 0.6, 3.2, Math.sin(a) * 0.6);
                AbyssFx.billboard(p, AbyssFx.glow(b, FOAM), c, cam.subtract(c), 0.22, a, 0.7F * Mth.clamp((TidebreakerWarlordEntity.LENGTH[s] - age) / 6F, 0, 1));
            }
        } else if (s == TidebreakerWarlordEntity.GUARD) {
            // 正面潮盾，受击时闪亮
            if (age >= TidebreakerWarlordEntity.GUARD_START && age < TidebreakerWarlordEntity.GUARD_COUNTER) {
                float hit = Mth.clamp(1 - e.guardHitAge(pt) / 6F, 0, 1);
                float rise = Mth.clamp((age - TidebreakerWarlordEntity.GUARD_START) / 3F, 0, 1);
                disc(p, b, GUARD, e.forward().scale(1.7).add(0, 1.9, 0), e.forward(), 1.2 + hit * 0.15, rise * (0.45F + 0.5F * hit));
            }
            if (age >= 21 && age <= 27) swing(p, b, bodyYaw, COUNTER, age, 2, 1.2, 3.4, 1.6, window(age, 21, 22, 24, 27));
        } else if (s == TidebreakerWarlordEntity.ROAR) {
            // 咆哮冲击环
            float t = age - TidebreakerWarlordEntity.ROAR_PUSH;
            if (t >= 0 && t < 14) {
                double r = 1 + (TidebreakerWarlordEntity.ROAR_RADIUS - 1) * Math.min(1, t / 8);
                float fade = Mth.clamp((14 - t) / 6F, 0, 1);
                AbyssFx.flatRing(p, AbyssFx.glow(b, AbyssFx.RING), ground, Math.max(0, r - 1.4), r, 48, 8, t * 0.04F, fade);
                AbyssFx.wallRing(p, AbyssFx.glow(b, WAVE), ground, r, 1.0, 0.3, 48, 12, fade * 0.7F);
                Vec3 mouth = e.forward().scale(1.3).add(0, 2.9, 0);
                AbyssFx.axialRing(p, AbyssFx.glow(b, AbyssFx.RING), mouth.add(e.forward().scale(t * 0.25)), e.forward(), 0.3 + t * 0.12, 0.8 + t * 0.18, 24, fade);
            }
        } else if (s == TidebreakerWarlordEntity.SURGE) {
            // 锤击点裂地
            if (age >= TidebreakerWarlordEntity.SURGE_SMASH) impact(p, b, hammer, cam, age - TidebreakerWarlordEntity.SURGE_SMASH, 1.6);
            for (int i = 0; i < TidebreakerWarlordEntity.SURGE_COUNT; i++) {
                int erupt = e.geyserErupt(i);
                for (int line = 0; line < TidebreakerWarlordEntity.SURGE_LINES; line++) {
                    Vec3 c = e.geyser(pos, line, i).subtract(pos).add(0, 0.06, 0);
                    // 喷发前在落点画预警印记
                    if (age >= 8 && age < erupt) warning(p, b, c, TidebreakerWarlordEntity.GEYSER_RADIUS, (age - 8) / (float) (erupt - 8));
                    // 喷发水柱：迅速升起后回落
                    float t = age - erupt;
                    if (t >= 0 && t < 12) geyser(p, b, c, cam, t);
                }
            }
        }
    }

    // 预警圈：越接近出手越亮
    private static void warning(PoseStack.Pose p, MultiBufferSource b, Vec3 c, double radius, float charge) {
        float k = Mth.clamp(charge, 0, 1);
        AbyssFx.decal(p, AbyssFx.glow(b, SEAL), c, radius / 0.88, k * 0.6, 0.25F + 0.55F * k);
    }

    // 落地冲击：裂地贴花、冲击闪光与水花环
    private static void impact(PoseStack.Pose p, MultiBufferSource b, Vec3 c, Vec3 cam, float t, double radius) {
        float crack = Mth.clamp((18 - t) / 8F, 0, 1);
        if (crack > 0) AbyssFx.decal(p, AbyssFx.glow(b, CRACK), c.add(0, 0.01, 0), radius * 1.3, 0.4, crack);
        if (t < 7) {
            float fade = 1 - t / 7F;
            Vec3 up = c.add(0, 0.6, 0);
            AbyssFx.billboard(p, AbyssFx.glow(b, IMPACT), up, cam.subtract(up), radius * (0.5 + 0.08 * t), t * 0.04, fade);
            AbyssFx.flatRing(p, AbyssFx.glow(b, AbyssFx.RING), c.add(0, 0.03, 0), radius * (0.3 + 0.12 * t), radius * (0.8 + 0.15 * t), 32, 6, t * 0.06F, fade);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + 0.3;
                Vec3 f = c.add(Math.cos(a) * radius * 0.15 * t, 0.3 + t * 0.25 - t * t * 0.03, Math.sin(a) * radius * 0.15 * t);
                AbyssFx.billboard(p, AbyssFx.glow(b, FOAM), f, cam.subtract(f), 0.25, a, fade);
            }
        }
    }

    // 弧光：按角度表取锤头位置，向后拖出一段尾迹
    private static void swing(PoseStack.Pose p, MultiBufferSource b, float bodyYaw, float[][] table, float age, float trailTicks, double r0, double r1, double y, float alpha) {
        if (alpha <= 0) return;
        double head = toArc(bodyYaw, lookup(table, age));
        double tail = toArc(bodyYaw, lookup(table, age - trailTicks));
        if (Math.abs(head - tail) < 0.05) return;
        AbyssFx.arcBlade(p, AbyssFx.glow(b, AbyssFx.ARC), new Vec3(0, y, 0), head, head - tail, r0, r1, 0.05, 16, alpha);
    }

    // 身体角度转换为 AbyssFx 环形角度
    private static double toArc(float bodyYaw, float relative) {
        return Math.toRadians(90 + bodyYaw - relative);
    }

    private static float lookup(float[][] table, float age) {
        if (age <= table[0][0]) return table[0][1];
        for (int i = 1; i < table.length; i++) {
            if (age <= table[i][0]) {
                float t = (age - table[i - 1][0]) / (table[i][0] - table[i - 1][0]);
                return Mth.lerp(t, table[i - 1][1], table[i][1]);
            }
        }
        return table[table.length - 1][1];
    }

    // 淡入、保持、淡出
    private static float window(float age, float in, float full, float hold, float out) {
        if (age < full) return Mth.clamp((age - in) / (full - in), 0, 1);
        if (age <= hold) return 1;
        return Mth.clamp((out - age) / (out - hold), 0, 1);
    }

    // 冲撞路线：贴地长条，箭头沿冲锋方向流动
    private static void lane(PoseStack.Pose p, MultiBufferSource b, TidebreakerWarlordEntity e, float k, float age) {
        Vec3 f = e.forward(), side = e.right().scale(1.0);
        Vec3 start = f.scale(0.8).add(0, 0.06, 0), end = f.scale(TidebreakerWarlordEntity.RAM_LENGTH).add(0, 0.06, 0);
        float repeat = (float) (TidebreakerWarlordEntity.RAM_LENGTH / 2.0);
        float scroll = age * 0.12F;
        AbyssFx.quad(p, AbyssFx.glow(b, LANE), end.subtract(side), end.add(side), start.add(side), start.subtract(side), 0, scroll, 1, scroll + repeat, k);
    }

    private static void geyser(PoseStack.Pose p, MultiBufferSource b, Vec3 c, Vec3 cam, float t) {
        float rise = Math.min(1, t / 3F), fade = Mth.clamp((12 - t) / 5F, 0, 1);
        double h = 2.6 * rise;
        AbyssFx.wallRing(p, AbyssFx.glow(b, WAVE), c, 0.55, h, 0.1, 16, 3, fade);
        AbyssFx.wallRing(p, AbyssFx.glow(b, WAVE), c, 0.9, h * 0.45, 0.35, 16, 4, fade * 0.7F);
        Vec3 top = c.add(0, h, 0);
        AbyssFx.billboard(p, AbyssFx.glow(b, FOAM), top, cam.subtract(top), 0.6 + 0.2 * rise, t * 0.2, fade);
    }

    private static void disc(PoseStack.Pose p, MultiBufferSource b, ResourceLocation tex, Vec3 center, Vec3 axis, double radius, float alpha) {
        Vec3 right = axis.cross(new Vec3(0, 1, 0)).normalize().scale(radius);
        Vec3 up = right.normalize().cross(axis).normalize().scale(radius);
        AbyssFx.quad(p, AbyssFx.glow(b, tex), center.subtract(right).subtract(up), center.add(right).subtract(up), center.add(right).add(up), center.subtract(right).add(up), 0, 0, 1, 1, alpha);
    }

    // 模型、贴图、动画资源路径
    private static final class Model extends GeoModel<TidebreakerWarlordEntity> {
        @Override public ResourceLocation getModelResource(TidebreakerWarlordEntity entity) { return GEO; }
        @Override public ResourceLocation getTextureResource(TidebreakerWarlordEntity entity) { return TEXTURE; }
        @Override public ResourceLocation getAnimationResource(TidebreakerWarlordEntity entity) { return ANIMATION; }
    }
}
