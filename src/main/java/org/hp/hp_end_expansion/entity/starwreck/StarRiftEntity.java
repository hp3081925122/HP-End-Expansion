package org.hp.hp_end_expansion.entity.starwreck;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.StarwreckEntities;

/**
 * 一道星雨裂隙。位置在自己那颗陨星的落点斜上方，自身不动。
 * 年龄由服务端同步。0 到 {@link #OPEN_TICKS} 张开，接着 {@link FallingStarEntity#FALL_TICKS} 保持张开并放出陨星，再 {@link #CLOSE_TICKS} 闭合。
 */
public final class StarRiftEntity extends Entity {
    public static final int OPEN_TICKS = 20;
    public static final int CLOSE_TICKS = 16;
    public static final int HEIGHT = 42;
    public static final double OFFSET = 6;
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(StarRiftEntity.class, EntityDataSerializers.INT);
    private Vec3 target = Vec3.ZERO;
    private boolean released;

    public StarRiftEntity(EntityType<? extends StarRiftEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static int totalTicks() {
        return OPEN_TICKS + FallingStarEntity.FALL_TICKS + CLOSE_TICKS;
    }

    public static void spawn(ServerLevel level, Vec3 riftPos, Vec3 impact) {
        StarRiftEntity rift = StarwreckEntities.STAR_RIFT.get().create(level);
        if (rift == null) return;
        rift.target = impact;
        rift.moveTo(riftPos.x, riftPos.y, riftPos.z, level.random.nextFloat() * 360, 0);
        level.addFreshEntity(rift);
        // 一场里会同时开好几道，音量比单道时收一点，近处仍听得见撕裂声
        level.playSound(null, rift.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.AMBIENT, 2.5F, 0.5F);
        level.playSound(null, rift.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 2.0F, 0.5F);
        LogUtils.getLogger().debug("Star rain rift opened at {}, {}", impact.x, impact.z);
    }

    public int getAge() { return entityData.get(AGE); }

    /** 渲染用的连续年龄。tick 已经把年龄加过，所以用上一拍加上 partial。 */
    public float visualAge(float partialTick) { return Math.max(0, getAge() - 1 + partialTick); }

    /** 张开程度 0～1：张开阶段线性增长，保持阶段为 1，闭合阶段线性回落。 */
    public static float openness(float visualAge) {
        if (visualAge < OPEN_TICKS) return Math.max(0, visualAge / OPEN_TICKS);
        float closing = visualAge - OPEN_TICKS - FallingStarEntity.FALL_TICKS;
        return closing <= 0 ? 1 : Math.max(0, 1 - closing / CLOSE_TICKS);
    }

    public Phase phase() {
        int age = getAge();
        if (age < OPEN_TICKS) return Phase.OPENING;
        if (age < OPEN_TICKS + FallingStarEntity.FALL_TICKS) return Phase.HOLD;
        return Phase.CLOSING;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(AGE, 0); }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientEffects();
            return;
        }
        int age = getAge() + 1;
        entityData.set(AGE, age);
        if (!released && age >= OPEN_TICKS && level() instanceof ServerLevel server) {
            released = true;
            FallingStarEntity.spawn(server, position(), target);
        }
        if (age >= totalTicks()) discard();
    }

    // 碎屑：边缘剥落的暗色星灰，芯里往下滴的火星；陨星放出那一拍芯里迸一小团
    private void clientEffects() {
        float open = openness(getAge());
        if (open <= 0) return;
        double half = StarRiftVisual.HEIGHT * 0.5 * Math.min(1, open * 3);
        if (random.nextFloat() < 0.8F * open) {
            double a = random.nextDouble() * Math.PI * 2;
            level().addParticle(ModParticles.STAR_ASH.get(), getX() + Math.cos(a) * open, getY() + (random.nextDouble() - 0.5) * 2 * half, getZ() + Math.sin(a) * open,
                Math.cos(a) * 0.03, -0.01 + random.nextDouble() * 0.02, Math.sin(a) * 0.03);
        }
        if (random.nextFloat() < 0.6F * open) {
            level().addParticle(ModParticles.STAR_EMBER.get(), getX() + (random.nextDouble() - 0.5) * 0.5, getY() + (random.nextDouble() - 0.5) * half, getZ() + (random.nextDouble() - 0.5) * 0.5,
                (random.nextDouble() - 0.5) * 0.04, -0.06 - random.nextDouble() * 0.06, (random.nextDouble() - 0.5) * 0.04);
        }
        if (getAge() == OPEN_TICKS) {
            for (int i = 0; i < 24; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(ModParticles.STAR_EMBER.get(), getX(), getY() + (random.nextDouble() - 0.5) * 2, getZ(),
                    Math.cos(a) * 0.12, (random.nextDouble() - 0.5) * 0.2, Math.sin(a) * 0.12);
            }
        }
    }

    /** 画面尺寸，渲染器和粒子共用。 */
    public static final class StarRiftVisual {
        public static final float HEIGHT = 7F, WIDTH = 2F;
        private StarRiftVisual() {}
    }

    // 玩家在 32～80 格外看它，默认按碰撞箱算的可见距离不够
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(AGE, tag.getInt("Age"));
        released = tag.getBoolean("Released");
        if (tag.contains("TargetX")) target = new Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"));
        else if (tag.contains("AnchorX")) target = new Vec3(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"), tag.getDouble("AnchorZ"));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", getAge());
        tag.putBoolean("Released", released);
        tag.putDouble("TargetX", target.x);
        tag.putDouble("TargetY", target.y);
        tag.putDouble("TargetZ", target.z);
    }

    public enum Phase { OPENING, HOLD, CLOSING }
}
