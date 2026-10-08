package org.hp.hp_end_expansion.entity.tidelight;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModEntities;

/** 潮压水箭：拉长的水滴，不追踪，很轻的重力，存活 2 秒。命中 4 伤害、击退 0.6、缓慢 I 2 秒，能浇灭着火的目标。 */
public final class TideWaterBoltEntity extends ThrowableProjectile {
    public static final float SPEED = 1.6F;
    private static final float DAMAGE = 4;
    private static final int LIFETIME = 40;

    public TideWaterBoltEntity(EntityType<? extends TideWaterBoltEntity> type, Level level) {
        super(type, level);
    }

    public static void shoot(Level level, LivingEntity owner, Vec3 from, Vec3 aim) {
        TideWaterBoltEntity bolt = ModEntities.TIDE_WATER_BOLT.get().create(level);
        if (bolt == null) return;
        bolt.setOwner(owner);
        bolt.moveTo(from.x, from.y, from.z, owner.getYRot(), 0);
        Vec3 d = aim.subtract(from);
        // 补偿重力下坠，让水箭在中段对准瞄准点
        double flight = d.length() / SPEED;
        bolt.shoot(d.x, d.y + 0.5 * bolt.getDefaultGravity() * flight * flight, d.z, SPEED, 0);
        level.addFreshEntity(bolt);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override protected double getDefaultGravity() { return 0.01; }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 v = getDeltaMovement();
            level().addParticle(ParticleTypes.SPLASH, getX(), getY(), getZ(), -v.x * 0.1, 0.02, -v.z * 0.1);
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.FALLING_WATER, getX(), getY(), getZ(), 0, 0, 0);
        } else if (tickCount > LIFETIME) {
            discard();
        }
    }

    @Override protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && TideRemnantHermitCrabEntity.canHit(target);
    }

    @Override protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(result.getEntity() instanceof LivingEntity living)) return;
        Entity owner = getOwner();
        if (living.hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity o ? o : null), DAMAGE)) {
            Vec3 v = getDeltaMovement().multiply(1, 0, 1);
            if (v.lengthSqr() > 1.0E-6) {
                v = v.normalize();
                living.knockback(0.6, -v.x, -v.z);
            }
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), owner);
        }
        living.clearFire();
    }

    @Override protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) return;
        if (level() instanceof ServerLevel server) {
            Vec3 at = result.getLocation();
            server.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 16, 0.25, 0.25, 0.25, 0.2);
            server.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.05);
            level().playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH, SoundSource.HOSTILE, 0.6F, 1.4F);
        }
        discard();
    }

    @Override protected void onHitBlock(BlockHitResult result) { super.onHitBlock(result); }

    @Override public boolean shouldBeSaved() { return false; }
}
