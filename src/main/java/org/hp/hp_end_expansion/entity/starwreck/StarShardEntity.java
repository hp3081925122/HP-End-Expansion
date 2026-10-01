package org.hp.hp_end_expansion.entity.starwreck;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.StarwreckEntities;

/**
 * 圣星抛射：负星者从圣星上掰下的一块燃烧碎星，沿抛物线飞向目标脚下，{@link #FLIGHT} 拍后正好落在烙印中心。
 * 轨迹在出手时算死，不受方块碰撞影响（和原设计「1.5 秒后落下」的可躲窗口一致）。落地伤害与小陨星相同。
 */
public final class StarShardEntity extends Entity {
    public static final int FLIGHT = 30;
    public static final float RADIUS = FallingStarEntity.SMALL_RADIUS;
    public static final float DAMAGE = FallingStarEntity.SMALL_DAMAGE;
    private static final double GRAVITY = 0.06;
    @Nullable private LivingEntity owner;
    private Vec3 target = Vec3.ZERO;

    public StarShardEntity(EntityType<? extends StarShardEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void throwAt(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 to) {
        StarShardEntity shard = StarwreckEntities.STAR_SHARD.get().create(level);
        if (shard == null) return;
        shard.owner = owner;
        shard.target = to;
        // 离散积分 y_T = y_0 + T*vy - g*T(T-1)/2，反解出手速度，保证第 FLIGHT 拍落在 to
        double t = FLIGHT;
        Vec3 d = to.subtract(from);
        shard.setDeltaMovement(d.x / t, (d.y + GRAVITY * t * (t - 1) / 2) / t, d.z / t);
        shard.moveTo(from.x, from.y, from.z, 0, 0);
        level.addFreshEntity(shard);
        BearerVfxEntity.spawn(level, BearerVfxEntity.BRAND, to, 0, 1, FLIGHT);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        setPos(position().add(v));
        setDeltaMovement(v.x, v.y - GRAVITY, v.z);
        if (level().isClientSide) {
            level().addParticle(ModParticles.BEARER_FLAME.get(), getX(), getY(), getZ(), -v.x * 0.1, 0.02, -v.z * 0.1);
            if (tickCount % 2 == 0) level().addParticle(ModParticles.BEARER_SHARD.get(), getX(), getY(), getZ(), -v.x * 0.2, 0.05, -v.z * 0.2);
            return;
        }
        if (tickCount >= FLIGHT) land();
    }

    private void land() {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 at = target;
        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RADIUS, 2, RADIUS),
            e -> e.isAlive() && !StarBearerEntity.isKin(e))) {
            if (living.distanceToSqr(at.x, living.getY(), at.z) > RADIUS * RADIUS) continue;
            if (living.hurt(owner != null ? damageSources().mobProjectile(this, owner) : damageSources().magic(), DAMAGE))
                living.knockback(0.5, at.x - living.getX(), at.z - living.getZ());
        }
        BearerVfxEntity.spawn(server, BearerVfxEntity.SLAM, at, 0, RADIUS / BearerVfxEntity.RING_TO * 1.1F, 0);
        level().playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.3F, 0.7F);
        level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6F, 1.4F);
        discard();
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 160 * 160; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
