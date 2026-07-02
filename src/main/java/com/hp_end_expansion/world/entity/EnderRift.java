package com.hp_end_expansion.world.entity;

import com.hp_end_expansion.world.EndShipTeleportation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class EnderRift extends Entity {
    // 裂隙默认存在 30 秒。
    private static final int DEFAULT_LIFE = 600;
    private int life = DEFAULT_LIFE;

    // 创建裂隙实体。
    public EnderRift(EntityType<? extends EnderRift> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    // 裂隙没有需要同步的数据。
    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    // 每刻播放粒子并检测穿过裂隙的实体。
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.spawnClientParticles();
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        this.life--;
        if (this.life <= 0) {
            this.discard();
            return;
        }
        if (this.tickCount % 5 == 0) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 0.5D, this.getZ(), 16, 0.45D, 0.55D, 0.45D, 0.08D);
            for (LivingEntity livingEntity : serverLevel.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.8D), entity -> entity.isAlive() && !entity.isOnPortalCooldown())) {
                if (EndShipTeleportation.teleportToNearestEndShip(serverLevel, livingEntity)) {
                    this.discard();
                    return;
                }
            }
        }
    }

    // 保存裂隙剩余存在时间。
    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putInt("Life", this.life);
    }

    // 读取裂隙剩余存在时间。
    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.life = compound.getInt("Life");
        if (this.life <= 0) {
            this.life = DEFAULT_LIFE;
        }
    }

    // 裂隙不能被攻击伤害。
    @Override
    public boolean isAttackable() {
        return false;
    }

    // 客户端粒子让裂隙在占位渲染阶段可见。
    private void spawnClientParticles() {
        for (int i = 0; i < 2; i++) {
            this.level().addParticle(ParticleTypes.REVERSE_PORTAL, this.getRandomX(0.7D), this.getY() + this.random.nextDouble() * 1.2D, this.getRandomZ(0.7D), 0.0D, 0.02D, 0.0D);
        }
        if (this.random.nextInt(4) == 0) {
            this.level().addParticle(ParticleTypes.END_ROD, this.getRandomX(0.45D), this.getY() + this.random.nextDouble() * 1.0D, this.getRandomZ(0.45D), 0.0D, 0.02D, 0.0D);
        }
    }
}
