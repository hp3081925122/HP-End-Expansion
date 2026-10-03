package org.hp.hp_end_expansion.entity.starwreck;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.config.CombatConfigs;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import org.joml.Vector3f;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 裂天之主：从天幕外撕开天空落进坠星巨坑的四足星骸巨兽。
 * 状态机在 {@link #customServerAiStep()} 里跑，STATE + ACTION 计数同步给客户端播动画和特效；
 * 阶段 1 猎（100%～60%）、2 瞳（60%～25%）、3 坠天（25%～0）。头部是单独的判定 {@link SkyrenderPart}。
 */
public class SkyrenderEntity extends Monster implements GeoEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final byte NONE = 0, RITUAL = 1, DESCEND = 2, ROAR = 3, PHASE_UP = 4, GORE = 5, RAKE = 6, TAIL = 7,
        LEAP = 8, SHARDS = 9, GAZE = 10, REND = 11, METEOR = 12, STAGGER = 13;
    // 各招时长与命中拍（tick）；动作控制器有 2 拍过渡，动画里的命中时刻比这里早 2 拍
    public static final int RITUAL_LEN = 240, RITUAL_RIFT = 40, RITUAL_CRUMBLE = 200, DESCEND_LEN = 24, ROAR_LEN = 50, PHASE_LEN = 60,
        GORE_LEN = 30, GORE_COMBO_LEN = 44, GORE_HIT = 14, GORE_HIT2 = 30,
        RAKE_LEN = 36, RAKE_FROM = 13, RAKE_TO = 16, RAKE_SNAP = 32,
        TAIL_LEN = 32, TAIL_HIT = 17,
        LEAP_LEN = 56, LEAP_LOCK = 12, LEAP_OFF = 18, LEAP_LAND = 34,
        SHARDS_LEN = 32, SHARDS_CAST = 14,
        GAZE_CHARGE = 50, GAZE_CHARGE_FINAL = 40, GAZE_REST = 50,
        REND_LEN = 58, REND_LOCK = 16, REND_TEAR = 20, REND_DROP = 24, REND_COUNT = 12,
        DEATH_LEN = 100, SHARD_WARN = 24, REND_WARN = 16,
        // 天陨：起势 0、撕天 30、坠星 60、撞击 150、余烬到 200，之后跪伏脱力 60 拍
        METEOR_TEAR = 30, METEOR_DROP = 60, METEOR_HIT = 150, METEOR_LEN = 200, METEOR_REST = 60,
        // 注视蓄力被打头打断：前 14 拍往后滑退约 6 格，12、22 拍踉跄两步，32 拍站稳低吼，40 拍结束
        STAGGER_LEN = 40, STAGGER_SLIDE = 14;
    /** 打断击退的初速度（格/拍），之后按 (1-t/14)^1.5 衰减。 */
    private static final double STAGGER_SPEED = 1.1, STAGGER_HOP = 0.25;
    /** 天陨的星从坑外 METEOR_RANGE 格、METEOR_HEIGHT 格高的天裂里斜着砸进坑心：远处先探出一小块，再朝玩家越压越大。 */
    public static final double METEOR_HEIGHT = 150, METEOR_RANGE = 300;
    /** 渲染缩放，以及服务端估算的眼睛位置（离脚底的高度、离身体中心的前伸距离，单位格）。 */
    public static final float MODEL_SCALE = 1.75F;
    public static final double EYE_FORWARD = 3.6, EYE_UP = 2.4, RIFT_HEIGHT = 34, SKY_TEAR_HEIGHT = 26;
    /** 爪扫后留在空中的三道裂痕：离身体中心的半径（格）、左右各张开的角度（度）、离地高度范围。 */
    public static final double[] TEAR_RADII = {5.5, 6.5, 7.5};
    public static final double TEAR_SPAN = 70, TEAR_LOW = 0.3, TEAR_HIGH = 2.2, RAKE_REACH = 7.0, GAZE_RANGE = 48, GAZE_CONE = 75;
    private static final ResourceLocation PARTY_HEALTH = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "skyrender_party_health");
    private static final ResourceLocation FINAL_SPEED = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "skyrender_final_speed");

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(SkyrenderEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(SkyrenderEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> VARIANT = SynchedEntityData.defineId(SkyrenderEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(SkyrenderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LOCK_YAW = SynchedEntityData.defineId(SkyrenderEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> AIM = SynchedEntityData.defineId(SkyrenderEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> ANCHOR = SynchedEntityData.defineId(SkyrenderEntity.class, EntityDataSerializers.VECTOR3);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.skyrender.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.skyrender.walk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.skyrender.death");
    private static final RawAnimation A_DESCEND = RawAnimation.begin().thenLoop("animation.skyrender.descend");
    private static final RawAnimation A_ROAR = RawAnimation.begin().thenPlay("animation.skyrender.roar");
    private static final RawAnimation A_PHASE = RawAnimation.begin().thenPlay("animation.skyrender.rear_roar");
    private static final RawAnimation A_GORE = RawAnimation.begin().thenPlay("animation.skyrender.gore");
    private static final RawAnimation A_GORE_COMBO = RawAnimation.begin().thenPlay("animation.skyrender.gore_combo");
    private static final RawAnimation A_RAKE_LEFT = RawAnimation.begin().thenPlay("animation.skyrender.rake_left");
    private static final RawAnimation A_RAKE_RIGHT = RawAnimation.begin().thenPlay("animation.skyrender.rake_right");
    private static final RawAnimation A_TAIL = RawAnimation.begin().thenPlay("animation.skyrender.tail_slam");
    private static final RawAnimation A_LEAP = RawAnimation.begin().thenPlay("animation.skyrender.leap");
    private static final RawAnimation A_SHARDS = RawAnimation.begin().thenPlay("animation.skyrender.shards");
    private static final RawAnimation A_GAZE = RawAnimation.begin().thenPlayAndHold("animation.skyrender.gaze");
    private static final RawAnimation A_GAZE_REST = RawAnimation.begin().thenPlay("animation.skyrender.gaze_release").thenLoop("animation.skyrender.exhausted");
    private static final RawAnimation A_REND = RawAnimation.begin().thenPlay("animation.skyrender.rend");
    private static final RawAnimation A_METEOR = RawAnimation.begin().thenPlay("animation.skyrender.meteor").thenLoop("animation.skyrender.meteor_rest");
    private static final RawAnimation A_STAGGER = RawAnimation.begin().thenPlay("animation.skyrender.staggered");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20);
    private final SkyrenderPart head;
    private final PartEntity<?>[] parts;

    // 战场：坑中心、坑半径、坑底高度
    private Vec3 center = Vec3.ZERO;
    private double radius = 28, floorY;
    private boolean arenaReady, partyScaled, meteorUsed;
    private float eyeDamage;
    // 状态机计时与冷却
    private int stateTicks, gapCd = 20, goreCd, rakeCd = 30, tailCd, leapCd = 100, shardCd = 140, gazeCd = 120, rendCd = 160, skyfallCd = 140;
    private int behindTicks, stuckTicks;
    private Vec3 stuckCheck = Vec3.ZERO, leapFrom = Vec3.ZERO, rendFrom = Vec3.ZERO, rendDir = Vec3.ZERO, descendFrom = Vec3.ZERO;
    private final Set<Integer> hits = new HashSet<>();
    private final List<UUID> cultists = new ArrayList<>();
    // 客户端：当前动作已过的拍数；竖瞳中心相对脚底的位置由渲染器每帧写回，给注视的眼光用
    private int clientAction = -1, clientAge, animAction = -1;
    @Nullable public Vec3 eyeAnchor;

    public SkyrenderEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        // 头部判定：编号紧跟在本体后面，客户端按同样规则对上
        head = new SkyrenderPart(this, 2.2F, 2.5F);
        parts = new PartEntity<?>[]{head};
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        xpReward = 600;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 800)
            .add(Attributes.ATTACK_DAMAGE, 15)
            .add(Attributes.ARMOR, 14)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.FOLLOW_RANGE, 48)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1)
            .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, NONE).define(PHASE, (byte) 0).define(VARIANT, (byte) 1).define(ACTION, 0).define(LOCK_YAW, 0F)
            .define(AIM, new Vector3f()).define(ANCHOR, new Vector3f());
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new ChaseGoal());
    }

    @Override public void setId(int id) {
        super.setId(id);
        // 部件编号 = 本体编号 + 1，服务端和客户端一致
        for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    @Override public boolean isMultipartEntity() { return true; }
    @Override public PartEntity<?>[] getParts() { return parts; }

    // ---------- 同步数据 ----------
    public byte getState() { return entityData.get(STATE); }
    public byte getPhase() { return entityData.get(PHASE); }
    public int getVariant() { return entityData.get(VARIANT); }
    public int getActionCount() { return entityData.get(ACTION); }
    public int getClientAge() { return clientAge; }
    public Vec3 getAim() { Vector3f v = entityData.get(AIM); return new Vec3(v.x, v.y, v.z); }
    public Vec3 getAnchor() { Vector3f v = entityData.get(ANCHOR); return new Vec3(v.x, v.y, v.z); }
    private void setAim(Vec3 v) { entityData.set(AIM, v.toVector3f()); }
    public boolean isCombo() { return getVariant() == 2; }

    /** 注视蓄力长度：第三阶段更短。 */
    public int gazeCharge() {
        if (getPhase() >= 3) return GAZE_CHARGE_FINAL;
        return GAZE_CHARGE;
    }

    /** 注视后喘息、天陨后跪伏：这段时间受到的伤害加倍率。 */
    public boolean isExhausted() {
        byte s = getState();
        if (s == METEOR) return stateOrClientAge() >= METEOR_LEN;
        return s == GAZE && stateOrClientAge() >= gazeCharge();
    }

    private int stateOrClientAge() {
        if (level().isClientSide) return clientAge;
        return stateTicks;
    }

    private static double stat(String key) { return CombatConfigs.SKYRENDER.value(key); }
    private static float damage(String key) { return CombatConfigs.SKYRENDER.damage(key); }

    public Vec3 forward() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /** 服务端用的眼睛位置：注视时头抬平，按固定前伸距离估算，和客户端暗角判定共用。 */
    public Vec3 eyeCenter() { return position().add(forward().scale(EYE_FORWARD)).add(0, EYE_UP, 0); }

    // ---------- 召唤 ----------
    /** 天瞳兆石调用：在坑底找一块站得下的地方当落点，本体在落点正上方的裂口里待命，开始约 12 秒的仪式。 */
    @Nullable public static SkyrenderEntity summon(ServerLevel level, Vec3 craterCenter, double craterRadius, Player summoner) {
        SkyrenderEntity boss = StarwreckEntities.SKYRENDER.get().create(level);
        if (boss == null) return null;
        boss.setupArena(craterCenter, craterRadius, false);
        Vec3 land = boss.findLandingSpot(summoner.position());
        if (land == null) return null;
        boss.entityData.set(ANCHOR, land.toVector3f());
        boss.moveTo(land.x, land.y + RIFT_HEIGHT, land.z, summoner.getYRot() + 180, 0);
        boss.noPhysics = true;
        boss.setNoGravity(true);
        boss.startState(RITUAL);
        level.addFreshEntity(boss);
        boss.spawnCultists(level, land);
        level.playSound(null, land.x, land.y, land.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 4, 0.5F);
        LOGGER.debug("Skyrender ritual started: center={}, radius={}, landing={}", craterCenter, craterRadius, land);
        return boss;
    }

    /** 刷怪蛋或指令生成：没有仪式，就地当战场中心，直接落地怒吼。 */
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        setupArena(position(), 28, true);
        entityData.set(ANCHOR, position().toVector3f());
        entityData.set(PHASE, (byte) 1);
        startState(ROAR);
        return result;
    }

    private void setupArena(Vec3 craterCenter, double craterRadius, boolean here) {
        center = craterCenter;
        radius = craterRadius;
        floorY = craterCenter.y;
        if (!here && level() instanceof ServerLevel) {
            // 坑底高度：中心附近一圈取最低的地面
            double best = Double.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                int x = Mth.floor(craterCenter.x + Math.cos(a) * craterRadius * 0.45);
                int z = Mth.floor(craterCenter.z + Math.sin(a) * craterRadius * 0.45);
                best = Math.min(best, level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            }
            floorY = best;
        }
        arenaReady = true;
    }

    /** 落点：从坑中心往召唤者方向偏半个坑半径，避开坑中间的巨型残骸；站不下就绕一圈换方向。 */
    @Nullable private Vec3 findLandingSpot(Vec3 toward) {
        double base = Math.atan2(toward.z - center.z, toward.x - center.x);
        for (int i = 0; i < 12; i++) {
            double a = base + Math.PI * 2 * i / 12;
            for (double r : new double[]{radius * 0.5, radius * 0.35, radius * 0.65}) {
                double x = center.x + Math.cos(a) * r, z = center.z + Math.sin(a) * r;
                double y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                Vec3 p = new Vec3(x, y, z);
                if (fitsAt(p)) return p;
            }
        }
        return null;
    }

    private boolean fitsAt(Vec3 p) {
        return level().noCollision(this, getDimensions(Pose.STANDING).makeBoundingBox(p));
    }

    private void spawnCultists(ServerLevel level, Vec3 land) {
        // 坑沿等距站一个唤星者、三个殉星者，不动、不受伤，只是演出
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 2 * i / 4 + 0.4;
            double x = center.x + Math.cos(a) * radius * 0.9, z = center.z + Math.sin(a) * radius * 0.9;
            double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            Mob cultist;
            if (i == 0) cultist = StarwreckEntities.STAR_CALLER.get().create(level);
            else cultist = StarwreckEntities.STAR_MARTYR.get().create(level);
            if (cultist == null) continue;
            cultist.moveTo(x, y, z, (float) (Mth.atan2(land.z - z, land.x - x) * Mth.RAD_TO_DEG) - 90, 0);
            cultist.setNoAi(true);
            cultist.setInvulnerable(true);
            cultist.setPersistenceRequired();
            cultist.setSilent(true);
            cultist.addTag("skyrender_ritual");
            level.addFreshEntity(cultist);
            cultists.add(cultist.getUUID());
        }
    }

    private void clearCultists(ServerLevel level) {
        for (UUID id : cultists) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        cultists.clear();
        // 读档后列表丢了也能按标签清掉
        for (Entity e : level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(radius + 12, 40, radius + 12),
            m -> m.getTags().contains("skyrender_ritual"))) e.discard();
    }

    /** 星雨调度和兆石用：这个维度里是否已有一只活着的裂天之主。 */
    public static boolean present(ServerLevel level) {
        return !level.getEntities(StarwreckEntities.SKYRENDER.get(), Entity::isAlive).isEmpty();
    }

    // ---------- 主循环 ----------
    private void startState(byte state) {
        entityData.set(STATE, state);
        entityData.set(ACTION, entityData.get(ACTION) + 1);
        stateTicks = 0;
        hits.clear();
        getNavigation().stop();
        entityData.set(LOCK_YAW, yBodyRot);
        if (state != NONE) {
            LivingEntity target = getTarget();
            int targetId = -1;
            if (target != null) targetId = target.getId();
            LOGGER.debug("Skyrender action started: entity={}, action={}, state={}, phase={}, target={}, health={}/{}",
                getId(), getActionCount(), state, getPhase(), targetId, getHealth(), getMaxHealth());
        }
    }

    private boolean locksYaw(byte s) {
        return s != NONE && s != RITUAL && s != DESCEND;
    }

    @Override public void tick() {
        byte state = getState();
        // 仪式、坠落由状态机直接摆位置
        if (state == RITUAL || state == DESCEND) setDeltaMovement(Vec3.ZERO);
        super.tick();
        // 出招期间锁住朝向，两端用同一个同步角度
        if (locksYaw(getState()) && !isDeadOrDying()) {
            float yaw = entityData.get(LOCK_YAW);
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
        updateParts();
        if (level().isClientSide) clientTick();
    }

    private void updateParts() {
        // 头部判定跟着身体朝向；人立、仰天时抬高后收
        Vec3 f = forward();
        double reach = 3.25, lift = 0.7;
        byte s = getState();
        int age = stateOrClientAge();
        if ((s == PHASE_UP && age > 6 && age < 46) || (s == REND && age > 6 && age < 26)) {
            reach = 2.2;
            lift = 2.6;
        }
        if (s == GAZE && age >= gazeCharge()) lift = 0.3;
        head.xo = head.getX();
        head.yo = head.getY();
        head.zo = head.getZ();
        head.setPos(getX() + f.x * reach, getY() + lift, getZ() + f.z * reach);
    }

    private void clientTick() {
        int action = entityData.get(ACTION);
        if (action != clientAction) {
            clientAction = action;
            clientAge = 0;
        } else clientAge++;
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level) || isDeadOrDying()) return;
        // 刷怪蛋、指令或旧存档生成：就地当战场
        if (!arenaReady) {
            setupArena(position(), 28, true);
            entityData.set(ANCHOR, position().toVector3f());
        }
        if (getPhase() == 0 && getState() != RITUAL && getState() != DESCEND) entityData.set(PHASE, (byte) 1);
        tickBossBar(level);
        List<Player> fighters = fighters();
        byte state = getState();
        if (state == RITUAL) {
            tickRitual(level);
            return;
        }
        if (state == DESCEND) {
            tickDescend(level);
            return;
        }
        pickTarget(fighters);
        scaleForParty(fighters);
        tickCooldowns();
        if (getPhase() >= 3 && state != PHASE_UP) tickSkyfall(level, fighters);
        if (state == NONE) {
            if (!checkPhase() && !checkMeteor()) chooseAction(level, fighters);
            return;
        }
        stateTicks++;
        LivingEntity target = getTarget();
        switch (state) {
            case ROAR -> tickRoar(level);
            case PHASE_UP -> tickPhaseUp(level, fighters);
            case GORE -> tickGore(target);
            case RAKE -> tickRake(level, target);
            case TAIL -> { if (stateTicks == TAIL_HIT) tailHit(level); }
            case LEAP -> tickLeap(level, target);
            case SHARDS -> { if (stateTicks == SHARDS_CAST) castShards(level, fighters); }
            case GAZE -> tickGaze(level, fighters);
            case REND -> tickRend(level, target);
            case METEOR -> tickMeteor(level, fighters);
            case STAGGER -> tickStagger(level);
            default -> {}
        }
        if (getState() == state && stateTicks >= duration(state)) finishAction(state);
    }

    private int duration(byte s) {
        switch (s) {
            case ROAR: return ROAR_LEN;
            case PHASE_UP: return PHASE_LEN;
            case GORE:
                if (isCombo()) return GORE_COMBO_LEN;
                return GORE_LEN;
            case RAKE: return RAKE_LEN;
            case TAIL: return TAIL_LEN;
            case LEAP: return LEAP_LEN;
            case SHARDS: return SHARDS_LEN;
            case GAZE: return gazeCharge() + GAZE_REST;
            case REND: return REND_LEN;
            case METEOR: return METEOR_LEN + METEOR_REST;
            case STAGGER: return STAGGER_LEN;
            default: return 1;
        }
    }

    private void finishAction(byte state) {
        startState(NONE);
        // 两招之间的空档，越往后越短
        byte phase = getPhase();
        if (phase >= 3) gapCd = 6;
        else if (phase == 2) gapCd = 11;
        else gapCd = 16;
        if (state == LEAP) {
            noPhysics = false;
            setNoGravity(false);
        }
    }

    private void tickCooldowns() {
        // 天陨演出期间各招冷却不走
        if (getState() == METEOR) return;
        // 第三阶段冷却快 1.4 倍
        int step = 1;
        if (getPhase() >= 3 && tickCount % 5 < 2) step = 2;
        goreCd -= step;
        rakeCd -= step;
        tailCd -= step;
        leapCd -= step;
        shardCd -= step;
        gazeCd -= step;
        rendCd -= step;
        if (getState() == NONE) gapCd--;
    }

    private void tickBossBar(ServerLevel level) {
        bossEvent.setProgress(getHealth() / getMaxHealth());
        byte s = getState();
        bossEvent.setVisible(s != RITUAL && s != DESCEND);
        if (tickCount % 10 != 0) return;
        // 96 格内的玩家看得到血条
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 96 * 96) bossEvent.addPlayer(p);
            else bossEvent.removePlayer(p);
        }
    }

    private List<Player> fighters() {
        double r = radius + 16;
        return level().getEntitiesOfClass(Player.class, new AABB(center, center).inflate(r, 48, r),
            p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.position().subtract(center).horizontalDistance() <= r);
    }

    private void pickTarget(List<Player> fighters) {
        LivingEntity current = getTarget();
        boolean valid = current instanceof Player p && fighters.contains(p);
        if (valid && tickCount % 20 != 0) return;
        // 每秒重选一次最近的玩家
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : fighters) {
            double d = p.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        setTarget(best);
    }

    private void scaleForParty(List<Player> fighters) {
        if (partyScaled || fighters.isEmpty()) return;
        partyScaled = true;
        // 多人加血：每多一人 +50%，最多按 4 人算，最终受原版 1024 上限约束
        int players = Math.min(4, fighters.size());
        if (players <= 1) return;
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        health.addOrReplacePermanentModifier(new AttributeModifier(PARTY_HEALTH, 0.5 * (players - 1), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        setHealth(getMaxHealth());
        LOGGER.debug("Skyrender scaled for {} players, max health {}", players, getMaxHealth());
    }

    // ---------- 仪式与降临 ----------
    private void tickRitual(ServerLevel level) {
        stateTicks++;
        Vec3 land = getAnchor();
        // 兆石悬在落点上方慢慢往上冒天幕碎屑；天上的裂口从第 2 秒开始撕开
        if (stateTicks % 3 == 0) level.sendParticles(ModParticles.SKY_MOTE.get(), land.x, land.y + 2, land.z, 3, 0.4, 0.4, 0.4, 0.02);
        if (stateTicks % 20 == 0 && stateTicks < RITUAL_CRUMBLE)
            level.playSound(null, land.x, land.y, land.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 4, 0.5F);
        if (stateTicks == RITUAL_RIFT) playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 6, 0.4F);
        if (stateTicks == RITUAL_CRUMBLE) playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 8, 0.5F);
        // 教徒一个个碎成天幕碎屑，往裂口飘
        if (stateTicks > RITUAL_CRUMBLE && stateTicks % 9 == 0 && !cultists.isEmpty()) {
            Entity e = level.getEntity(cultists.remove(0));
            if (e != null) {
                level.sendParticles(ModParticles.SKY_MOTE.get(), e.getX(), e.getY() + 1, e.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
                e.discard();
            }
        }
        // 兆石碎掉
        if (stateTicks == RITUAL_LEN - 4) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(StarwreckEntities.SKY_EYE_OMEN.get())),
                land.x, land.y + 2, land.z, 24, 0.2, 0.2, 0.2, 0.15);
            level.playSound(null, land.x, land.y, land.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3, 0.6F);
        }
        if (stateTicks >= RITUAL_LEN) {
            clearCultists(level);
            descendFrom = position();
            startState(DESCEND);
        }
    }

    private void tickDescend(ServerLevel level) {
        stateTicks++;
        Vec3 land = getAnchor();
        // 从裂口里越落越快地砸进坑底
        double s = Math.min(1, stateTicks / (double) DESCEND_LEN);
        setPos(descendFrom.lerp(land, s * s));
        if (stateTicks == 6) playSound(SoundEvents.ENDER_DRAGON_GROWL, 8, 0.5F);
        if (stateTicks < DESCEND_LEN) return;
        // 落地：10 格内的生物受伤并被掀开
        setPos(land);
        noPhysics = false;
        setNoGravity(false);
        float dmg = damage("landDamage");
        for (LivingEntity e : targets(new AABB(land, land).inflate(10, 5, 10))) {
            Vec3 away = e.position().subtract(land).multiply(1, 0, 1);
            if (away.lengthSqr() > 100) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) push(e, away, 1.4, 0.6);
        }
        groundBurst(level, land, 3.5F, 80);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, land.x, land.y + 0.5, land.z, 1, 0, 0, 0, 0);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 8, 0.45F);
        playSound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 6, 0.5F);
        entityData.set(PHASE, (byte) 1);
        startState(ROAR);
        LOGGER.debug("Skyrender landed at {}", land);
    }

    private void tickRoar(ServerLevel level) {
        if (stateTicks == 12) {
            playSound(SoundEvents.RAVAGER_ROAR, 6, 0.45F);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 5, 0.7F);
        }
        if (stateTicks > 12 && stateTicks < 36 && stateTicks % 4 == 0)
            level.sendParticles(ModParticles.SKY_MOTE.get(), getX(), getY() + 3, getZ(), 12, 2.5, 1.5, 2.5, 0.06);
    }

    // ---------- 阶段 ----------
    private float threshold(byte phase) {
        if (phase == 1) return 0.6F;
        if (phase == 2) return 0.25F;
        return 0;
    }

    private boolean checkPhase() {
        byte phase = getPhase();
        if (phase >= 3 || getHealth() > getMaxHealth() * threshold(phase)) return false;
        startState(PHASE_UP);
        return true;
    }

    private void tickPhaseUp(ServerLevel level, List<Player> fighters) {
        if (stateTicks == 1) {
            byte next = (byte) (getPhase() + 1);
            entityData.set(PHASE, next);
            if (next == 3) {
                AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) speed.addOrReplacePermanentModifier(new AttributeModifier(FINAL_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            LOGGER.debug("Skyrender enters phase {}", next);
        }
        // 人立怒吼：近处的玩家被吼退，不掉血
        if (stateTicks == 16) {
            playSound(SoundEvents.RAVAGER_ROAR, 8, 0.4F);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 6, 0.55F);
            for (LivingEntity e : targets(getBoundingBox().inflate(8.75, 3.75, 8.75))) push(e, e.position().subtract(position()), 1.4, 0.45);
        }
        // 第三阶段：坑上方撕开一道不再合拢的长缝，先落一圈碎片
        if (getPhase() >= 3 && stateTicks == 22) {
            playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 8, 0.5F);
            for (int i = 0; i < 8; i++) {
                Vec3 p = ring(center, 4, radius * 0.85);
                SkyShardEntity.spawn(level, this, ground(p), damage("shardDamage"), 30, 160, 1.5F);
            }
        }
        if (stateTicks == 46) groundBurst(level, position().add(forward().scale(2.5)), 2.5F, 30);
        if (stateTicks >= PHASE_LEN - 1 && getPhase() == 2) gazeCd = Math.min(gazeCd, 100);
    }

    // ---------- 天陨 ----------
    /** 第二阶段掉到半血时固定放一次，只在两招之间检查，不打断正在出的招。 */
    private boolean checkMeteor() {
        if (meteorUsed || getPhase() != 2 || getHealth() > getMaxHealth() * meteorTrigger()) return false;
        startState(METEOR);
        if (level() instanceof ServerLevel level) {
            Vec3 at = new Vec3(center.x, floorY, center.z);
            // 星从玩家对面的天上来：取玩家指向坑心的平均方向，没有玩家就放在首领背后
            Vec3 from = Vec3.ZERO;
            for (Player p : fighters()) from = from.add(new Vec3(center.x - p.getX(), 0, center.z - p.getZ()).normalize());
            if (from.lengthSqr() < 0.04) from = forward().scale(-1);
            float fromYaw = (float) Math.toDegrees(Math.atan2(from.z, from.x));
            // 星要大到一进画面就占一大块天，落地前一秒撑满整个视野：半径约等于坑半径
            SkyMeteorEntity.spawn(level, at, (float) Math.max(24, radius * 0.9), (float) radius, fromYaw);
        }
        playSound(SoundEvents.WITHER_SPAWN, 10, 0.5F);
        for (Player p : fighters()) p.displayClientMessage(Component.translatable("message.hp_end_expansion.skyrender.meteor"), true);
        LOGGER.debug("Skyrender meteor started: health={}/{}", getHealth(), getMaxHealth());
        return true;
    }

    private static float meteorTrigger() { return (float) stat("meteorTrigger"); }

    private void tickMeteor(ServerLevel level, List<Player> fighters) {
        int t = stateTicks;
        // 撕天：两只前爪各撕一下
        if (t == METEOR_TEAR || t == METEOR_TEAR + 15) playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 8, 0.5F);
        if (t == METEOR_DROP) playSound(SoundEvents.ENDER_DRAGON_GROWL, 8, 0.4F);
        if (t == METEOR_HIT - 30) playSound(SoundEvents.WARDEN_SONIC_CHARGE, 8, 0.6F);
        if (t == METEOR_HIT - 20) playSound(SoundEvents.BEACON_POWER_SELECT, 8, 0.5F);
        // 坠星：满场往下飘余烬
        if (t > METEOR_DROP && t < METEOR_HIT) {
            for (int i = 0; i < 6; i++) {
                Vec3 p = ring(center, 0, radius);
                level.sendParticles(ModParticles.STAR_EMBER.get(), p.x, floorY + 10 + random.nextDouble() * 14, p.z, 0, 0, -1, 0, 0.15);
            }
        }
        if (t == METEOR_HIT) meteorImpact(level, fighters);
        // 余烬：碎屑往上飘回天裂
        if (t > METEOR_HIT && t < METEOR_LEN && t % 2 == 0) {
            Vec3 p = ring(center, 0, radius * 0.8);
            level.sendParticles(ModParticles.SKY_MOTE.get(), p.x, floorY + 1, p.z, 6, 1.5, 0.5, 1.5, 0.08);
        }
        if (t == METEOR_LEN) playSound(SoundEvents.BEACON_DEACTIVATE, 6, 0.5F);
    }

    /** 星落地：场内每个玩家的生命变成当前的一半。直接改血，不走伤害，护甲、抗性、不死图腾都不管用，也不会致死。 */
    private void meteorImpact(ServerLevel level, List<Player> fighters) {
        meteorUsed = true;
        float ratio = (float) stat("meteorHealthRatio");
        for (Player p : fighters) {
            p.setHealth(p.getHealth() * ratio);
            level.getChunkSource().broadcastAndSend(p, new ClientboundHurtAnimationPacket(p));
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1, 0.8F);
            push(p, p.position().subtract(center), 1.0, 0.4);
        }
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 10, 0.5F);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 10, 0.6F);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 10, 0.6F);
        playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 10, 0.4F);
        Vec3 floor = new Vec3(center.x, floorY, center.z);
        groundBurst(level, floor, 3, 60);
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            double r = radius * (0.35 + 0.45 * random.nextDouble());
            groundBurst(level, ground(floor.add(Math.cos(a) * r, 0, Math.sin(a) * r)), 1.5F, 18);
        }
        LOGGER.debug("Skyrender meteor impact: players={}, ratio={}", fighters.size(), ratio);
    }

    // ---------- 选招 ----------
    private void chooseAction(ServerLevel level, List<Player> fighters) {
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) {
            behindTicks = 0;
            return;
        }
        Vec3 to = t.position().subtract(position());
        double dist = to.horizontalDistance();
        double ang = signedAngle(to);
        boolean above = t.getY() - getY() > 2.5;
        // 目标在身后近处持续 1 秒：尾锤
        if (dist < 9.375 && Math.abs(ang) > 115) behindTicks++;
        else behindTicks = 0;
        // 追了 2 秒还挪不动：多半被地形卡住
        if (tickCount % 40 == 0) {
            if (position().distanceToSqr(stuckCheck) < 1 && dist > 6) stuckTicks += 40;
            else stuckTicks = 0;
            stuckCheck = position();
        }
        if (gapCd > 0) return;
        byte phase = getPhase();
        double fromCenter = position().subtract(center).horizontalDistance();
        boolean farFromArena = fromCenter > radius + 22;
        if (behindTicks >= 20 && tailCd <= 0) {
            tailCd = 80;
            behindTicks = 0;
            startState(TAIL);
            playSound(SoundEvents.RAVAGER_ATTACK, 3, 0.5F);
            return;
        }
        if (phase >= 2 && gazeCd <= 0 && anyoneSees(fighters)) {
            startGaze();
            return;
        }
        boolean wantLeap = dist > 11 || above || stuckTicks >= 80 || farFromArena;
        if (wantLeap && leapCd <= 0 && dist < 34) {
            Vec3 target = t.position();
            if (farFromArena) target = center;
            Vec3 land = landingFor(target);
            if (land != null) {
                leapCd = 170;
                stuckTicks = 0;
                setAim(land);
                startState(LEAP);
                playSound(SoundEvents.RAVAGER_ROAR, 4, 0.7F);
                return;
            }
        }
        if (phase >= 2 && rendCd <= 0 && dist >= 6 && dist <= 32 && random.nextInt(3) == 0) {
            rendCd = 300;
            startState(REND);
            playSound(SoundEvents.RAVAGER_ROAR, 5, 0.55F);
            return;
        }
        if (shardCd <= 0 && (dist > 8 || above || random.nextInt(8) == 0)) {
            shardCd = 230;
            startState(SHARDS);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 5, 0.8F);
            return;
        }
        if (above) return;
        if (dist <= 6.5 && Math.abs(ang) <= 55 && goreCd <= 0) {
            goreCd = 36;
            byte combo = 1;
            if (phase >= 3) combo = 2;
            entityData.set(VARIANT, combo);
            startState(GORE);
            playSound(SoundEvents.RAVAGER_ATTACK, 3, 0.55F);
            return;
        }
        if (dist <= RAKE_REACH + 0.5 && Math.abs(ang) <= 110 && rakeCd <= 0) {
            rakeCd = 56;
            // 目标在左边就用左前爪从左往右扫，反之用右前爪
            byte side = 1;
            if (ang < 0) side = -1;
            entityData.set(VARIANT, side);
            startState(RAKE);
            playSound(SoundEvents.RAVAGER_ATTACK, 3, 0.65F);
        }
    }

    /** 相对身体正前方的水平夹角（度），左正右负。 */
    private double signedAngle(Vec3 d) {
        Vec3 f = forward();
        double left = f.z * d.x - f.x * d.z;
        double dot = f.x * d.x + f.z * d.z;
        return Math.toDegrees(Math.atan2(left, dot));
    }

    private void turnToward(Vec3 at, float maxStep) {
        double dx = at.x - getX(), dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-4) return;
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        float yaw = Mth.approachDegrees(entityData.get(LOCK_YAW), want, maxStep);
        entityData.set(LOCK_YAW, yaw);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ---------- 角挑 ----------
    private void tickGore(@Nullable LivingEntity t) {
        if (t != null && (stateTicks <= 9 || (isCombo() && stateTicks > GORE_HIT + 2 && stateTicks <= GORE_HIT2 - 6))) turnToward(t.position(), 10);
        // 命中前两拍往前一蹿
        if (stateTicks == GORE_HIT - 2 || (isCombo() && stateTicks == GORE_HIT2 - 2)) {
            Vec3 f = forward();
            setDeltaMovement(getDeltaMovement().add(f.x * 0.45, 0, f.z * 0.45));
        }
        if (stateTicks == GORE_HIT || (isCombo() && stateTicks == GORE_HIT2)) goreHit();
    }

    private void goreHit() {
        hits.clear();
        Vec3 f = forward();
        float dmg = damage("goreDamage");
        int count = 0;
        // 前方 55° 扇形、6.75 格内上挑
        for (LivingEntity e : targets(getBoundingBox().inflate(6.875, 3.75, 6.875))) {
            Vec3 d = e.position().subtract(position());
            if (d.horizontalDistance() > 6.75 || Math.abs(signedAngle(d)) > 55 || d.y < -1.875 || d.y > 5) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                e.setDeltaMovement(e.getDeltaMovement().add(f.x * 1.1, 0.8, f.z * 1.1));
                e.hurtMarked = true;
                count++;
            }
        }
        Vec3 tip = position().add(f.scale(4)).add(0, 1.5, 0);
        ((ServerLevel) level()).sendParticles(ModParticles.STAR_DEBRIS.get(), tip.x, tip.y, tip.z, 14, 0.6, 0.4, 0.6, 0.15);
        playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 3, 0.5F);
        playSound(SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
        LOGGER.debug("Skyrender gore resolved: entity={}, tick={}, hits={}", getId(), stateTicks, count);
    }

    // ---------- 裂爪 ----------
    /** 爪扫前锋角度（度）：从起手一侧 100° 匀速扫到另一侧，渲染器用同一个公式。 */
    public static double rakeLead(double tick, int side) {
        double t = Mth.clamp((tick - (RAKE_FROM - 1)) / (double) (RAKE_TO - RAKE_FROM + 1), 0, 1);
        return side * Mth.lerp(t, 100, -100);
    }

    private void tickRake(ServerLevel level, @Nullable LivingEntity t) {
        if (t != null && stateTicks <= 10) turnToward(t.position(), 6);
        int side = getVariant();
        if (stateTicks >= RAKE_FROM && stateTicks <= RAKE_TO) {
            double a0 = rakeLead(stateTicks - 1, side), a1 = rakeLead(stateTicks, side);
            double lo = Math.min(a0, a1), hi = Math.max(a0, a1);
            Vec3 f = forward();
            Vec3 sideDir = new Vec3(f.z, 0, -f.x).scale(-side);
            float dmg = damage("rakeDamage");
            for (LivingEntity e : targets(getBoundingBox().inflate(RAKE_REACH, 2.5, RAKE_REACH))) {
                Vec3 d = e.position().subtract(position());
                double a = signedAngle(d);
                if (d.horizontalDistance() > RAKE_REACH || a < lo - 4 || a > hi + 4 || d.y < -1.875 || d.y > 4.375 || !hits.add(e.getId())) continue;
                if (e.hurt(damageSources().mobAttack(this), dmg)) push(e, sideDir, 1.2, 0.35);
            }
            if (stateTicks == RAKE_FROM) playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3, 0.5F);
        }
        if (stateTicks == RAKE_TO + 1) playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3, 0.5F);
        if (stateTicks == RAKE_SNAP) tearSnap(level);
    }

    /** 爪扫后空中三道裂痕合拢：碰到任何一道的生物受伤。 */
    private void tearSnap(ServerLevel level) {
        hits.clear();
        float dmg = damage("tearDamage");
        double far = TEAR_RADII[TEAR_RADII.length - 1] + 1;
        for (LivingEntity e : targets(getBoundingBox().inflate(far, 3, far))) {
            Vec3 d = e.position().subtract(position());
            double r = d.horizontalDistance();
            if (Math.abs(signedAngle(d)) > TEAR_SPAN + 4 || d.y < TEAR_LOW - 2 || d.y > TEAR_HIGH + 0.5) continue;
            boolean touch = false;
            for (double tr : TEAR_RADII) if (Math.abs(r - tr) < 0.9375) touch = true;
            if (touch && hits.add(e.getId())) e.hurt(damageSources().mobAttack(this), dmg);
        }
        // 合拢处迸出天幕碎屑
        Vec3 f = forward();
        for (double tr : TEAR_RADII) {
            for (int i = -2; i <= 2; i++) {
                double a = Math.toRadians(i * TEAR_SPAN / 2.5);
                Vec3 dir = new Vec3(f.x * Math.cos(a) + f.z * Math.sin(a), 0, f.z * Math.cos(a) - f.x * Math.sin(a));
                Vec3 p = position().add(dir.scale(tr)).add(0, (TEAR_LOW + TEAR_HIGH) / 2, 0);
                level.sendParticles(ModParticles.SKY_MOTE.get(), p.x, p.y, p.z, 4, 0.3, 0.5, 0.3, 0.05);
            }
        }
        playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 3, 0.6F);
        playSound(SoundEvents.GLASS_BREAK, 2, 0.5F);
    }

    // ---------- 尾锤 ----------
    private void tailHit(ServerLevel level) {
        Vec3 f = forward();
        Vec3 side = new Vec3(-f.z, 0, f.x);
        float dmg = damage("tailDamage");
        // 身后一条 1.875～9.375 格长、5 格宽的长条
        for (LivingEntity e : targets(getBoundingBox().inflate(10, 3.75, 10))) {
            Vec3 d = e.position().subtract(position());
            double back = -d.dot(f), across = Math.abs(d.dot(side));
            if (back < 1.875 || back > 9.375 || across > 2.5 || d.y < -1.875 || d.y > 4.375) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) push(e, f.scale(-1), 1.0, 0.7);
        }
        Vec3 club = position().subtract(f.scale(5));
        groundBurst(level, club, 1.6F, 30);
        playSound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 4, 0.6F);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.8F);
    }

    // ---------- 跃袭 ----------
    /** 跃袭落点：目标脚下能站下本体的地面；站不下就往自己这边退，退 10 格还不行就放弃。 */
    @Nullable private Vec3 landingFor(Vec3 want) {
        Vec3 back = position().subtract(want).multiply(1, 0, 1);
        if (back.lengthSqr() > 1.0E-4) back = back.normalize();
        for (int i = 0; i <= 10; i++) {
            Vec3 p = want.add(back.scale(i));
            double y = StarChaserEntity.groundY(level(), p.x, p.z, want.y);
            if (Double.isNaN(y)) y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
            Vec3 at = new Vec3(p.x, y, p.z);
            if (fitsAt(at)) return at;
        }
        return null;
    }

    private void tickLeap(ServerLevel level, @Nullable LivingEntity t) {
        Vec3 land = getAim();
        // 蓄力前段还会追着目标改落点，第 12 拍锁定
        if (stateTicks < LEAP_LOCK && t != null && position().subtract(center).horizontalDistance() <= radius + 22) {
            Vec3 again = landingFor(t.position());
            if (again != null) {
                land = again;
                setAim(land);
            }
        }
        if (stateTicks <= LEAP_OFF) turnToward(land, 8);
        if (stateTicks == LEAP_OFF) {
            leapFrom = position();
            noPhysics = true;
            setNoGravity(true);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3, 0.6F);
            groundBurst(level, position(), 2F, 24);
        }
        if (stateTicks > LEAP_OFF && stateTicks <= LEAP_LAND) {
            // 抛物线：水平匀速，弧顶高度随距离增加
            double s = (stateTicks - LEAP_OFF) / (double) (LEAP_LAND - LEAP_OFF);
            double peak = 5 + 0.15 * leapFrom.distanceTo(land);
            Vec3 p = leapFrom.lerp(land, s).add(0, peak * 4 * s * (1 - s), 0);
            setDeltaMovement(Vec3.ZERO);
            setPos(p);
        }
        if (stateTicks == LEAP_LAND) leapLand(level, land);
    }

    private void leapLand(ServerLevel level, Vec3 land) {
        setPos(land);
        noPhysics = false;
        setNoGravity(false);
        Vec3 f = forward();
        Vec3 side = new Vec3(-f.z, 0, f.x);
        float dmg = damage("leapDamage");
        int count = 0;
        // 落地按身体投影判定：沿朝向 9 格长、6 格宽
        for (LivingEntity e : targets(new AABB(land, land).inflate(6.25, 3.75, 6.25))) {
            Vec3 d = e.position().subtract(land);
            if (Math.abs(d.dot(f)) > 4.5 || Math.abs(d.dot(side)) > 3.0 || d.y < -1.875 || d.y > 3.75) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                push(e, d, 1.0, 0.5);
                count++;
            }
        }
        groundBurst(level, land, 3F, 60);
        level.sendParticles(ModParticles.SKY_MOTE.get(), land.x, land.y + 1, land.z, 40, 2.5, 0.6, 2.5, 0.08);
        playSound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 6, 0.5F);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 3, 0.6F);
        LOGGER.debug("Skyrender leap landed: entity={}, at={}, hits={}", getId(), land, count);
    }

    // ---------- 天幕坠片 ----------
    private void castShards(ServerLevel level, List<Player> fighters) {
        int count = 5;
        byte phase = getPhase();
        if (phase == 2) count = 7;
        if (phase >= 3) count = 10;
        float dmg = damage("shardDamage");
        List<Vec3> spots = new ArrayList<>();
        // 每个玩家脚下按走位预判一片，身边再落两片
        for (Player p : fighters) {
            Vec3 lead = p.position().add(p.getDeltaMovement().multiply(10, 0, 10));
            spots.add(lead);
            spots.add(ring(p.position(), 2.5, 5));
            spots.add(ring(p.position(), 2.5, 5));
        }
        while (spots.size() < count) spots.add(ring(center, 3, radius * 0.9));
        for (Vec3 s : spots) SkyShardEntity.spawn(level, this, ground(s), dmg, SHARD_WARN, 160, 1.5F);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 6, 0.4F);
        playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 3, 0.7F);
        // 第二阶段起：碎片落下就是在给掩体，多半紧接一次注视
        if (phase >= 2 && random.nextFloat() < 0.6F) gazeCd = Math.min(gazeCd, 30 + random.nextInt(30));
    }

    private void tickSkyfall(ServerLevel level, List<Player> fighters) {
        if (--skyfallCd > 0) return;
        skyfallCd = 140;
        // 第三阶段：天上那道长缝每 7 秒往每个玩家头上掉一片
        for (Player p : fighters) SkyShardEntity.spawn(level, this, ground(p.position()), damage("shardDamage"), SHARD_WARN, 100, 1.5F);
    }

    private Vec3 ring(Vec3 c, double r0, double r1) {
        double a = random.nextDouble() * Math.PI * 2, r = r0 + random.nextDouble() * (r1 - r0);
        return new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
    }

    private Vec3 ground(Vec3 p) {
        return new Vec3(p.x, level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z)), p.z);
    }

    // ---------- 注视 ----------
    private void startGaze() {
        byte phase = getPhase();
        if (phase >= 3) gazeCd = 420 + random.nextInt(140);
        else gazeCd = 500 + random.nextInt(200);
        eyeDamage = 0;
        startState(GAZE);
        playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 8, 0.5F);
    }

    private void tickGaze(ServerLevel level, List<Player> fighters) {
        int charge = gazeCharge();
        if (stateTicks < charge) {
            // 蓄力时慢慢转向最近的玩家，绕到它身后来得及
            LivingEntity t = getTarget();
            if (t != null) turnToward(t.position(), 2.5F);
            if (stateTicks % 3 == 0) {
                Vec3 eye = eyeCenter();
                level.sendParticles(ModParticles.SKY_MOTE.get(), eye.x, eye.y, eye.z, 4, 1.5, 1.0, 1.5, 0.02);
            }
            return;
        }
        if (stateTicks != charge) return;
        Vec3 eye = eyeCenter();
        float dmg = damage("gazeDamage");
        int count = 0;
        for (Player p : fighters) {
            if (!sees(p)) continue;
            if (p.hurt(damageSources().mobAttack(this), dmg)) count++;
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
        }
        level.sendParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
        playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 8, 0.6F);
        playSound(SoundEvents.BEACON_DEACTIVATE, 6, 0.5F);
        LOGGER.debug("Skyrender gaze released: entity={}, seen={}", getId(), count);
    }

    // ---------- 注视被打断 ----------
    /** 蓄力时眼睛被打中：眼前炸开，整只往后仰着滑出去，踉跄两步再站稳。朝向沿用蓄力时锁住的角度。 */
    private void startStagger() {
        startState(STAGGER);
        playSound(SoundEvents.RAVAGER_STUNNED, 8, 0.5F);
        playSound(SoundEvents.ENDER_DRAGON_HURT, 8, 0.55F);
        playSound(SoundEvents.GLASS_BREAK, 6, 0.5F);
        if (level() instanceof ServerLevel level) {
            Vec3 eye = eyeCenter();
            level.sendParticles(ModParticles.SKY_MOTE.get(), eye.x, eye.y, eye.z, 24, 0.8, 0.6, 0.8, 0.25);
        }
    }

    private void tickStagger(ServerLevel level) {
        int t = stateTicks;
        if (t < STAGGER_SLIDE) {
            Vec3 back = forward().scale(-1);
            double k = Math.pow(1 - (double) t / STAGGER_SLIDE, 1.5);
            double up = getDeltaMovement().y;
            if (t == 1) up = STAGGER_HOP;
            setDeltaMovement(back.x * STAGGER_SPEED * k, up, back.z * STAGGER_SPEED * k);
            hurtMarked = true;
            // 四爪在地上犁出的土
            if (t % 2 == 0 && onGround()) groundBurst(level, position().add(forward().scale(1.5)), 1.6F, 12);
        }
        if (t == 6) playSound(SoundEvents.MACE_SMASH_GROUND, 5, 0.6F);
        if (t == 12 || t == 22) playSound(SoundEvents.RAVAGER_STEP, 3, 0.6F);
        if (t == 32) playSound(SoundEvents.ENDER_DRAGON_GROWL, 5, 0.65F);
    }

    private boolean anyoneSees(List<Player> fighters) {
        for (Player p : fighters) if (sees(p)) return true;
        return false;
    }

    /** 注视判定：玩家在眼睛正前方 75° 内、48 格内，并且从眼睛连向头或脚有一条没被方块、插地碎片挡住。两端共用。 */
    public boolean sees(Player p) {
        Vec3 eye = eyeCenter();
        Vec3 d = p.position().subtract(position());
        if (d.horizontalDistance() > GAZE_RANGE || Math.abs(signedAngle(d)) > GAZE_CONE) return false;
        return clear(eye, p.getEyePosition(), p) || clear(eye, p.position().add(0, 0.15, 0), p);
    }

    private boolean clear(Vec3 from, Vec3 to, Entity viewer) {
        if (level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer)).getType() != HitResult.Type.MISS) return false;
        for (SkyShardEntity shard : level().getEntitiesOfClass(SkyShardEntity.class, new AABB(from, to).inflate(1))) {
            AABB box = shard.blocker();
            if (box != null && box.clip(from, to).isPresent()) return false;
        }
        return true;
    }

    // ---------- 裂天 ----------
    private void tickRend(ServerLevel level, @Nullable LivingEntity t) {
        if (stateTicks < REND_LOCK && t != null) turnToward(t.position(), 5);
        if (stateTicks == REND_LOCK) {
            Vec3 f = forward();
            rendFrom = position().add(f.scale(3));
            rendDir = f;
            setAim(rendFrom.add(f.scale(2.2 * (REND_COUNT + 1))));
        }
        if (stateTicks == REND_TEAR) {
            playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 6, 0.5F);
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 5, 0.4F);
        }
        int i = (stateTicks - REND_DROP) / 2;
        if (stateTicks >= REND_DROP && (stateTicks - REND_DROP) % 2 == 0 && i < REND_COUNT) {
            float dmg = damage("rendDamage");
            // 沿锁定方向一路砸下去；第三阶段左右各偏 18° 两条
            if (getPhase() >= 3) {
                for (int s = -1; s <= 1; s += 2) dropRend(level, rotateY(rendDir, s * 18), i, dmg);
            } else dropRend(level, rendDir, i, dmg);
        }
    }

    private void dropRend(ServerLevel level, Vec3 dir, int i, float dmg) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 p = rendFrom.add(dir.scale(2.2 * (i + 1))).add(side.scale((random.nextDouble() - 0.5) * 0.8));
        SkyShardEntity.spawn(level, this, ground(p), dmg, REND_WARN, 0, 1.3F);
    }

    private static Vec3 rotateY(Vec3 v, double degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, 0, v.x * s + v.z * c);
    }

    // ---------- 公用 ----------
    private List<LivingEntity> targets(AABB box) {
        return level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive()
            && !(e instanceof Player p && (p.isCreative() || p.isSpectator())) && !e.getTags().contains("skyrender_ritual"));
    }

    private static void push(LivingEntity e, Vec3 dir, double horizontal, double up) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(1, 0, 0);
        flat = flat.normalize();
        e.setDeltaMovement(e.getDeltaMovement().add(flat.x * horizontal, up, flat.z * horizontal));
        e.hurtMarked = true;
    }

    private void groundBurst(ServerLevel level, Vec3 at, float spread, int count) {
        BlockState state = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
        if (!state.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y + 0.1, at.z, count, spread, 0.1, spread, 0.2);
        level.sendParticles(ModParticles.STAR_DEBRIS.get(), at.x, at.y + 0.2, at.z, count / 3, spread * 0.6, 0.2, spread * 0.6, 0.25);
    }

    // ---------- 受伤 ----------
    private boolean invulnerableNow() {
        byte s = getState();
        if (s == METEOR) return stateTicks < METEOR_HIT;
        return s == RITUAL || s == DESCEND || s == ROAR || s == PHASE_UP;
    }

    @Override public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FIRE)
            || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FREEZE)
            || source.getEntity() == this || super.isInvulnerableTo(source);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        return hurtScaled(source, amount, false);
    }

    boolean hurtFromHead(DamageSource source, float amount) {
        return hurtScaled(source, amount, true);
    }

    private boolean hurtScaled(DamageSource source, float amount, boolean onHead) {
        if (level().isClientSide || isInvulnerableTo(source)) return false;
        // 指令、虚空这类无视无敌的伤害照常结算
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (invulnerableNow() || isDeadOrDying()) return false;
        Player attacker = source.getEntity() instanceof Player player ? player : null;
        boolean eyeVulnerable = onHead && getState() == GAZE && stateTicks < gazeCharge() && attacker != null;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) amount *= (float) stat("explosionMultiplier");
        // 注视后喘息：全身受伤加倍率，打头更多
        if (isExhausted()) {
            if (onHead) amount *= (float) stat("eyeMultiplier");
            else amount *= (float) stat("exhaustedMultiplier");
        }
        amount = Math.min(amount, getMaxHealth() * (float) stat("maxHitFraction"));
        // 每个阶段的门槛之下要先播完转换，一次打不穿
        float floor = getMaxHealth() * threshold(getPhase()) - 1;
        // 第二阶段没放过天陨时，半血线同样一次打不穿
        if (getPhase() == 2 && !meteorUsed) floor = Math.max(floor, getMaxHealth() * meteorTrigger() - 1);
        if (getPhase() < 3 && getHealth() - amount < floor) amount = Math.max(0, getHealth() - floor);
        if (amount <= 0) return false;
        float healthBefore = getHealth();
        boolean hurt = super.hurt(source, amount);
        if (hurt && eyeVulnerable) {
            eyeDamage += Math.max(0, healthBefore - getHealth());
            float requiredDamage = getMaxHealth() * 0.05F;
            if (eyeDamage >= requiredDamage) {
                startStagger();
                LOGGER.debug("Skyrender eye weak point broken: entity={}, player={}, damage={}/{}", getId(), attacker.getUUID(), eyeDamage, requiredDamage);
            }
        }
        return hurt;
    }

    @Override public boolean canBeAffected(MobEffectInstance effect) { return false; }
    @Override public void knockback(double strength, double x, double z) {}
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean canChangeDimensions(Level from, Level to) { return false; }
    @Override public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 256 * 256; }

    /** 仪式时本体悬在 34 格高的裂口里，剔除框要往下包住坑底的兆石；天上的长缝也在这个范围里。 */
    @Override public AABB getBoundingBoxForCulling() {
        if (getState() == RITUAL) return getBoundingBox().inflate(6, 4, 6).expandTowards(0, -RIFT_HEIGHT - 4, 0);
        return getBoundingBox().inflate(14, 6, 14).expandTowards(0, SKY_TEAR_HEIGHT + 6, 0);
    }

    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.RAVAGER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.RAVAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return null; }
    @Override public float getVoicePitch() { return 0.45F + random.nextFloat() * 0.1F; }
    @Override protected float getSoundVolume() { return 3; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.RAVAGER_STEP, 1.2F, 0.6F); }

    @Override public void die(DamageSource source) {
        super.die(source);
        // 跃在半空里被打死也要落回地面
        noPhysics = false;
        setNoGravity(false);
        if (level() instanceof ServerLevel level) {
            bossEvent.removeAllPlayers();
            clearCultists(level);
            LOGGER.debug("Skyrender died: killer={}", source.getEntity());
        }
    }

    @Override protected void tickDeath() {
        deathTime++;
        // 熄瞳：一边石化一边往上散天幕碎屑
        if (level() instanceof ServerLevel level) {
            if (deathTime % 3 == 0) {
                level.sendParticles(ModParticles.SKY_MOTE.get(), getX(), getY() + 2, getZ(), 10, 2.5, 1.5, 2.5, 0.03);
                level.sendParticles(ModParticles.STAR_ASH.get(), getX(), getY() + 1.5, getZ(), 8, 2.5, 1, 2.5, 0.02);
            }
            if (deathTime >= DEATH_LEN && !isRemoved()) {
                level.broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }

    // ---------- 存档 ----------
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Phase", getPhase());
        tag.putDouble("CX", center.x);
        tag.putDouble("CY", center.y);
        tag.putDouble("CZ", center.z);
        tag.putDouble("Radius", radius);
        tag.putDouble("FloorY", floorY);
        tag.putBoolean("ArenaReady", arenaReady);
        tag.putBoolean("PartyScaled", partyScaled);
        tag.putBoolean("MeteorUsed", meteorUsed);
        Vec3 anchor = getAnchor();
        tag.putDouble("LandX", anchor.x);
        tag.putDouble("LandY", anchor.y);
        tag.putDouble("LandZ", anchor.z);
        byte s = getState();
        tag.putBoolean("Ritual", s == RITUAL || s == DESCEND);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        center = new Vec3(tag.getDouble("CX"), tag.getDouble("CY"), tag.getDouble("CZ"));
        radius = Math.max(16, tag.getDouble("Radius"));
        floorY = tag.getDouble("FloorY");
        arenaReady = tag.getBoolean("ArenaReady");
        partyScaled = tag.getBoolean("PartyScaled");
        meteorUsed = tag.getBoolean("MeteorUsed");
        Vec3 land = new Vec3(tag.getDouble("LandX"), tag.getDouble("LandY"), tag.getDouble("LandZ"));
        entityData.set(ANCHOR, land.toVector3f());
        // 读档后从当前阶段的待机开始；仪式或坠落中途存的档直接落到落点
        entityData.set(PHASE, (byte) Mth.clamp(tag.getByte("Phase"), 1, 3));
        entityData.set(STATE, NONE);
        noPhysics = false;
        setNoGravity(false);
        if (tag.getBoolean("Ritual") && arenaReady) setPos(land);
        if (level() instanceof ServerLevel level) clearCultists(level);
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    // ---------- 动画 ----------
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 4, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            if (getState() == DESCEND || getState() == RITUAL) return state.setAndContinue(A_DESCEND);
            // 被打断滑退时不是在走路，底下不叠走路循环
            if (!state.isMoving() || getState() == STAGGER) return state.setAndContinue(IDLE);
            // 步频跟着实际移速走
            double speed = Math.sqrt(Mth.lengthSquared(getX() - xo, getZ() - zo)) * 20;
            state.getController().setAnimationSpeed(Mth.clamp(speed / 2.4, 0.6, 2.0));
            return state.setAndContinue(WALK);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> {
            RawAnimation anim = actionAnimation(getState());
            if (isDeadOrDying() || anim == null) {
                animAction = -1;
                return PlayState.STOP;
            }
            int action = entityData.get(ACTION);
            if (action != animAction) {
                animAction = action;
                state.getController().forceAnimationReset();
            }
            return state.setAndContinue(anim);
        }));
    }

    @Nullable private RawAnimation actionAnimation(byte s) {
        switch (s) {
            case ROAR: return A_ROAR;
            case PHASE_UP: return A_PHASE;
            case METEOR: return A_METEOR;
            case GORE:
                if (isCombo()) return A_GORE_COMBO;
                return A_GORE;
            case RAKE:
                if (getVariant() > 0) return A_RAKE_LEFT;
                return A_RAKE_RIGHT;
            case TAIL: return A_TAIL;
            case LEAP: return A_LEAP;
            case SHARDS: return A_SHARDS;
            case GAZE:
                if (clientAge >= gazeCharge()) return A_GAZE_REST;
                return A_GAZE;
            case REND: return A_REND;
            case STAGGER: return A_STAGGER;
            default: return null;
        }
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    /** 追击：只在没出招时走向目标，贴近后原地转身对准；出招期间由状态机接管。 */
    private final class ChaseGoal extends Goal {
        private int repath;

        ChaseGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() {
            LivingEntity t = getTarget();
            return getState() == NONE && t != null && t.isAlive();
        }

        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { getNavigation().stop(); }

        @Override public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 20, 20);
            double d = position().subtract(t.position()).horizontalDistance();
            if (d > 4.2) {
                if (--repath <= 0) {
                    getNavigation().moveTo(t, 1.0);
                    repath = 6 + random.nextInt(6);
                }
                return;
            }
            // 贴近了：停下转身
            getNavigation().stop();
            float want = (float) (Mth.atan2(t.getZ() - getZ(), t.getX() - getX()) * Mth.RAD_TO_DEG) - 90;
            float yaw = Mth.approachDegrees(yBodyRot, want, 7);
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
    }
}
