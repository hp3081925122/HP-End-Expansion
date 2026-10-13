package org.hp.hp_end_expansion.entity.tidelight;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.config.CombatConfigs;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;

public final class TidebreakerWarlordEntity extends Monster implements GeoEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG = Boolean.getBoolean("hp_end_expansion.debugTidebreakerWarlord");

    // 技能编号与动作长度、冷却
    public static final int NONE = 0, COMBO = 1, SLAM = 2, SPIN = 3, RAM = 4, STAGGER = 5, GUARD = 6, ROAR = 7, SURGE = 8;
    private static final String[] NAMES = {"idle", "combo", "slam", "spin", "ram", "stagger", "guard", "roar", "surge"};
    public static final int[] LENGTH = {0, 46, 50, 36, 46, 32, 34, 44, 50};
    private static final int[] COOLDOWN = {0, 50, 120, 90, 160, 0, 200, 0, 220};

    // 技能关键帧
    public static final int COMBO_SWEEP = 12, COMBO_HOOK = 20, COMBO_SMASH = 32;
    public static final int SLAM_IMPACT = 22, WAVE_START = 23, WAVE_END = 33;
    public static final int SPIN_START = 14, SPIN_END = 19;
    public static final int RAM_START = 18, RAM_END = 34;
    public static final int GUARD_START = 5, GUARD_COUNTER = 22;
    public static final int ROAR_PUSH = 12;
    public static final int SURGE_LOCK = 12, SURGE_SMASH = 16, SURGE_ERUPT = 18;

    // 技能范围
    public static final double SMASH_RADIUS = 1.8, SLAM_RADIUS = 2.4, WAVE_MAX = 8, SPIN_RADIUS = 4.2, RAM_LENGTH = 10.5, ROAR_RADIUS = 6;
    public static final int SURGE_LINES = 3, SURGE_COUNT = 6;
    public static final double SURGE_SPREAD = 24, SURGE_START = 3.5, SURGE_STEP = 1.8, GEYSER_RADIUS = 1.0;

    private static final java.util.UUID PHASE_SPEED = java.util.UUID.nameUUIDFromBytes((Hp_end_expansion.MODID + ":tidebreaker_warlord_phase_speed").getBytes(java.nio.charset.StandardCharsets.UTF_8));

    // 同步字段
    private static final EntityDataAccessor<Integer> SKILL = data(EntityDataSerializers.INT), START = data(EntityDataSerializers.INT),
        SEQUENCE = data(EntityDataSerializers.INT), GUARD_TIME = data(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YAW = data(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> ENRAGED = data(EntityDataSerializers.BOOLEAN);

    private static <T> EntityDataAccessor<T> data(EntityDataSerializer<T> serializer) {
        return SynchedEntityData.defineId(TidebreakerWarlordEntity.class, serializer);
    }

    // 动画资源
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.tidebreaker_warlord.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.tidebreaker_warlord.walk");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.tidebreaker_warlord.hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.tidebreaker_warlord.death");
    private static final RawAnimation[] ACTIONS = new RawAnimation[NAMES.length];

    static {
        // 技能动作与编号一一对应
        ACTIONS[NONE] = IDLE;
        for (int i = 1; i < NAMES.length; i++) ACTIONS[i] = RawAnimation.begin().thenPlay("animation.tidebreaker_warlord." + NAMES[i]);
    }

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private final int[] cooldowns = new int[NAMES.length];
    private final Set<Integer> hitIds = new HashSet<>();
    private int pause = 20, seenSequence = -1, lostSight, lastFrontHit = -100, frontHits, lastSkill;

    public TidebreakerWarlordEntity(EntityType<? extends TidebreakerWarlordEntity> type, Level level) {
        super(type, level);
        xpReward = 25;
    }

    // 精英怪基础属性，实际数值由战斗配置覆盖
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 170)
            .add(Attributes.ARMOR, 10)
            .add(Attributes.MOVEMENT_SPEED, 0.27)
            .add(Attributes.ATTACK_DAMAGE, 10)
            .add(Attributes.FOLLOW_RANGE, 32)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SKILL, NONE);
        entityData.define(START, 0);
        entityData.define(SEQUENCE, 0);
        entityData.define(GUARD_TIME, -1000);
        entityData.define(YAW, 0F);
        entityData.define(ENRAGED, false);
    }

    // 技能期间不游荡，索敌只针对玩家
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override public boolean canUse() { return skill() == NONE && getTarget() == null && super.canUse(); }
            @Override public boolean canContinueToUse() { return skill() == NONE && getTarget() == null && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public int skill() { return entityData.get(SKILL); }
    public boolean enraged() { return entityData.get(ENRAGED); }
    public float aimYaw() { return entityData.get(YAW); }
    public float skillAge(float partial) { return Math.max(0, (int) level().getGameTime() - entityData.get(START) + partial); }
    public float guardHitAge(float partial) { return (int) level().getGameTime() - entityData.get(GUARD_TIME) + partial; }

    // 朝向向量
    public Vec3 forward() {
        double a = Math.toRadians(aimYaw());
        return new Vec3(-Math.sin(a), 0, Math.cos(a));
    }

    public Vec3 right() {
        double a = Math.toRadians(aimYaw());
        return new Vec3(-Math.cos(a), 0, -Math.sin(a));
    }

    // 锤头落点：身前约 2.24 格、偏右 0.6 格
    public Vec3 hammerPoint(Vec3 base) {
        return base.add(forward().scale(2.24)).add(right().scale(0.6));
    }

    // 潮涌喷泉位置：三条线、每条六个，地面高度按方块逐个修正
    public Vec3 geyser(Vec3 base, int line, int index) {
        double yaw = Math.toRadians(aimYaw() + (line - 1) * SURGE_SPREAD);
        double d = SURGE_START + SURGE_STEP * index;
        Vec3 p = base.add(-Math.sin(yaw) * d, 0, Math.cos(yaw) * d);
        return new Vec3(p.x, groundY(p), p.z);
    }

    public int geyserErupt(int index) {
        return SURGE_ERUPT + 2 * index;
    }

    // 从上往下找可站立的地面，找不到时沿用原高度
    private double groundY(Vec3 p) {
        BlockPos top = BlockPos.containing(p.x, p.y + 2, p.z);
        for (int dy = 0; dy <= 6; dy++) {
            BlockPos pos = top.below(dy);
            BlockState below = level().getBlockState(pos.below());
            if (level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty() && below.isFaceSturdy(level(), pos.below(), Direction.UP)) return pos.getY();
        }
        return p.y;
    }

    @Override
    public void tick() {
        super.tick();
        // 技能中身体与头部朝向技能方向
        if (skill() != NONE && !isDeadOrDying()) {
            setYRot(aimYaw());
            yBodyRot = aimYaw();
            yHeadRot = aimYaw();
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isDeadOrDying()) return;
        // 冷却与停顿递减
        for (int i = 1; i < cooldowns.length; i++) {
            if (cooldowns[i] > 0) cooldowns[i]--;
        }
        if (pause > 0) pause--;

        // 目标失效或长期丢失视线时放弃
        LivingEntity target = getTarget();
        if (target != null && (!canHit(target) || distanceToSqr(target) > 32 * 32)) {
            setTarget(null);
            target = null;
        }
        if (target != null) {
            if (hasLineOfSight(target)) lostSight = 0;
            else if (++lostSight > 120) {
                setTarget(null);
                target = null;
            }
        }

        // 半血进入第二阶段，先咆哮
        if (!enraged() && skill() == NONE && getHealth() <= getMaxHealth() * 0.5F) {
            start(ROAR, target);
            return;
        }

        if (skill() != NONE) {
            tickSkill(target);
            return;
        }
        if (target == null) return;

        // 停顿结束后从可用技能里随机，尽量不重复上一招
        if (pause == 0) {
            int next = chooseSkill(target);
            if (next != NONE) {
                start(next, target);
                return;
            }
        }
        getNavigation().moveTo(target, 1.0);
    }

    private int chooseSkill(LivingEntity target) {
        double distance = distanceTo(target);
        Vec3 dir = target.position().subtract(position());
        dir = new Vec3(dir.x, 0, dir.z).normalize();
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        boolean behind = dir.dot(look) < -0.2 && distance <= 5;
        boolean sight = hasLineOfSight(target);
        List<Integer> options = new ArrayList<>();
        // 近身三选一：连锤、重砸、回旋
        if (distance <= 3.6 && cooldowns[COMBO] == 0) options.add(COMBO);
        if (distance <= 4.8 && cooldowns[SLAM] == 0) options.add(SLAM);
        if ((distance <= 3.8 || behind) && cooldowns[SPIN] == 0) options.add(SPIN);
        // 中距离冲撞与第二阶段潮涌
        if (distance >= 5 && distance <= 14 && sight && cooldowns[RAM] == 0) options.add(RAM);
        if (enraged() && distance >= 4 && distance <= 16 && sight && cooldowns[SURGE] == 0) options.add(SURGE);
        if (options.isEmpty()) return NONE;
        if (options.size() > 1) options.remove(Integer.valueOf(lastSkill));
        return options.get(random.nextInt(options.size()));
    }

    private void start(int action, LivingEntity target) {
        entityData.set(YAW, getYRot());
        entityData.set(SKILL, action);
        entityData.set(START, (int) level().getGameTime());
        entityData.set(SEQUENCE, entityData.get(SEQUENCE) + 1);
        hitIds.clear();
        getNavigation().stop();
        if (target != null) aim(target, 360);
        if (action != STAGGER && action != ROAR && action != GUARD) lastSkill = action;
        if (DEBUG) LOGGER.info("TidebreakerWarlord skill started: entity={}, skill={}, target={}, distance={}, enraged={}", getId(), NAMES[action], targetId(target), targetDistance(target), enraged());
    }

    private void finish(int s) {
        // 第二阶段冷却缩短、技能间停顿缩短
        int cooldown = COOLDOWN[s];
        if (enraged()) cooldown = Math.round(cooldown * 0.7F);
        cooldowns[s] = cooldown;
        pause = 14;
        if (enraged()) pause = 8;
        entityData.set(SKILL, NONE);
        if (DEBUG) LOGGER.info("TidebreakerWarlord skill finished: entity={}, skill={}, cooldown={}", getId(), NAMES[s], cooldown);
    }

    private void aim(LivingEntity target, float maxTurn) {
        Vec3 d = target.position().subtract(position());
        float wanted = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        entityData.set(YAW, Mth.approachDegrees(aimYaw(), wanted, maxTurn));
    }

    private void tickSkill(LivingEntity target) {
        int s = skill();
        int age = (int) skillAge(0);
        getNavigation().stop();
        // 冲撞阶段自己控制速度，其余技能原地出招
        if (s != RAM || age < RAM_START || age > RAM_END) setDeltaMovement(0, getDeltaMovement().y, 0);

        // 各技能可追踪方向的时间段
        boolean tracking = false;
        if (s == COMBO) tracking = age < 10 || (age >= 14 && age < 19) || (age >= 22 && age < 30);
        else if (s == SLAM) tracking = age < 15;
        else if (s == SPIN) tracking = age < 8;
        else if (s == RAM) tracking = age < 15;
        else if (s == GUARD) tracking = age < GUARD_COUNTER;
        else if (s == SURGE) tracking = age < SURGE_LOCK;
        if (target != null && tracking) aim(target, 10);

        if (s == COMBO) tickCombo(age);
        else if (s == SLAM) tickSlam(age);
        else if (s == SPIN) tickSpin(age);
        else if (s == RAM) {
            if (tickRam(age)) return;
        } else if (s == GUARD) tickGuard(age);
        else if (s == ROAR) tickRoar(age);
        else if (s == SURGE) tickSurge(age);

        if (age >= LENGTH[s]) finish(s);
    }

    // 连锤：前扫、左勾、下砸
    private void tickCombo(int age) {
        if (age == COMBO_SWEEP) {
            hitCone(4.2, Math.cos(Math.toRadians(70)), damage("comboSweepDamage"), 0.6, 0.15, false);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
        } else if (age == COMBO_HOOK) {
            hitCone(3.0, Math.cos(Math.toRadians(30)), damage("comboHookDamage"), 0.5, 0.1, false);
            playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.1F, 0.7F);
        } else if (age == COMBO_SMASH) {
            hitCircle(hammerPoint(position()), SMASH_RADIUS, damage("comboSmashDamage"), 0.3, 0.35, false);
            playSound(SoundEvents.GENERIC_EXPLODE, 0.7F, 1.4F);
        }
    }

    // 重砸：落点范围伤害，随后扩散可跳过的地面震波
    private void tickSlam(int age) {
        Vec3 center = hammerPoint(position());
        if (age == SLAM_IMPACT) {
            hitCircle(center, SLAM_RADIUS, damage("slamDamage"), 0.5, 0.45, false);
            hitIds.clear();
            playSound(SoundEvents.GENERIC_EXPLODE, 1.2F, 0.8F);
        }
        if (age >= WAVE_START && age <= WAVE_END) {
            double radius = waveRadius(age);
            int hits = 0;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 1, 2, radius + 1), this::canHit)) {
                double d = Math.hypot(e.getX() - center.x, e.getZ() - center.z);
                // 只命中站在地面、处于波前附近的目标，跳起即可躲开
                if (!e.onGround() || d < radius - 0.8 || d > radius + 0.4 || Math.abs(e.getY() - center.y) > 1.2 || hitIds.contains(e.getId())) continue;
                if (deal(e, damage("waveDamage"), e.position().subtract(center), 0.5, 0.3)) hits++;
            }
            if (DEBUG && hits > 0) LOGGER.info("TidebreakerWarlord slam wave hit: entity={}, age={}, radius={}, hits={}", getId(), age, radius, hits);
        }
    }

    public static double waveRadius(float age) {
        float t = Mth.clamp((age - WAVE_START) / (WAVE_END - WAVE_START), 0, 1);
        return SLAM_RADIUS + (WAVE_MAX - SLAM_RADIUS) * t;
    }

    // 回旋：一圈内每个目标只吃一次，强力外推
    private void tickSpin(int age) {
        if (age == SPIN_START) playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.5F);
        if (age >= SPIN_START && age <= SPIN_END) hitCircle(position(), SPIN_RADIUS, damage("spinDamage"), 1.2, 0.35, true);
    }

    // 冲撞：撞墙进入硬直，返回 true 表示已切换技能
    private boolean tickRam(int age) {
        if (age == RAM_START) playSound(SoundEvents.RAVAGER_ROAR, 1.0F, 1.3F);
        if (age > RAM_START && age <= RAM_END && horizontalCollision) {
            if (DEBUG) LOGGER.info("TidebreakerWarlord ram hit wall: entity={}, age={}, pos={}", getId(), age, position());
            playSound(SoundEvents.ANVIL_LAND, 1.0F, 0.6F);
            finish(RAM);
            start(STAGGER, null);
            return true;
        }
        if (age >= RAM_START && age <= RAM_END) {
            Vec3 move = forward().scale(0.65);
            setDeltaMovement(move.x, getDeltaMovement().y, move.z);
            AABB box = getBoundingBox().inflate(0.5).expandTowards(move);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
                if (hitIds.contains(e.getId())) continue;
                deal(e, damage("ramDamage"), forward(), 1.4, 0.45);
            }
        }
        return false;
    }

    // 格挡：正面减伤，结束时反击
    private void tickGuard(int age) {
        if (age == GUARD_COUNTER) {
            hitCone(3.6, Math.cos(Math.toRadians(40)), damage("counterDamage"), 0.9, 0.25, false);
            playSound(SoundEvents.SHIELD_BLOCK, 1.2F, 0.6F);
        }
    }

    // 咆哮：进入第二阶段并推开周围目标
    private void tickRoar(int age) {
        if (age == 1 && !enraged()) {
            entityData.set(ENRAGED, true);
            applyPhaseSpeed();
            if (DEBUG) LOGGER.info("TidebreakerWarlord entered phase two: entity={}, health={}", getId(), getHealth());
        }
        if (age == ROAR_PUSH) {
            playSound(SoundEvents.RAVAGER_ROAR, 1.6F, 0.7F);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(ROAR_RADIUS), this::canHit)) {
                Vec3 d = e.position().subtract(position());
                if (d.horizontalDistance() > ROAR_RADIUS) continue;
                Vec3 push = new Vec3(d.x, 0, d.z).normalize().scale(1.1);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.35, push.z));
                e.hurtMarked = true;
            }
        }
    }

    // 潮涌：锤击地面后三条喷泉线依次爆发
    private void tickSurge(int age) {
        if (age == SURGE_SMASH) playSound(SoundEvents.GENERIC_EXPLODE, 1.0F, 1.1F);
        for (int i = 0; i < SURGE_COUNT; i++) {
            if (age != geyserErupt(i)) continue;
            for (int line = 0; line < SURGE_LINES; line++) {
                Vec3 p = geyser(position(), line, i);
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(GEYSER_RADIUS + 0.5, 2.5, GEYSER_RADIUS + 0.5), this::canHit)) {
                    if (hitIds.contains(e.getId()) || Math.hypot(e.getX() - p.x, e.getZ() - p.z) > GEYSER_RADIUS + e.getBbWidth() * 0.5) continue;
                    if (e.getY() < p.y - 0.5 || e.getY() > p.y + 2.5) continue;
                    deal(e, damage("surgeDamage"), Vec3.ZERO, 0, 0.8);
                }
            }
            playSound(SoundEvents.GENERIC_SPLASH, 1.0F, 0.8F + i * 0.08F);
        }
    }

    // 扇形判定
    private void hitCone(double range, double cosHalf, float damage, double knock, double up, boolean once) {
        Vec3 f = forward();
        int hits = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range), this::canHit)) {
            Vec3 d = e.position().subtract(position());
            Vec3 flat = new Vec3(d.x, 0, d.z);
            double dist = flat.length() - e.getBbWidth() * 0.5;
            if (dist > range || Math.abs(d.y) > 3) continue;
            if (flat.length() > 1.2 && flat.normalize().dot(f) < cosHalf) continue;
            if (once && hitIds.contains(e.getId())) continue;
            if (!visible(e)) continue;
            if (deal(e, damage, flat, knock, up)) hits++;
        }
        if (DEBUG) LOGGER.info("TidebreakerWarlord cone resolved: entity={}, skill={}, age={}, range={}, hits={}", getId(), NAMES[skill()], (int) skillAge(0), range, hits);
    }

    // 圆形判定
    private void hitCircle(Vec3 center, double radius, float damage, double knock, double up, boolean once) {
        int hits = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 1, 3, radius + 1), this::canHit)) {
            Vec3 d = e.position().subtract(center);
            if (Math.hypot(d.x, d.z) - e.getBbWidth() * 0.5 > radius || d.y < -1.5 || d.y > 2.5) continue;
            if (once && hitIds.contains(e.getId())) continue;
            if (!visible(e)) continue;
            if (deal(e, damage, new Vec3(d.x, 0, d.z), knock, up)) hits++;
        }
        if (DEBUG && (hits > 0 || !once)) LOGGER.info("TidebreakerWarlord circle resolved: entity={}, skill={}, age={}, radius={}, hits={}", getId(), NAMES[skill()], (int) skillAge(0), radius, hits);
    }

    // 实际造成伤害与击退
    private boolean deal(LivingEntity e, float damage, Vec3 push, double knock, double up) {
        hitIds.add(e.getId());
        e.invulnerableTime = 0;
        if (!e.hurt(damageSources().mobAttack(this), damage)) return false;
        Vec3 dir = Vec3.ZERO;
        if (push.lengthSqr() > 1.0E-4) dir = push.normalize().scale(knock);
        e.setDeltaMovement(e.getDeltaMovement().add(dir.x, up, dir.z));
        e.hurtMarked = true;
        if (DEBUG) LOGGER.info("TidebreakerWarlord damage applied: entity={}, skill={}, target={}, damage={}", getId(), NAMES[skill()], e.getId(), damage);
        return true;
    }

    private boolean visible(LivingEntity e) {
        Vec3 from = position().add(0, 1.2, 0), to = e.getBoundingBox().getCenter();
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getLocation().distanceToSqr(to) < 0.01;
    }

    private boolean canHit(LivingEntity e) {
        if (e == this || !e.isAlive() || isAlliedTo(e) || e instanceof TidebreakerWarlordEntity) return false;
        return !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private static float damage(String key) {
        return CombatConfigs.TIDEBREAKER_WARLORD.damage(key);
    }

    // 第二阶段移速加成
    private void applyPhaseSpeed() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) org.hp.hp_end_expansion.compat.AttributeCompat.transientModifier(speed, new AttributeModifier(PHASE_SPEED, "hp_end_expansion.phase_speed", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        int s = skill();
        int age = (int) skillAge(0);
        // 咆哮期间无敌
        if (s == ROAR && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        // 重砸卡锤与撞墙硬直期间受伤提高
        if ((s == SLAM && age >= 24 && age <= 40) || s == STAGGER) amount *= (float) CombatConfigs.TIDEBREAKER_WARLORD.value("punishMultiplier");
        // 正面判断
        boolean front = false;
        Vec3 from = source.getSourcePosition();
        if (from != null) {
            Vec3 d = from.subtract(position());
            d = new Vec3(d.x, 0, d.z);
            if (d.lengthSqr() > 1.0E-4) front = d.normalize().dot(Vec3.directionFromRotation(0, getYRot())) > Math.cos(Math.toRadians(60));
        }
        // 格挡期间正面大幅减伤
        if (s == GUARD && age >= GUARD_START && age < GUARD_COUNTER && front) {
            amount *= (float) CombatConfigs.TIDEBREAKER_WARLORD.value("guardMultiplier");
            entityData.set(GUARD_TIME, (int) level().getGameTime());
            playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F);
            if (DEBUG) LOGGER.info("TidebreakerWarlord guard blocked: entity={}, age={}, amount={}", getId(), age, amount);
        }
        boolean accepted = super.hurt(source, amount);
        if (accepted && !isDeadOrDying() && source.getEntity() instanceof LivingEntity attacker && canHit(attacker)) {
            setTarget(attacker);
            lostSight = 0;
            // 两秒内被正面连打两次，起手格挡
            if (front) {
                if (tickCount - lastFrontHit < 40) frontHits++;
                else frontHits = 1;
                lastFrontHit = tickCount;
            }
            if (frontHits >= 2 && skill() == NONE && cooldowns[GUARD] == 0) {
                frontHits = 0;
                start(GUARD, attacker);
            }
        }
        return accepted;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Enraged", enraged());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // 读档后恢复第二阶段
        if (tag.getBoolean("Enraged")) {
            entityData.set(ENRAGED, true);
            applyPhaseSpeed();
        }
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= 40 && !level().isClientSide && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SyncedController(this, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            // 收到新技能时重置动画
            int sequence = entityData.get(SEQUENCE);
            if (seenSequence != sequence) {
                seenSequence = sequence;
                state.getController().forceAnimationReset();
                if (DEBUG) LOGGER.info("TidebreakerWarlord animation state received: entity={}, sequence={}, skill={}", getId(), sequence, NAMES[skill()]);
            }
            if (skill() != NONE) {
                state.getController().setAnimationSpeed(1);
                return state.setAndContinue(ACTIONS[skill()]);
            }
            if (hurtTime > 0) {
                state.getController().setAnimationSpeed(1);
                return state.setAndContinue(HURT);
            }
            // 走路动画速度跟随实际移动速度
            double speed = new Vec3(getX() - xo, 0, getZ() - zo).length();
            if (state.isMoving() && speed > 0.01) {
                state.getController().setAnimationSpeed(Mth.clamp(speed / 0.13, 0.6, 1.8));
                return state.setAndContinue(WALK);
            }
            state.getController().setAnimationSpeed(1);
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private static String targetId(LivingEntity target) {
        if (target == null) return "none";
        return String.valueOf(target.getId());
    }

    private double targetDistance(LivingEntity target) {
        if (target == null) return -1;
        return distanceTo(target);
    }

    // 技能期间动画时间与服务端技能时间同步
    private static final class SyncedController extends AnimationController<TidebreakerWarlordEntity> {
        SyncedController(TidebreakerWarlordEntity e, AnimationStateHandler<TidebreakerWarlordEntity> handler) {
            super(e, "action", 0, handler);
        }

        @Override
        protected double adjustTick(double tick) {
            if (animatable.skill() != NONE && !animatable.isDeadOrDying() && !shouldResetTick && getAnimationState() == State.RUNNING) {
                return animatable.skillAge((float) (tick - Math.floor(tick)));
            }
            return super.adjustTick(tick);
        }
    }
    @Override protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose, net.minecraft.world.entity.EntityDimensions dimensions) { return 2.7F; }
}
