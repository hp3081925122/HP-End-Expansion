package org.hp.hp_end_expansion.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.hp.hp_end_expansion.registry.ModEntities;
import org.hp.hp_end_expansion.registry.ModParticles;

public final class RiftVfxEntity extends Entity {
    // 特效种类：镰刃弧光与裂隙门
    public static final int KIND_SLASH = 0;
    public static final int KIND_PORTAL = 1;

    // 同步字段：种类、寿命、弧面滚转角、整体缩放
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(RiftVfxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(RiftVfxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(RiftVfxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(RiftVfxEntity.class, EntityDataSerializers.FLOAT);

    public RiftVfxEntity(EntityType<? extends RiftVfxEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    // 在指定位置生成一个特效实体
    public static RiftVfxEntity spawn(Level level, int kind, double x, double y, double z, float yaw, float roll, float scale, int life) {
        RiftVfxEntity vfx = new RiftVfxEntity(ModEntities.RIFT_VFX.get(), level);
        vfx.moveTo(x, y, z, yaw, 0.0F);
        vfx.entityData.set(KIND, kind);
        vfx.entityData.set(ROLL, roll);
        vfx.entityData.set(SCALE, scale);
        vfx.entityData.set(LIFE, life);
        level.addFreshEntity(vfx);
        return vfx;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 默认值为短寿命弧光
        builder.define(KIND, KIND_SLASH);
        builder.define(LIFE, 8);
        builder.define(ROLL, 0.0F);
        builder.define(SCALE, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        // 客户端：裂隙门张开期间向中心吸入少量火花作为辅助
        if (this.level().isClientSide()) {
            if (this.getKind() == KIND_PORTAL && this.tickCount % 2 == 0 && this.tickCount < this.getLife() - 4) {
                RandomSource random = this.random;
                float scale = this.getScale();
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 0.9D * scale;
                double ox = Math.cos(angle) * radius;
                double oy = (random.nextDouble() - 0.5D) * 2.2D * scale;
                double oz = Math.sin(angle) * radius;
                this.level().addParticle(ModParticles.RIFT_SPARK.get(),
                    this.getX() + ox, this.getY() + 1.3D * scale + oy, this.getZ() + oz,
                    -ox * 0.12D, -oy * 0.12D, -oz * 0.12D);
            }
            return;
        }
        // 服务端：寿命结束后移除
        if (this.tickCount >= this.getLife()) {
            this.discard();
        }
    }

    public int getKind() {
        return this.entityData.get(KIND);
    }

    public int getLife() {
        return this.entityData.get(LIFE);
    }

    public float getRoll() {
        return this.entityData.get(ROLL);
    }

    public float getScale() {
        return this.entityData.get(SCALE);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
