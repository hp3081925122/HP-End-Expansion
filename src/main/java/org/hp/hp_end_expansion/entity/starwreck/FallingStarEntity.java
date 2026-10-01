package org.hp.hp_end_expansion.entity.starwreck;

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
    // 逐星兽召星用的小号陨星：一半大小，从正上方快速落下，不伤逐星兽
    public static final int SMALL_FALL_TICKS = 10;
    public static final float SMALL_RADIUS = 2;
    public static final float SMALL_DAMAGE = 6;
    private static final EntityDataAccessor<Boolean> SMALL = SynchedEntityData.defineId(FallingStarEntity.class, EntityDataSerializers.BOOLEAN);
    private Vec3 start = Vec3.ZERO;
    private Vec3 target = Vec3.ZERO;
    private int age;
    private boolean impacted;
    private float damage = -1.0F;

    public FallingStarEntity(EntityType<? extends FallingStarEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static void spawn(ServerLevel level, Vec3 from, Vec3 to) {
        spawn(level, from, to, false);
    }

    public static void spawn(ServerLevel level, Vec3 from, Vec3 to, boolean small) {
        spawn(level, from, to, small, small ? SMALL_DAMAGE : IMPACT_DAMAGE);
    }

    public static void spawn(ServerLevel level, Vec3 from, Vec3 to, boolean small, float damage) {
        FallingStarEntity star = StarwreckEntities.FALLING_STAR.get().create(level);
        if (star == null) return;
        star.damage = damage;
        star.entityData.set(SMALL, small);
        star.start = from;
        star.target = to;
        star.moveTo(from.x, from.y, from.z, yaw(from, to), 0);
        level.addFreshEntity(star);
    }

    private static float yaw(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(SMALL, false); }

    public boolean isSmall() { return entityData.get(SMALL); }

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
        int fall = isSmall() ? SMALL_FALL_TICKS : FALL_TICKS;
        float t = Math.min(1F, age / (float) fall);
        float eased = t * t;
        Vec3 next = new Vec3(
            Mth.lerp(eased, start.x, target.x),
            Mth.lerp(eased, start.y, target.y),
            Mth.lerp(eased, start.z, target.z));
        setDeltaMovement(next.subtract(position()));
        setPos(next.x, next.y, next.z);
        setYRot(yaw(start, target));
        if (age >= fall) impact();
    }

    private void impact() {
        impacted = true;
        if (level() instanceof ServerLevel server) {
            boolean small = isSmall();
            float radius = small ? SMALL_RADIUS : IMPACT_RADIUS;
            AABB area = new AABB(target, target).inflate(radius, 2, radius);
            // 逐星兽追着陨星走，唤星者和负星者自己召陨星，任何陨星都砸不伤它们
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e.isAlive() && !(e instanceof StarChaserEntity) && !(e instanceof StarCallerEntity) && !(e instanceof StarBearerEntity))) {
                if (living.distanceToSqr(target) > radius * radius) continue;
                if (living.hurt(damageSources().explosion(this, this), damage >= 0 ? damage : small ? SMALL_DAMAGE : IMPACT_DAMAGE))
                    living.knockback(small ? 0.5 : 0.7, getX() - living.getX(), getZ() - living.getZ());
            }
            StarImpactEntity.spawn(server, target, target.subtract(start), small);
            server.playSound(null, BlockPos.containing(target), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, small ? 2.5F : 5.0F, small ? 0.85F : 0.55F);
            if (!small) StarRain.onImpact(server, target);
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
        entityData.set(SMALL, tag.getBoolean("Small"));
        age = tag.getInt("Age");
        impacted = tag.getBoolean("Impacted");
        damage = tag.contains("Damage") ? tag.getFloat("Damage") : -1.0F;
        start = new Vec3(tag.getDouble("StartX"), tag.getDouble("StartY"), tag.getDouble("StartZ"));
        target = new Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Small", isSmall());
        tag.putInt("Age", age);
        tag.putBoolean("Impacted", impacted);
        tag.putFloat("Damage", damage);
        tag.putDouble("StartX", start.x);
        tag.putDouble("StartY", start.y);
        tag.putDouble("StartZ", start.z);
        tag.putDouble("TargetX", target.x);
        tag.putDouble("TargetY", target.y);
        tag.putDouble("TargetZ", target.z);
    }
}
