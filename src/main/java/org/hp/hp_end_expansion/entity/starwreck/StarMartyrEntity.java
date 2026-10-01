package org.hp.hp_end_expansion.entity.starwreck;

import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModParticles;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * 殉星者：坠星教团的狂信徒。近了抡链锤砸地，远了把链锤掷出去，砸中人或方块就在周围召陨星。
 * 血量掉到四成以下开始殉星：狂奔 1.5 秒扑向目标，然后停下侧举双臂，头顶落下一颗小陨星把自己连同周围一起砸碎。
 * 任何陨星都伤不到它，它只死于自己的那一颗。
 */
public final class StarMartyrEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.star_martyr.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.star_martyr.walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.star_martyr.attack");
    private static final RawAnimation THROW = RawAnimation.begin().thenPlay("animation.star_martyr.throw");
    private static final RawAnimation MARTYR = RawAnimation.begin().thenPlayAndHold("animation.star_martyr.martyr");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.star_martyr.death");
    private static final EntityDataAccessor<Boolean> MARTYRING = SynchedEntityData.defineId(StarMartyrEntity.class, EntityDataSerializers.BOOLEAN);
    // 抡砸 0.8 s，第 0.4 s 砸中；投掷 0.9 s，第 0.45 s 脱手；殉星第 1.5 s 停步召星
    private static final int SWING_TICKS = 16, SWING_HIT = 9;
    private static final int THROW_TICKS = 18, THROW_RELEASE = 10;
    private static final int MARTYR_CALL = 30, MARTYR_END = MARTYR_CALL + FallingStarEntity.SMALL_FALL_TICKS + 1;
    private static final float MARTYR_HEALTH = 0.4F;
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private int swingTicks = -1, throwTicks = -1, throwCooldown, martyrTicks = -1;

    public StarMartyrEntity(EntityType<? extends StarMartyrEntity> type, Level level) {
        super(type, level);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMonsterAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.ATTACK_DAMAGE, 5)
            .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MARTYRING, false);
    }

    public boolean isMartyring() { return entityData.get(MARTYRING); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MartyrGoal());
        goalSelector.addGoal(2, new ThrowGoal());
        goalSelector.addGoal(3, new SwingGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide && isMartyring() && isAlive()) martyrParticles();
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if ((swingTicks >= 0 || throwTicks >= 0 || martyrTicks >= 0) && target != null)
            getLookControl().setLookAt(target, 40, 40);
        if (throwCooldown > 0) throwCooldown--;
        if (swingTicks >= 0 && ++swingTicks == SWING_HIT) {
            if (target != null && isWithinMeleeAttackRange(target)) doHurtTarget(target);
            slam();
        }
        if (swingTicks >= SWING_TICKS) swingTicks = -1;
        if (throwTicks >= 0 && ++throwTicks == THROW_RELEASE && target != null && target.isAlive()) launch(target);
        if (throwTicks >= THROW_TICKS) {
            throwTicks = -1;
            throwCooldown = 60 + random.nextInt(40);
        }
        if (martyrTicks >= 0) {
            martyrTicks++;
            if (martyrTicks == MARTYR_CALL && level() instanceof ServerLevel server) {
                FallingStarEntity.spawn(server, position().add(0, 20, 0), position(), true);
                playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 1.6F);
            }
            if (martyrTicks >= MARTYR_END) hurt(damageSources().explosion(this, this), Float.MAX_VALUE);
        }
    }

    // 链锤砸在身前地面，热浪和碎石用小号陨星的落地效果
    private void slam() {
        if (!(level() instanceof ServerLevel server)) return;
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 at = position().add(forward.scale(1.3));
        double y = StarChaserEntity.groundY(level(), at.x, at.z, at.y);
        if (Double.isNaN(y)) y = at.y;
        StarImpactEntity.spawn(server, new Vec3(at.x, y, at.z), forward, true);
        playSound(SoundEvents.MACE_SMASH_GROUND, 0.8F, 0.7F);
    }

    private void launch(LivingEntity target) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 origin = position().add(right.scale(0.45)).add(forward.scale(0.4)).add(0, 1.5, 0);
        Vec3 eye = target.getEyePosition();
        Vec3 aim = eye.add(0, origin.distanceTo(eye) * 0.12, 0).subtract(origin);
        StarFlailEntity.launch(this, origin, aim);
    }

    private void startMartyr() {
        martyrTicks = 0;
        swingTicks = -1;
        throwTicks = -1;
        entityData.set(MARTYRING, true);
        triggerAnim("action", "martyr");
        playSound(SoundEvents.EVOKER_PREPARE_WOLOLO, 1.4F, 0.6F);
    }

    // 胸口星晶往外喷火星，召星后更猛
    private void martyrParticles() {
        float yaw = yBodyRot * ((float) Math.PI / 180F);
        Vec3 core = position().add(-Math.sin(yaw) * 0.3, 1.15, Math.cos(yaw) * 0.3);
        int n = martyrTicks >= MARTYR_CALL || tickCount % 2 == 0 ? 2 : 1;
        for (int i = 0; i < n; i++)
            level().addParticle(ModParticles.STAR_EMBER.get(), core.x, core.y, core.z,
                (random.nextDouble() - 0.5) * 0.12, 0.02 + random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.12);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof FallingStarEntity) return false;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive() && martyrTicks < 0 && getHealth() <= getMaxHealth() * MARTYR_HEALTH) startMartyr();
        return hurt;
    }

    @Override public void die(DamageSource source) {
        boolean first = !dead && !isRemoved();
        super.die(source);
        if (first && level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.STAR_EMBER.get(), getX(), getY() + 1.1, getZ(), 24, 0.3, 0.5, 0.3, 0.08);
            server.sendParticles(ModParticles.STAR_ASH.get(), getX(), getY() + 0.2, getZ(), 8, 0.4, 0.1, 0.4, 0.02);
            playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 0.6F);
        }
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.AMETHYST_BLOCK_CHIME; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.AMETHYST_BLOCK_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.AMETHYST_BLOCK_BREAK; }
    @Override public float getVoicePitch() { return super.getVoicePitch() * 0.8F; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("MartyrTicks", martyrTicks);
        tag.putInt("ThrowCooldown", throwCooldown);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("MartyrTicks")) martyrTicks = tag.getInt("MartyrTicks");
        if (martyrTicks >= 0) entityData.set(MARTYRING, true);
        throwCooldown = tag.getInt("ThrowCooldown");
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new MartyrAnimationController(this, state -> {
            AnimationController<StarMartyrEntity> controller = state.getController();
            controller.setAnimationSpeed(1);
            if (isDeadOrDying()) {
                stopTriggeredAnim("action", null);
                controller.transitionLength(2);
                return state.setAndContinue(DEATH);
            }
            if (controller.isPlayingTriggeredAnimation()) {
                controller.transitionLength(0);
                return PlayState.CONTINUE;
            }
            controller.transitionLength(3);
            if (state.isMoving()) {
                controller.setAnimationSpeed(Mth.clamp(state.getLimbSwingAmount() / 0.18F, 0.65F, 2.7F));
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }).receiveTriggeredAnimations()
            .triggerableAnim("attack", ATTACK).triggerableAnim("throw", THROW).triggerableAnim("martyr", MARTYR));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    private static final class MartyrAnimationController extends AnimationController<StarMartyrEntity> {
        private double previousTick = Double.NaN;
        private double playbackTick;

        MartyrAnimationController(StarMartyrEntity entity, AnimationStateHandler<StarMartyrEntity> handler) {
            super(entity, "action", 0, handler);
        }

        @Override protected double adjustTick(double tick) {
            if (shouldResetTick) {
                previousTick = tick;
                playbackTick = 0;
                shouldResetTick = false;
                return 0;
            }
            if (Double.isFinite(previousTick))
                playbackTick += animationSpeedModifier.apply(animatable) * Math.max(tick - previousTick, 0);
            previousTick = tick;
            return playbackTick;
        }
    }

    /** 追上去后先抬手蓄力，动画砸下那一拍才结算伤害。 */
    private final class SwingGoal extends MeleeAttackGoal {
        SwingGoal() { super(StarMartyrEntity.this, 1.2, false); }

        @Override protected void checkAndPerformAttack(LivingEntity target) {
            if (swingTicks >= 0 || throwTicks >= 0 || !canPerformAttack(target)) return;
            resetAttackCooldown();
            swingTicks = 0;
            triggerAnim("action", "attack");
            playSound(SoundEvents.CHAIN_PLACE, 1.0F, 0.7F);
        }
    }

    /** 殉星期间接管移动：先全速扑向目标，召星后原地站定迎星。 */
    private final class MartyrGoal extends Goal {
        MartyrGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        @Override public boolean canUse() { return martyrTicks >= 0; }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void tick() {
            LivingEntity target = getTarget();
            if (target != null) getLookControl().setLookAt(target, 40, 40);
            if (martyrTicks >= MARTYR_CALL) {
                getNavigation().stop();
                return;
            }
            if (target != null) getNavigation().moveTo(target, 1.6);
        }
    }

    /** 距离 3.5～12 格且看得见时掷出链锤，掷出期间站住并一直朝向目标。 */
    private final class ThrowGoal extends Goal {
        ThrowGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || martyrTicks >= 0 || throwTicks >= 0 || swingTicks >= 0 || throwCooldown > 0)
                return false;
            double d = distanceToSqr(target);
            return d > 3.5 * 3.5 && d < 12 * 12 && getSensing().hasLineOfSight(target);
        }

        @Override public boolean canContinueToUse() { return throwTicks >= 0; }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void start() {
            throwTicks = 0;
            triggerAnim("action", "throw");
            playSound(SoundEvents.TRIDENT_THROW.value(), 1.0F, 0.65F);
            getNavigation().stop();
        }

        @Override public void tick() {
            LivingEntity target = getTarget();
            if (target != null) getLookControl().setLookAt(target, 40, 40);
            getNavigation().stop();
        }
    }
}
