package org.hp.hp_end_expansion.entity.tidelight;

import javax.annotation.Nullable;
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
import org.joml.Vector3f;

/**
 * 深渊守望者的地面/空中特效，全部由 AbyssVfxRenderer 用自绘贴图画出，不堆原版粒子。
 * SPIKE：晶核从 START 沿弧线飞到落点（0-8 tick），落点亮起法阵预警（8-24），第 24 tick 晶刺丛破土并结算伤害，之后碎裂淡出。
 * VORTEX：地面漩涡，半径 SIZE，跟随渊潮漩涡的盘旋段。
 * SHOCKWAVE：贴地扩散的冲击波环 + 立起的环墙，最大半径 SIZE，只有表现。
 * BURST：空中一闪的星芒 + 小环，SIZE 为大小，只有表现。
 */
public final class AbyssVfxEntity extends Entity {
    public static final byte SPIKE = 0, VORTEX = 1, SHOCKWAVE = 2, BURST = 3;
    public static final int SPIKE_FLIGHT = 8, SPIKE_ERUPT = 24, SPIKE_LIFE = 44;
    public static final int VORTEX_LIFE = 62, SHOCK_LIFE = 14, BURST_LIFE = 8;
    public static final double SPIKE_RADIUS = 1.4;
    private static final float SPIKE_DAMAGE = 6;

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(AbyssVfxEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(AbyssVfxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> START = SynchedEntityData.defineId(AbyssVfxEntity.class, EntityDataSerializers.VECTOR3);
    @Nullable private LivingEntity owner;

    public AbyssVfxEntity(EntityType<? extends AbyssVfxEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void spawn(ServerLevel level, @Nullable LivingEntity owner, byte kind, Vec3 at, float size, Vec3 start) {
        AbyssVfxEntity e = ModEntities.ABYSS_VFX.get().create(level);
        if (e == null) return;
        e.owner = owner;
        e.entityData.set(KIND, kind);
        e.entityData.set(SIZE, size);
        e.entityData.set(START, new Vector3f((float) start.x, (float) start.y, (float) start.z));
        e.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
        level.addFreshEntity(e);
    }

    public byte getKind() { return entityData.get(KIND); }
    public float getSize() { return entityData.get(SIZE); }
    public Vec3 getStart() { Vector3f v = entityData.get(START); return new Vec3(v.x, v.y, v.z); }

    public int life() {
        return switch (getKind()) { case SPIKE -> SPIKE_LIFE; case VORTEX -> VORTEX_LIFE; case SHOCKWAVE -> SHOCK_LIFE; default -> BURST_LIFE; };
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, SPIKE);
        builder.define(SIZE, 1F);
        builder.define(START, new Vector3f());
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (getKind() == SPIKE) {
            if (tickCount == SPIKE_FLIGHT) server.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.HOSTILE, 1.2F, 0.6F);
            if (tickCount == SPIKE_ERUPT) erupt(server);
        }
        if (tickCount >= life()) discard();
    }

    private void erupt(ServerLevel server) {
        AABB box = new AABB(getX() - SPIKE_RADIUS, getY() - 1, getZ() - SPIKE_RADIUS, getX() + SPIKE_RADIUS, getY() + 3, getZ() + SPIKE_RADIUS);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, box, AbyssWatcherEntity::canHit)) {
            if (v.position().subtract(position()).horizontalDistance() > SPIKE_RADIUS + v.getBbWidth() * 0.5) continue;
            v.invulnerableTime = 0;
            if (v.hurt(source(), SPIKE_DAMAGE)) {
                v.setDeltaMovement(v.getDeltaMovement().multiply(0.4, 0, 0.4).add(0, 0.75, 0));
                v.hurtMarked = true;
            }
        }
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.8F, 0.7F + random.nextFloat() * 0.2F);
    }

    private DamageSource source() {
        return owner != null && owner.isAlive() ? damageSources().mobAttack(owner) : damageSources().magic();
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public void push(Entity entity) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
