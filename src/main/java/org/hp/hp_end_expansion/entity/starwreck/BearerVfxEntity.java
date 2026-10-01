package org.hp.hp_end_expansion.entity.starwreck;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.StarwreckEntities;

/**
 * 负星者专属特效的画面载体，不参与伤害、不存档。和星雨那套橙红余烬区分，走白金圣火路线：
 * <ul>
 *   <li>{@link #SLAM} 圣砸：地面烙下旋转的圣印，一圈竖直光墙沿伤害环推出去，中心一道光柱，地表碎块跟着光墙掀起；</li>
 *   <li>{@link #FLARE} 星焰：从圣星向背后喷出的白金火舌锥，朝向就是实体 yaw；</li>
 *   <li>{@link #SHATTER} 碎星：一团白闪加放射光刺和一圈横向光环，碎晶四溅；</li>
 *   <li>{@link #BRAND} 抛星落点：目标脚下的小圣印，越临近落地转得越快、越亮。</li>
 *   <li>{@link #ARC} 负星横扫：冷色负光路线，靛紫拖尾、青白刃口的斜劈弧光，外沿一道弧形光幕，虚蚀微粒顺挥砍方向甩出。</li>
 * </ul>
 * 光墙半径与 {@link StarBearerEntity} 里冲击环的伤害判定共用 {@link #ringRadius}。
 */
