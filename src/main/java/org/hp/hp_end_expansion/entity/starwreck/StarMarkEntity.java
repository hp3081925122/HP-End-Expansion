package org.hp.hp_end_expansion.entity.starwreck;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.StarwreckEntities;

/**
 * 逐星兽召星的落点警示圈（设计文档 4.3）：地面亮起半径 2 格的圈，1.5 秒后从正上方 20 格落下一颗小陨星，
 * 陨星落地后圈再留几拍就消失。伤害由小陨星结算。
 */
public final class StarMarkEntity extends Entity {
    public static final int WARN_TICKS = 30;
    public static final int LIFE = WARN_TICKS + FallingStarEntity.SMALL_FALL_TICKS + 4;
    public static final float RADIUS = FallingStarEntity.SMALL_RADIUS;
    private static final double DROP_HEIGHT = 20;
    private float damage = FallingStarEntity.SMALL_DAMAGE;

    public StarMarkEntity(EntityType<? extends StarMarkEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void spawn(ServerLevel level, Vec3 at) {
        spawn(level, at, FallingStarEntity.SMALL_DAMAGE);
    }

    public static void spawn(ServerLevel level, Vec3 at, float damage) {
        StarMarkEntity mark = StarwreckEntities.STAR_MARK.get().create(level);
        if (mark == null) return;
        mark.damage = damage;
        mark.moveTo(at.x, at.y, at.z, 0, 0);
        level.addFreshEntity(mark);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount < WARN_TICKS && random.nextInt(2) == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(ModParticles.STAR_EMBER.get(), getX() + Math.cos(a) * RADIUS, getY() + 0.05, getZ() + Math.sin(a) * RADIUS,
                    0, 0.03 + random.nextDouble() * 0.04, 0);
            }
            return;
        }
        if (tickCount == 0) level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.4F, 0.6F);
        if (tickCount == WARN_TICKS && level() instanceof ServerLevel server)
            FallingStarEntity.spawn(server, position().add(0, DROP_HEIGHT, 0), position(), true, damage);
        if (tickCount >= LIFE) discard();
    }

    /** 客户端画警示圈用：0～1 的预警进度，落下后保持 1。 */
    public float charge(float partialTick) {
        return Math.min(1, (tickCount + partialTick) / WARN_TICKS);
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(RADIUS, 0, RADIUS).expandTowards(0, DROP_HEIGHT, 0); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
