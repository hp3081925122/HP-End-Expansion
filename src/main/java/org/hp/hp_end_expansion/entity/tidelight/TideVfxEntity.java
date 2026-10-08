package org.hp.hp_end_expansion.entity.tidelight;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModEntities;

/**
 * 骸潮炮的地面特效，自带伤害判定。
 * GEYSER：0-9 tick 地面冒水预警，第 10 tick 喷出 3 格高的水柱，半径 1 格内 4 伤害并抛起，第 30 tick 消失。
 * WAVE：半径 0.5 格起在 10 tick 内扩到 5 格的潮汐水墙，扫到的生物各受一次 6 伤害、击退 1.2。
 */
public final class TideVfxEntity extends Entity {
    public static final byte GEYSER = 0, WAVE = 1, SPLASH = 2;
    // SPLASH：钳子砸地的落点水花，只有表现没有伤害。一圈贴地扩散的小浪 + 一簇冲起的水柱，scale 控制大小
    public static final int SPLASH_LIFE = 12;
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(TideVfxEntity.class, EntityDataSerializers.FLOAT);
    public static final int GEYSER_ERUPT = 10, GEYSER_LIFE = 30;
    public static final float GEYSER_HEIGHT = 3, GEYSER_RADIUS = 1;
    public static final int WAVE_GROW = 10, WAVE_LIFE = 16;
    public static final float WAVE_RADIUS = 5, WAVE_HEIGHT = 1.2F;
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(TideVfxEntity.class, EntityDataSerializers.BYTE);
    @Nullable private LivingEntity owner;
    private final Set<Integer> hit = new HashSet<>();

    public TideVfxEntity(EntityType<? extends TideVfxEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void spawnGeyser(ServerLevel level, LivingEntity owner, Vec3 at) { spawn(level, owner, at, GEYSER); }
    public static void spawnWave(ServerLevel level, LivingEntity owner, Vec3 at) { spawn(level, owner, at, WAVE); }

    private static void spawn(ServerLevel level, LivingEntity owner, Vec3 at, byte kind) {
        TideVfxEntity vfx = ModEntities.TIDE_VFX.get().create(level);
        if (vfx == null) return;
        vfx.owner = owner;
        vfx.entityData.set(KIND, kind);
        vfx.moveTo(at.x, at.y, at.z, 0, 0);
        level.addFreshEntity(vfx);
    }

    public static void spawnSplash(ServerLevel level, Vec3 at, float scale) {
        TideVfxEntity vfx = ModEntities.TIDE_VFX.get().create(level);
        if (vfx == null) return;
        vfx.entityData.set(KIND, SPLASH);
        vfx.entityData.set(SCALE, scale);
        vfx.moveTo(at.x, at.y, at.z, 0, 0);
        level.addFreshEntity(vfx);
    }

    public byte getKind() { return entityData.get(KIND); }
    public float getScale() { return entityData.get(SCALE); }

    /** 水墙当前半径。 */
    public static float waveRadius(float age) { return 0.5F + (WAVE_RADIUS - 0.5F) * Math.min(1, age / WAVE_GROW); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, GEYSER);
        builder.define(SCALE, 1F);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        byte kind = getKind();
        if (kind == GEYSER) tickGeyser(server);
        else if (kind == WAVE) tickWave(server);
        else if (tickCount >= SPLASH_LIFE) discard();
    }

    private void tickGeyser(ServerLevel server) {
        if (tickCount < GEYSER_ERUPT) {
            server.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.1, getZ(), 4, 0.4, 0.02, 0.4, 0.05);
            if (tickCount % 3 == 0) server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 0.1, getZ(), 2, 0.3, 0, 0.3, 0.02);
        } else if (tickCount == GEYSER_ERUPT) {
            AABB box = new AABB(getX() - GEYSER_RADIUS, getY(), getZ() - GEYSER_RADIUS, getX() + GEYSER_RADIUS, getY() + GEYSER_HEIGHT, getZ() + GEYSER_RADIUS);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, TideRemnantHermitCrabEntity::canHit)) {
                e.invulnerableTime = 0;
                if (e.hurt(source(), 4)) {
                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.5, 0, 0.5).add(0, 0.8, 0));
                    e.hurtMarked = true;
                }
            }
            server.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 30, 0.4, 1.0, 0.4, 0.3);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 0.9F, 1.1F + random.nextFloat() * 0.3F);
        } else if (tickCount < GEYSER_LIFE - 6 && tickCount % 2 == 0) {
            server.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY() + GEYSER_HEIGHT, getZ(), 3, 0.5, 0.1, 0.5, 0);
        }
        if (tickCount >= GEYSER_LIFE) discard();
    }

    private void tickWave(ServerLevel server) {
        float r = waveRadius(tickCount);
        if (tickCount <= WAVE_GROW + 1) {
            AABB box = getBoundingBox().inflate(r + 1, 2, r + 1);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, TideRemnantHermitCrabEntity::canHit)) {
                if (hit.contains(e.getId())) continue;
                Vec3 d = e.position().subtract(position()).multiply(1, 0, 1);
                double dist = d.length();
                if (dist > r + e.getBbWidth() * 0.5 || Math.abs(e.getY() - getY()) > 2) continue;
                hit.add(e.getId());
                if (e.hurt(source(), 6)) {
                    Vec3 away = dist < 1.0E-3 ? new Vec3(0, 0, 1) : d.scale(1 / dist);
                    e.knockback(1.2, -away.x, -away.z);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.3, 0));
                    e.hurtMarked = true;
                }
            }
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24 + tickCount;
                server.sendParticles(ParticleTypes.SPLASH, getX() + Math.cos(a) * r, getY() + 0.8, getZ() + Math.sin(a) * r, 1, 0.1, 0.2, 0.1, 0.1);
            }
        }
        if (tickCount >= WAVE_LIFE) discard();
    }

    private DamageSource source() {
        return owner != null && owner.isAlive() ? damageSources().mobAttack(owner) : damageSources().magic();
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 96 * 96; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); }

    @Override public boolean isOnFire() { return false; }

    @Override public boolean hurt(DamageSource source, float amount) { return false; }

    @Override public void push(Entity entity) {}
}
