package org.hp.hp_end_expansion.entity.starwreck;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import org.hp.hp_end_expansion.registry.StarwreckEntities;

/**
 * 殉星者掷出的链锤。命中生物或方块后，在落点脚下和周围亮起落点圈，1.5 秒后各砸下一颗小陨星。
 */
public final class StarFlailEntity extends Projectile {
    private static final int MAX_LIFE = 40;
    private static final float DAMAGE = 4;
    private static final double GRAVITY = 0.03;

    public StarFlailEntity(EntityType<? extends StarFlailEntity> type, Level level) {
        super(type, level);
        noPhysics = false;
    }

    public static void launch(LivingEntity owner, Vec3 origin, Vec3 direction) {
        StarFlailEntity flail = StarwreckEntities.STAR_FLAIL.get().create(owner.level());
        if (flail == null) return;
        flail.setOwner(owner);
        flail.setPos(origin);
        flail.shoot(direction.x, direction.y, direction.z, 0.9F, 0.5F);
        owner.level().addFreshEntity(flail);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement().add(0, -GRAVITY, 0);
        setDeltaMovement(motion);
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (!level().isClientSide && hit.getType() != HitResult.Type.MISS) {
            hitTargetOrDeflectSelf(hit);
            if (isRemoved()) return;
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        updateRotation();
        if (!level().isClientSide && tickCount > MAX_LIFE) discard();
    }

    @Override protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        if (getOwner() instanceof LivingEntity owner && target instanceof LivingEntity living)
            living.hurt(damageSources().mobProjectile(this, owner), DAMAGE);
        land(result.getLocation());
    }

    @Override protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        land(result.getLocation());
    }

    @Override protected boolean canHitEntity(Entity target) {
        return !(target instanceof StarMartyrEntity) && !(target instanceof StarCallerEntity) && !(target instanceof StarChaserEntity)
            && !(target instanceof StarBearerEntity)
            && super.canHitEntity(target);
    }

    // 落点一颗，周围 2.5～4.5 格再两颗，圈与圈至少隔 2 格
    private void land(Vec3 at) {
        if (level() instanceof ServerLevel server) {
            List<Vec3> spots = new ArrayList<>();
            spots.add(at);
            for (int i = 0, tries = 0; i < 2 && tries < 16; tries++) {
                double a = random.nextDouble() * Math.PI * 2, r = 2.5 + random.nextDouble() * 2;
                Vec3 p = at.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                if (spots.stream().anyMatch(s -> s.distanceToSqr(p.x, s.y, p.z) < 4)) continue;
                spots.add(p);
                i++;
            }
            for (Vec3 p : spots) {
                double y = StarChaserEntity.groundY(level(), p.x, p.z, p.y);
                if (!Double.isNaN(y)) StarMarkEntity.spawn(server, new Vec3(p.x, y, p.z));
            }
            StarImpactEntity.spawn(server, at, getDeltaMovement(), true);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.7F, 0.6F);
        }
        discard();
    }

    @Override public boolean isPickable() { return false; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
}
