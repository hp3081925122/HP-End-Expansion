package org.hp.hp_end_expansion.entity.starwreck;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.StarwreckEntities;

/**
 * 陨星落地的画面载体：只负责热浪、焦痕和碎石，不参与伤害。朝向就是陨星的飞行方向。
 * 伤害和击退仍在 {@link FallingStarEntity#impact} 里结算。
 */
public final class StarImpactEntity extends Entity {
    public static final int LIFE = 100;
    public static final int RING_TICKS = 14;
    /** 客户端缓存：焦痕每一格地表相对落点的高度，渲染器第一次用时填。 */
    public float[] ground;

    public StarImpactEntity(EntityType<? extends StarImpactEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void spawn(ServerLevel level, Vec3 at, Vec3 flight) {
        StarImpactEntity impact = StarwreckEntities.STAR_IMPACT.get().create(level);
        if (impact == null) return;
        float yaw = (float) (Mth.atan2(-flight.x, flight.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(flight.y, flight.horizontalDistance()) * Mth.RAD_TO_DEG);
        impact.moveTo(at.x, at.y, at.z, yaw, pitch);
        level.addFreshEntity(impact);
    }

    /** 热浪前沿半径，先快后慢地推到伤害半径。 */
    public static float ringRadius(float age) {
        float t = Mth.clamp(age / RING_TICKS, 0, 1);
        return (FallingStarEntity.IMPACT_RADIUS + 0.3F) * (1 - (1 - t) * (1 - t) * (1 - t));
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientEffects();
            return;
        }
        if (tickCount >= LIFE) discard();
    }

    private void clientEffects() {
        Level level = level();
        RandomSource r = random;
        if (tickCount == 1) {
            // 碎石贴地滚开，火星顺着热浪往外甩，全都压在 2 格以内
            for (int i = 0; i < 26; i++) {
                double a = r.nextDouble() * Math.PI * 2, s = 0.18 + r.nextDouble() * 0.26;
                level.addParticle(ModParticles.STAR_DEBRIS.get(), getX(), getY() + 0.3, getZ(),
                    Math.cos(a) * s, 0.06 + r.nextDouble() * 0.12, Math.sin(a) * s);
            }
            for (int i = 0; i < 40; i++) {
                double a = r.nextDouble() * Math.PI * 2, s = 0.2 + r.nextDouble() * 0.3;
                level.addParticle(ModParticles.STAR_EMBER.get(), getX(), getY() + 0.15, getZ(),
                    Math.cos(a) * s, 0.02 + r.nextDouble() * 0.06, Math.sin(a) * s);
            }
        }
        if (tickCount < RING_TICKS) {
            float radius = ringRadius(tickCount);
            for (int i = 0; i < 5; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                level.addParticle(ModParticles.STAR_EMBER.get(), getX() + Math.cos(a) * radius, getY() + 0.1, getZ() + Math.sin(a) * radius,
                    Math.cos(a) * 0.05, 0.01, Math.sin(a) * 0.05);
            }
        }
        if (tickCount < LIFE - 30 && r.nextFloat() < 0.7F) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * 2.6;
            level.addParticle(ModParticles.STAR_EMBER.get(), getX() + Math.cos(a) * d, getY() + 0.05, getZ() + Math.sin(a) * d, 0, 0, 0);
        }
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
