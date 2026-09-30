package org.hp.hp_end_expansion.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModEntities;
import org.hp.hp_end_expansion.registry.ModParticles;

public final class RiftBladeEntity extends Projectile {
    // 飞刃存在上限与伤害
    private static final int MAX_LIFE = 40;
    private static final float DAMAGE = 7.0F;
    // 可被 Boss 覆盖的伤害值
    private float damage = DAMAGE;

    public RiftBladeEntity(EntityType<? extends RiftBladeEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
    }

    // 从持有者位置向目标方向发射
    public static RiftBladeEntity launch(LivingEntity owner, Vec3 origin, Vec3 direction, float speed) {
        RiftBladeEntity blade = new RiftBladeEntity(ModEntities.RIFT_BLADE.get(), owner.level());
        blade.setOwner(owner);
        blade.setPos(origin.x, origin.y, origin.z);
        blade.shoot(direction.x, direction.y, direction.z, speed, 0.0F);
        owner.level().addFreshEntity(blade);
        return blade;
    }

    // 设置飞刃伤害
    public RiftBladeEntity withDamage(float value) {
        this.damage = value;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        // 命中检测与飞行位移
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            this.hitTargetOrDeflectSelf(hit);
        }
        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        this.updateRotation();
        // 客户端：尾部拖出少量碎火花
        if (this.level().isClientSide()) {
            if (this.tickCount % 2 == 0) {
                this.level().addParticle(ModParticles.RIFT_SPARK.get(),
                    this.getX() - motion.x * 0.5D, this.getY() + 0.1D, this.getZ() - motion.z * 0.5D,
                    0.0D, 0.01D, 0.0D);
            }
            return;
        }
        // 服务端：超时消散
        if (this.tickCount > MAX_LIFE) {
            this.burst();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        // 命中生物：造成魔法伤害并短暂减速
        if (owner instanceof LivingEntity livingOwner && target instanceof LivingEntity livingTarget) {
            if (livingTarget.hurt(this.damageSources().mobProjectile(this, livingOwner), this.damage)) {
                livingTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), livingOwner);
            }
        }
        this.burst();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.burst();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        // 不命中持有者及其同类
        if (target instanceof RiftMantisEntity || target instanceof RiftMatriarchEntity) {
            return false;
        }
        return super.canHitEntity(target);
    }

    // 碎裂：碎片粒子与音效，随后移除
    private void burst() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.RIFT_SHARD.get(), this.getX(), this.getY(), this.getZ(), 8, 0.15D, 0.15D, 0.15D, 0.12D);
            serverLevel.sendParticles(ModParticles.RIFT_SPARK.get(), this.getX(), this.getY(), this.getZ(), 4, 0.1D, 0.1D, 0.1D, 0.05D);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 0.9F, 1.3F);
        }
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