public final class BearerVfxEntity extends Entity {
    public static final int SLAM = 0, FLARE = 1, SHATTER = 2, BRAND = 3, ARC = 4;
    public static final int RING_TICKS = 12;
    public static final float RING_FROM = 1, RING_TO = 6;
    /** 负星横扫：前锋 4 拍内从右后 -130° 匀速扫到左前 60°（0 是正前，负数在右手边），半径 4.2；右高左低斜劈。和 sweep 动画里右拳的轨迹逐拍对过。 */
    public static final int ARC_SWING = 4;
    public static final float ARC_FROM = -130, ARC_TO = 60, ARC_R = 4.2F, ARC_Y_RIGHT = 2.1F, ARC_Y_LEFT = 1.45F;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(BearerVfxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(BearerVfxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> WARN = SynchedEntityData.defineId(BearerVfxEntity.class, EntityDataSerializers.INT);

    public BearerVfxEntity(EntityType<? extends BearerVfxEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void spawn(ServerLevel level, int kind, Vec3 at, float yaw, float scale, int warn) {
        BearerVfxEntity vfx = StarwreckEntities.BEARER_VFX.get().create(level);
        if (vfx == null) return;
        vfx.entityData.set(KIND, kind);
        vfx.entityData.set(SCALE, scale);
        vfx.entityData.set(WARN, warn);
        vfx.moveTo(at.x, at.y, at.z, yaw, 0);
        level.addFreshEntity(vfx);
    }

    /** 光墙（也是伤害环）前沿半径，均速推进。 */
    public static float ringRadius(float age) {
        return Mth.lerp(Mth.clamp(age / RING_TICKS, 0, 1), RING_FROM, RING_TO);
    }

    /** 横扫前锋角度（度）。负星者的伤害按前锋扫过的角度区间结算，和画面同一个公式。 */
    public static float arcLead(float age) {
        return Mth.lerp(Mth.clamp(age / ARC_SWING, 0, 1), ARC_FROM, ARC_TO);
    }

    public static float arcY(float angle) {
        return Mth.lerp((angle - ARC_FROM) / (ARC_TO - ARC_FROM), ARC_Y_RIGHT, ARC_Y_LEFT);
    }

    /** 以 yaw 为正前方（+z）、右手边为 -x 的局部坐标转成世界方向；渲染器里 YP.rotationDegrees(-yaw) 的同一个变换。 */
    public static Vec3 toWorld(float yaw, double lx, double ly, double lz) {
        float r = yaw * Mth.DEG_TO_RAD;
        double c = Mth.cos(r), s = Mth.sin(r);
        return new Vec3(lx * c - lz * s, ly, lx * s + lz * c);
    }

    public int kind() { return entityData.get(KIND); }
    public float scale() { return entityData.get(SCALE); }
    public int warn() { return entityData.get(WARN); }

    public int life() {
        return switch (kind()) {
            case FLARE -> 12;
            case SHATTER -> 16;
            case BRAND -> warn() + 10;
            case ARC -> 14;
            default -> 28;
        };
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, SLAM);
        builder.define(SCALE, 1F);
        builder.define(WARN, 0);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) clientParticles();
        else if (tickCount >= life()) discard();
    }

    private void clientParticles() {
        Level level = level();
        float k = scale();
        switch (kind()) {
            case SLAM -> {
                BlockState ground = level.getBlockState(BlockPos.containing(getX(), getY() - 0.2, getZ()));
                BlockParticleOption crumbs = new BlockParticleOption(ParticleTypes.BLOCK, ground);
                if (tickCount == 1) {
                    for (int i = 0; i < 26 * k; i++) {
                        double a = random.nextDouble() * Math.PI * 2, s = (0.12 + random.nextDouble() * 0.22) * k;
                        level.addParticle(ModParticles.BEARER_SHARD.get(), getX(), getY() + 0.3, getZ(),
                            Math.cos(a) * s, 0.25 + random.nextDouble() * 0.25, Math.sin(a) * s);
                    }
                    if (!ground.isAir()) for (int i = 0; i < 30 * k; i++) {
                        double a = random.nextDouble() * Math.PI * 2, s = 0.1 + random.nextDouble() * 0.25;
                        level.addParticle(crumbs, getX(), getY() + 0.1, getZ(), Math.cos(a) * s, 0.3 + random.nextDouble() * 0.3, Math.sin(a) * s);
                    }
                }
                if (tickCount <= RING_TICKS) {
                    // 光墙前沿：地皮被掀起、圣焰贴着墙脚往上窜
                    float radius = ringRadius(tickCount) * k;
                    int n = Math.max(2, (int) (radius * 2.2F));
                    for (int i = 0; i < n; i++) {
                        double a = random.nextDouble() * Math.PI * 2;
                        double x = getX() + Math.cos(a) * radius, z = getZ() + Math.sin(a) * radius;
                        if (i % 2 == 0 && !ground.isAir())
                            level.addParticle(crumbs, x, getY() + 0.1, z, Math.cos(a) * 0.08, 0.2 + random.nextDouble() * 0.15, Math.sin(a) * 0.08);
                        else level.addParticle(ModParticles.BEARER_FLAME.get(), x, getY() + 0.05, z, 0, 0.06 + random.nextDouble() * 0.06, 0);
                    }
                }
            }
            case FLARE -> {
                if (tickCount > 9) return;
                Vec3 dir = getViewVector(1);
                for (int i = 0; i < 7; i++) {
                    double spread = 0.5, s = 0.3 + random.nextDouble() * 0.3;
                    Vec3 v = dir.add((random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.6) * spread * 0.5, (random.nextDouble() - 0.5) * spread)
                        .normalize().scale(s);
                    level.addParticle(ModParticles.BEARER_FLAME.get(), getX(), getY(), getZ(), v.x, v.y, v.z);
                }
            }
            case SHATTER -> {
                if (tickCount != 1) return;
                for (int i = 0; i < 46; i++) {
                    Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6 + 0.35, random.nextGaussian()).normalize()
                        .scale(0.25 + random.nextDouble() * 0.35);
                    level.addParticle(ModParticles.BEARER_SHARD.get(), getX(), getY(), getZ(), v.x, v.y, v.z);
                }
                for (int i = 0; i < 24; i++) {
                    Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(0.12 + random.nextDouble() * 0.12);
                    level.addParticle(ModParticles.BEARER_FLAME.get(), getX(), getY(), getZ(), v.x, v.y, v.z);
                }
            }
            case BRAND -> {
                float charge = Math.min(1, tickCount / (float) Math.max(1, warn()));
                if (tickCount < warn() && random.nextFloat() < 0.3F + 0.6F * charge) {
                    double a = random.nextDouble() * Math.PI * 2, r = StarShardEntity.RADIUS * k;
                    level.addParticle(ModParticles.BEARER_FLAME.get(), getX() + Math.cos(a) * r, getY() + 0.05, getZ() + Math.sin(a) * r,
                        0, 0.04 + charge * 0.08, 0);
                }
            }
            case ARC -> {
                // 前锋这一拍扫过的扇区里甩出虚蚀微粒，顺着挥砍方向飞；刃口外沿迸出青白星芒
                int age = tickCount;
                if (age < 1 || age > ARC_SWING + 1) return;
                float a0 = arcLead(age - 1), a1 = arcLead(age);
                for (int i = 0; i < 8; i++) {
                    boolean glint = i >= 6;
                    float a = Mth.lerp(random.nextFloat(), a0, a1) * Mth.DEG_TO_RAD;
                    double r = glint ? ARC_R * (0.9 + random.nextDouble() * 0.15) : 1.6 + random.nextDouble() * (ARC_R - 1.6);
                    double y = arcY(a * Mth.RAD_TO_DEG) + (random.nextDouble() - 0.5) * 0.5;
                    Vec3 p = toWorld(getYRot(), Math.sin(a) * r, y, Math.cos(a) * r);
                    double sp = glint ? 0.12 : 0.18 + random.nextDouble() * 0.12;
                    Vec3 v = toWorld(getYRot(), Math.cos(a) * sp + Math.sin(a) * 0.04, (random.nextDouble() - 0.4) * 0.05, -Math.sin(a) * sp + Math.cos(a) * 0.04);
                    level.addParticle(glint ? ModParticles.BEARER_GLINT.get() : ModParticles.BEARER_VOID.get(),
                        getX() + p.x, getY() + p.y, getZ() + p.z, v.x, v.y, v.z);
                }
            }
            default -> {}
        }
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 160 * 160; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(RING_TO + 1, 6, RING_TO + 1); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
