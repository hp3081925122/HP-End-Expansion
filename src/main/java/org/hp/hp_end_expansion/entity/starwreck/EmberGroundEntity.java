package org.hp.hp_end_expansion.entity.starwreck;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModParticles;

/**
 * 余烬甲虫死后留下的余烬地面（设计文档 9.2）：存在 2 秒，每秒对站在上面的生物造成 1 点火焰伤害。
 * 不放置火方块、不点燃实体；甲虫自己不受影响，免得一群甲虫连锁烧死。
 */
public final class EmberGroundEntity extends Entity {
    public static final int LIFETIME = 40;
    private int age;
    private int life = LIFETIME;

    public EmberGroundEntity(EntityType<? extends EmberGroundEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                double angle = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * getBbWidth() * 0.5;
                level().addParticle(ModParticles.STAR_EMBER.get(), getX() + Math.cos(angle) * r, getY() + 0.05, getZ() + Math.sin(angle) * r, 0, 0, 0);
            }
            if (random.nextInt(8) == 0)
                level().addParticle(ParticleTypes.SMALL_FLAME, getX() + (random.nextDouble() - 0.5) * getBbWidth(), getY() + 0.05,
                    getZ() + (random.nextDouble() - 0.5) * getBbWidth(), 0, 0.01, 0);
            return;
        }
        if (age % 20 == 0) {
            AABB area = getBoundingBox().inflate(0, 0.3, 0);
            for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !(e instanceof EmberBeetleEntity)))
                victim.hurt(damageSources().inFire(), 1);
        }
        if (++age >= life) discard();
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { age = tag.getInt("Age"); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("Age", age); }

    public static void spawn(ServerLevel level, Entity source, EntityType<EmberGroundEntity> type) {
        spawn(level, source.position(), type, LIFETIME);
    }

    public static void spawn(ServerLevel level, Vec3 at, EntityType<EmberGroundEntity> type, int life) {
        EmberGroundEntity ground = type.create(level);
        if (ground == null) return;
        ground.life = life;
        ground.moveTo(at.x, at.y, at.z, 0, 0);
        level.addFreshEntity(ground);
    }
}
