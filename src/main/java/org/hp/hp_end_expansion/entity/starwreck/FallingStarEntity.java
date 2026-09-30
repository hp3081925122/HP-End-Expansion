package org.hp.hp_end_expansion.entity.starwreck;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.StarwreckEntities;

/**
 * 从裂隙落到地面的陨星。服务端按先慢后快的曲线移动，不挖方块。
 * 客户端看 {@code position()} 和 {@code getDeltaMovement()}，位移指向这一拍的飞行方向。
 */
public final class FallingStarEntity extends Entity {
    public static final int FALL_TICKS = 30;
    public static final float IMPACT_RADIUS = 4;
    public static final float IMPACT_DAMAGE = 8;
    private Vec3 start = Vec3.ZERO;
    private Vec3 target = Vec3.ZERO;
    private int age;
    private boolean impacted;

    public FallingStarEntity(EntityType<? extends FallingStarEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static void spawn(ServerLevel level, Vec3 from, Vec3 to) {
        FallingStarEntity star = StarwreckEntities.FALLING_STAR.get().create(level);
        if (star == null) return;
        star.start = from;
        star.target = to;
        star.moveTo(from.x, from.y, from.z, yaw(from, to), 0);
        level.addFreshEntity(star);
    }

    private static float yaw(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90;
    }

    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {}

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTrail();
            return;
        }
        if (impacted) return;
        if (start.distanceToSqr(target) < 1.0E-4) {
            discard();
            return;
        }
        age++;
        float t = Math.min(1F, age / (float) FALL_TICKS);
        float eased = t * t;
        Vec3 next = new Vec3(
            Mth.lerp(eased, start.x, target.x),
            Mth.lerp(eased, start.y, target.y),
            Mth.lerp(eased, start.z, target.z));
        setDeltaMovement(next.subtract(position()));
        setPos(next.x, next.y, next.z);
        setYRot(yaw(start, target));
        if (age >= FALL_TICKS) impact();
    }

    private void impact() {
        impacted = true;
        if (level() instanceof ServerLevel server) {
            AABB area = new AABB(target, target).inflate(IMPACT_RADIUS, 2, IMPACT_RADIUS);
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
                if (living.distanceToSqr(target) > IMPACT_RADIUS * IMPACT_RADIUS) continue;
                if (living.hurt(damageSources().explosion(this, this), IMPACT_DAMAGE))
                    living.knockback(0.7, getX() - living.getX(), getZ() - living.getZ());
            }
            StarImpactEntity.spawn(server, target, target.subtract(start));
            server.playSound(null, BlockPos.containing(target), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0F, 0.55F);
        }
        discard();
    }

    // 尾焰里甩出的火星和星灰，速度越快越多
    private void clientTrail() {
        Vec3 v = getDeltaMovement();
        double speed = v.length();
        if (speed < 0.02) return;
        int count = 1 + (int) Math.min(4, speed * 1.6);
        for (int i = 0; i < count; i++) {
            double back = random.nextDouble() * Math.min(6, speed * 2);
            Vec3 p = position().add(0, 0.7, 0).subtract(v.scale(back / speed));
            level().addParticle(ModParticles.STAR_EMBER.get(), p.x + (random.nextDouble() - 0.5) * 0.6, p.y + (random.nextDouble() - 0.5) * 0.6, p.z + (random.nextDouble() - 0.5) * 0.6,
                -v.x * 0.04 + (random.nextDouble() - 0.5) * 0.06, -v.y * 0.04 + 0.02, -v.z * 0.04 + (random.nextDouble() - 0.5) * 0.06);
        }
        if (random.nextFloat() < 0.5F) {
            level().addParticle(ModParticles.STAR_ASH.get(), getX(), getY() + 0.7, getZ(), -v.x * 0.03, 0.01, -v.z * 0.03);
        }
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        impacted = tag.getBoolean("Impacted");
        start = new Vec3(tag.getDouble("StartX"), tag.getDouble("StartY"), tag.getDouble("StartZ"));
        target = new Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putBoolean("Impacted", impacted);
        tag.putDouble("StartX", start.x);
        tag.putDouble("StartY", start.y);
        tag.putDouble("StartZ", start.z);
        tag.putDouble("TargetX", target.x);
        tag.putDouble("TargetY", target.y);
        tag.putDouble("TargetZ", target.z);
    }
}
