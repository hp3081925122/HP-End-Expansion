package org.hp.hp_end_expansion.entity.tidelight;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

public final class TidecrownAnemoneEntity extends Monster implements GeoEntity {
    public static final byte NONE = 0, LASH = 1, BIND = 2, BLOOM = 3;
    public static final int LASH_LEFT = 20, LASH_RIGHT = 26, BIND_HIT = 14, BLOOM_START = 17, BLOOM_END = 25;
    public static final double LASH_RADIUS = 0.5, BIND_RADIUS = 2, BLOOM_RADIUS = 6, WAVE_WIDTH = 0.65, WAVE_HEIGHT = 0.85;
    public static final int[] DURATION = {0, 42, 25, 34};
    private static final EntityDataAccessor<Byte> SKILL = SynchedEntityData.defineId(TidecrownAnemoneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> START = SynchedEntityData.defineId(TidecrownAnemoneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEQUENCE = SynchedEntityData.defineId(TidecrownAnemoneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> AIM_YAW = SynchedEntityData.defineId(TidecrownAnemoneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LEFT_YAW = SynchedEntityData.defineId(TidecrownAnemoneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RIGHT_YAW = SynchedEntityData.defineId(TidecrownAnemoneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> POINT = SynchedEntityData.defineId(TidecrownAnemoneEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> LEFT_END = SynchedEntityData.defineId(TidecrownAnemoneEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> RIGHT_END = SynchedEntityData.defineId(TidecrownAnemoneEntity.class, EntityDataSerializers.VECTOR3);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.tidecrown_anemone.idle");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.tidecrown_anemone.death");
    private static final RawAnimation[] ACTIONS = {IDLE, RawAnimation.begin().thenPlay("animation.tidecrown_anemone.lash"),
        RawAnimation.begin().thenPlay("animation.tidecrown_anemone.bind"), RawAnimation.begin().thenPlay("animation.tidecrown_anemone.bloom")};
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private final Set<Integer> waveHit = new HashSet<>();
    private int cooldown = 30, lashCd, bindCd, bloomCd = 120, seenSequence = -1;

    public TidecrownAnemoneEntity(EntityType<? extends TidecrownAnemoneEntity> type, Level level) {
        super(type, level);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 36).add(Attributes.ARMOR, 4)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.MOVEMENT_SPEED, 0)
            .add(Attributes.FOLLOW_RANGE, 14).add(Attributes.ATTACK_DAMAGE, 5);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(SKILL, NONE); b.define(START, 0); b.define(SEQUENCE, 0); b.define(AIM_YAW, 0F);
        b.define(LEFT_YAW, 0F); b.define(RIGHT_YAW, 0F);
        b.define(POINT, new Vector3f()); b.define(LEFT_END, new Vector3f()); b.define(RIGHT_END, new Vector3f());
    }

    @Override protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public byte skill() { return entityData.get(SKILL); }
    public float skillAge(float partial) { return Math.max(0, (int) level().getGameTime() - entityData.get(START) + partial); }
    public float aimYaw() { return entityData.get(AIM_YAW); }
    public Vec3 mark() { Vector3f p = entityData.get(POINT); return new Vec3(p.x, p.y, p.z); }
    public Vec3 lashEnd(int side) { Vector3f p = entityData.get(side == 0 ? LEFT_END : RIGHT_END); return new Vec3(p.x, p.y, p.z); }
    private float lashYaw(int side) { return entityData.get(side == 0 ? LEFT_YAW : RIGHT_YAW); }
    public Vec3 lashStart(int side) { return local(lashYaw(side), side == 0 ? 0.20 : -0.20, 1.27, -0.35); }
    public Vec3 lashTip(int side) { return local(lashYaw(side), side == 0 ? -0.027 : 0.027, 1.59, 3.98); }
    public static Vec3 local(float yaw, double right, double up, double forward) {
        double a = Math.toRadians(yaw);
        return new Vec3(-Math.cos(a) * right - Math.sin(a) * forward, up, -Math.sin(a) * right + Math.cos(a) * forward);
    }
    public static double waveRadius(float age) { return 0.8 + 5.2 * Mth.clamp((age - BLOOM_START) / (BLOOM_END - BLOOM_START), 0, 1); }
    public static double horizontalDistanceToBox(Vec3 p, AABB b) {
        double x = Math.max(Math.max(b.minX - p.x, 0), p.x - b.maxX);
        double z = Math.max(Math.max(b.minZ - p.z, 0), p.z - b.maxZ);
        return Math.hypot(x, z);
    }

    @Override public void travel(Vec3 input) {
        super.travel(Vec3.ZERO);
        setDeltaMovement(0, getDeltaMovement().y, 0);
    }
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity other) {}

    @Override public void tick() {
        super.tick();
        if (skill() != NONE && !isDeadOrDying()) {
            setYRot(aimYaw()); yBodyRot = aimYaw(); yHeadRot = aimYaw();
        }
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        getNavigation().stop();
        if (cooldown > 0) cooldown--;
        if (lashCd > 0) lashCd--;
        if (bindCd > 0) bindCd--;
        if (bloomCd > 0) bloomCd--;
        if (isDeadOrDying()) return;
        if (skill() != NONE) {
            int age = (int) skillAge(0);
            if (skill() == LASH && age <= LASH_RIGHT) trackLash(age);
            setYRot(aimYaw()); yBodyRot = aimYaw(); yHeadRot = aimYaw();
            if (skill() == LASH && (age == LASH_LEFT || age == LASH_RIGHT)) lash(age == LASH_LEFT ? 0 : 1);
            if (skill() == BIND && age == BIND_HIT) bind();
            if (skill() == BLOOM && age >= BLOOM_START && age <= BLOOM_END) wave(age);
            if (age >= DURATION[skill()]) { entityData.set(SKILL, NONE); cooldown = 26; }
            return;
        }
        LivingEntity target = getTarget();
        if (target == null) return;
        if (!canHit(target) || distanceToSqr(target) > 196) { setTarget(null); return; }
        if (cooldown > 0 || !onGround() || level().getDifficulty() == Difficulty.PEACEFUL || !hasLineOfSight(target)) return;
        double d = target.position().subtract(position()).horizontalDistance();
        if (bloomCd == 0 && d <= 6 && Math.abs(target.getY() - getY()) < 2) start(BLOOM, target, null);
        else if (lashCd == 0 && d <= 4.6 && target.getBoundingBox().maxY >= getY() + 0.9 && target.getY() <= getY() + 1.9) start(LASH, target, null);
        else if (bindCd == 0 && d <= 11) {
            Vec3 from = target.position().add(0, 0.4, 0);
            var hit = level().clip(new ClipContext(from, from.add(0, -4, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.BLOCK && Math.abs(hit.getLocation().y - getY()) <= 3) start(BIND, target, hit.getLocation());
        }
    }

    private void start(byte action, LivingEntity target, Vec3 ground) {
        Vec3 delta = target.position().subtract(position());
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        entityData.set(AIM_YAW, yaw);
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
        if (action == BIND && ground != null) {
            Vec3 p = ground.subtract(position()).add(0, 0.035, 0);
            entityData.set(POINT, vector(p));
        }
        updateLashAim(0);
        updateLashAim(1);
        entityData.set(START, (int) level().getGameTime());
        entityData.set(SKILL, action);
        entityData.set(SEQUENCE, entityData.get(SEQUENCE) + 1);
        waveHit.clear();
        if (action == LASH) lashCd = 95;
        if (action == BIND) bindCd = 170;
        if (action == BLOOM) bloomCd = 240;
        playSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.2F, action == BLOOM ? 0.55F : 1.1F);
    }

    private static Vector3f vector(Vec3 v) { return new Vector3f((float) v.x, (float) v.y, (float) v.z); }
    private void trackLash(int age) {
        LivingEntity target = getTarget();
        if (target != null && canHit(target) && distanceToSqr(target) <= 196 && hasLineOfSight(target)) {
            Vec3 delta = target.position().subtract(position());
            if (delta.horizontalDistanceSqr() > 1.0E-6) {
                float wanted = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
                float turn = Mth.clamp(Mth.wrapDegrees(wanted - aimYaw()), -18F, 18F);
                entityData.set(AIM_YAW, aimYaw() + turn);
            }
        }
        if (age <= LASH_LEFT) updateLashAim(0);
        if (age <= LASH_RIGHT) updateLashAim(1);
    }
    private void updateLashAim(int side) {
        entityData.set(side == 0 ? LEFT_YAW : RIGHT_YAW, aimYaw());
        Vec3 begin = position().add(lashStart(side));
        Vec3 end = position().add(local(lashYaw(side), side == 0 ? -0.05 : 0.05, 1.59, 4.55));
        Vec3 clipped = level().clip(new ClipContext(begin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getLocation();
        entityData.set(side == 0 ? LEFT_END : RIGHT_END, vector(clipped.subtract(position())));
    }
    private boolean canHit(LivingEntity v) {
        return v != this && v.isAlive() && !(v instanceof TidecrownAnemoneEntity) && !isAlliedTo(v)
            && !(v instanceof Player p && (p.isCreative() || p.isSpectator()));
    }
    private boolean clearPath(Vec3 source, LivingEntity v) {
        Vec3 middle = v.getBoundingBox().getCenter();
        return level().clip(new ClipContext(source, middle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    private void lash(int side) {
        Vec3 from = position().add(lashStart(side)), to = position().add(lashEnd(side));
        if (Boolean.getBoolean("hp_end_expansion.debugTidecrownAim"))
            com.mojang.logging.LogUtils.getLogger().info("Tidecrown lash: side={}, age={}, yaw={}, from={}, to={}", side, skillAge(0), lashYaw(side), from, to);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(LASH_RADIUS), this::canHit)) {
            AABB box = v.getBoundingBox().inflate(LASH_RADIUS);
            if ((box.contains(from) || box.clip(from, to).isPresent()) && clearPath(from, v)) hit(v, 5, to.subtract(from), 0.25);
        }
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, side == 0 ? 0.8F : 1.05F);
        playSound(SoundEvents.GENERIC_SPLASH, 1.0F, 1.4F);
    }
    private void bind() {
        Vec3 c = position().add(mark());
        AABB area = new AABB(c.x - BIND_RADIUS, c.y, c.z - BIND_RADIUS, c.x + BIND_RADIUS, c.y + 1.9, c.z + BIND_RADIUS);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, area, this::canHit)) {
            if (horizontalDistanceToBox(c, v.getBoundingBox()) <= BIND_RADIUS && clearPath(c.add(0, 0.4, 0), v)) {
                if (v.hurt(damageSources().mobAttack(this), 4)) v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
        }
        playSound(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.6F, 0.75F);
    }
    private void wave(int age) {
        double outer = waveRadius(age), inner = Math.max(0, waveRadius(age - 1) - WAVE_WIDTH);
        Vec3 c = position();
        AABB area = new AABB(c.x - outer, c.y + 0.05, c.z - outer, c.x + outer, c.y + WAVE_HEIGHT, c.z + outer);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, area, this::canHit)) {
            AABB b = v.getBoundingBox();
            double farX = Math.max(Math.abs(b.minX - c.x), Math.abs(b.maxX - c.x));
            double farZ = Math.max(Math.abs(b.minZ - c.z), Math.abs(b.maxZ - c.z));
            if (horizontalDistanceToBox(c, b) <= outer && Math.hypot(farX, farZ) >= inner && !waveHit.contains(v.getId())
                    && clearPath(c.add(0, 0.4, 0), v)) {
                waveHit.add(v.getId()); hit(v, 7, v.position().subtract(c), 0.55);
            }
        }
        if (age == BLOOM_START) { playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.1F, 1.65F); playSound(SoundEvents.GENERIC_SPLASH, 2F, 0.65F); }
    }
    private void hit(LivingEntity v, float damage, Vec3 dir, double push) {
        if (!v.hurt(damageSources().mobAttack(this), damage)) return;
        Vec3 horizontal = new Vec3(dir.x, 0, dir.z).normalize().scale(push);
        v.setDeltaMovement(v.getDeltaMovement().add(horizontal).add(0, 0.12, 0)); v.hurtMarked = true;
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (skill() == BLOOM && skillAge(0) > BLOOM_END) amount *= 1.3F;
        return super.hurt(source, amount);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("TidecrownBloomCooldown", bloomCd); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); bloomCd = Math.max(40, tag.getInt("TidecrownBloomCooldown")); cooldown = 30;
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SyncedController(this, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            if (seenSequence != entityData.get(SEQUENCE)) { seenSequence = entityData.get(SEQUENCE); state.getController().forceAnimationReset(); }
            return state.setAndContinue(ACTIONS[skill()]);
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    private static final class SyncedController extends AnimationController<TidecrownAnemoneEntity> {
        SyncedController(TidecrownAnemoneEntity entity, AnimationStateHandler<TidecrownAnemoneEntity> handler) { super(entity, "action", 0, handler); }
        @Override protected double adjustTick(double tick) {
            if (animatable.skill() != NONE && !animatable.isDeadOrDying() && !shouldResetTick && getAnimationState() == State.RUNNING) {
                return animatable.skillAge((float) (tick - Math.floor(tick)));
            }
            return super.adjustTick(tick);
        }
    }
}
