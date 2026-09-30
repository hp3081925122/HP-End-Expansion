package org.hp.hp_end_expansion.entity;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.ModEntities;
import org.hp.hp_end_expansion.registry.ModItems;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.joml.Vector3f;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class StarDevourerEntity extends Monster implements GeoEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 动画定义
    private static final RawAnimation IDLE_FLY = RawAnimation.begin().thenLoop("animation.star_devourer.idle_fly");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.star_devourer.glide");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.star_devourer.death");
    private static final RawAnimation DIVE = RawAnimation.begin().thenPlay("animation.star_devourer.dive");
    private static final RawAnimation BEAM = RawAnimation.begin().thenPlay("animation.star_devourer.beam");
    private static final RawAnimation BLACK_HOLE = RawAnimation.begin().thenPlay("animation.star_devourer.black_hole");
    private static final RawAnimation STAR_RAIN = RawAnimation.begin().thenPlay("animation.star_devourer.star_rain");
    private static final RawAnimation WING_SPIN = RawAnimation.begin().thenPlay("animation.star_devourer.wing_spin");
    private static final RawAnimation DEVOUR = RawAnimation.begin().thenPlay("animation.star_devourer.devour");
    private static final RawAnimation STAGGER = RawAnimation.begin().thenPlay("animation.star_devourer.stagger");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.star_devourer.roar");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.star_devourer.hurt");

    // 技能编号
    public static final int SKILL_NONE = 0;
    public static final int SKILL_SPIRAL = 1;
    public static final int SKILL_PRISM = 2;
    public static final int SKILL_HOLE = 3;
    public static final int SKILL_RAIN = 4;
    public static final int SKILL_SPIN = 5;
    public static final int SKILL_DEVOUR = 6;
    public static final int SKILL_STAGGER = 7;
    public static final int SKILL_ROAR = 8;

    // 核心编号：左棱镜、中黑洞、右星陨
    public static final int CORE_LEFT = 0;
    public static final int CORE_CENTER = 1;
    public static final int CORE_RIGHT = 2;
    private static final float CORE_MAX_HEALTH = 80.0F;
    // 模型中核心骨骼坐标（像素）与渲染缩放
    private static final double[] CORE_X = {6.0D, 0.0D, -6.0D};
    private static final double CORE_Y = 17.3D;
    private static final double CORE_Z = 1.0D;
    private static final double MODEL_SCALE = 2.5D / 16.0D;

    // 螺旋俯冲时序：每段前摇 12、全段 24，共 3 段，收招盘旋 30
    public static final int DIVE_WINDUP = 12;
    public static final int DIVE_CYCLE = 24;
    public static final int DIVE_END = DIVE_CYCLE * 3 + 30;
    // 棱镜射线时序
    public static final int BEAM_CHARGE = 20;
    public static final int BEAM_STOP = 80;
    public static final int BEAM_END_TICK = 92;
    public static final double BEAM_RANGE = 32.0D;
    public static final double SUB_BEAM_RANGE = 12.0D;
    public static final float SUB_BEAM_ANGLE = 25.0F;
    // 翼刃回旋时序
    public static final int SPIN_WINDUP = 16;
    public static final int SPIN_DASH_END = 26;
    public static final int SPIN_END = 40;
    // 吞星时序：上升、吸入、星爆
    public static final int DEVOUR_RISE = 30;
    public static final int DEVOUR_BURST = 130;
    public static final int DEVOUR_END = 150;

    // 场地
    private static final double ARENA_RADIUS = 32.0D;
    private static final int LEAVE_LIMIT = 1200;
    private static final ResourceLocation PARTY_HEALTH = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "devourer_party_health");

    // 同步字段：技能、技能 tick、阶段、射线终点、已碎核心位掩码
    private static final EntityDataAccessor<Integer> SKILL = SynchedEntityData.defineId(StarDevourerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKILL_TICK = SynchedEntityData.defineId(StarDevourerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(StarDevourerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> BEAM_POINT = SynchedEntityData.defineId(StarDevourerEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> BROKEN_CORES = SynchedEntityData.defineId(StarDevourerEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);

    // 技能冷却
    private int spiralCooldown = 60;
    private int prismCooldown = 120;
    private int holeCooldown = 200;
    private int rainCooldown = 160;
    private int spinCooldown = 100;
    private int devourCooldown;
    private int globalCooldown = 40;
    // 三核心生命与左核心过热
    private final float[] coreHealth = {CORE_MAX_HEALTH, CORE_MAX_HEALTH, CORE_MAX_HEALTH};
    private int leftOverheat;
    // 硬直：时长与受伤倍率
    private int staggerLength;
    private float staggerMultiplier = 1.0F;
    // 俯冲路径与已命中目标
    private Vec3 diveFrom;
    private Vec3 diveLow;
    private Vec3 diveTo;
    private Vec3 diveBaseDir;
    private final Set<Integer> hitOnce = new HashSet<>();
    // 翼刃路径
    private Vec3 spinStart;
    private Vec3 spinDir;
    // 吞星：已释放次数与场上星核
    private int devourCount;
    private int devourTotal;
    private final List<StarCoreEntity> starCores = new ArrayList<>();
    // 护卫：是否已召唤、全灭后的补充计时
    private boolean guardsSummoned;
    private int guardRespawnTicks;
    // 场地与脱战
    private Vec3 arenaCenter;
    private int emptyTicks;
    private boolean summonedByBeacon;
    private boolean partyScaled;
    private int pendingPhase;

    public StarDevourerEntity(EntityType<? extends StarDevourerEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 400;
        this.setNoGravity(true);
        this.moveControl = new DevourerMoveControl(this);
    }

    // Boss 属性
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 520.0D)
            .add(Attributes.ATTACK_DAMAGE, 16.0D)
            .add(Attributes.ARMOR, 10.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.55D)
            .add(Attributes.FLYING_SPEED, 0.55D)
            .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKILL, SKILL_NONE);
        builder.define(SKILL_TICK, 0);
        builder.define(PHASE, 1);
        builder.define(BEAM_POINT, new Vector3f());
        builder.define(BROKEN_CORES, 0);
    }

    @Override
    protected void registerGoals() {
        // 行为：战斗盘旋
        this.goalSelector.addGoal(1, new DevourerCombatGoal(this));
        // 索敌：受击反击，主动攻击 48 格内玩家
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, VoidRayEntity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
            player -> player.distanceToSqr(this) < 48.0D * 48.0D));
    }

    public int getSkill() {
        return this.entityData.get(SKILL);
    }

    public int getSkillTick() {
        return this.entityData.get(SKILL_TICK);
    }

    public int getPhase() {
        return this.entityData.get(PHASE);
    }

    public boolean isCasting() {
        return this.getSkill() != SKILL_NONE;
    }

    public boolean isCoreBroken(int core) {
        return (this.entityData.get(BROKEN_CORES) & (1 << core)) != 0;
    }

    public Vec3 getBeamPoint() {
        Vector3f v = this.entityData.get(BEAM_POINT);
        return new Vec3(v.x, v.y, v.z);
    }

    public void setSummonedByBeacon(boolean value) {
        this.summonedByBeacon = value;
    }

    // 棱镜射线是否处于引导阶段
    public boolean isBeamFiring() {
        int t = this.getSkillTick();
        return this.getSkill() == SKILL_PRISM && t >= BEAM_CHARGE && t < BEAM_STOP;
    }

    // 核心相对实体脚底的偏移，按身体朝向旋转
    public Vec3 coreOffset(int core) {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
        Vec3 left = new Vec3(Mth.cos(yaw), 0.0D, Mth.sin(yaw));
        return left.scale(CORE_X[core] * MODEL_SCALE).add(0.0D, CORE_Y * MODEL_SCALE, 0.0D).add(forward.scale(-CORE_Z * MODEL_SCALE));
    }

    public Vec3 corePos(int core) {
        return this.position().add(this.coreOffset(core));
    }

    // 棱镜子射线终点：以主射线落点为起点，水平旋转 -25°、0°、+25°
    public Vec3[] subBeamEnds(Vec3 origin, Vec3 point) {
        Vec3 dir = point.subtract(origin).normalize();
        Vec3[] ends = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            Vec3 d = dir.yRot((i - 1) * SUB_BEAM_ANGLE * Mth.DEG_TO_RAD);
            Vec3 start = point.subtract(dir.scale(0.3D));
            Vec3 far = start.add(d.scale(SUB_BEAM_RANGE));
            BlockHitResult block = this.level().clip(new ClipContext(start, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (block.getType() != HitResult.Type.MISS) {
                far = block.getLocation();
            }
            ends[i] = far;
        }
        return ends;
    }

    private void startSkill(int skill, String anim) {
        this.entityData.set(SKILL, skill);
        this.entityData.set(SKILL_TICK, 0);
        this.getNavigation().stop();
        this.hitOnce.clear();
        this.triggerAnim("skill", anim);
        LOGGER.debug("Star Devourer {} starts skill {} ({})", this.getId(), skill, anim);
    }

    private void endSkill() {
        this.entityData.set(SKILL, SKILL_NONE);
        this.entityData.set(SKILL_TICK, 0);
        this.globalCooldown = this.cd(24);
    }

    // 第三阶段冷却乘 0.7
    private int cd(int ticks) {
        if (this.getPhase() >= 3) {
            return Math.round(ticks * 0.7F);
        }
        return ticks;
    }

    // 按阶段的巡航高度
    double cruiseHeight() {
        if (this.getPhase() >= 3) {
            return 5.0D;
        }
        return 14.0D;
    }

    // 地面高度：向下最多探测 32 格，找不到返回 null
    Vec3 groundBelow(Vec3 from) {
        Vec3 to = from.subtract(0.0D, 32.0D, 0.0D);
        BlockHitResult hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        return hit.getLocation();
    }

    void faceTarget(Vec3 point, float maxTurn) {
        double dx = point.x - this.getX();
        double dz = point.z - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float turned = Mth.approachDegrees(this.getYRot(), yaw, maxTurn);
        this.setYRot(turned);
        this.yBodyRot = turned;
        this.yHeadRot = turned;
    }

    // Boss 离地高度
    private double heightAboveGround() {
        Vec3 ground = this.groundBelow(this.position().add(0.0D, 0.5D, 0.0D));
        if (ground == null) {
            return 32.0D;
        }
        return this.getY() - ground.y;
    }

    // 场内非旁观玩家
    private List<Player> playersInArena(ServerLevel level) {
        Vec3 c = this.arenaCenter;
        if (c == null) {
            c = this.position();
        }
        AABB area = new AABB(c, c).inflate(ARENA_RADIUS, 40.0D, ARENA_RADIUS);
        List<Player> result = new ArrayList<>();
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            if (player.isAlive() && !player.isSpectator() && player.position().distanceToSqr(c) <= ARENA_RADIUS * ARENA_RADIUS) {
                result.add(player);
            }
        }
        return result;
    }

    // 场上存活的护卫
    private List<VoidRayEntity> aliveGuards(ServerLevel level) {
        List<VoidRayEntity> result = new ArrayList<>();
        for (VoidRayEntity ray : level.getEntitiesOfClass(VoidRayEntity.class, this.getBoundingBox().inflate(ARENA_RADIUS + 16.0D))) {
            if (ray.isMinion() && ray.isAlive()) {
                result.add(ray);
            }
        }
        return result;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            // 客户端：未碎核心闪烁星光，射线引导时左核心火花
            if (this.random.nextInt(3) == 0) {
                int core = this.random.nextInt(3);
                if (!this.isCoreBroken(core)) {
                    Vec3 p = this.corePos(core);
                    this.level().addParticle(ParticleTypes.END_ROD, p.x, p.y - 0.3D, p.z, 0.0D, -0.03D, 0.0D);
                }
            }
            if (this.getSkill() == SKILL_DEVOUR) {
                // 吞星：周围粒子被吸向头部
                for (int i = 0; i < 3; i++) {
                    double a = this.random.nextDouble() * Math.PI * 2.0D;
                    Vec3 head = this.position().add(0.0D, 2.5D, 0.0D);
                    Vec3 from = head.add(Math.cos(a) * 12.0D, (this.random.nextDouble() - 0.5D) * 8.0D, Math.sin(a) * 12.0D);
                    Vec3 v = head.subtract(from).scale(0.08D);
                    this.level().addParticle(ParticleTypes.PORTAL, from.x, from.y, from.z, v.x, v.y, v.z);
                }
            }
            return;
        }
        // 服务端：冷却计时
        if (this.spiralCooldown > 0) {
            this.spiralCooldown--;
        }
        if (this.prismCooldown > 0) {
            this.prismCooldown--;
        }
        if (this.holeCooldown > 0) {
            this.holeCooldown--;
        }
        if (this.rainCooldown > 0) {
            this.rainCooldown--;
        }
        if (this.spinCooldown > 0) {
            this.spinCooldown--;
        }
        if (this.devourCooldown > 0) {
            this.devourCooldown--;
        }
        if (this.globalCooldown > 0) {
            this.globalCooldown--;
        }
        if (this.leftOverheat > 0) {
            this.leftOverheat--;
        }
        ServerLevel serverLevel = (ServerLevel) this.level();
        // 初始化场地中心与多人血量
        if (this.arenaCenter == null) {
            this.arenaCenter = this.position();
        }
        if (!this.partyScaled) {
            this.partyScaled = true;
            int players = Math.min(4, this.playersInArena(serverLevel).size());
            if (players > 1) {
                this.getAttribute(Attributes.MAX_HEALTH).addOrReplacePermanentModifier(
                    new AttributeModifier(PARTY_HEALTH, 0.5D * (players - 1), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                this.setHealth(this.getMaxHealth());
                LOGGER.debug("Star Devourer {} scaled for {} players, max health {}", this.getId(), players, this.getMaxHealth());
            }
        }
        // 场地约束：水平离开半径 32 时拉回中心上空
        double hx = this.getX() - this.arenaCenter.x;
        double hz = this.getZ() - this.arenaCenter.z;
        if (this.isAlive() && hx * hx + hz * hz > ARENA_RADIUS * ARENA_RADIUS) {
            LOGGER.debug("Star Devourer {} left arena at {}, pulled back", this.getId(), this.position());
            this.teleportTo(this.arenaCenter.x, this.arenaCenter.y + this.cruiseHeight(), this.arenaCenter.z);
        }
        // 脱战：场内 60 秒无玩家则回满血并消失
        if (this.tickCount % 20 == 0) {
            if (this.playersInArena(serverLevel).isEmpty()) {
                this.emptyTicks += 20;
            } else {
                this.emptyTicks = 0;
            }
            if (this.emptyTicks >= LEAVE_LIMIT && this.isAlive()) {
                LOGGER.debug("Star Devourer {} despawns, no players for 60s", this.getId());
                this.setHealth(this.getMaxHealth());
                if (this.summonedByBeacon) {
                    ItemEntity beacon = new ItemEntity(serverLevel, this.arenaCenter.x, this.arenaCenter.y + 0.5D, this.arenaCenter.z, new ItemStack(ModItems.STAR_BEACON.get()));
                    serverLevel.addFreshEntity(beacon);
                }
                this.clearSummons(serverLevel);
                serverLevel.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 2.0D, this.getZ(), 60, 3.0D, 1.5D, 3.0D, 0.1D);
                this.discard();
                return;
            }
        }
        // 阶段判定：生命跨过 55% 与 20%，或三核心全碎
        float ratio = this.getHealth() / this.getMaxHealth();
        int phase = this.getPhase();
        if (this.pendingPhase == 0 && this.isAlive()) {
            if (phase < 3 && (ratio <= 0.2F || this.entityData.get(BROKEN_CORES) == 7)) {
                this.pendingPhase = 3;
            } else if (phase == 1 && ratio <= 0.55F) {
                this.pendingPhase = 2;
            }
        }
        if (this.pendingPhase != 0 && !this.isCasting() && this.isAlive()) {
            this.entityData.set(PHASE, this.pendingPhase);
            LOGGER.debug("Star Devourer {} enters phase {}", this.getId(), this.pendingPhase);
            if (this.pendingPhase == 3) {
                // 三阶段 3 秒后首次吞星
                this.devourCooldown = 60;
            }
            this.pendingPhase = 0;
            this.startSkill(SKILL_ROAR, "roar");
        }
        if (this.getPhase() >= 2 && this.isAlive()) {
            this.tickGuards(serverLevel);
            // 二阶段起：场地上空持续落下小陨石
            if (this.tickCount % 30 == 0) {
                double a = this.random.nextDouble() * Math.PI * 2.0D;
                double r = this.random.nextDouble() * 24.0D;
                Vec3 ground = this.groundBelow(this.arenaCenter.add(Math.cos(a) * r, 16.0D, Math.sin(a) * r));
                if (ground != null) {
                    VoidRayVfxEntity.spawn(serverLevel, VoidRayVfxEntity.KIND_STAR, ground.add(0.0D, 0.05D, 0.0D), 0.0F, 2.0F, 0, VoidRayVfxEntity.STAR_LIFE, this);
                }
            }
        }
        this.starCores.removeIf(core -> !core.isAlive());
    }

    // 护卫：二阶段首次召唤 4 只，全灭 40 秒后补 2 只
    private void tickGuards(ServerLevel serverLevel) {
        if (!this.guardsSummoned) {
            this.guardsSummoned = true;
            this.spawnGuards(serverLevel, 4);
            return;
        }
        if (this.tickCount % 20 != 0) {
            return;
        }
        if (this.aliveGuards(serverLevel).isEmpty()) {
            this.guardRespawnTicks += 20;
            if (this.guardRespawnTicks >= 800) {
                this.guardRespawnTicks = 0;
                this.spawnGuards(serverLevel, 2);
            }
        } else {
            this.guardRespawnTicks = 0;
        }
    }

    private void spawnGuards(ServerLevel serverLevel, int count) {
        for (int i = 0; i < count; i++) {
            VoidRayEntity ray = ModEntities.VOID_RAY.get().create(serverLevel);
            if (ray == null) {
                continue;
            }
            double a = Math.PI * 2.0D * i / count;
            ray.moveTo(this.getX() + Math.cos(a) * 6.0D, this.getY() + 2.0D, this.getZ() + Math.sin(a) * 6.0D, this.getYRot(), 0.0F);
            ray.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(ray.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            ray.makeMinion(40.0F);
            ray.setTarget(this.getTarget());
            serverLevel.addFreshEntity(ray);
            serverLevel.sendParticles(ParticleTypes.PORTAL, ray.getX(), ray.getY() + 0.5D, ray.getZ(), 20, 0.8D, 0.4D, 0.8D, 0.4D);
        }
        LOGGER.debug("Star Devourer {} summoned {} guards", this.getId(), count);
    }

    // 清理护卫与星核
    private void clearSummons(ServerLevel serverLevel) {
        for (VoidRayEntity ray : this.aliveGuards(serverLevel)) {
            ray.discard();
        }
        for (StarCoreEntity core : this.starCores) {
            core.discard();
        }
        this.starCores.clear();
    }

    // 技能推进：由战斗 Goal 每 tick 调用
    void tickSkill(LivingEntity target) {
        int skill = this.getSkill();
        int t = this.getSkillTick() + 1;
        this.entityData.set(SKILL_TICK, t);
        if (skill == SKILL_SPIRAL) {
            this.tickSpiral(target, t);
        } else if (skill == SKILL_PRISM) {
            this.tickPrism(target, t);
        } else if (skill == SKILL_HOLE) {
            this.tickHole(target, t);
        } else if (skill == SKILL_RAIN) {
            this.tickRain(target, t);
        } else if (skill == SKILL_SPIN) {
            this.tickSpin(target, t);
        } else if (skill == SKILL_DEVOUR) {
            this.tickDevour(target, t);
        } else if (skill == SKILL_STAGGER) {
            // 硬直：坠落到离地 4 格后悬停
            if (this.heightAboveGround() > 4.0D) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.5D, 0.0D, 0.5D).add(0.0D, -0.35D, 0.0D));
            } else {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
            }
            if (t >= this.staggerLength) {
                this.staggerMultiplier = 1.0F;
                this.endSkill();
            }
        } else if (skill == SKILL_ROAR) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.6D));
            if (t == 6) {
                this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.8F);
            }
            if (t >= 40) {
                this.endSkill();
            }
        }
    }

    // 挑选技能：返回是否已开始释放
    boolean chooseSkill(LivingEntity target) {
        if (this.globalCooldown > 0) {
            return false;
        }
        double hDist = Math.sqrt(this.distanceToSqr(target.getX(), this.getY(), target.getZ()));
        boolean sight = this.hasLineOfSight(target);
        int phase = this.getPhase();
        if (phase >= 3 && this.devourCooldown <= 0) {
            this.startSkill(SKILL_DEVOUR, "devour");
            return true;
        }
        if (phase >= 2 && this.spinCooldown <= 0 && hDist < 20.0D) {
            this.startSkill(SKILL_SPIN, "wing_spin");
            return true;
        }
        if (!this.isCoreBroken(CORE_LEFT) && this.prismCooldown <= 0 && sight && this.distanceTo(target) < BEAM_RANGE - 4.0D) {
            this.startSkill(SKILL_PRISM, "beam");
            return true;
        }
        if (!this.isCoreBroken(CORE_CENTER) && this.holeCooldown <= 0 && this.groundBelow(target.position().add(0.0D, 3.5D, 0.0D)) != null) {
            Vec3 ground = this.groundBelow(target.position().add(0.0D, 3.5D, 0.0D));
            if (target.position().y + 3.5D - ground.y <= 10.0D) {
                this.startSkill(SKILL_HOLE, "black_hole");
                return true;
            }
        }
        if (!this.isCoreBroken(CORE_RIGHT) && this.rainCooldown <= 0) {
            this.startSkill(SKILL_RAIN, "star_rain");
            return true;
        }
        if (this.spiralCooldown <= 0 && sight && hDist < 28.0D) {
            this.startSkill(SKILL_SPIRAL, "dive");
            return true;
        }
        return false;
    }

    // 螺旋俯冲：3 段，每段前摇 12 tick 预警线，路径依次旋转 60°
    private void tickSpiral(LivingEntity target, int t) {
        int seg = (t - 1) / DIVE_CYCLE;
        int local = (t - 1) % DIVE_CYCLE + 1;
        if (seg < 3) {
            if (local == 1) {
                Vec3 ground = this.groundBelow(target.position().add(0.0D, 0.5D, 0.0D));
                if (ground == null) {
                    ground = target.position();
                }
                if (seg == 0) {
                    Vec3 dir = target.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
                    if (dir.lengthSqr() < 1.0E-3D) {
                        dir = this.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
                    }
                    this.diveBaseDir = dir.normalize();
                }
                Vec3 dir = this.diveBaseDir.yRot(seg * 60.0F * Mth.DEG_TO_RAD);
                float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
                VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_WARN_LINE, ground.subtract(dir.scale(5.0D)), yaw, 14.0F, 0, DIVE_WINDUP + 2, this);
                this.diveLow = ground.add(0.0D, 0.8D, 0.0D);
                this.diveTo = ground.add(dir.scale(10.0D)).add(0.0D, 6.0D, 0.0D);
                this.hitOnce.clear();
                this.playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, 0.5F);
            }
            if (local < DIVE_WINDUP) {
                // 前摇：拉升到俯冲起点
                if (local == 1) {
                    this.diveFrom = this.diveLow.subtract(this.diveTo.subtract(this.diveLow).multiply(1.0D, 0.0D, 1.0D)).add(0.0D, 8.0D, 0.0D);
                }
                this.setDeltaMovement(this.diveFrom.subtract(this.position()).scale(0.2D));
                this.faceTarget(this.diveLow, 12.0F);
            } else {
                // 俯冲：沿二次贝塞尔曲线从起点经低点到终点
                float p = (float) (local - DIVE_WINDUP) / (DIVE_CYCLE - DIVE_WINDUP);
                float q = 1.0F - p;
                Vec3 wanted = this.diveFrom.scale(q * q).add(this.diveLow.scale(2.0F * q * p)).add(this.diveTo.scale(p * p));
                this.setDeltaMovement(wanted.subtract(this.position()));
                this.faceTarget(this.diveTo, 20.0F);
                this.diveHitCheck();
                // 最后一段落到低点时释放冲击环
                if (seg == 2 && local == DIVE_WINDUP + (DIVE_CYCLE - DIVE_WINDUP) / 2) {
                    VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_SHOCK, this.diveLow.subtract(0.0D, 0.8D, 0.0D), 0.0F, 6.0F, 0, 12, this);
                    this.hitCircle(this.diveLow, 6.0D, 10.0F, 0.5D);
                    this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.7F);
                }
            }
        } else {
            // 收招：减速回升
            this.setDeltaMovement(this.getDeltaMovement().scale(0.8D).add(0.0D, 0.08D, 0.0D));
            if (t >= DIVE_END) {
                this.spiralCooldown = this.cd(160);
                this.endSkill();
            }
        }
    }

    // 俯冲命中：每段每个目标只结算一次
    private void diveHitCheck() {
        AABB area = this.getBoundingBox().inflate(1.5D, 1.0D, 1.5D);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (this.isAlly(victim) || this.hitOnce.contains(victim.getId())) {
                continue;
            }
            this.hitOnce.add(victim.getId());
            if (victim.hurt(this.damageSources().mobAttack(this), 18.0F)) {
                victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.2D, 0.0D, 0.2D).add(0.0D, 0.7D, 0.0D));
                victim.hurtMarked = true;
                LOGGER.debug("Star Devourer {} dive hit {}", this.getId(), victim.getName().getString());
            }
        }
    }

    // 圆形范围伤害
    private void hitCircle(Vec3 center, double radius, float damage, double lift) {
        AABB area = new AABB(center, center).inflate(radius, 3.0D, radius);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (isAlly(victim) || victim.position().subtract(center).horizontalDistanceSqr() > radius * radius) {
                continue;
            }
            if (victim.hurt(this.damageSources().mobAttack(this), damage) && lift > 0.0D) {
                victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, lift, 0.0D));
                victim.hurtMarked = true;
            }
        }
    }

    private boolean isAlly(LivingEntity victim) {
        return victim == this || victim instanceof VoidRayEntity || victim instanceof StarCoreEntity || !victim.isAlive();
    }

    // 棱镜射线：左核心发射，每 tick 限速转向 3°，落点分裂 3 条子射线
    private void tickPrism(LivingEntity target, int t) {
        this.faceTarget(target.position(), 5.0F);
        this.setDeltaMovement(this.getDeltaMovement().scale(0.7D));
        Vec3 core = this.corePos(CORE_LEFT);
        if (t == 1) {
            Vec3 start = target.position().add(target.getDeltaMovement().scale(-6.0D));
            this.entityData.set(BEAM_POINT, start.toVector3f());
            this.playSound(SoundEvents.BEACON_ACTIVATE, 2.5F, 1.2F);
        }
        if (t == BEAM_CHARGE) {
            this.playSound(SoundEvents.GUARDIAN_ATTACK, 2.5F, 0.5F);
        }
        if (t >= BEAM_CHARGE && t < BEAM_STOP) {
            Vec3 current = this.getBeamPoint().subtract(core).normalize();
            Vec3 wanted = target.getBoundingBox().getCenter().subtract(core).normalize();
            double angle = Math.acos(Mth.clamp(current.dot(wanted), -1.0D, 1.0D));
            double maxStep = 3.0D * Mth.DEG_TO_RAD;
            Vec3 dir = wanted;
            if (angle > maxStep) {
                double k = maxStep / angle;
                dir = current.scale(1.0D - k).add(wanted.scale(k)).normalize();
            }
            Vec3 end = this.traceBeam(core, core.add(dir.scale(BEAM_RANGE)), 7.0F, (t - BEAM_CHARGE) % 10 == 0);
            this.entityData.set(BEAM_POINT, end.toVector3f());
            if ((t - BEAM_CHARGE) % 10 == 0) {
                for (Vec3 subEnd : this.subBeamEnds(core, end)) {
                    this.traceBeam(end, subEnd, 4.0F, true);
                }
            }
            if (t % 3 == 0 && this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 2, 0.15D, 0.15D, 0.15D, 0.05D);
            }
        }
        if (t == BEAM_STOP) {
            this.playSound(SoundEvents.BEACON_DEACTIVATE, 2.0F, 1.2F);
            // 收招：左核心过热 2 秒
            this.leftOverheat = 40;
        }
        if (t >= BEAM_END_TICK) {
            this.prismCooldown = this.cd(280);
            this.endSkill();
        }
    }

    // 射线判定：返回终点，可选结算伤害（举盾减半）
    private Vec3 traceBeam(Vec3 from, Vec3 far, float damage, boolean deal) {
        BlockHitResult block = this.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = far;
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(this.level(), this, from, end,
            new AABB(from, end).inflate(1.0D), e -> e instanceof LivingEntity living && !this.isAlly(living), 0.3F);
        if (entityHit != null) {
            end = entityHit.getLocation();
            if (deal && entityHit.getEntity() instanceof LivingEntity victim) {
                float dealt = damage;
                if (victim.isBlocking()) {
                    dealt = damage * 0.5F;
                }
                victim.hurt(this.damageSources().indirectMagic(this, this), dealt);
            }
        }
        return end;
    }

    // 黑洞：中核心在目标上方 3 格凝聚黑洞
    private void tickHole(LivingEntity target, int t) {
        this.faceTarget(target.position(), 6.0F);
        this.setDeltaMovement(this.getDeltaMovement().scale(0.7D));
        if (t == 12) {
            Vec3 pos = target.position().add(0.0D, 3.0D, 0.0D);
            VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_BLACK_HOLE, pos, 0.0F, 10.0F, 0, VoidRayVfxEntity.HOLE_LIFE, this);
            this.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 0.5F);
        }
        if (t >= 30) {
            this.holeCooldown = this.cd(360);
            this.endSkill();
        }
    }

    // 陨星雨：右核心充能 20 tick 后召唤 4 波大陨石，每波 1 颗对准目标
    private void tickRain(LivingEntity target, int t) {
        this.faceTarget(target.position(), 6.0F);
        this.setDeltaMovement(this.getDeltaMovement().scale(0.7D));
        if (t == 1) {
            this.playSound(SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.6F);
        }
        if (t >= 20 && t < 20 + 4 * 15 && (t - 20) % 15 == 0) {
            for (int i = 0; i < 4; i++) {
                Vec3 center = target.position();
                if (i > 0) {
                    double angle = this.random.nextDouble() * Math.PI * 2.0D;
                    double dist = 4.0D + this.random.nextDouble() * 8.0D;
                    center = center.add(Math.cos(angle) * dist, 0.0D, Math.sin(angle) * dist);
                }
                Vec3 ground = this.groundBelow(center.add(0.0D, 6.0D, 0.0D));
                if (ground == null) {
                    ground = center;
                }
                VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_BIG_STAR, ground, 0.0F, 3.0F, i * 3, VoidRayVfxEntity.STAR_LIFE, this);
            }
            this.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 3.0F, 0.5F);
        }
        if (t >= 20 + 4 * 15 + 10) {
            this.rainCooldown = this.cd(320);
            this.endSkill();
        }
    }

    // 翼刃回旋：宽预警后直线冲刺 20 格，两侧 3 格切割
    private void tickSpin(LivingEntity target, int t) {
        if (t == 1) {
            Vec3 dir = target.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
            if (dir.lengthSqr() < 1.0E-3D) {
                dir = this.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
            }
            this.spinDir = dir.normalize();
            Vec3 ground = this.groundBelow(this.position().add(0.0D, 0.5D, 0.0D));
            Vec3 start = this.position();
            if (ground != null && this.getY() - ground.y < 8.0D) {
                start = new Vec3(this.getX(), ground.y + 1.5D, this.getZ());
            } else {
                start = new Vec3(this.getX(), target.getY() + 1.5D, this.getZ());
            }
            this.spinStart = start;
            float yaw = (float) (Mth.atan2(this.spinDir.z, this.spinDir.x) * Mth.RAD_TO_DEG) - 90.0F;
            VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_WARN_WIDE, new Vec3(start.x, start.y - 1.5D, start.z), yaw, 20.0F, 0, SPIN_WINDUP + 2, this);
            this.playSound(SoundEvents.TRIDENT_RIPTIDE_3.value(), 2.5F, 0.6F);
        }
        if (this.spinDir == null || this.spinStart == null) {
            this.endSkill();
            return;
        }
        this.faceTarget(this.position().add(this.spinDir), 20.0F);
        if (t < SPIN_WINDUP) {
            // 前摇：移动到冲刺起点
            Vec3 delta = this.spinStart.subtract(this.position());
            this.setDeltaMovement(delta.scale(0.25D));
        } else if (t < SPIN_DASH_END) {
            // 冲刺：每 tick 2 格
            this.setDeltaMovement(this.spinDir.scale(2.0D));
            AABB area = this.getBoundingBox().inflate(3.0D, 1.5D, 3.0D);
            for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
                if (this.isAlly(victim) || this.hitOnce.contains(victim.getId())) {
                    continue;
                }
                this.hitOnce.add(victim.getId());
                if (victim.hurt(this.damageSources().mobAttack(this), 14.0F)) {
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                }
            }
        } else {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.6D));
        }
        if (t >= SPIN_END) {
            this.spinCooldown = this.cd(200);
            this.endSkill();
        }
    }

    // 吞星：升至场地中心上空，释放星核，时限内未击碎则星爆
    private void tickDevour(LivingEntity target, int t) {
        Vec3 center = this.arenaCenter;
        if (center == null) {
            center = target.position();
        }
        Vec3 top = center.add(0.0D, 20.0D, 0.0D);
        if (t <= DEVOUR_RISE) {
            this.setDeltaMovement(top.subtract(this.position()).scale(0.12D));
        } else {
            this.setDeltaMovement(top.subtract(this.position()).scale(0.05D));
        }
        if (t == DEVOUR_RISE && this.level() instanceof ServerLevel serverLevel) {
            int count = 5;
            if (this.devourCount > 0) {
                count = 4;
            }
            this.starCores.clear();
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0D * i / count;
                StarCoreEntity core = ModEntities.STAR_CORE.get().create(serverLevel);
                if (core == null) {
                    continue;
                }
                core.moveTo(center.x + Math.cos(angle) * 12.0D, center.y + 4.0D, center.z + Math.sin(angle) * 12.0D, 0.0F, 0.0F);
                serverLevel.addFreshEntity(core);
                this.starCores.add(core);
            }
            this.devourTotal = this.starCores.size();
            this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 1.3F);
            LOGGER.debug("Star Devourer {} devour spawned {} cores", this.getId(), this.devourTotal);
        }
        if (t > DEVOUR_RISE && t < DEVOUR_BURST) {
            this.starCores.removeIf(core -> !core.isAlive());
            // 星核全碎：硬直 100 tick，受伤翻倍
            if (this.starCores.isEmpty() && this.devourTotal > 0) {
                LOGGER.debug("Star Devourer {} devour interrupted", this.getId());
                this.finishDevour();
                this.startStagger(100, 2.0F);
                return;
            }
        }
        if (t == DEVOUR_BURST && this.level() instanceof ServerLevel serverLevel) {
            this.starCores.removeIf(core -> !core.isAlive());
            float damage = 0.0F;
            if (this.devourTotal > 0) {
                damage = 40.0F * this.starCores.size() / this.devourTotal;
            }
            for (Player player : this.playersInArena(serverLevel)) {
                player.hurt(this.damageSources().indirectMagic(this, this), damage);
            }
            serverLevel.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 2.0D, this.getZ(), 200, 6.0D, 4.0D, 6.0D, 0.6D);
            this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.5F);
            LOGGER.debug("Star Devourer {} star burst damage {}", this.getId(), damage);
            for (StarCoreEntity core : this.starCores) {
                core.discard();
            }
            this.starCores.clear();
        }
        if (t >= DEVOUR_END) {
            this.finishDevour();
            this.endSkill();
        }
    }

    // 吞星收尾：计数与冷却
    private void finishDevour() {
        this.devourCount++;
        this.devourTotal = 0;
        this.devourCooldown = this.cd(700);
    }

    // 进入硬直
    private void startStagger(int length, float multiplier) {
        this.staggerLength = length;
        this.staggerMultiplier = multiplier;
        this.startSkill(SKILL_STAGGER, "stagger");
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide()) {
            return super.hurt(source, amount);
        }
        Entity direct = source.getDirectEntity();
        boolean projectile = direct != null && direct != source.getEntity();
        float dealt = amount;
        // 护卫存活时弹射物伤害减半
        if (projectile && this.level() instanceof ServerLevel serverLevel && !this.aliveGuards(serverLevel).isEmpty()) {
            dealt = dealt * 0.5F;
        }
        // 核心判定：弹射物，或离地较低时的近战
        int core = -1;
        if (direct != null && (projectile || this.heightAboveGround() < 6.0D)) {
            Vec3 point = direct.position();
            if (!projectile) {
                point = direct.getEyePosition().add(direct.getLookAngle().scale(direct.distanceTo(this)));
            }
            double best = 2.5D * 2.5D;
            for (int i = 0; i < 3; i++) {
                if (this.isCoreBroken(i)) {
                    continue;
                }
                double d = this.corePos(i).distanceToSqr(point);
                if (d < best) {
                    best = d;
                    core = i;
                }
            }
        }
        if (core == CORE_LEFT && this.leftOverheat > 0) {
            dealt = dealt * 2.0F;
        }
        dealt = dealt * this.staggerMultiplier;
        boolean result = super.hurt(source, dealt);
        if (result && core >= 0 && this.isAlive()) {
            this.coreHealth[core] -= dealt;
            LOGGER.debug("Star Devourer {} core {} hit for {}, left {}", this.getId(), core, dealt, this.coreHealth[core]);
            if (this.coreHealth[core] <= 0.0F) {
                this.breakCore(core);
            }
        }
        if (result && !this.isCasting() && this.isAlive()) {
            this.triggerAnim("skill", "hurt");
        }
        return result;
    }

    // 击碎核心：同步位掩码并硬直 60 tick
    private void breakCore(int core) {
        this.coreHealth[core] = 0.0F;
        this.entityData.set(BROKEN_CORES, this.entityData.get(BROKEN_CORES) | (1 << core));
        Vec3 p = this.corePos(core);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 60, 0.6D, 0.6D, 0.6D, 0.3D);
        }
        this.playSound(SoundEvents.GLASS_BREAK, 3.0F, 0.5F);
        LOGGER.debug("Star Devourer {} core {} broken", this.getId(), core);
        if (this.getSkill() == SKILL_DEVOUR) {
            for (StarCoreEntity star : this.starCores) {
                star.discard();
            }
            this.starCores.clear();
            this.finishDevour();
        }
        this.startStagger(60, 1.5F);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 2.0D, this.getZ(), 120, 3.0D, 2.0D, 3.0D, 0.3D);
            // 首杀必掉鳐王之翼，之后 20%
            for (Player player : this.playersInArena(serverLevel)) {
                CompoundTag data = player.getPersistentData();
                boolean give = false;
                if (!data.getBoolean("hp_end_expansion.devourer_first_kill")) {
                    data.putBoolean("hp_end_expansion.devourer_first_kill", true);
                    give = true;
                } else if (this.random.nextFloat() < 0.2F) {
                    give = true;
                }
                if (give) {
                    ItemEntity wings = new ItemEntity(serverLevel, player.getX(), player.getY() + 0.5D, player.getZ(), new ItemStack(ModItems.DEVOURER_WINGS.get()));
                    wings.setNoPickUpDelay();
                    serverLevel.addFreshEntity(wings);
                    LOGGER.debug("Star Devourer wings given to {}", player.getName().getString());
                }
            }
            this.clearSummons(serverLevel);
        }
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.5D, 0.0D, 0.5D).add(0.0D, -0.15D, 0.0D));
        // 死亡动画 3 秒后消散
        if (this.deathTime >= 60 && !this.level().isClientSide() && !this.isRemoved()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 2.0D, this.getZ(), 150, 3.0D, 2.0D, 3.0D, 0.5D);
            }
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F + this.random.nextFloat() * 0.1F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", this.getPhase());
        tag.putInt("BrokenCores", this.entityData.get(BROKEN_CORES));
        for (int i = 0; i < 3; i++) {
            tag.putFloat("CoreHealth" + i, this.coreHealth[i]);
        }
        tag.putInt("DevourCount", this.devourCount);
        tag.putBoolean("GuardsSummoned", this.guardsSummoned);
        tag.putBoolean("SummonedByBeacon", this.summonedByBeacon);
        tag.putBoolean("PartyScaled", this.partyScaled);
        if (this.arenaCenter != null) {
            tag.putDouble("ArenaX", this.arenaCenter.x);
            tag.putDouble("ArenaY", this.arenaCenter.y);
            tag.putDouble("ArenaZ", this.arenaCenter.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Phase")) {
            this.entityData.set(PHASE, Math.max(1, tag.getInt("Phase")));
        }
        this.entityData.set(BROKEN_CORES, tag.getInt("BrokenCores"));
        for (int i = 0; i < 3; i++) {
            if (tag.contains("CoreHealth" + i)) {
                this.coreHealth[i] = tag.getFloat("CoreHealth" + i);
            }
        }
        this.devourCount = tag.getInt("DevourCount");
        this.guardsSummoned = tag.getBoolean("GuardsSummoned");
        this.summonedByBeacon = tag.getBoolean("SummonedByBeacon");
        this.partyScaled = tag.getBoolean("PartyScaled");
        if (tag.contains("ArenaX")) {
            this.arenaCenter = new Vec3(tag.getDouble("ArenaX"), tag.getDouble("ArenaY"), tag.getDouble("ArenaZ"));
        }
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 基础控制器：死亡、滑翔、悬停
        controllers.add(new AnimationController<>(this, "base", 6, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (state.isMoving()) {
                return state.setAndContinue(GLIDE);
            }
            return state.setAndContinue(IDLE_FLY);
        }));
        // 技能控制器：只播放服务端触发的动画
        controllers.add(new AnimationController<>(this, "skill", 3, state -> PlayState.STOP)
            .triggerableAnim("dive", DIVE)
            .triggerableAnim("beam", BEAM)
            .triggerableAnim("black_hole", BLACK_HOLE)
            .triggerableAnim("star_rain", STAR_RAIN)
            .triggerableAnim("wing_spin", WING_SPIN)
            .triggerableAnim("devour", DEVOUR)
            .triggerableAnim("stagger", STAGGER)
            .triggerableAnim("roar", ROAR)
            .triggerableAnim("hurt", HURT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    // 飞行移动：向目标点加速，技能期间由技能直接控制速度
    static final class DevourerMoveControl extends MoveControl {
        private final StarDevourerEntity boss;

        DevourerMoveControl(StarDevourerEntity boss) {
            super(boss);
            this.boss = boss;
        }

        @Override
        public void tick() {
            if (this.boss.isCasting() || this.operation != Operation.MOVE_TO) {
                return;
            }
            Vec3 delta = new Vec3(this.wantedX - this.boss.getX(), this.wantedY - this.boss.getY(), this.wantedZ - this.boss.getZ());
            double dist = delta.length();
            if (dist < 1.5D) {
                this.operation = Operation.WAIT;
                this.boss.setDeltaMovement(this.boss.getDeltaMovement().scale(0.6D));
                return;
            }
            double speed = this.speedModifier * this.boss.getAttributeValue(Attributes.FLYING_SPEED) * 0.06D;
            this.boss.setDeltaMovement(this.boss.getDeltaMovement().scale(0.92D).add(delta.scale(speed / dist)));
        }
    }

    // 战斗：绕目标大圈盘旋并释放技能
    static final class DevourerCombatGoal extends Goal {
        private final StarDevourerEntity boss;
        private float orbitAngle;

        DevourerCombatGoal(StarDevourerEntity boss) {
            this.boss = boss;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.boss.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.boss.setAggressive(true);
            LivingEntity target = this.boss.getTarget();
            if (target != null) {
                this.orbitAngle = (float) Mth.atan2(this.boss.getZ() - target.getZ(), this.boss.getX() - target.getX());
            }
        }

        @Override
        public void stop() {
            this.boss.setAggressive(false);
        }

        @Override
        public void tick() {
            LivingEntity target = this.boss.getTarget();
            if (target == null) {
                return;
            }
            // 施法中：推进技能（硬直与咆哮不依赖目标）
            if (this.boss.isCasting()) {
                this.boss.tickSkill(target);
                return;
            }
            this.boss.faceTarget(target.position(), 8.0F);
            if (this.boss.chooseSkill(target)) {
                return;
            }
            // 盘旋：半径 16 格，高度按阶段
            this.orbitAngle += 0.03F;
            double x = target.getX() + Mth.cos(this.orbitAngle) * 16.0D;
            double z = target.getZ() + Mth.sin(this.orbitAngle) * 16.0D;
            double y = target.getY() + this.boss.cruiseHeight();
            this.boss.getMoveControl().setWantedPosition(x, y, z, 1.0D);
        }
    }
}
