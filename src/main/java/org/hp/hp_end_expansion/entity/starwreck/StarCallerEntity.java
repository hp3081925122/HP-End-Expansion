package org.hp.hp_end_expansion.entity.starwreck;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * 唤星者：星骸荒原里少见的施法小怪。和玩家保持 6～14 格，举杖召星：目标脚下和周围随机几处亮起落点圈，
 * 1.5 秒后各砸下一颗小陨星，半血以下落得更多。被打时有概率星遁到 6～10 格外，打断正在进行的召星。
 */
public final class StarCallerEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.star_caller.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.star_caller.walk");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.star_caller.cast");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.star_caller.death");
    private static final EntityDataAccessor<Boolean> CASTING = SynchedEntityData.defineId(StarCallerEntity.class, EntityDataSerializers.BOOLEAN);
    // 召星动画 1.5 s，第 1.0 s 杖头劈下时放落点圈
    public static final int CAST_TICKS = 30, CAST_HIT = 20;
    private static final double NEAR = 6, FAR = 14, CAST_RANGE = 20;
    private static final float BLINK_CHANCE = 0.35F;
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private int castTicks = -1, castCooldown = 60, blinkCooldown;

    public StarCallerEntity(EntityType<? extends StarCallerEntity> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMonsterAttributes().add(Attributes.MAX_HEALTH, 28).add(Attributes.ARMOR, 2)
            .add(Attributes.MOVEMENT_SPEED, 0.24).add(Attributes.FOLLOW_RANGE, 24).add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    // 自然刷出时身边跟着一两个殉星者
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) return result;
        for (int i = 1 + random.nextInt(2); i > 0; i--) {
            StarMartyrEntity martyr = StarwreckEntities.STAR_MARTYR.get().create(level.getLevel());
            if (martyr == null) continue;
            double a = random.nextDouble() * Math.PI * 2, r = 1.5 + random.nextDouble() * 2;
            int x = Mth.floor(getX() + Math.cos(a) * r), z = Mth.floor(getZ() + Math.sin(a) * r);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - getY()) > 3) continue;
            martyr.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
            if (!level.noCollision(martyr)) continue;
            martyr.finalizeSpawn(level, difficulty, MobSpawnType.MOB_SUMMONED, null);
            level.addFreshEntity(martyr);
        }
        return result;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CASTING, false);
    }

    public boolean isCasting() { return entityData.get(CASTING); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CallStarsGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide && isCasting()) castParticles();
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (castCooldown > 0) castCooldown--;
        if (blinkCooldown > 0) blinkCooldown--;
    }

    private void startCast() {
        castTicks = 0;
        entityData.set(CASTING, true);
        triggerAnim("action", "cast");
        playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.2F, 1.4F);
    }

    private void endCast() {
        castTicks = -1;
        entityData.set(CASTING, false);
        castCooldown = 90 + random.nextInt(50);
    }

    // 目标脚下一颗，周围 3～6.5 格随机再落 2～3 颗（半血以下 4～5 颗），落点圈之间至少隔 3 格
    private void callStars(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        int around = 2 + random.nextInt(2) + (getHealth() < getMaxHealth() / 2 ? 2 : 0);
        List<Vec3> spots = new ArrayList<>();
        spots.add(target.position());
        for (int i = 0, tries = 0; i < around && tries < around * 8; tries++) {
            double a = random.nextDouble() * Math.PI * 2, r = 3 + random.nextDouble() * 3.5;
            Vec3 p = target.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            if (spots.stream().anyMatch(s -> s.distanceToSqr(p.x, s.y, p.z) < 9)) continue;
            spots.add(p);
            i++;
        }
        for (Vec3 p : spots) {
            double y = StarChaserEntity.groundY(level(), p.x, p.z, p.y);
            if (!Double.isNaN(y)) StarMarkEntity.spawn(server, new Vec3(p.x, y, p.z));
        }
        playSound(SoundEvents.EVOKER_CAST_SPELL, 1.2F, 1.3F);
    }

    // 杖头在右手上方：举杖时约 2.6 格高，火星从四周往杖头收
    private void castParticles() {
        float yaw = yBodyRot * ((float) Math.PI / 180F);
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)), forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 tip = position().add(right.scale(0.35)).add(forward.scale(0.2)).add(0, 2.6, 0);
        for (int i = 0; i < 2; i++) {
            Vec3 from = tip.add((random.nextDouble() - 0.5) * 2.4, (random.nextDouble() - 0.5) * 2.0, (random.nextDouble() - 0.5) * 2.4);
            Vec3 v = tip.subtract(from).scale(0.09);
            level().addParticle(ModParticles.STAR_EMBER.get(), from.x, from.y, from.z, v.x, v.y, v.z);
        }
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive() && blinkCooldown <= 0 && source.getEntity() != null && random.nextFloat() < BLINK_CHANCE)
            blink();
        return hurt;
    }

    private void blink() {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = position();
        for (int i = 0; i < 10; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 6 + random.nextDouble() * 4;
            if (!randomTeleport(getX() + Math.cos(a) * r, getY() + 4, getZ() + Math.sin(a) * r, false)) continue;
            burst(server, from);
            burst(server, position());
            server.playSound(null, from.x, from.y, from.z, SoundEvents.CHORUS_FRUIT_TELEPORT, getSoundSource(), 1, 1.4F);
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.8F);
            blinkCooldown = 160;
            if (castTicks >= 0) endCast();
            getNavigation().stop();
            return;
        }
    }

    private void burst(ServerLevel server, Vec3 at) {
        server.sendParticles(ModParticles.STAR_EMBER.get(), at.x, at.y + 1, at.z, 24, 0.35, 0.7, 0.35, 0.06);
        server.sendParticles(ModParticles.STAR_ASH.get(), at.x, at.y + 0.2, at.z, 10, 0.4, 0.1, 0.4, 0.02);
    }

    @Override public void die(DamageSource source) {
        boolean first = !dead && !isRemoved();
        super.die(source);
        if (first && level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.STAR_EMBER.get(), getX(), getY() + 1.2, getZ(), 30, 0.3, 0.6, 0.3, 0.08);
            playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 0.8F);
        }
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.AMETHYST_BLOCK_CHIME; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.AMETHYST_BLOCK_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.AMETHYST_BLOCK_BREAK; }
    @Override public float getVoicePitch() { return super.getVoicePitch() * 0.6F; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CastCooldown", castCooldown);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        castCooldown = tag.getInt("CastCooldown");
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 4, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP).triggerableAnim("cast", CAST));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    /** 保持距离、看得见目标且冷却好了就召星；召星期间原地不动。 */
    private final class CallStarsGoal extends Goal {
        CallStarsGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive();
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void stop() {
            if (castTicks >= 0) endCast();
            getNavigation().stop();
        }

        @Override public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30, 30);
            if (castTicks >= 0) {
                getNavigation().stop();
                castTicks++;
                if (castTicks == CAST_HIT) callStars(target);
                if (castTicks >= CAST_TICKS) endCast();
                return;
            }
            double d = distanceToSqr(target);
            boolean see = getSensing().hasLineOfSight(target);
            if (castCooldown <= 0 && see && d < CAST_RANGE * CAST_RANGE) {
                startCast();
            } else if (d < NEAR * NEAR) {
                if (getNavigation().isDone()) {
                    Vec3 away = DefaultRandomPos.getPosAway(StarCallerEntity.this, 10, 4, target.position());
                    if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 1.2);
                }
            } else if (d > FAR * FAR || !see) {
                getNavigation().moveTo(target, 1.0);
            } else {
                getNavigation().stop();
            }
        }
    }
}
