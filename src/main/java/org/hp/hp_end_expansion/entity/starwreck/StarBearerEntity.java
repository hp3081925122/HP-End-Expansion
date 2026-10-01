package org.hp.hp_end_expansion.entity.starwreck;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
 * 负星者：坠星教团的朝圣者。走得慢、正面硬，背后的圣星是弱点。
 * 圣星打碎后跪地硬直，怒吼后进入碎星狂暴。
 */
public final class StarBearerEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.star_bearer.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.star_bearer.walk");
    private static final RawAnimation WALK_RAGE = RawAnimation.begin().thenLoop("animation.star_bearer.walk_rage");
    private static final RawAnimation STAGGER = RawAnimation.begin().thenLoop("animation.star_bearer.stagger");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.star_bearer.death");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("animation.star_bearer.slam");
    private static final RawAnimation SLAM_FAST = RawAnimation.begin().thenPlay("animation.star_bearer.slam_fast");
    private static final RawAnimation BACKFIRE = RawAnimation.begin().thenPlay("animation.star_bearer.backfire");
    private static final RawAnimation TOSS = RawAnimation.begin().thenPlay("animation.star_bearer.toss");
    private static final RawAnimation SHATTER = RawAnimation.begin().thenPlay("animation.star_bearer.shatter");
    private static final RawAnimation RAGE = RawAnimation.begin().thenPlay("animation.star_bearer.rage");
    private static final RawAnimation SWEEP = RawAnimation.begin().thenPlay("animation.star_bearer.sweep");
    private static final RawAnimation BEAM = RawAnimation.begin().thenPlay("animation.star_bearer.beam");

    public static final int NONE = 0, ACT_SLAM = 1, ACT_BACKFIRE = 2, ACT_TOSS = 3, ACT_FAST = 4, ACT_SHATTER = 5, ACT_STAGGER = 6, ACT_RAGE = 7,
        ACT_SWEEP = 8, ACT_BEAM = 9;
    private static final EntityDataAccessor<Boolean> STAR_BROKEN = SynchedEntityData.defineId(StarBearerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> STAR_HEALTH = SynchedEntityData.defineId(StarBearerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(StarBearerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(StarBearerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> BEAM_YAW = SynchedEntityData.defineId(StarBearerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_PITCH = SynchedEntityData.defineId(StarBearerEntity.class, EntityDataSerializers.FLOAT);
    public static final float STAR_MAX = 30;
    private static final int SLAM_HIT = 20, SLAM_END = 32, FAST_HIT = 12, FAST_AGAIN = 20, FAST_HIT2 = 32, FAST_END = 40;
    private static final int FLAME_HIT = 12, FLAME_END = 24, TOSS_HIT = 16, TOSS_END = 28;
    private static final int SHATTER_END = 16, VULNERABLE = 96, RAGE_END = 24;
    /** 负星横扫：第 12 tick 起挥，前锋 4 拍扫完；负星光束：蓄力 20 tick，照射到第 44 tick，第 54 tick 收招。 */
    public static final int SWEEP_SWING = 12, SWEEP_END = 26, BEAM_FIRE = 20, BEAM_STOP = 44, BEAM_END = 54;
    public static final float BEAM_RANGE = 24;

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private final List<ShockRing> rings = new ArrayList<>();
    private int actionTicks = -1, slamCd, flameCd, tossCd, sweepCd, beamCd = 100, behindTicks, starWindow, starHits, calmTicks, vulnerable;
    private int seenAction = NONE, actionStart;
    private boolean wantFlame;
    private float lockedYaw;
    private final Set<Integer> sweepHit = new HashSet<>();
    /** 客户端：渲染器每帧写入的圣星世界坐标。 */
    @Nullable public Vec3 starAnchor;
    /** 客户端：渲染器每帧写入的胸口星晶、右拳世界坐标；光束从胸口射出，横扫在右拳上蓄光。 */
    @Nullable public Vec3 chestAnchor, fistAnchor;
    /** 客户端：上一拍的光束朝向和这一拍按方块碰撞量出的光束长度，渲染器拿来插值。 */
    public float beamYawO, beamPitchO, beamLength, beamLengthO;
    private float beamYawLast, beamPitchLast;

    public StarBearerEntity(EntityType<? extends StarBearerEntity> type, Level level) {
        super(type, level);
        xpReward = 40;
        moveControl = new ActionLockMove();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMonsterAttributes().add(Attributes.MAX_HEALTH, 180).add(Attributes.ARMOR, 10).add(Attributes.ARMOR_TOUGHNESS, 4)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.FOLLOW_RANGE, 24)
            .add(Attributes.ATTACK_DAMAGE, 10).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STAR_BROKEN, false);
        builder.define(STAR_HEALTH, STAR_MAX);
        builder.define(ACTION, NONE);
        builder.define(ENRAGED, false);
        builder.define(BEAM_YAW, 0F);
        builder.define(BEAM_PITCH, 0F);
    }

    public boolean isStarBroken() { return entityData.get(STAR_BROKEN); }
    public float starHealth() { return entityData.get(STAR_HEALTH); }
    public int action() { return entityData.get(ACTION); }
    public boolean isEnraged() { return entityData.get(ENRAGED); }
    public boolean isVulnerable() { return vulnerable > 0; }
    public float beamYaw() { return entityData.get(BEAM_YAW); }
    public float beamPitch() { return entityData.get(BEAM_PITCH); }
    /** 客户端：当前招式和它开始至今的拍数（按收到同步的那一拍算）。 */
    public int clientAction() { return seenAction; }
    public float clientActionAge(float partialTick) { return tickCount - actionStart + partialTick; }

    /** 光束方向：yaw 同原版朝向，pitch 向上为正。 */
    public static Vec3 beamDir(float yaw, float pitch) {
        float y = yaw * Mth.DEG_TO_RAD, p = pitch * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(y) * Mth.cos(p), Mth.sin(p), Mth.cos(y) * Mth.cos(p));
    }

    // 光束能照 24 格，照射时实体本身可能在屏幕外，放大裁剪盒免得光束跟着消失
    @Override public AABB getBoundingBoxForCulling() {
        return action() == ACT_BEAM ? super.getBoundingBoxForCulling().inflate(BEAM_RANGE) : super.getBoundingBoxForCulling();
    }

    @Override protected BodyRotationControl createBodyControl() { return new ActionLockTurn(); }

    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }

    @Override protected void registerGoals() {
        goalSelector.addGoal(2, new FightGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // 自然刷出时身边带两只殉星者；刷怪蛋和指令不带
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        if (spawnType != MobSpawnType.NATURAL) return result;
        for (int i = 0; i < 2; i++) {
            StarMartyrEntity martyr = StarwreckEntities.STAR_MARTYR.get().create(level.getLevel());
            if (martyr == null) continue;
            double a = random.nextDouble() * Math.PI * 2, r = 2 + random.nextDouble() * 1.5;
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

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (action() != seenAction) {
                seenAction = action();
                actionStart = tickCount;
            }
            if (isAlive()) {
                clientEmbers();
                clientNegative();
            }
        } else if (hasCustomName()) {
            String text = getCustomName().getString();
            if (text.startsWith("闲逛") || text.startsWith("索敌")) {
                setCustomName(null);
                setCustomNameVisible(false);
            }
        }
    }

    /** 客户端：反扑和抛星前摇的蓄力进度，出手后 4 拍内熄回去。渲染器用它把圣星越聚越亮。 */
    public float chargeUp(float partialTick) {
        int hit = seenAction == ACT_BACKFIRE ? FLAME_HIT : seenAction == ACT_TOSS ? TOSS_HIT : 0;
        if (hit == 0) return 0;
        float age = tickCount - actionStart + partialTick;
        return age <= hit ? Mth.clamp(age / hit, 0, 1) : Math.max(0, 1 - (age - hit) / 4);
    }

    /** 坠星教团的同伴不吃负星者的范围伤害。 */
    public static boolean isKin(Entity entity) {
        return entity instanceof StarBearerEntity || entity instanceof StarMartyrEntity || entity instanceof StarCallerEntity;
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (slamCd > 0) slamCd--;
        if (flameCd > 0) flameCd--;
        if (tossCd > 0) tossCd--;
        if (sweepCd > 0) sweepCd--;
        if (beamCd > 0) beamCd--;
        if (starWindow > 0 && --starWindow == 0) starHits = 0;
        if (vulnerable > 0) vulnerable--;
        if (actionTicks >= 0 || action() == ACT_STAGGER) advanceAction();
        tickRings();
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || distanceToSqr(target) > 32 * 32) {
            if (++calmTicks >= 1200) {
                setHealth(getMaxHealth());
                calmTicks = 0;
            }
        } else calmTicks = 0;
    }

    // 死亡动画 2 秒，原版 1 秒就会移除
    @Override protected void tickDeath() {
        deathTime++;
        if (deathTime >= 40 && level() instanceof ServerLevel server) server.broadcastEntityEvent(this, (byte) 60);
        if (deathTime >= 40) remove(RemovalReason.KILLED);
    }

    private void begin(int action, String trigger) {
        entityData.set(ACTION, action);
        actionTicks = 0;
        lockedYaw = yBodyRot;
        setYRot(lockedYaw);
        yHeadRot = lockedYaw;
        getNavigation().stop();
        if (trigger != null) triggerAnim("action", trigger);
    }

    private void endAction() {
        actionTicks = -1;
        if (action() != ACT_STAGGER) entityData.set(ACTION, NONE);
    }

    private void advanceAction() {
        int act = action();
        actionTicks++;
        setYRot(lockedYaw);
        yBodyRot = lockedYaw;
        yHeadRot = lockedYaw;
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        if (act == ACT_SLAM && actionTicks == SLAM_HIT) slam(false);
        if (act == ACT_FAST && (actionTicks == FAST_HIT || actionTicks == FAST_HIT2)) slam(true);
        if (act == ACT_FAST && actionTicks == FAST_AGAIN) triggerAnim("action", "slam_fast");
        if (act == ACT_BACKFIRE && actionTicks == FLAME_HIT) backfire();
        if (act == ACT_TOSS && actionTicks == TOSS_HIT) toss();
        if (act == ACT_SWEEP) sweepTick();
        if (act == ACT_BEAM) beamTick();
        if (act == ACT_SHATTER && actionTicks >= SHATTER_END) {
            entityData.set(ACTION, ACT_STAGGER);
            actionTicks = SHATTER_END;
            return;
        }
        if (act == ACT_STAGGER && vulnerable <= 0) {
            begin(ACT_RAGE, "rage");
            playSound(SoundEvents.RAVAGER_ROAR, 1.4F, 0.6F);
            return;
        }
        if (act == ACT_RAGE && actionTicks >= RAGE_END) {
            entityData.set(ENRAGED, true);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28);
            endAction();
            return;
        }
        int end = act == ACT_SLAM ? SLAM_END : act == ACT_FAST ? FAST_END : act == ACT_BACKFIRE ? FLAME_END : act == ACT_TOSS ? TOSS_END
            : act == ACT_SWEEP ? SWEEP_END : act == ACT_BEAM ? BEAM_END : Integer.MAX_VALUE;
        if (actionTicks >= end) {
            if (act == ACT_SLAM) slamCd = 60;
            else if (act == ACT_FAST) slamCd = 40;
            else if (act == ACT_BACKFIRE) flameCd = 120;
            else if (act == ACT_TOSS) {
                tossCd = 160;
                beamCd = Math.max(beamCd, 60);
            } else if (act == ACT_SWEEP) {
                sweepCd = 70;
                slamCd = Math.max(slamCd, 20);
            } else if (act == ACT_BEAM) {
                beamCd = isEnraged() ? 160 : 220;
                tossCd = Math.max(tossCd, 60);
            }
            endAction();
        }
    }

    /** 本体局部的水平角度（度）：0 正前，负数在右手边。和横扫弧光的角度同一套。 */
    private float localAngle(Vec3 p) {
        double dx = p.x - getX(), dz = p.z - getZ();
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double lx = dx * Mth.cos(yaw) + dz * Mth.sin(yaw), lz = -dx * Mth.sin(yaw) + dz * Mth.cos(yaw);
        return (float) Math.toDegrees(Math.atan2(lx, lz));
    }

    // 负星横扫：右拳从右后方抡到左前方。伤害跟着弧光前锋走，每拍只结算前锋这一拍扫过的扇区，所以左侧的人比右侧晚挨两拍
    private void sweepTick() {
        int age = actionTicks - SWEEP_SWING;
        if (age == -SWEEP_SWING + 1) playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.2F, 1.4F);
        if (age < 0 || age > BearerVfxEntity.ARC_SWING) return;
        if (age == 0) {
            sweepHit.clear();
            if (level() instanceof ServerLevel server) BearerVfxEntity.spawn(server, BearerVfxEntity.ARC, position(), yBodyRot, 1, 0);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.5F);
        }
        float lo = age == 0 ? BearerVfxEntity.ARC_FROM - 15 : BearerVfxEntity.arcLead(age - 1);
        float hi = age == BearerVfxEntity.ARC_SWING ? BearerVfxEntity.ARC_TO + 10 : BearerVfxEntity.arcLead(age);
        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5, 2, 5), e -> e.isAlive() && !isKin(e))) {
            if (sweepHit.contains(living.getId())) continue;
            double d = Math.sqrt(living.distanceToSqr(getX(), living.getY(), getZ())) - living.getBbWidth() / 2;
            double dy = living.getY() - getY();
            if (d > BearerVfxEntity.ARC_R || dy < -1.5 || dy > 2.5) continue;
            float a = localAngle(living.position());
            if (a <= lo || a > hi) continue;
            sweepHit.add(living.getId());
            if (!living.hurt(damageSources().mobAttack(this), 9)) continue;
            // 顺着挥砍方向横着甩出去，再带一点往外
            float r = a * Mth.DEG_TO_RAD;
            Vec3 push = BearerVfxEntity.toWorld(yBodyRot, Mth.cos(r) * 0.9 + Mth.sin(r) * 0.5, 0, -Mth.sin(r) * 0.9 + Mth.cos(r) * 0.5);
            double keep = 1 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            if (keep > 0) living.push(push.x * keep, 0.32 * keep, push.z * keep);
        }
    }

    private Vec3 beamOrigin() { return position().add(forward().scale(0.3)).add(0, 1.55, 0); }

    private BlockHitResult beamClip(Vec3 from, Vec3 dir) {
        return level().clip(new ClipContext(from, from.add(dir.scale(BEAM_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
    }

    // 负星光束：蓄力时朝向追得快（每拍 2.5°），开火后只能每拍挪 0.9°（狂暴 1.5°），横着跑就躲得开，站着不动会被一直照
    private void beamTick() {
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && actionTicks < BEAM_STOP) {
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(beamOrigin());
            float wantYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
            float wantPitch = Mth.clamp((float) (Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG), -40, 30);
            float rate = actionTicks < BEAM_FIRE ? 2.5F : isEnraged() ? 1.5F : 0.9F;
            entityData.set(BEAM_YAW, Mth.approachDegrees(beamYaw(), wantYaw, rate));
            entityData.set(BEAM_PITCH, Mth.approach(beamPitch(), wantPitch, rate));
        }
        lockedYaw = beamYaw();
        if (actionTicks == BEAM_FIRE) {
            playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.6F);
            playSound(SoundEvents.ENDER_DRAGON_SHOOT, 1.2F, 0.6F);
        }
        if (actionTicks == BEAM_STOP) playSound(SoundEvents.BEACON_DEACTIVATE, 1.5F, 1.4F);
        if (actionTicks < BEAM_FIRE || actionTicks >= BEAM_STOP) return;
        if ((actionTicks - BEAM_FIRE) % 8 == 4) playSound(SoundEvents.BEACON_AMBIENT, 1.6F, 1.8F);
        Vec3 from = beamOrigin(), dir = beamDir(beamYaw(), beamPitch());
        Vec3 end = beamClip(from, dir).getLocation();
        // 每拍都判定，原版 10 tick 的受击无敌自然把它限成每半秒一跳
        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1), e -> e.isAlive() && e != this && !isKin(e))) {
            AABB box = living.getBoundingBox().inflate(0.35);
            if (!box.contains(from) && box.clip(from, end).isEmpty()) continue;
            if (living.hurt(damageSources().mobAttack(this), 5)) living.push(dir.x * 0.15, 0.04, dir.z * 0.15);
        }
    }

    private Vec3 forward() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    // 模型里圣星中心约在背后 0.5 格、离地 2.1 格（上身静止前倾 14° 后）；碎星残块矮一截
    private Vec3 starPos() { return position().add(forward().scale(-0.5)).add(0, isStarBroken() ? 1.8 : 2.1, 0); }

    private Vec3 rightSide() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
    }

    /** 攻击来向和身体朝向的水平夹角，0 是正前方。 */
    private float attackAngle(DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null && source.getEntity() != null) from = source.getEntity().position();
        return from == null ? 90 : angleFrom(from);
    }

    private float angleFrom(Vec3 from) {
        Vec3 to = from.subtract(position());
        double len = to.horizontalDistance();
        if (len < 1.0E-4) return 0;
        double dot = forward().dot(new Vec3(to.x / len, 0, to.z / len));
        return (float) Math.toDegrees(Math.acos(Mth.clamp(dot, -1, 1)));
    }

    private boolean frontGuard(float angle) { return !isEnraged() && !isVulnerable() && angle < 60; }

    // 正面的弹射物交给原版弹反：箭在命中前就被反弹，不会先走 hurt 再被原版二次反向
    @Override public ProjectileDeflection deflection(Projectile projectile) {
        if (frontGuard(angleFrom(projectile.position()))) {
            playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F);
            return ProjectileDeflection.REVERSE;
        }
        return super.deflection(projectile);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof FallingStarEntity || source.getDirectEntity() instanceof StarShardEntity) return false;
        float angle = attackAngle(source);
        boolean projectile = source.getDirectEntity() instanceof Projectile;
        if (projectile && frontGuard(angle)) return false;
        if (isVulnerable()) amount *= 1.5F;
        else if (isEnraged()) amount *= 1.25F;
        else if (angle < 60) amount *= 0.3F;
        boolean back = angle >= 120;
        if (projectile && back) {
            Vec3 at = source.getSourcePosition();
            if (at == null || at.y - getY() < 1.6) back = false;
        }
        boolean hurt = super.hurt(source, amount);
        // 只有真正打进去的一下才算到圣星上，无敌帧里连射的箭不会白白削圣星
        if (hurt && back && !isStarBroken() && damageStar(amount) && isAlive()) shatter();
        return hurt;
    }

    private boolean damageStar(float amount) {
        float left = Math.max(0, starHealth() - amount);
        entityData.set(STAR_HEALTH, left);
        if (starWindow <= 0) {
            starHits = 1;
            starWindow = 60;
        } else if (++starHits >= 2) wantFlame = true;
        if (left > 0) {
            playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.2F, 0.6F);
            return false;
        }
        return true;
    }

    private void shatter() {
        entityData.set(STAR_BROKEN, true);
        entityData.set(STAR_HEALTH, 0F);
        vulnerable = VULNERABLE;
        wantFlame = false;
        Vec3 at = starPos();
        if (level() instanceof ServerLevel server) {
            BearerVfxEntity.spawn(server, BearerVfxEntity.SHATTER, at, yBodyRot, 1, 0);
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4), e -> e.isAlive() && !isKin(e))) {
                if (living.distanceToSqr(at) > 9) continue;
                living.hurt(damageSources().mobAttack(this), 5);
            }
        }
        playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6F, 0.5F);
        begin(ACT_SHATTER, "shatter");
    }

    private void slam(boolean fast) {
        Vec3 forward = forward();
        Vec3 at = position().add(forward.scale(1.6));
        double y = StarChaserEntity.groundY(level(), at.x, at.z, at.y);
        if (Double.isNaN(y)) y = getY();
        Vec3 ground = new Vec3(at.x, y, at.z);
        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4), e -> e.isAlive() && !isKin(e))) {
            if (!inCone(living, forward, 3, 45)) continue;
            if (living.hurt(damageSources().mobAttack(this), 10))
                living.knockback(1.1, getX() - living.getX(), getZ() - living.getZ());
        }
        if (level() instanceof ServerLevel server) BearerVfxEntity.spawn(server, BearerVfxEntity.SLAM, ground, yBodyRot, 1, 0);
        rings.add(new ShockRing(ground));
        playSound(fast ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND, 1.1F, fast ? 1.1F : 0.7F);
        playSound(SoundEvents.BELL_RESONATE, 0.8F, fast ? 1.3F : 0.9F);
    }

    // 冲击环的画面由 BearerVfxEntity 画，这里只结算伤害；半径公式两边共用
    private void tickRings() {
        for (int i = rings.size() - 1; i >= 0; i--) {
            ShockRing ring = rings.get(i);
            float prev = ring.radius;
            ring.age++;
            ring.radius = BearerVfxEntity.ringRadius(ring.age);
            var area = new net.minecraft.world.phys.AABB(ring.at, ring.at).inflate(ring.radius, 1.2, ring.radius);
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !isKin(e) && e.onGround())) {
                double d = Math.sqrt(living.distanceToSqr(ring.at.x, living.getY(), ring.at.z));
                boolean crossed = ring.age == 1 ? d <= ring.radius : d <= ring.radius && d >= prev - 0.3;
                if (!crossed || !ring.hit.add(living.getId())) continue;
                if (living.hurt(damageSources().mobAttack(this), 6))
                    living.setDeltaMovement(living.getDeltaMovement().x, 0.55, living.getDeltaMovement().z);
            }
            if (ring.age >= BearerVfxEntity.RING_TICKS) rings.remove(i);
        }
    }

    private void backfire() {
        Vec3 back = forward().scale(-1);
        if (level() instanceof ServerLevel server) BearerVfxEntity.spawn(server, BearerVfxEntity.FLARE, starPos(), yBodyRot + 180, 1, 0);
        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5), e -> e.isAlive() && !isKin(e))) {
            if (!inCone(living, back, 4, 30)) continue;
            if (living.hurt(damageSources().mobAttack(this), 6)) {
                living.knockback(0.7, getX() - living.getX(), getZ() - living.getZ());
                if (!living.fireImmune()) living.igniteForSeconds(2);
            }
        }
        playSound(SoundEvents.FIRECHARGE_USE, 1.2F, 0.6F);
        playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 0.5F);
        wantFlame = false;
    }

    // 一次甩出一簇：正中一块，四块贴着它的爆炸圈。目标正在走时，整簇往他的去向前探，脚下再留一块
    private void toss() {
        LivingEntity target = getTarget();
        if (target == null || !(level() instanceof ServerLevel server)) return;
        Vec3 hand = position().add(rightSide().scale(0.7)).add(forward().scale(0.3)).add(0, 2.7, 0);
        Vec3 feet = groundAt(target.getX(), target.getZ(), target.getY());
        Vec3 vel = new Vec3(target.getDeltaMovement().x, 0, target.getDeltaMovement().z);
        double lead = Math.min(6, vel.length() * StarShardEntity.FLIGHT * 0.7);
        Vec3 aim = feet;
        if (lead > 0.5) {
            Vec3 dir = vel.normalize();
            aim = groundAt(feet.x + dir.x * lead, feet.z + dir.z * lead, feet.y);
        }
        StarShardEntity.throwAt(server, this, hand, aim);
        if (aim.distanceToSqr(feet) > 2.25) StarShardEntity.throwAt(server, this, hand, feet);
        double spin = random.nextDouble() * Math.PI / 2;
        for (int i = 0; i < 4; i++) {
            double a = spin + i * Math.PI / 2;
            StarShardEntity.throwAt(server, this, hand, groundAt(aim.x + Math.cos(a) * 2.6, aim.z + Math.sin(a) * 2.6, aim.y));
        }
        playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 1.2F);
        playSound(SoundEvents.TRIDENT_THROW.value(), 1.2F, 0.6F);
    }

    private Vec3 groundAt(double x, double z, double refY) {
        double y = StarChaserEntity.groundY(level(), x, z, refY);
        return new Vec3(x, Double.isNaN(y) ? refY : y, z);
    }

    private boolean inCone(LivingEntity living, Vec3 dir, double range, float halfAngle) {
        Vec3 to = living.position().add(0, living.getBbHeight() * 0.5, 0).subtract(position().add(0, 1, 0));
        if (to.horizontalDistance() > range || Math.abs(to.y) > 3) return false;
        double len = to.horizontalDistance();
        if (len < 1.0E-4) return true;
        return dir.dot(new Vec3(to.x / len, 0, to.z / len)) >= Math.cos(Math.toRadians(halfAngle));
    }

    private void clientEmbers() {
        Vec3 at = starAnchor != null ? starAnchor : starPos();
        boolean broken = isStarBroken();
        // 圣星：白金火舌往上舔；碎星后残块只偶尔冒一缕赤焰
        if (!broken || tickCount % 4 == 0) {
            level().addParticle(broken ? ModParticles.BEARER_RAGE.get() : ModParticles.BEARER_FLAME.get(),
                at.x + (random.nextDouble() - 0.5) * 0.4, at.y + 0.15, at.z + (random.nextDouble() - 0.5) * 0.4,
                (random.nextDouble() - 0.5) * 0.02, 0.05 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.02);
        }
        if (!isEnraged()) return;
        // 狂暴：胸甲和肩甲缝里往外喷赤焰
        Vec3 side = rightSide().scale(random.nextBoolean() ? 0.55 : -0.55);
        Vec3 p = position().add(forward().scale(0.25)).add(random.nextInt(3) == 0 ? side : Vec3.ZERO).add(0, 1.4 + random.nextDouble() * 0.6, 0);
        level().addParticle(ModParticles.BEARER_RAGE.get(), p.x, p.y, p.z, (random.nextDouble() - 0.5) * 0.06, 0.04, (random.nextDouble() - 0.5) * 0.06);
    }

    // 负光：横扫前摇时虚蚀微粒被吸进右拳；光束蓄力时吸进胸口，照射时从落点往外炸
    private void clientNegative() {
        beamYawO = beamYawLast;
        beamPitchO = beamPitchLast;
        beamYawLast = beamYaw();
        beamPitchLast = beamPitch();
        beamLengthO = beamLength;
        int age = tickCount - actionStart;
        if (seenAction == ACT_SWEEP && age < SWEEP_SWING) {
            Vec3 fist = fistAnchor != null ? fistAnchor : position().add(rightSide().scale(1.1)).add(0, 1.6, 0);
            converge(fist, 0.9, 1.6, 3);
        }
        if (seenAction != ACT_BEAM || age >= BEAM_STOP + 4) {
            beamLength = 0;
            return;
        }
        Vec3 from = chestAnchor != null ? chestAnchor : beamOrigin();
        Vec3 dir = beamDir(beamYaw(), beamPitch());
        BlockHitResult hit = beamClip(from, dir);
        Vec3 end = hit.getLocation();
        beamLength = (float) end.distanceTo(from);
        if (age <= 0) {
            // 刚开招：上一拍的值是很久以前的，别从那里插值过来
            beamYawO = beamYaw();
            beamPitchO = beamPitch();
            beamLengthO = beamLength;
        }
        if (age < BEAM_FIRE) {
            converge(from, 1.4, 2.6, 4);
            return;
        }
        if (age >= BEAM_STOP) return;
        for (int i = 0; i < 4; i++) {
            Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(0.1 + random.nextDouble() * 0.12)
                .subtract(dir.scale(0.08));
            level().addParticle(i == 0 ? ModParticles.BEARER_GLINT.get() : ModParticles.BEARER_VOID.get(), end.x, end.y, end.z, v.x, v.y, v.z);
        }
        if (hit.getType() == HitResult.Type.BLOCK && tickCount % 2 == 0) {
            BlockState state = level().getBlockState(hit.getBlockPos());
            if (!state.isAir()) level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), end.x, end.y, end.z,
                (random.nextDouble() - 0.5) * 0.3, 0.2, (random.nextDouble() - 0.5) * 0.3);
        }
        // 光束上零星浮起的星芒
        double s = random.nextDouble() * beamLength;
        level().addParticle(ModParticles.BEARER_GLINT.get(), from.x + dir.x * s, from.y + dir.y * s, from.z + dir.z * s,
            (random.nextDouble() - 0.5) * 0.04, 0.03, (random.nextDouble() - 0.5) * 0.04);
    }

    /** 在 at 周围 r0~r1 的球壳上撒虚蚀微粒，初速正好让它们在寿命内被吸到 at。 */
    private void converge(Vec3 at, double r0, double r1, int n) {
        for (int i = 0; i < n; i++) {
            Vec3 off = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(r0 + random.nextDouble() * (r1 - r0));
            level().addParticle(random.nextInt(4) == 0 ? ModParticles.BEARER_GLINT.get() : ModParticles.BEARER_VOID.get(),
                at.x + off.x, at.y + off.y, at.z + off.z, -off.x * 0.12, -off.y * 0.12, -off.z * 0.12);
        }
    }

    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!isStarBroken() || !(source.getEntity() instanceof Player player)) return;
        var looting = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
        int levelBonus = EnchantmentHelper.getItemEnchantmentLevel(looting, player.getWeaponItem());
        if (random.nextFloat() < 0.15F + 0.05F * levelBonus)
            spawnAtLocation(new ItemStack(StarwreckEntities.STAR_CORE_EMBER.get()));
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.AMETHYST_BLOCK_CHIME; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }
    @Override protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.6F, 0.6F);
    }
    @Override public float getVoicePitch() { return super.getVoicePitch() * 0.55F; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("StarHealth", starHealth());
        tag.putBoolean("StarBroken", isStarBroken());
        tag.putBoolean("Enraged", isEnraged());
        tag.putInt("Action", action());
        tag.putInt("Vulnerable", vulnerable);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(STAR_HEALTH, tag.contains("StarHealth") ? tag.getFloat("StarHealth") : STAR_MAX);
        entityData.set(STAR_BROKEN, tag.getBoolean("StarBroken"));
        entityData.set(ENRAGED, tag.getBoolean("Enraged"));
        entityData.set(ACTION, tag.getInt("Action"));
        vulnerable = tag.getInt("Vulnerable");
        int act = action();
        boolean lying = act == ACT_STAGGER || act == ACT_SHATTER;
        // 出招计时不存档：读档时没打完的招直接作废；怒吼中途存档的话直接进入狂暴，免得碎了星却不狂暴
        if (!lying) entityData.set(ACTION, NONE);
        if (isStarBroken() && !lying && !isEnraged()) entityData.set(ENRAGED, true);
        if (isEnraged()) getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28);
        if (lying) actionTicks = 0;
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 4, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            if (action() == ACT_STAGGER) return state.setAndContinue(STAGGER);
            // 闲逛移速 0.12，水平速度大约 0.01，过不了 GeckoLib isMoving 的 0.015。腿的摆动已经起来了。
            if (walkAnimation.speed() > 0.02F) return state.setAndContinue(isEnraged() ? WALK_RAGE : WALK);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
            .triggerableAnim("slam", SLAM).triggerableAnim("slam_fast", SLAM_FAST).triggerableAnim("backfire", BACKFIRE)
            .triggerableAnim("toss", TOSS).triggerableAnim("shatter", SHATTER).triggerableAnim("rage", RAGE)
            .triggerableAnim("sweep", SWEEP).triggerableAnim("beam", BEAM));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    /** 近了砸地，背后被盯住就喷焰，远了抛星。硬直和出招期间站住。 */
    private final class FightGoal extends Goal {
        FightGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive();
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void tick() {
            if (actionTicks >= 0 || action() == ACT_STAGGER) {
                getNavigation().stop();
                return;
            }
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target);
            float angle = angleTo(target);
            if (angle >= 120) behindTicks++;
            else behindTicks = 0;
            double dist = distanceTo(target);
            boolean see = getSensing().hasLineOfSight(target);
            if (!isEnraged() && flameCd <= 0 && (wantFlame || behindTicks >= 30)) {
                begin(ACT_BACKFIRE, "backfire");
                playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.2F, 0.6F);
            } else if (dist < 4.5 && angle > 40 && angle <= 110 && sweepCd <= 0) {
                begin(ACT_SWEEP, "sweep");
            } else if (dist < 4 && angle <= 45 && slamCd <= 0) {
                if (sweepCd <= 0 && random.nextFloat() < 0.35F) begin(ACT_SWEEP, "sweep");
                else if (isEnraged()) begin(ACT_FAST, "slam_fast");
                else begin(ACT_SLAM, "slam");
                if (action() != ACT_SWEEP) playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.6F);
            } else if (beamCd <= 0 && see && dist >= 7 && dist <= 22 && angle <= 25 && (isEnraged() || tossCd > 0 || random.nextBoolean())) {
                begin(ACT_BEAM, "beam");
                Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(beamOrigin());
                entityData.set(BEAM_YAW, yBodyRot);
                entityData.set(BEAM_PITCH, Mth.clamp((float) (Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG), -40, 30));
                playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.5F, 0.5F);
            } else if (!isEnraged() && tossCd <= 0 && see && dist >= 8 && dist <= 24) {
                begin(ACT_TOSS, "toss");
            } else {
                getNavigation().moveTo(target, 1.0);
            }
        }

        private float angleTo(LivingEntity target) {
            Vec3 to = target.position().subtract(position());
            double len = to.horizontalDistance();
            if (len < 1.0E-4) return 0;
            double dot = forward().dot(new Vec3(to.x / len, 0, to.z / len));
            return (float) Math.toDegrees(Math.acos(Mth.clamp(dot, -1, 1)));
        }
    }

    /** 平时用原版转向。出招时停步并把身体锁在出手朝向；光束会把这个朝向改成光束方向。 */
    private final class ActionLockMove extends MoveControl {
        ActionLockMove() { super(StarBearerEntity.this); }

        @Override public void tick() {
            if (actionTicks < 0) {
                super.tick();
                return;
            }
            operation = Operation.WAIT;
            setSpeed(0);
            setZza(0);
            setYRot(lockedYaw);
        }
    }

    private final class ActionLockTurn extends BodyRotationControl {
        ActionLockTurn() { super(StarBearerEntity.this); }

        @Override public void clientTick() {
            if (actionTicks < 0) {
                super.clientTick();
                return;
            }
            setYRot(lockedYaw);
            yBodyRot = lockedYaw;
            yHeadRot = lockedYaw;
        }
    }

    private static final class ShockRing {
        final Vec3 at;
        final Set<Integer> hit = new HashSet<>();
        int age;
        float radius = 1;

        ShockRing(Vec3 at) { this.at = at; }
    }
}
