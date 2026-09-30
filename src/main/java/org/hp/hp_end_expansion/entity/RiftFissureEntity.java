package org.hp.hp_end_expansion.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModEntities;
import org.hp.hp_end_expansion.registry.ModParticles;

public final class RiftFissureEntity extends Entity {
    // 地裂参数：蔓延时长、晶刺爆发时刻、总寿命
    public static final int SPREAD_TICKS = 10;
    public static final int ERUPT_TICK = 16;
    public static final int LIFE_TICKS = 34;
    private static final float DAMAGE = 9.0F;

    // 同步字段：地裂长度与宽度倍率
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(RiftFissureEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(RiftFissureEntity.class, EntityDataSerializers.FLOAT);

    private UUID ownerId;
    // 可被 Boss 覆盖的伤害与击飞
    private float damage = DAMAGE;
    private double launch = 0.8D;

    public RiftFissureEntity(EntityType<? extends RiftFissureEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    // 沿朝向生成一道地裂，起点为 origin
    public static RiftFissureEntity spawn(LivingEntity owner, Vec3 origin, float yaw, float length, float width) {
        RiftFissureEntity fissure = new RiftFissureEntity(ModEntities.RIFT_FISSURE.get(), owner.level());
        fissure.moveTo(origin.x, origin.y, origin.z, yaw, 0.0F);
        fissure.entityData.set(LENGTH, length);
        fissure.entityData.set(WIDTH, width);
        fissure.ownerId = owner.getUUID();
        owner.level().addFreshEntity(fissure);
        return fissure;
    }

    // 设置地裂伤害与向上击飞力度
    public RiftFissureEntity withDamage(float value, double upward) {
        this.damage = value;
        this.launch = upward;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LENGTH, 8.0F);
        builder.define(WIDTH, 1.0F);
    }

    public float getLength() {
        return this.entityData.get(LENGTH);
    }

    public float getWidth() {
        return this.entityData.get(WIDTH);
    }

    // 地裂前进方向（水平）
    public Vec3 getForward() {
        float rad = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) this.level();
        Vec3 forward = this.getForward();
        float length = this.getLength();
        // 蔓延阶段：裂口前端扬起少量方块碎屑
        if (this.tickCount <= SPREAD_TICKS) {
            double reach = length * this.tickCount / SPREAD_TICKS;
            Vec3 tip = this.position().add(forward.scale(reach));
            serverLevel.sendParticles(ModParticles.RIFT_SHARD.get(), tip.x, tip.y + 0.1D, tip.z, 2, 0.2D * this.getWidth(), 0.05D, 0.2D * this.getWidth(), 0.08D);
        }
        // 爆发时刻：沿线结算伤害与击飞
        if (this.tickCount == ERUPT_TICK) {
            this.erupt(serverLevel, forward, length);
        }
        if (this.tickCount >= LIFE_TICKS) {
            this.discard();
        }
    }

    // 晶刺爆发：沿线胶囊范围命中
    private void erupt(ServerLevel serverLevel, Vec3 forward, float length) {
        Entity owner = null;
        if (this.ownerId != null) {
            owner = serverLevel.getEntity(this.ownerId);
        }
        Vec3 start = this.position();
        Vec3 end = start.add(forward.scale(length));
        double radius = 1.5D * this.getWidth();
        AABB area = new AABB(start, end).inflate(radius, 2.0D * this.getWidth(), radius);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            // 跳过施放者与同类
            if (target instanceof RiftMantisEntity || target instanceof RiftMatriarchEntity || !target.isAlive()) {
                continue;
            }
            // 计算目标到裂线的水平距离
            Vec3 rel = target.position().subtract(start);
            double along = Mth.clamp(rel.dot(forward), 0.0D, length);
            Vec3 closest = start.add(forward.scale(along));
            double dx = target.getX() - closest.x;
            double dz = target.getZ() - closest.z;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            boolean hurt;
            if (owner instanceof LivingEntity livingOwner) {
                hurt = target.hurt(this.damageSources().indirectMagic(this, livingOwner), this.damage);
            } else {
                hurt = target.hurt(this.damageSources().magic(), this.damage);
            }
            if (hurt) {
                target.push(0.0D, this.launch, 0.0D);
                target.hurtMarked = true;
            }
        }
        // 爆发碎片与音效
        for (int i = 0; i <= (int) length; i += 2) {
            Vec3 p = start.add(forward.scale(i));
            serverLevel.sendParticles(ModParticles.RIFT_SHARD.get(), p.x, p.y + 0.6D * this.getWidth(), p.z, 3, 0.3D * this.getWidth(), 0.4D * this.getWidth(), 0.3D * this.getWidth(), 0.18D);
        }
        BlockPos mid = BlockPos.containing(start.add(forward.scale(length * 0.5D)));
        serverLevel.playSound(null, mid, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.6F, 0.6F);
        serverLevel.playSound(null, mid, SoundEvents.SHULKER_BULLET_HIT, SoundSource.HOSTILE, 1.2F, 0.7F);
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
    public AABB getBoundingBoxForCulling() {
        // 渲染裁剪范围覆盖整条地裂
        Vec3 end = this.position().add(this.getForward().scale(this.getLength()));
        return new AABB(this.position(), end).inflate(1.5D * this.getWidth(), 3.0D * this.getWidth(), 1.5D * this.getWidth());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
