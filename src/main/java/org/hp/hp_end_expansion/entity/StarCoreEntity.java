package org.hp.hp_end_expansion.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

// 吞星技能的星核：悬浮不动，击碎可打断星爆
public class StarCoreEntity extends Mob {
    public StarCoreEntity(EntityType<? extends StarCoreEntity> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.xpReward = 0;
    }

    // 星核属性
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 30.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    public void tick() {
        super.tick();
        // 保持悬停
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        // 客户端星光粒子
        if (this.level().isClientSide() && this.random.nextInt(2) == 0) {
            this.level().addParticle(ParticleTypes.END_ROD, this.getRandomX(0.6D), this.getRandomY(), this.getRandomZ(0.6D), 0.0D, 0.02D, 0.0D);
        }
        // 无主星核 20 秒后消失
        if (!this.level().isClientSide() && this.tickCount > 400) {
            this.discard();
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        // 碎裂特效
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.5D, this.getZ(), 30, 0.4D, 0.4D, 0.4D, 0.2D);
        }
        this.playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 2.0F, 0.8F);
    }

    @Override
    protected void tickDeath() {
        // 立即移除
        if (!this.level().isClientSide()) {
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }
}
