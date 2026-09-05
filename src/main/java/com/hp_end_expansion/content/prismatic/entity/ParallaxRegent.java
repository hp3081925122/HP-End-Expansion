package com.hp_end_expansion.content.prismatic.entity;

import com.hp_end_expansion.HpEndExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

// 万相冕主保存阶段和巢点，离巢返航但不会借失去目标恢复生命。
public final class ParallaxRegent extends PrismaticMonster {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ParallaxRegent.class, EntityDataSerializers.INT);
    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private BlockPos nest;
    private int lostTargetTicks;
    private boolean returning;
    private int pendingBeams;

    public ParallaxRegent(EntityType<? extends ParallaxRegent> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 120;
    }

    @Override public String speciesId() { return "parallax_regent"; }
    public int phase() { return entityData.get(PHASE); }
    public void setNest(BlockPos position) { nest = position.immutable(); }
    public BlockPos nest() { return nest == null ? blockPosition() : nest; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(PHASE, 1); }

    // 阶段一单线，阶段二双线，阶段三三线；每条线都完整重复前摇和收招。
    @Override
    protected Attack chooseAttack(LivingEntity target) {
        if (pendingBeams > 0) { pendingBeams--; return Attack.BEAM; }
        if (phase() >= 2 && attackSequence % 4 == 3) { return Attack.PULSE; }
        if (distanceToSqr(target) < 30.0 && attackSequence % 3 == 0) { return Attack.STRIKE; }
        pendingBeams = phase() - 1;
        return Attack.BEAM;
    }

    @Override protected boolean usesSpecial(Attack attack) { return attack != Attack.STRIKE; }
    @Override protected double preferredRange() { return phase() >= 2 && attackSequence % 4 == 3 && pendingBeams == 0 ? 5.0 : 16.0; }
    @Override public double attackRadius() { return currentAttack() == Attack.STRIKE ? 5.0 : 7.0; }
    @Override protected int idleCooldown() { return pendingBeams > 0 ? 10 : phase() == 3 ? 18 : 30; }

    // 自然生成和祭台生成均在最终放置坐标记录巢点。
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        setNest(blockPosition());
        return result;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !returning && target.distanceToSqr(nest().getCenter()) <= 42.0 * 42.0 && super.canAttack(target);
    }

    // 半血变化只向更高阶段推进，返巢期间关闭索敌且不改玩家视角或地形。
    @Override
    public void tick() {
        if (!level().isClientSide && isAlive()) {
            if (nest == null) { setNest(blockPosition()); }
            int nextPhase = getHealth() <= getMaxHealth() * 0.25F ? 3 : getHealth() <= getMaxHealth() * 0.6F ? 2 : 1;
            if (nextPhase > phase()) {
                entityData.set(PHASE, nextPhase);
                HpEndExpansion.LOGGER.info("Prismatic boss phase changed: uuid={}, phase={}, health={}", getUUID(), nextPhase, getHealth());
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.7, getZ(), 30, 1.8, 0.6, 1.8, 0.03);
                }
            }
            if (getTarget() == null || !hasLineOfSight(getTarget())) { lostTargetTicks++; } else { lostTargetTicks = 0; }
            if (distanceToSqr(nest.getCenter()) > 38.0 * 38.0 || lostTargetTicks > 600
                    || getTarget() != null && getTarget().distanceToSqr(nest.getCenter()) > 42.0 * 42.0) {
                returning = true;
            }
            if (returning) {
                setTarget(null);
                if (currentAttack() != Attack.NONE) { cancelAttack(); }
                pendingBeams = 0;
                attackCooldown = 40;
            }
        }
        super.tick();
        if (!level().isClientSide) {
            bossEvent.setProgress(Math.max(0, getHealth() / getMaxHealth()));
            bossEvent.setName(getDisplayName());
            if (returning && isAlive()) {
                if (distanceToSqr(nest().getCenter()) < 9) {
                    returning = false;
                    lostTargetTicks = 0;
                } else if (tickCount % 10 == 0) {
                    getNavigation().moveTo(nest().getX() + 0.5, nest().getY(), nest().getZ() + 0.5, 1.1);
                }
            }
        }
    }

    // 只给正在追踪该实体的玩家添加血条。
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossEvent.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossEvent.removePlayer(player); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("PrismaticPhase", phase());
        tag.putLong("PrismaticNest", nest().asLong());
        tag.putBoolean("PrismaticReturning", returning);
        tag.putInt("PrismaticLostTargetTicks", lostTargetTicks);
        tag.putInt("PrismaticPendingBeams", pendingBeams);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PHASE, Math.clamp(tag.getInt("PrismaticPhase"), 1, 3));
        nest = tag.contains("PrismaticNest") ? BlockPos.of(tag.getLong("PrismaticNest")) : blockPosition();
        returning = tag.getBoolean("PrismaticReturning");
        lostTargetTicks = Math.max(0, tag.getInt("PrismaticLostTargetTicks"));
        pendingBeams = Math.clamp(tag.getInt("PrismaticPendingBeams"), 0, 2);
    }
}
