package org.hp.hp_end_expansion.entity.starwreck;

import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.config.CombatConfigs;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * 逐星兽（设计文档第 4 节）：出场 → 咬合 / 践踏震荡波 / 彗星冲锋 / 甩尾 → 半血嚎叫进入召星阶段 → 1/4 血过载。
 * 动作由服务端状态机驱动，STATE + ACTION 计数同步给客户端播动画和特效。
 * 星雨生成的不会自然消失，但 32 格内 60 秒没有可攻击的玩家就蜷身化成陨星飞走，不掉落。
 */
public final class StarChaserEntity extends Monster implements GeoEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final byte NONE = 0, EMERGE = 1, BITE = 2, WINDUP = 3, CHARGE = 4, STAGGER = 5, SWEEP = 6, HOWL = 7, SUMMON = 8, LEAVE = 9, STOMP = 10;
    // 各动作时长比动画长 2 拍，留给 action 控制器的过渡；命中帧同样后移 2 拍
    private static final int[] DURATION = {0, 42, 14, 18, 18, 22, 18, 42, 22, 44, 24};
    public static final int BITE_HIT = 8, WINDUP_TRACK = 8, SWEEP_HIT = 10, HOWL_ROAR = 12, SUMMON_CAST = 12, LEAVE_LIFT = 18;
    // 践踏：人立期间追踪目标，砸地前 3 拍锁定方向；震荡波从爪下往外扩 WAVE_TICKS 拍
    public static final int STOMP_HIT = 14, STOMP_WAVE_TICKS = 10;
    public static final double STOMP_REACH = 1.5, STOMP_RADIUS = 2.4, WAVE_START = 1.5, WAVE_RADIUS = 7, WAVE_RADIUS_OVERLOAD = 8.5;
    private static final int LEAVE_AFTER = 20 * 60;
    private static final double LEAVE_RANGE = 32;
    public static final double CHARGE_SPEED = 0.85, CHARGE_RANGE = 12;
    private static final int BITE_CD = 24, CHARGE_CD = 160, SWEEP_CD = 80, SUMMON_CD = 240, STOMP_CD = 100;
    private static final float BITE_DAMAGE = 8;
    private static final ResourceLocation OVERLOAD_SPEED = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "star_chaser_overload");

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(StarChaserEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(StarChaserEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(StarChaserEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> AIM = SynchedEntityData.defineId(StarChaserEntity.class, EntityDataSerializers.VECTOR3);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.star_chaser.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.star_chaser.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.star_chaser.run");
    private static final double RUN_SPEED = 0.2;
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.star_chaser.death");
    private static final RawAnimation[] ACTIONS = {
        null,
        RawAnimation.begin().thenPlayAndHold("animation.star_chaser.emerge"),
        RawAnimation.begin().thenPlay("animation.star_chaser.bite"),
        RawAnimation.begin().thenPlayAndHold("animation.star_chaser.charge_windup"),
        RawAnimation.begin().thenLoop("animation.star_chaser.charge"),
        RawAnimation.begin().thenPlay("animation.star_chaser.stagger"),
        RawAnimation.begin().thenPlay("animation.star_chaser.tail_sweep"),
        RawAnimation.begin().thenPlay("animation.star_chaser.howl"),
        RawAnimation.begin().thenPlay("animation.star_chaser.summon"),
        // 飞走：先伏低蓄力，再保持冲锋姿态腾空
        RawAnimation.begin().thenPlay("animation.star_chaser.charge_windup").thenLoop("animation.star_chaser.charge"),
        RawAnimation.begin().thenPlay("animation.star_chaser.stomp"),
    };
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    private int stateTicks, biteCd, chargeCd, sweepCd, summonCd, stompCd, behindTicks, aloneTicks;
    private boolean howled, fromStarRain;
    private Vec3 chargeStart = Vec3.ZERO, lastEmber = Vec3.ZERO, stompCenter = Vec3.ZERO;
    private final Set<Integer> chargeHits = new HashSet<>();

    // 客户端：当前动作已经过的拍数、冲锋起点、过载爆发计时；骨骼的世界坐标由渲染器每帧写回，供粒子定位
    private int clientAction = -1, clientAge, animAction = -1;
    private byte lastPhase = -1, clientState = NONE;
    public int overloadAge = -1, trailAge = -1;
    public Vec3 vfxOrigin = Vec3.ZERO, trailEnd = Vec3.ZERO;
    @Nullable public Vec3 vfxCore, vfxJaw, vfxPaw, vfxTail;
    public float sweepAngle;
    public int sweepSign = 1;

    public StarChaserEntity(EntityType<? extends StarChaserEntity> type, Level level) {
        super(type, level);
        xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 180)
            .add(Attributes.ARMOR, 6)
            .add(Attributes.ATTACK_DAMAGE, BITE_DAMAGE)
            .add(Attributes.MOVEMENT_SPEED, 0.32)
            .add(Attributes.FOLLOW_RANGE, 32)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, NONE);
        builder.define(ACTION, 0);
        builder.define(PHASE, (byte) 0);
        builder.define(AIM, new Vector3f(0, 0, 1));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CombatGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        Difficulty d = level.getDifficulty();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(d == Difficulty.EASY ? 140 : d == Difficulty.HARD ? 220 : 180);
        setHealth(getMaxHealth());
        chargeCd = 60;
        summonCd = 100;
        startState(EMERGE);
        return result;
    }

    /** 星雨生成：不自然消失，改由脱战飞走。 */
    public void markFromStarRain() {
        fromStarRain = true;
        setPersistenceRequired();
    }

    public byte getState() { return entityData.get(STATE); }
    public byte getPhase() { return entityData.get(PHASE); }
    public boolean isOverloaded() { return getPhase() >= 2; }
    public Vec3 getAim() { Vector3f v = entityData.get(AIM); return new Vec3(v.x, v.y, v.z); }
    public int getClientAge() { return clientAge; }
    public int getActionCount() { return entityData.get(ACTION); }

    public Vec3 forward() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    private void startState(byte state) {
        entityData.set(STATE, state);
        entityData.set(ACTION, entityData.get(ACTION) + 1);
        stateTicks = 0;
        getNavigation().stop();
        switch (state) {
            case EMERGE -> playSound(SoundEvents.WARDEN_EMERGE, 2.0F, 1.4F);
            case BITE -> {
                aimAt(getTarget());
                faceAim();
                playSound(SoundEvents.RAVAGER_ATTACK, 1.4F, 1.3F);
            }
            case WINDUP -> {
                aimAt(getTarget());
                playSound(SoundEvents.RAVAGER_ROAR, 1.6F, 1.5F);
            }
            case CHARGE -> {
                chargeStart = lastEmber = position();
                chargeHits.clear();
                playSound(SoundEvents.FIRECHARGE_USE, 2.0F, 0.6F);
                playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.8F, 1.8F);
            }
            case STAGGER -> {
                playSound(SoundEvents.ANVIL_LAND, 1.4F, 0.5F);
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.6F);
            }
            case SWEEP -> playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.8F, 0.55F);
            case STOMP -> {
                aimAt(getTarget());
                chargeHits.clear();
                playSound(SoundEvents.RAVAGER_ROAR, 1.8F, 1.15F);
            }
            case HOWL -> playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.7F);
            case SUMMON -> playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.6F);
            case LEAVE -> {
                setTarget(null);
                playSound(SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.8F);
            }
            default -> {}
        }
        if (state != NONE) {
            LivingEntity target = getTarget();
            LOGGER.debug("Star chaser action started: entity={}, action={}, state={}, target={}, bodyYaw={}, aim={}",
                getId(), getActionCount(), state, target == null ? -1 : target.getId(), yBodyRot, getAim());
        }
    }

    private void aimAt(@Nullable LivingEntity target) {
        Vec3 to = target != null ? target.position().subtract(position()) : forward();
        to = new Vec3(to.x, 0, to.z);
        if (to.lengthSqr() < 1.0E-4) to = forward();
        Vec3 n = to.normalize();
        entityData.set(AIM, new Vector3f((float) n.x, 0, (float) n.z));
    }

    private void faceAim() {
        Vec3 aim = getAim();
        float targetYaw = (float) (Mth.atan2(aim.z, aim.x) * Mth.RAD_TO_DEG) - 90;
        float yaw = getYRot() + Mth.wrapDegrees(targetYaw - getYRot());
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    public void tick() {
        super.tick();
        if (getState() == BITE && !isDeadOrDying()) faceAim();
        if (level().isClientSide) clientTick();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        int cd = isOverloaded() ? 2 : 1;
        biteCd -= cd;
        chargeCd -= cd;
        sweepCd -= cd;
        summonCd -= cd;
        stompCd -= cd;
        byte state = getState();
        if (state == NONE) {
            if (fromStarRain && tickCount % 20 == 0) {
                boolean watched = level().getNearestPlayer(getX(), getY(), getZ(), LEAVE_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR) != null;
                aloneTicks = watched ? 0 : aloneTicks + 20;
                if (aloneTicks >= LEAVE_AFTER) {
                    startState(LEAVE);
                    return;
                }
            }
            if (!checkPhases()) chooseAction();
            return;
        }
        stateTicks++;
        switch (state) {
            case BITE -> {
                if (stateTicks <= BITE_HIT) aimAt(getTarget());
                faceAim();
                if (stateTicks == BITE_HIT - 2) setDeltaMovement(getDeltaMovement().add(forward().scale(0.45)));
                if (stateTicks == BITE_HIT) biteHit();
            }
            case WINDUP -> {
                if (stateTicks <= WINDUP_TRACK) aimAt(getTarget());
                faceAim();
            }
            case CHARGE -> {
                tickCharge();
                return;
            }
            case SWEEP -> { if (stateTicks == SWEEP_HIT) sweepHit(); }
            case STOMP -> {
                if (stateTicks <= STOMP_HIT - 3) aimAt(getTarget());
                faceAim();
                setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
                if (stateTicks == STOMP_HIT) stompHit();
                if (stateTicks >= STOMP_HIT && stateTicks <= STOMP_HIT + STOMP_WAVE_TICKS) waveTick(stateTicks - STOMP_HIT);
            }
            case HOWL -> {
                if (stateTicks == 1) entityData.set(PHASE, (byte) Math.max(1, getPhase()));
                if (stateTicks == HOWL_ROAR) playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 1.3F);
            }
            case SUMMON -> { if (stateTicks == SUMMON_CAST) castSummon(); }
            case LEAVE -> {
                if (stateTicks == LEAVE_LIFT) {
                    setNoGravity(true);
                    noPhysics = true;
                    playSound(SoundEvents.FIRECHARGE_USE, 2.5F, 0.5F);
                    playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.6F);
                }
                if (stateTicks >= LEAVE_LIFT) setDeltaMovement(0, Math.min(1.8, 0.15 * (stateTicks - LEAVE_LIFT + 1)), 0);
            }
            default -> {}
        }
        if (getState() == state && stateTicks >= DURATION[state]) {
            // 飞走直接移除，不走死亡流程，也就没有掉落
            if (state == LEAVE) discard();
            else startState(state == WINDUP ? CHARGE : NONE);
        }
    }

    private boolean checkPhases() {
        float hp = getHealth() / getMaxHealth();
        if (!howled && hp <= 0.5F) {
            howled = true;
            startState(HOWL);
            return true;
        }
        if (howled && !isOverloaded() && hp <= 0.25F && level().getDifficulty() != Difficulty.EASY) {
            entityData.set(PHASE, (byte) 2);
            AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && !speed.hasModifier(OVERLOAD_SPEED))
                speed.addPermanentModifier(new AttributeModifier(OVERLOAD_SPEED, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 1.4F);
            playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.6F);
        }
        return false;
    }

    private void chooseAction() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            behindTicks = 0;
            return;
        }
        Vec3 to = target.position().subtract(position());
        double dist = Math.sqrt(to.x * to.x + to.z * to.z);
        Vec3 f = forward();
        double facing = dist < 1.0E-4 ? 1 : (f.x * to.x + f.z * to.z) / dist;
        behindTicks = dist < 4 && facing < -0.1 ? behindTicks + 1 : 0;
        boolean sameHeight = Math.abs(target.getY() - getY()) < 2.5;
        if (behindTicks > 20 && sweepCd <= 0) {
            sweepCd = SWEEP_CD;
            behindTicks = 0;
            startState(SWEEP);
        } else if (dist < 4.5 && sameHeight && stompCd <= 0 && onGround() && random.nextInt(dist < 3.4 && biteCd <= 0 ? 3 : 10) == 0) {
            // 贴身时和撕咬混着出；撕咬冷却时也会补一脚，把绕到身侧的目标震开
            stompCd = STOMP_CD;
            biteCd = Math.max(biteCd, 12);
            startState(STOMP);
        } else if (dist < 3.4 && facing > 0.5 && sameHeight && biteCd <= 0) {
            biteCd = BITE_CD;
            startState(BITE);
        } else if (dist >= 6 && dist <= 16 && chargeCd <= 0 && onGround() && hasLineOfSight(target) && random.nextInt(6) == 0) {
            chargeCd = CHARGE_CD;
            startState(WINDUP);
        } else if (getPhase() >= 1 && summonCd <= 0 && dist <= 24 && hasLineOfSight(target) && random.nextInt(30) == 0) {
            summonCd = SUMMON_CD;
            startState(SUMMON);
        }
    }

    private boolean canHit(LivingEntity e) {
        return e != this && e.isAlive() && (!(e instanceof StarChaserEntity) || e == getTarget())
            && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e);
    }

    private void biteHit() {
        Vec3 f = forward();
        Vec3 mouth = position().add(f.scale(2.4)).add(0, 1.7, 0);
        boolean hit = false;
        int hits = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().expandTowards(f.scale(2)).inflate(0.8), this::canHit)) {
            if (e.getBoundingBox().inflate(0.4).distanceToSqr(mouth) > 1.6 * 1.6) continue;
            if (e.hurt(damageSources().mobAttack(this), CombatConfigs.STAR_CHASER.damage("biteDamage"))) {
                hit = true;
                hits++;
            }
        }
        LivingEntity target = getTarget();
        LOGGER.debug("Star chaser bite resolved: entity={}, target={}, stateTick={}, bodyYaw={}, aim={}, mouth={}, hits={}",
            getId(), target == null ? -1 : target.getId(), stateTicks, yBodyRot, getAim(), mouth, hits);
        playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.4F, hit ? 0.7F : 1.0F);
    }

    private void tickCharge() {
        Vec3 aim = getAim();
        faceAim();
        if (stateTicks > 1 && horizontalCollision && !minorHorizontalCollision) {
            setDeltaMovement(aim.scale(-0.35).add(0, 0.25, 0));
            startState(STAGGER);
            return;
        }
        setDeltaMovement(aim.x * CHARGE_SPEED, getDeltaMovement().y, aim.z * CHARGE_SPEED);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.5), this::canHit)) {
            if (!chargeHits.add(e.getId())) continue;
            if (e.hurt(damageSources().mobAttack(this), CombatConfigs.STAR_CHASER.damage("chargeDamage"))) {
                e.setDeltaMovement(e.getDeltaMovement().add(aim.x * 1.3, 0.65, aim.z * 1.3));
                e.hurtMarked = true;
                playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6F, 0.6F);
            }
        }
        if (level() instanceof ServerLevel server && position().distanceToSqr(lastEmber) >= 4) {
            EmberGroundEntity.spawn(server, position(), StarwreckEntities.EMBER_GROUND.get(), 60);
            lastEmber = position();
        }
        double dx = getX() - chargeStart.x, dz = getZ() - chargeStart.z;
        if (stateTicks >= DURATION[CHARGE] || dx * dx + dz * dz >= CHARGE_RANGE * CHARGE_RANGE) {
            setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
            startState(NONE);
        }
    }

    private void sweepHit() {
        Vec3 f = forward();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.6, 1, 3.6), this::canHit)) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ(), d = Math.sqrt(dx * dx + dz * dz);
            if (d > 4.4 || d > 1.0E-4 && (f.x * dx + f.z * dz) / d > 0.35) continue;
            if (e.hurt(damageSources().mobAttack(this), CombatConfigs.STAR_CHASER.damage("sweepDamage"))) {
                e.knockback(1.4, getX() - e.getX(), getZ() - e.getZ());
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.3, 0));
                e.hurtMarked = true;
            }
        }
    }

    /** 双前爪砸地：爪下一圈重击并掀起；同时定下震荡波圆心。 */
    private void stompHit() {
        stompCenter = position().add(forward().scale(STOMP_REACH));
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(stompCenter, stompCenter).inflate(STOMP_RADIUS, 2, STOMP_RADIUS), this::canHit)) {
            Vec3 away = e.position().subtract(stompCenter).multiply(1, 0, 1);
            if (away.lengthSqr() > STOMP_RADIUS * STOMP_RADIUS) continue;
            chargeHits.add(e.getId());
            if (e.hurt(damageSources().mobAttack(this), CombatConfigs.STAR_CHASER.damage("stompDamage"))) {
                away = away.lengthSqr() < 1.0E-4 ? forward() : away.normalize();
                e.knockback(1.2, -away.x, -away.z);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.55, 0));
                e.hurtMarked = true;
            }
        }
        if (level() instanceof ServerLevel server) {
            BlockPos below = BlockPos.containing(stompCenter.x, getY() - 0.2, stompCenter.z);
            BlockState ground = level().getBlockState(below);
            if (!ground.isAir())
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                    stompCenter.x, getY() + 0.1, stompCenter.z, 40, 1.2, 0.1, 1.2, 0.2);
        }
        playSound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.2F, 0.6F);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 1.5F);
    }

    public double waveRadius(float waveAge) {
        float u = Mth.clamp(waveAge / STOMP_WAVE_TICKS, 0, 1);
        return WAVE_START + ((isOverloaded() ? WAVE_RADIUS_OVERLOAD : WAVE_RADIUS) - WAVE_START) * (1 - (1 - u) * (1 - u));
    }

    /** 震荡波前沿扫过时命中一次；贴地才吃到，跳起来能躲。爪下已被重击的不重复算。 */
    private void waveTick(int waveAge) {
        double r0 = waveAge == 0 ? 0 : waveRadius(waveAge - 1), r1 = waveRadius(waveAge);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(stompCenter, stompCenter).inflate(r1 + 0.5, 2, r1 + 0.5), this::canHit)) {
            if (!e.onGround() || Math.abs(e.getY() - getY()) > 1.5 || chargeHits.contains(e.getId())) continue;
            double dx = e.getX() - stompCenter.x, dz = e.getZ() - stompCenter.z, d = Math.sqrt(dx * dx + dz * dz);
            if (d < r0 - 0.6 || d > r1 + 0.6) continue;
            chargeHits.add(e.getId());
            if (e.hurt(damageSources().mobAttack(this), CombatConfigs.STAR_CHASER.damage("waveDamage"))) {
                double k = d < 1.0E-4 ? 0 : 1 / d;
                e.knockback(0.7, -dx * k, -dz * k);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.4, 0));
                e.hurtMarked = true;
            }
        }
    }

    private void castSummon() {
        if (!(level() instanceof ServerLevel server)) return;
        LivingEntity target = getTarget();
        Vec3 center = target != null ? target.position() : position();
        int count = level().getDifficulty() == Difficulty.HARD ? 5 : 3;
        double start = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            Vec3 p = center;
            if (i > 0) {
                double a = start + i * Math.PI * 2 / (count - 1) + (random.nextDouble() - 0.5) * 0.8, r = 3.5 + random.nextDouble() * 4.5;
                p = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            }
            double y = groundY(level(), p.x, p.z, p.y);
            if (!Double.isNaN(y)) StarMarkEntity.spawn(server, new Vec3(p.x, y, p.z), CombatConfigs.STAR_CHASER.damage("summonStarDamage"));
        }
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 2.5F, 0.5F);
    }

    /** 从 refY 上方 4 格往下找 12 格内第一个可站的面，找不到返回 NaN。 */
    public static double groundY(Level level, double x, double z, double refY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(refY) + 4, Mth.floor(z));
        for (int i = 0; i < 12; i++) {
            BlockPos below = pos.below();
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
                return pos.getY();
            pos.move(Direction.DOWN);
        }
        return Double.NaN;
    }

    // 地面预警线、冲锋拖尾、嚎叫冲击环都远超碰撞箱
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(12, 1, 12); }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        byte state = getState();
        if ((state == EMERGE || state == HOWL || state == LEAVE) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, isOverloaded() ? amount * 1.25F : amount);
    }

    // ---- 客户端粒子 ----

    private void clientTick() {
        int action = entityData.get(ACTION);
        byte state = getState();
        if (action != clientAction) {
            // 冲锋结束后拖尾再亮 10 拍
            if (clientState == CHARGE) {
                trailEnd = position();
                trailAge = 0;
            }
            clientAction = action;
            clientState = state;
            clientAge = 0;
            if (state == CHARGE || state == WINDUP || state == LEAVE) vfxOrigin = position();
        } else clientAge++;
        if (trailAge >= 0 && ++trailAge > 10) trailAge = -1;
        byte phase = getPhase();
        if (phase >= 2 && lastPhase >= 0 && lastPhase < 2) overloadAge = 0;
        else if (overloadAge >= 0 && ++overloadAge > 40) overloadAge = -1;
        lastPhase = phase;
        if (isDeadOrDying()) return;

        Vec3 f = forward();
        Vec3 core = vfxCore != null ? vfxCore : position().add(f.scale(1.2)).add(0, 1.35, 0);
        Vec3 jaw = vfxJaw != null ? vfxJaw : position().add(f.scale(2.3)).add(0, 1.8, 0);
        Vec3 tail = vfxTail != null ? vfxTail : position().add(f.scale(-3.6)).add(0, 1.6, 0);
        int age = clientAge;
        switch (state) {
            case EMERGE -> {
                if (age < 26) burst(ModParticles.STAR_DEBRIS.get(), position(), 2, 1.2, 0.1, 0.12);
                if (age == 26) burst(ModParticles.STAR_EMBER.get(), position().add(0, 0.3, 0), 30, 1.0, 0.25, 0.3);
            }
            case WINDUP -> {
                Vec3 paw = vfxPaw != null ? vfxPaw : position().add(f.scale(0.9)).add(0, 0.1, 0);
                if (age == 5 || age == 11) {
                    burst(ModParticles.STAR_EMBER.get(), paw, 10, 0.2, 0.15, 0.2);
                    burst(ModParticles.STAR_DEBRIS.get(), paw, 5, 0.2, 0.2, 0.15);
                }
                if (random.nextBoolean()) level().addParticle(ModParticles.STAR_EMBER.get(), tail.x + random.nextGaussian() * 0.3, tail.y, tail.z + random.nextGaussian() * 0.3,
                    random.nextGaussian() * 0.02, 0.12 + random.nextDouble() * 0.1, random.nextGaussian() * 0.02);
            }
            case CHARGE -> {
                Vec3 side = new Vec3(-f.z, 0, f.x);
                for (int i = 0; i < 3; i++) {
                    double s = random.nextBoolean() ? 0.7 : -0.7;
                    level().addParticle(ModParticles.STAR_EMBER.get(), getX() + side.x * s + random.nextGaussian() * 0.2, getY() + 0.1, getZ() + side.z * s + random.nextGaussian() * 0.2,
                        side.x * s * 0.12 - f.x * 0.1, 0.05 + random.nextDouble() * 0.08, side.z * s * 0.12 - f.z * 0.1);
                }
                level().addParticle(ModParticles.STAR_DEBRIS.get(), getX(), getY() + 0.2, getZ(), random.nextGaussian() * 0.1, 0.15, random.nextGaussian() * 0.1);
                level().addParticle(ParticleTypes.END_ROD, core.x + random.nextGaussian() * 0.4, core.y + random.nextGaussian() * 0.4, core.z + random.nextGaussian() * 0.4,
                    -f.x * 0.3, 0.02, -f.z * 0.3);
            }
            case BITE -> {
                if (age == BITE_HIT) {
                    LOGGER.debug("Star chaser bite VFX: entity={}, age={}, bodyYaw={}, mouth={}, offset={}, tracked={}",
                        getId(), age, yBodyRot, jaw, jaw.subtract(position()), vfxJaw != null);
                    burst(ModParticles.STAR_EMBER.get(), jaw, 16, 0.15, 0.25, 0.25);
                    burst(ParticleTypes.CRIT, jaw, 8, 0.2, 0.4, 0.3);
                }
            }
            case STAGGER -> {
                if (age == 1) {
                    burst(ModParticles.STAR_DEBRIS.get(), jaw, 18, 0.4, 0.35, 0.3);
                    burst(ModParticles.STAR_EMBER.get(), jaw, 20, 0.4, 0.3, 0.3);
                    level().addParticle(ParticleTypes.EXPLOSION, jaw.x, jaw.y, jaw.z, 0, 0, 0);
                }
                if (age < 16 && age % 3 == 0) level().addParticle(ParticleTypes.CRIT, jaw.x, jaw.y + 0.6, jaw.z, random.nextGaussian() * 0.1, 0.1, random.nextGaussian() * 0.1);
            }
            case STOMP -> {
                if (age >= 4 && age < STOMP_HIT) {
                    // 人立时火星往抬起的前爪收拢
                    Vec3 paw = vfxPaw != null ? vfxPaw : position().add(f.scale(1.4)).add(0, 1.6, 0);
                    double a = random.nextDouble() * Math.PI * 2;
                    Vec3 from = paw.add(Math.cos(a) * 1.2, random.nextGaussian() * 0.4, Math.sin(a) * 1.2);
                    Vec3 v = paw.subtract(from).scale(0.18);
                    level().addParticle(ModParticles.STAR_EMBER.get(), from.x, from.y, from.z, v.x, v.y, v.z);
                }
                if (age == STOMP_HIT) {
                    vfxOrigin = position().add(f.scale(STOMP_REACH));
                    burst(ModParticles.STAR_DEBRIS.get(), vfxOrigin, 24, 0.8, 0.3, 0.45);
                    burst(ModParticles.STAR_EMBER.get(), vfxOrigin.add(0, 0.2, 0), 30, 0.6, 0.35, 0.3);
                }
                int wave = age - STOMP_HIT;
                if (wave >= 0 && wave <= STOMP_WAVE_TICKS) {
                    double r = waveRadius(wave);
                    for (int i = 0; i < 6; i++) {
                        double a = random.nextDouble() * Math.PI * 2;
                        level().addParticle(ModParticles.STAR_EMBER.get(), vfxOrigin.x + Math.cos(a) * r, getY() + 0.1, vfxOrigin.z + Math.sin(a) * r,
                            Math.cos(a) * 0.12, 0.08 + random.nextDouble() * 0.1, Math.sin(a) * 0.12);
                    }
                    if (wave % 3 == 0) {
                        double a = random.nextDouble() * Math.PI * 2;
                        level().addParticle(ModParticles.STAR_DEBRIS.get(), vfxOrigin.x + Math.cos(a) * r, getY() + 0.1, vfxOrigin.z + Math.sin(a) * r,
                            Math.cos(a) * 0.1, 0.25, Math.sin(a) * 0.1);
                    }
                }
            }
            case SWEEP -> {
                if (age >= SWEEP_HIT - 3 && age <= SWEEP_HIT + 2) {
                    Vec3 v = new Vec3(tail.x - xo, 0, tail.z - zo);
                    for (int i = 0; i < 5; i++)
                        level().addParticle(ModParticles.STAR_EMBER.get(), tail.x + random.nextGaussian() * 0.5, tail.y + random.nextGaussian() * 0.4, tail.z + random.nextGaussian() * 0.5,
                            v.z * 0.08 + random.nextGaussian() * 0.08, 0.05 + random.nextDouble() * 0.1, -v.x * 0.08 + random.nextGaussian() * 0.08);
                }
            }
            case HOWL -> {
                if (age >= HOWL_ROAR && age < 36) {
                    for (int i = 0; i < 3; i++) {
                        double a = random.nextDouble() * Math.PI * 2, r = 1.5 + random.nextDouble() * 2.5;
                        level().addParticle(ModParticles.STAR_EMBER.get(), getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r,
                            -Math.sin(a) * 0.1, 0.2 + random.nextDouble() * 0.2, Math.cos(a) * 0.1);
                    }
                    if (age % 6 == 0) burst(ModParticles.STAR_DEBRIS.get(), position(), 8, 1.6, 0.25, 0.3);
                }
            }
            case SUMMON -> {
                if (age >= 4 && age < SUMMON_CAST) {
                    double a = random.nextDouble() * Math.PI * 2;
                    Vec3 from = core.add(Math.cos(a) * 1.8, random.nextGaussian() * 0.6, Math.sin(a) * 1.8);
                    Vec3 v = core.subtract(from).scale(0.2);
                    level().addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, v.x, v.y, v.z);
                }
                if (age == SUMMON_CAST) {
                    burst(ModParticles.STAR_EMBER.get(), core, 24, 0.2, 0.35, 0.35);
                    for (int i = 0; i < 12; i++)
                        level().addParticle(ParticleTypes.END_ROD, core.x, core.y, core.z, random.nextGaussian() * 0.05, 0.5 + random.nextDouble() * 0.4, random.nextGaussian() * 0.05);
                }
            }
            case LEAVE -> {
                if (age < LEAVE_LIFT) {
                    // 伏低时火星往胸核收拢
                    double a = random.nextDouble() * Math.PI * 2;
                    Vec3 from = core.add(Math.cos(a) * 2.2, random.nextGaussian() * 0.8, Math.sin(a) * 2.2);
                    Vec3 v = core.subtract(from).scale(0.15);
                    level().addParticle(ModParticles.STAR_EMBER.get(), from.x, from.y, from.z, v.x, v.y, v.z);
                } else {
                    if (age == LEAVE_LIFT) {
                        burst(ModParticles.STAR_DEBRIS.get(), position(), 20, 1.0, 0.3, 0.35);
                        burst(ModParticles.STAR_EMBER.get(), position().add(0, 0.2, 0), 30, 1.0, 0.3, 0.2);
                    }
                    for (int i = 0; i < 3; i++)
                        level().addParticle(ModParticles.STAR_EMBER.get(), core.x + random.nextGaussian() * 0.4, core.y - 0.5, core.z + random.nextGaussian() * 0.4,
                            random.nextGaussian() * 0.05, -0.3 - random.nextDouble() * 0.3, random.nextGaussian() * 0.05);
                }
            }
            default -> {}
        }
        if (overloadAge == 1) {
            burst(ModParticles.STAR_EMBER.get(), core, 40, 0.3, 0.45, 0.45);
            level().addParticle(ParticleTypes.FLASH, core.x, core.y, core.z, 0, 0, 0);
        }
        if (isOverloaded() && random.nextInt(2) == 0) {
            double x = getX() + random.nextGaussian() * 0.6, y = getY() + 0.8 + random.nextDouble() * 1.1, z = getZ() + random.nextGaussian() * 0.6;
            level().addParticle(ModParticles.STAR_EMBER.get(), x, y, z, random.nextGaussian() * 0.02, 0.04 + random.nextDouble() * 0.05, random.nextGaussian() * 0.02);
        }
    }

    private void burst(ParticleOptions type, Vec3 at, int count, double spread, double speed, double up) {
        for (int i = 0; i < count; i++)
            level().addParticle(type, at.x + random.nextGaussian() * spread, at.y + random.nextDouble() * spread * 0.5, at.z + random.nextGaussian() * spread,
                random.nextGaussian() * speed, random.nextDouble() * up, random.nextGaussian() * speed);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level().isClientSide() && deathTime <= 16) {
            // 彗尾熄灭时从尾根散出火星
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            double x = getX() + Mth.sin(yaw) * 1.95, z = getZ() - Mth.cos(yaw) * 1.95;
            for (int i = 0; i < 3; i++)
                level().addParticle(ModParticles.STAR_ASH.get(), x + random.nextGaussian() * 0.45, getY() + 1.35 + random.nextGaussian() * 0.3,
                    z + random.nextGaussian() * 0.3, random.nextGaussian() * 0.04, 0.03 + random.nextFloat() * 0.05, random.nextGaussian() * 0.04);
        }
        if (deathTime >= 30 && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Phase", getPhase());
        tag.putBoolean("Howled", howled);
        tag.putBoolean("FromStarRain", fromStarRain);
        tag.putInt("AloneTicks", aloneTicks);
        tag.putInt("ChargeCooldown", chargeCd);
        tag.putInt("SummonCooldown", summonCd);
        tag.putInt("StompCooldown", stompCd);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PHASE, tag.getByte("Phase"));
        howled = tag.getBoolean("Howled");
        fromStarRain = tag.getBoolean("FromStarRain");
        aloneTicks = tag.getInt("AloneTicks");
        chargeCd = tag.getInt("ChargeCooldown");
        summonCd = tag.getInt("SummonCooldown");
        stompCd = tag.getInt("StompCooldown");
        // 飞走中途存档时状态不保存，读档后别悬在半空
        setNoGravity(false);
    }

    // ---- 动画 ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 3, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            if (!state.isMoving()) return state.setAndContinue(IDLE);
            return state.setAndContinue(Mth.lengthSquared(getX() - xo, getZ() - zo) > RUN_SPEED * RUN_SPEED ? RUN : WALK);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> {
            byte s = getState();
            if (isDeadOrDying() || s <= NONE || s >= ACTIONS.length) {
                animAction = -1;
                return PlayState.STOP;
            }
            int action = entityData.get(ACTION);
            if (action != animAction) {
                animAction = action;
                state.getController().forceAnimationReset();
                LOGGER.debug("Star chaser animation started: entity={}, action={}, state={}", getId(), action, s);
            }
            return state.setAndContinue(ACTIONS[s]);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** 追击 / 原地站定；动作期间锁住移动和朝向，由状态机接管。 */
    private final class CombatGoal extends Goal {
        private int repath;

        CombatGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() {
            LivingEntity target = getTarget();
            return getState() != NONE || target != null && target.isAlive();
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void stop() { getNavigation().stop(); }

        @Override public void tick() {
            LivingEntity target = getTarget();
            byte state = getState();
            if (state != NONE) {
                getNavigation().stop();
                if (target != null && (state == BITE && stateTicks < BITE_HIT || state == SUMMON || state == HOWL))
                    getLookControl().setLookAt(target, 30, 30);
                return;
            }
            if (target == null) return;
            getLookControl().setLookAt(target, 30, 30);
            if (distanceToSqr(target) > 2.6 * 2.6) {
                if (--repath <= 0) {
                    getNavigation().moveTo(target, 1.1);
                    repath = 4 + random.nextInt(4);
                }
            } else getNavigation().stop();
        }
    }
}
