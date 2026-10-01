package org.hp.hp_end_expansion.entity;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.config.CombatConfigs;
import org.hp.hp_end_expansion.registry.ModEntities;
import org.hp.hp_end_expansion.registry.ModItems;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class RiftMatriarchEntity extends Monster implements GeoEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 动画定义
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rift_matriarch.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.rift_matriarch.walk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.rift_matriarch.death");
    private static final RawAnimation TRIPLE = RawAnimation.begin().thenPlay("animation.rift_matriarch.triple_slash");
    private static final RawAnimation HUNT = RawAnimation.begin().thenPlay("animation.rift_matriarch.hunt");
    private static final RawAnimation STORM = RawAnimation.begin().thenPlay("animation.rift_matriarch.storm");
    private static final RawAnimation WEB = RawAnimation.begin().thenPlay("animation.rift_matriarch.web");
    private static final RawAnimation SUMMON = RawAnimation.begin().thenPlay("animation.rift_matriarch.summon");
    private static final RawAnimation FIELD = RawAnimation.begin().thenPlay("animation.rift_matriarch.field");
    private static final RawAnimation FINALE = RawAnimation.begin().thenPlay("animation.rift_matriarch.finale");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.rift_matriarch.roar");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.rift_matriarch.hurt");

    // 技能编号：0 为空闲
    public static final int SKILL_NONE = 0;
    public static final int SKILL_TRIPLE = 1;
    public static final int SKILL_HUNT = 2;
    public static final int SKILL_STORM = 3;
    public static final int SKILL_WEB = 4;
    public static final int SKILL_SUMMON = 5;
    public static final int SKILL_FIELD = 6;
    public static final int SKILL_FINALE = 7;
    public static final int SKILL_TRANSITION = 8;

    // 场地与脱战参数
    private static final double ARENA_RADIUS = 32.0D;
    private static final int LEAVE_LIMIT = 1200;
    private static final int MAX_MINIONS = 3;

    // 属性修饰符标识
    private static final ResourceLocation PARTY_HEALTH = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "matriarch_party_health");
    private static final ResourceLocation NEST_SPEED = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "matriarch_nest_speed");
    private static final ResourceLocation SHATTER_ARMOR = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "matriarch_shatter_armor");

    // 同步字段：当前技能、技能 tick、阶段、背部核心暴露
    private static final EntityDataAccessor<Integer> SKILL = SynchedEntityData.defineId(RiftMatriarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKILL_TICK = SynchedEntityData.defineId(RiftMatriarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(RiftMatriarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CORE_OPEN = SynchedEntityData.defineId(RiftMatriarchEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    // Boss 血条：20 格刻度，60% 与 25% 恰好落在刻度上
    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);

    // 各技能冷却
    private int tripleCooldown = 20;
    private int huntCooldown = 80;
    private int stormCooldown = 100;
    private int webCooldown = 140;
    private int summonCooldown = 60;
    private int fieldCooldown = 40;
    private int finaleCooldown;
    private int fieldBlinkCooldown;
    // 场地中心与脱战计时
    private Vec3 arenaCenter;
    private int emptyTicks;
    private boolean summonedByEgg;
    private boolean partyScaled;
    // 阶段转换待处理标记
    private int pendingPhase;
    // 追猎落点
    private Vec3 huntSpot;
    // 裂隙场：圆心列表与剩余时间
    private final List<Vec3> fieldZones = new ArrayList<>();
    private int fieldTicks;
    // 终结技：斩线与下劈落点
    private final List<Vec3[]> finaleLines = new ArrayList<>();
    private Vec3 diveSpot;
    private int finaleLineCount = 12;

    public RiftMatriarchEntity(EntityType<? extends RiftMatriarchEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 300;
        this.setPersistenceRequired();
    }

    // Boss 属性
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 600.0D)
            .add(Attributes.ATTACK_DAMAGE, 18.0D)
            .add(Attributes.ARMOR, 16.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.32D)
            .add(Attributes.FOLLOW_RANGE, 48.0D)
            .add(Attributes.STEP_HEIGHT, 1.5D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKILL, SKILL_NONE);
        builder.define(SKILL_TICK, 0);
        builder.define(PHASE, 1);
        builder.define(CORE_OPEN, false);
    }

    @Override
    protected void registerGoals() {
        // 行为目标：战斗优先，其次观察
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MatriarchCombatGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // 索敌目标：受击反击与主动攻击玩家
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, RiftMantisEntity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
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

    public boolean isCoreOpen() {
        return this.entityData.get(CORE_OPEN);
    }

    public boolean isCasting() {
        return this.getSkill() != SKILL_NONE;
    }

    // 标记为召唤物召唤，脱战时归还裂隙之卵
    public void setSummonedByEgg(boolean value) {
        this.summonedByEgg = value;
    }

    // 启动技能：设置同步状态并触发动画
    private void startSkill(int skill, String anim) {
        this.entityData.set(SKILL, skill);
        this.entityData.set(SKILL_TICK, 0);
        this.getNavigation().stop();
        this.triggerAnim("skill", anim);
        LOGGER.debug("Rift Matriarch {} starts skill {} ({}) in phase {}", this.getId(), skill, anim, this.getPhase());
    }

    private void endSkill() {
        this.entityData.set(SKILL, SKILL_NONE);
        this.entityData.set(SKILL_TICK, 0);
        this.entityData.set(CORE_OPEN, false);
        this.setNoGravity(false);
    }

    // 三阶段攻速 +30%：冷却乘 0.77
    private int cd(int ticks) {
        if (this.getPhase() >= 3) {
            return (int) (ticks * 0.77F);
        }
        return ticks;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            // 客户端：核心暴露时背部溢出火花
            if (this.isCoreOpen() && this.random.nextInt(2) == 0) {
                float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
                Vec3 back = new Vec3(Mth.sin(yaw), 0.0D, -Mth.cos(yaw));
                Vec3 p = this.position().add(back.scale(2.2D)).add((this.random.nextDouble() - 0.5D) * 1.5D, 4.2D + this.random.nextDouble(), (this.random.nextDouble() - 0.5D) * 1.5D);
                this.level().addParticle(ModParticles.RIFT_SPARK.get(), p.x, p.y, p.z, 0.0D, 0.06D, 0.0D);
            }
            return;
        }
        // 服务端：冷却计时
        if (this.tripleCooldown > 0) {
            this.tripleCooldown--;
        }
        if (this.huntCooldown > 0) {
            this.huntCooldown--;
        }
        if (this.stormCooldown > 0) {
            this.stormCooldown--;
        }
        if (this.webCooldown > 0) {
            this.webCooldown--;
        }
        if (this.summonCooldown > 0) {
            this.summonCooldown--;
        }
        if (this.fieldCooldown > 0) {
            this.fieldCooldown--;
        }
        if (this.finaleCooldown > 0) {
            this.finaleCooldown--;
        }
        if (this.fieldBlinkCooldown > 0) {
            this.fieldBlinkCooldown--;
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
                LOGGER.debug("Rift Matriarch {} scaled for {} players, max health {}", this.getId(), players, this.getMaxHealth());
            }
        }
        // 场地约束：离开半径 32 时拉回中心
        if (this.isAlive() && this.position().distanceToSqr(this.arenaCenter) > ARENA_RADIUS * ARENA_RADIUS && this.getSkill() != SKILL_FINALE) {
            LOGGER.debug("Rift Matriarch {} left arena at {}, pulled back", this.getId(), this.position());
            RiftVfxEntity.spawn(serverLevel, RiftVfxEntity.KIND_PORTAL, this.arenaCenter.x, this.arenaCenter.y, this.arenaCenter.z, this.getYRot(), 0.0F, 3.0F, 16);
            this.teleportTo(this.arenaCenter.x, this.arenaCenter.y, this.arenaCenter.z);
        }
        // 脱战：场内 60 秒无玩家则回满血并消失
        if (this.tickCount % 20 == 0) {
            if (this.playersInArena(serverLevel).isEmpty()) {
                this.emptyTicks += 20;
            } else {
                this.emptyTicks = 0;
            }
            if (this.emptyTicks >= LEAVE_LIMIT && this.isAlive()) {
                LOGGER.debug("Rift Matriarch {} despawns, no players for 60s", this.getId());
                this.setHealth(this.getMaxHealth());
                if (this.summonedByEgg) {
                    ItemEntity egg = new ItemEntity(serverLevel, this.arenaCenter.x, this.arenaCenter.y + 0.5D, this.arenaCenter.z, new ItemStack(ModItems.RIFT_EGG.get()));
                    serverLevel.addFreshEntity(egg);
                }
                serverLevel.sendParticles(ModParticles.RIFT_SPARK.get(), this.getX(), this.getY() + 3.0D, this.getZ(), 40, 2.0D, 2.0D, 2.0D, 0.1D);
                this.discard();
                return;
            }
        }
        // 阶段判定：生命跨过 60% 与 25%
        float ratio = this.getHealth() / this.getMaxHealth();
        int phase = this.getPhase();
        if (this.pendingPhase == 0 && this.isAlive()) {
            if (phase == 1 && ratio <= 0.6F) {
                this.pendingPhase = 2;
            } else if (phase == 2 && ratio <= 0.25F) {
                this.pendingPhase = 3;
            }
        }
        // 裂隙场持续结算
        if (this.fieldTicks > 0) {
            this.fieldTicks--;
            this.tickFieldZones(serverLevel);
            if (this.fieldTicks == 0) {
                this.fieldZones.clear();
            }
        }
        // 三阶段：场地边缘每 2 秒生成一道向内的裂缝
        if (this.getPhase() >= 3 && this.isAlive() && this.tickCount % 40 == 0) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            Vec3 edge = this.arenaCenter.add(Math.cos(angle) * 20.0D, 0.0D, Math.sin(angle) * 20.0D);
            float yaw = (float) (Mth.atan2(this.arenaCenter.z - edge.z, this.arenaCenter.x - edge.x) * Mth.RAD_TO_DEG) - 90.0F;
            yaw += (this.random.nextFloat() - 0.5F) * 40.0F;
            Vec3 ground = this.groundAt(edge);
            if (ground != null) {
                RiftFissureEntity.spawn(this, ground, yaw, 9.0F, 1.2F, CombatConfigs.RIFT_MATRIARCH.damage("phaseThreeFissureDamage"), 0.6D);
            }
        }
    }

    // 场内非旁观玩家
    private List<Player> playersInArena(ServerLevel level) {
        Vec3 c = this.arenaCenter;
        if (c == null) {
            c = this.position();
        }
        AABB area = new AABB(c, c).inflate(ARENA_RADIUS, 24.0D, ARENA_RADIUS);
        List<Player> result = new ArrayList<>();
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            if (player.isAlive() && !player.isSpectator() && player.position().distanceToSqr(c) <= ARENA_RADIUS * ARENA_RADIUS) {
                result.add(player);
            }
        }
        return result;
    }

    // 找到某水平位置附近可站立的地面
    private Vec3 groundAt(Vec3 pos) {
        for (int dy = 4; dy >= -6; dy--) {
            BlockPos p = BlockPos.containing(pos.x, pos.y + dy, pos.z);
            BlockState below = this.level().getBlockState(p.below());
            if (below.isFaceSturdy(this.level(), p.below(), Direction.UP) && this.level().getBlockState(p).getCollisionShape(this.level(), p).isEmpty()) {
                return new Vec3(pos.x, p.getY(), pos.z);
            }
        }
        return null;
    }

    // 找到能容纳 Boss 碰撞箱的落点
    private Vec3 bodySpotAt(Vec3 pos) {
        Vec3 ground = this.groundAt(pos);
        if (ground == null) {
            return null;
        }
        AABB box = this.getDimensions(this.getPose()).makeBoundingBox(ground);
        if (this.level().noCollision(this, box) && !this.level().containsAnyLiquid(box)) {
            return ground;
        }
        return null;
    }

    // 技能逐 tick 推进，由战斗目标调用
    void tickSkill(LivingEntity target) {
        int skill = this.getSkill();
        int t = this.getSkillTick() + 1;
        this.entityData.set(SKILL_TICK, t);
        if (skill == SKILL_TRIPLE) {
            this.tickTriple(target, t);
        } else if (skill == SKILL_HUNT) {
            this.tickHunt(target, t);
        } else if (skill == SKILL_STORM) {
            this.tickStorm(target, t);
        } else if (skill == SKILL_WEB) {
            this.tickWeb(target, t);
        } else if (skill == SKILL_SUMMON) {
            this.tickSummon(target, t);
        } else if (skill == SKILL_FIELD) {
            this.tickField(target, t);
        } else if (skill == SKILL_FINALE) {
            this.tickFinale(target, t);
        } else if (skill == SKILL_TRANSITION) {
            this.tickTransition(t);
        }
    }

    // 朝向目标，覆盖身体惯性转向
    void faceTarget(LivingEntity target, float maxTurn) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float turned = Mth.approachDegrees(this.getYRot(), yaw, maxTurn);
        this.setYRot(turned);
        this.yBodyRot = turned;
        this.yHeadRot = turned;
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private Vec3 forwardVec() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    // 三连镰斩：第 8、15 tick 扇形斩，第 16~28 tick 高举后下劈
    private void tickTriple(LivingEntity target, int t) {
        // 每段出招前重新朝向，下劈前 0.3 秒锁定方向
        if (t <= 6 || (t >= 9 && t <= 13) || (t >= 16 && t <= 22)) {
            this.faceTarget(target, 25.0F);
        }
        // 前两段：左斩、右斩
        if (t == 5 || t == 12) {
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.5F);
        }
        if (t == 8 || t == 15) {
            float roll = 20.0F;
            if (t == 15) {
                roll = -20.0F;
            }
            Vec3 forward = this.forwardVec();
            Vec3 center = this.position().add(forward.scale(3.5D)).add(0.0D, 3.2D, 0.0D);
            RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_SLASH, center.x, center.y, center.z, this.getYRot(), roll, 2.5F, 8);
            this.hitCone(forward, 6.0D, 0.342D, CombatConfigs.RIFT_MATRIARCH.damage(t == 8 ? "tripleFirstDamage" : "tripleSecondDamage"), 0.8D, false);
        }
        // 第三段：高举前摇 0.6 秒
        if (t == 16) {
            this.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 1.8F);
        }
        if (t == 28) {
            Vec3 forward = this.forwardVec();
            Vec3 center = this.position().add(forward.scale(4.0D)).add(0.0D, 2.4D, 0.0D);
            RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_SLASH, center.x, center.y, center.z, this.getYRot(), 90.0F, 2.5F, 10);
            this.hitRect(forward, 7.0D, 1.0D, CombatConfigs.RIFT_MATRIARCH.damage("tripleFinisherDamage"));
            // 落地短地裂与碎片
            if (this.level() instanceof ServerLevel serverLevel) {
                for (int i = 2; i <= 7; i++) {
                    Vec3 p = this.position().add(forward.scale(i));
                    serverLevel.sendParticles(ModParticles.RIFT_SHARD.get(), p.x, p.y + 0.2D, p.z, 4, 0.3D, 0.1D, 0.3D, 0.2D);
                }
            }
            this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.0F);
        }
        if (t >= 40) {
            this.tripleCooldown = this.cd(60);
            this.endSkill();
        }
    }

    // 裂隙追猎：两次闪现，每次落点预警 0.6 秒
    private void tickHunt(LivingEntity target, int t) {
        this.faceTarget(target, 30.0F);
        // 第一次：目标侧面
        if (t == 1) {
            Vec3 look = target.getLookAngle();
            Vec3 side = new Vec3(-look.z, 0.0D, look.x);
            this.huntSpot = this.findHuntSpot(target, side);
            this.warnSpot(this.huntSpot);
            this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.5F, 1.2F);
        }
        // 第二次：目标背后
        if (t == 17) {
            Vec3 look = target.getLookAngle();
            this.huntSpot = this.findHuntSpot(target, new Vec3(-look.x, 0.0D, -look.z));
            this.warnSpot(this.huntSpot);
        }
        if (t == 13 || t == 29) {
            this.blinkTo(this.huntSpot);
            this.faceTarget(target, 180.0F);
        }
        if (t == 16 || t == 32) {
            Vec3 forward = this.forwardVec();
            Vec3 center = this.position().add(forward.scale(3.5D)).add(0.0D, 3.0D, 0.0D);
            RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_SLASH, center.x, center.y, center.z, this.getYRot(), 25.0F, 2.5F, 8);
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.7F);
            this.hitCone(forward, 6.0D, 0.3D, CombatConfigs.RIFT_MATRIARCH.damage("huntDamage"), 0.6D, false);
        }
        if (t >= 44) {
            this.huntCooldown = this.cd(160);
            this.tripleCooldown = 10;
            this.endSkill();
        }
    }

    // 在目标某方向寻找落点，失败依次尝试其他方向
    private Vec3 findHuntSpot(LivingEntity target, Vec3 preferred) {
        Vec3 dir = preferred;
        if (dir.lengthSqr() < 1.0E-4D) {
            dir = this.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        }
        dir = dir.normalize();
        for (int i = 0; i < 8; i++) {
            Vec3 d = dir.yRot(i * 45.0F * Mth.DEG_TO_RAD);
            Vec3 spot = this.bodySpotAt(target.position().add(d.scale(4.8D)));
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    // 落点地面光圈预警
    private void warnSpot(Vec3 spot) {
        if (spot != null) {
            VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_SHOCK, spot.add(0.0D, 0.05D, 0.0D), 0.0F, 3.0F, 0, 12, this);
            RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_PORTAL, spot.x, spot.y, spot.z, this.getYRot(), 0.0F, 2.5F, 14);
        }
    }

    // 闪现到指定位置
    private void blinkTo(Vec3 dest) {
        if (dest == null) {
            return;
        }
        Vec3 here = this.position();
        RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_PORTAL, here.x, here.y, here.z, this.getYRot(), 0.0F, 2.5F, 12);
        this.teleportTo(dest.x, dest.y, dest.z);
        this.level().playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5F, 0.8F);
        LOGGER.debug("Rift Matriarch {} blinked to {}", this.getId(), dest);
    }

    // 镰刃风暴：旋身 1 秒后甩出 3 波环形飞刃
    private void tickStorm(LivingEntity target, int t) {
        if (t == 2) {
            this.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.5F, 1.2F);
        }
        if (t == 20 || t == 26 || t == 32) {
            int wave = (t - 20) / 6;
            Vec3 origin = this.position().add(0.0D, 2.4D, 0.0D);
            // 二阶段起最后一波改为朝目标扇形
            if (wave == 2 && this.getPhase() >= 2) {
                Vec3 aim = target.getEyePosition().subtract(0.0D, 0.4D, 0.0D).subtract(origin).normalize();
                for (int i = -2; i <= 2; i++) {
                    RiftBladeEntity.launch(this, origin, aim.yRot(i * 11.0F * Mth.DEG_TO_RAD), 1.1F, CombatConfigs.RIFT_MATRIARCH.damage("stormBladeDamage"));
                }
            } else {
                float offset = wave * 22.5F + this.getYRot();
                for (int i = 0; i < 8; i++) {
                    float a = (offset + i * 45.0F) * Mth.DEG_TO_RAD;
                    Vec3 dir = new Vec3(-Mth.sin(a), 0.0D, Mth.cos(a));
                    RiftBladeEntity.launch(this, origin.add(dir.scale(2.0D)), dir, 0.9F, CombatConfigs.RIFT_MATRIARCH.damage("stormBladeDamage"));
                }
            }
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 1.3F);
        }
        if (t >= 44) {
            this.stormCooldown = this.cd(200);
            this.endSkill();
        }
    }

    // 裂地星网：砸地后放射 5 条地裂，三阶段追加第二轮
    private void tickWeb(LivingEntity target, int t) {
        if (t <= 12) {
            this.faceTarget(target, 30.0F);
        }
        if (t == 4) {
            this.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 1.2F, 1.2F);
        }
        if (t == 16 || (t == 32 && this.getPhase() >= 3)) {
            float base = this.getYRot();
            if (t == 32) {
                base += 36.0F;
            }
            for (int i = 0; i < 5; i++) {
                float yaw = base + i * 72.0F;
                float rad = yaw * Mth.DEG_TO_RAD;
                Vec3 start = this.position().add(-Mth.sin(rad) * 2.5D, 0.0D, Mth.cos(rad) * 2.5D);
                RiftFissureEntity.spawn(this, start, yaw, 16.0F, 1.5F, CombatConfigs.RIFT_MATRIARCH.damage("webFissureDamage"), 0.9D);
            }
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ModParticles.RIFT_SHARD.get(), this.getX(), this.getY() + 0.3D, this.getZ(), 30, 2.0D, 0.2D, 2.0D, 0.3D);
            }
            VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_SHOCK, this.position().add(0.0D, 0.05D, 0.0D), 0.0F, 5.0F, 0, 12, this);
            this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.9F);
        }
        if (t >= 50) {
            this.webCooldown = this.cd(240);
            this.endSkill();
        }
    }

    // 巢母召唤：背部裂隙张开 1.5 秒，从三道裂隙门召出螳螂
    private void tickSummon(LivingEntity target, int t) {
        if (t == 1) {
            this.entityData.set(CORE_OPEN, true);
            this.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 2.0F, 0.7F);
        }
        if (t == 30 && this.level() instanceof ServerLevel serverLevel) {
            this.entityData.set(CORE_OPEN, false);
            int alive = this.countMinions(serverLevel);
            int count = Math.min(3, MAX_MINIONS - alive);
            double start = this.random.nextDouble() * Math.PI * 2.0D;
            for (int i = 0; i < count; i++) {
                double a = start + i * Math.PI * 2.0D / 3.0D;
                Vec3 pos = this.arenaCenter.add(Math.cos(a) * 14.0D, 0.0D, Math.sin(a) * 14.0D);
                Vec3 ground = this.groundAt(pos);
                if (ground == null) {
                    continue;
                }
                RiftMantisEntity mantis = ModEntities.RIFT_MANTIS.get().create(serverLevel);
                if (mantis == null) {
                    continue;
                }
                mantis.moveTo(ground.x, ground.y, ground.z, this.random.nextFloat() * 360.0F, 0.0F);
                mantis.makeMinion((float) CombatConfigs.RIFT_MATRIARCH.value("summonMinionHealth"));
                mantis.setTarget(target);
                serverLevel.addFreshEntity(mantis);
                RiftVfxEntity.spawn(serverLevel, RiftVfxEntity.KIND_PORTAL, ground.x, ground.y, ground.z, 0.0F, 0.0F, 1.8F, 24);
            }
            LOGGER.debug("Rift Matriarch {} summoned {} mantises ({} already alive)", this.getId(), count, alive);
        }
        if (t >= 40) {
            this.summonCooldown = this.cd(500);
            this.endSkill();
        }
    }

    // 统计场上由螳后召唤的螳螂
    private int countMinions(ServerLevel level) {
        AABB area = new AABB(this.arenaCenter, this.arenaCenter).inflate(ARENA_RADIUS + 8.0D, 24.0D, ARENA_RADIUS + 8.0D);
        int n = 0;
        for (RiftMantisEntity mantis : level.getEntitiesOfClass(RiftMantisEntity.class, area)) {
            if (mantis.isAlive() && mantis.isMinion()) {
                n++;
            }
        }
        return n;
    }

    // 裂隙场：在目标周围生成 4 块裂隙地面，持续 8 秒
    private void tickField(LivingEntity target, int t) {
        this.faceTarget(target, 20.0F);
        if (t == 2) {
            this.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.5F, 0.6F);
        }
        if (t == 14) {
            this.fieldZones.clear();
            double start = this.random.nextDouble() * Math.PI * 2.0D;
            for (int i = 0; i < 4; i++) {
                double a = start + i * Math.PI / 2.0D;
                double r = 4.0D + this.random.nextDouble() * 3.0D;
                Vec3 ground = this.groundAt(target.position().add(Math.cos(a) * r, 0.0D, Math.sin(a) * r));
                if (ground != null) {
                    this.fieldZones.add(ground);
                    RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_PORTAL, ground.x, ground.y, ground.z, (float) (a * Mth.RAD_TO_DEG), 0.0F, 1.2F, 160);
                }
            }
            this.fieldTicks = 160;
            this.fieldBlinkCooldown = 40;
            LOGGER.debug("Rift Matriarch {} opened {} rift zones", this.getId(), this.fieldZones.size());
        }
        if (t >= 24) {
            this.fieldCooldown = this.cd(400);
            this.endSkill();
        }
    }

    // 裂隙地面：每秒 4 伤害并拉向中心，地面符文粒子
    private void tickFieldZones(ServerLevel level) {
        for (Vec3 zone : this.fieldZones) {
            if (this.fieldTicks % 4 == 0) {
                double a = this.random.nextDouble() * Math.PI * 2.0D;
                level.sendParticles(ModParticles.RIFT_SPARK.get(), zone.x + Math.cos(a) * 2.5D, zone.y + 0.1D, zone.z + Math.sin(a) * 2.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.sendParticles(ModParticles.RIFT_SHARD.get(), zone.x, zone.y + 0.1D, zone.z, 2, 1.2D, 0.02D, 1.2D, 0.02D);
            }
            AABB box = new AABB(zone, zone).inflate(2.5D, 2.0D, 2.5D);
            for (Player player : level.getEntitiesOfClass(Player.class, box)) {
                Vec3 rel = zone.subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
                if (rel.lengthSqr() > 6.25D || player.isSpectator()) {
                    continue;
                }
                if (this.fieldTicks % 20 == 0) {
                    player.hurt(this.damageSources().indirectMagic(this, this), CombatConfigs.RIFT_MATRIARCH.damage("fieldDamage"));
                }
                if (this.fieldTicks % 10 == 0 && rel.lengthSqr() > 0.25D) {
                    Vec3 pull = rel.normalize().scale(0.25D);
                    player.push(pull.x, 0.0D, pull.z);
                    player.hurtMarked = true;
                }
            }
        }
    }

    // 终结技千裂之刃：跃入裂隙，分 4 批斩线，最后下劈
    private void tickFinale(LivingEntity target, int t) {
        Vec3 c = this.arenaCenter;
        // 跃入场地中央上空的巨型裂隙门
        if (t == 6) {
            this.setNoGravity(true);
            this.setDeltaMovement(Vec3.ZERO);
            RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_PORTAL, c.x, c.y + 8.0D, c.z, this.getYRot(), 0.0F, 5.0F, 80);
            this.teleportTo(c.x, c.y + 9.0D, c.z);
            this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 1.4F);
            this.finaleLines.clear();
        }
        if (t > 6 && t < 90) {
            this.setDeltaMovement(Vec3.ZERO);
        }
        // 4 批预警，每批间隔 0.6 秒
        int perBatch = this.finaleLineCount / 4;
        if (t >= 10 && t <= 46 && (t - 10) % 12 == 0) {
            double batchAngle = this.random.nextDouble() * 180.0D;
            for (int i = 0; i < perBatch; i++) {
                double deg = batchAngle + i * (180.0D / perBatch) + (this.random.nextDouble() - 0.5D) * 20.0D;
                double rad = deg * Mth.DEG_TO_RAD;
                Vec3 dir = new Vec3(Math.cos(rad), 0.0D, Math.sin(rad));
                Vec3 normal = new Vec3(-dir.z, 0.0D, dir.x);
                Vec3 mid = c.add(normal.scale((this.random.nextDouble() - 0.5D) * 16.0D));
                Vec3 start = mid.subtract(dir.scale(ARENA_RADIUS));
                float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
                VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_WARN_LINE, start.add(0.0D, 0.05D, 0.0D), yaw, (float) (ARENA_RADIUS * 2.0D), 0, 20, this);
                this.finaleLines.add(new Vec3[] {start, dir, new Vec3(t + 20, 0.0D, 0.0D)});
            }
            this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 2.0F, 1.6F);
        }
        // 预警 1 秒后巨型镰刃沿线斩过
        for (Vec3[] line : this.finaleLines) {
            if ((int) line[2].x == t) {
                this.slashLine(line[0], line[1]);
            }
        }
        // 下劈前 1 秒显示落点
        if (t == 70) {
            this.diveSpot = this.groundAt(target.position());
            if (this.diveSpot == null) {
                this.diveSpot = this.groundAt(c);
            }
            if (this.diveSpot != null) {
                VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_SHOCK, this.diveSpot.add(0.0D, 0.05D, 0.0D), 0.0F, 6.0F, 0, 20, this);
            }
        }
        if (t == 90) {
            this.setNoGravity(false);
            Vec3 dest = this.diveSpot;
            if (dest == null) {
                dest = c;
            }
            this.teleportTo(dest.x, dest.y, dest.z);
            this.hitCircle(dest, 6.0D, CombatConfigs.RIFT_MATRIARCH.damage("finaleImpactDamage"));
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ModParticles.RIFT_SHARD.get(), dest.x, dest.y + 0.3D, dest.z, 60, 3.0D, 0.3D, 3.0D, 0.35D);
            }
            VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_SHOCK, dest.add(0.0D, 0.05D, 0.0D), 0.0F, 7.0F, 0, 14, this);
            this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.7F);
        }
        if (t >= 110) {
            this.finaleCooldown = 600;
            this.finaleLineCount = 8;
            this.finaleLines.clear();
            this.endSkill();
        }
    }

    // 沿直线斩击：宽 2 格，20 伤害
    private void slashLine(Vec3 start, Vec3 dir) {
        double length = ARENA_RADIUS * 2.0D;
        Vec3 end = start.add(dir.scale(length));
        AABB area = new AABB(start, end).inflate(1.0D, 4.0D, 1.0D).inflate(0.0D, 4.0D, 0.0D);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (this.isAlly(victim)) {
                continue;
            }
            Vec3 rel = victim.position().subtract(start);
            double along = Mth.clamp(rel.dot(dir), 0.0D, length);
            Vec3 closest = start.add(dir.scale(along));
            double dx = victim.getX() - closest.x;
            double dz = victim.getZ() - closest.z;
            if (dx * dx + dz * dz <= 1.0D + victim.getBbWidth() * 0.5D) {
                victim.hurt(this.damageSources().indirectMagic(this, this), CombatConfigs.RIFT_MATRIARCH.damage("finaleLineDamage"));
            }
        }
        // 镰刃弧光沿线排布
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        for (int i = 4; i < (int) length; i += 8) {
            Vec3 p = start.add(dir.scale(i));
            RiftVfxEntity.spawn(this.level(), RiftVfxEntity.KIND_SLASH, p.x, p.y + 1.0D, p.z, yaw, 90.0F, 2.5F, 6);
        }
        this.level().playSound(null, start.x + dir.x * length * 0.5D, start.y, start.z + dir.z * length * 0.5D, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    // 阶段转换：无敌 2 秒，第 20 tick 全场击飞冲击
    private void tickTransition(int t) {
        if (t == 20 && this.level() instanceof ServerLevel serverLevel) {
            for (Player player : this.playersInArena(serverLevel)) {
                Vec3 away = player.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
                if (away.lengthSqr() > 1.0E-4D) {
                    away = away.normalize().scale(0.6D);
                }
                player.push(away.x, 1.0D, away.z);
                player.hurtMarked = true;
            }
            VoidRayVfxEntity.spawn(serverLevel, VoidRayVfxEntity.KIND_SHOCK, this.position().add(0.0D, 0.05D, 0.0D), 0.0F, 16.0F, 0, 16, this);
            serverLevel.sendParticles(ModParticles.RIFT_SHARD.get(), this.getX(), this.getY() + 3.0D, this.getZ(), 50, 2.0D, 2.0D, 2.0D, 0.3D);
            this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.5F, 0.8F);
        }
        if (t >= 40) {
            this.endSkill();
            this.setInvulnerable(false);
        }
    }

    // 进入新阶段：应用属性变化并开始转场
    private void enterPhase(int phase) {
        this.entityData.set(PHASE, phase);
        this.pendingPhase = 0;
        this.applyPhaseAttributes(phase);
        if (phase == 3) {
            this.finaleCooldown = 100;
            this.finaleLineCount = 12;
        }
        this.setInvulnerable(true);
        this.startSkill(SKILL_TRANSITION, "roar");
        LOGGER.debug("Rift Matriarch {} enters phase {}", this.getId(), phase);
    }

    // 阶段属性：二阶段移速 +15%，三阶段护甲 -8
    private void applyPhaseAttributes(int phase) {
        if (phase >= 2) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(
                new AttributeModifier(NEST_SPEED, 0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        if (phase >= 3) {
            this.getAttribute(Attributes.ARMOR).addOrReplacePermanentModifier(
                new AttributeModifier(SHATTER_ARMOR, -8.0D, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // 同类与召唤物不受伤害
    private boolean isAlly(LivingEntity victim) {
        return victim == this || victim instanceof RiftMantisEntity || !victim.isAlive();
    }

    // 前方扇形判定
    private void hitCone(Vec3 forward, double range, double minDot, float damage, double knock, boolean wither) {
        AABB area = this.getBoundingBox().inflate(range, 2.0D, range);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (this.isAlly(victim)) {
                continue;
            }
            Vec3 rel = victim.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
            double dist = rel.length();
            if (dist > range + this.getBbWidth() * 0.5D + victim.getBbWidth() * 0.5D) {
                continue;
            }
            if (dist > 0.5D && rel.normalize().dot(forward) < minDot) {
                continue;
            }
            if (victim.hurt(this.damageSources().mobAttack(this), damage)) {
                victim.knockback(knock, -forward.x, -forward.z);
                if (wither) {
                    victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), this);
                }
            }
        }
    }

    // 前方矩形判定：下劈
    private void hitRect(Vec3 forward, double length, double halfWidth, float damage) {
        Vec3 start = this.position();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        AABB area = this.getBoundingBox().inflate(length + 1.0D, 2.0D, length + 1.0D);
        double reach = length + this.getBbWidth() * 0.5D;
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (this.isAlly(victim)) {
                continue;
            }
            Vec3 rel = victim.position().subtract(start).multiply(1.0D, 0.0D, 1.0D);
            double along = rel.dot(forward);
            double across = Math.abs(rel.dot(side));
            if (along < 0.0D || along > reach || across > halfWidth + victim.getBbWidth() * 0.5D) {
                continue;
            }
            if (victim.hurt(this.damageSources().mobAttack(this), damage)) {
                victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), this);
            }
        }
    }

    // 圆形落点判定：向上击飞
    private void hitCircle(Vec3 center, double radius, float damage) {
        AABB area = new AABB(center, center).inflate(radius, 3.0D, radius);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (this.isAlly(victim)) {
                continue;
            }
            if (victim.position().distanceToSqr(center) > radius * radius) {
                continue;
            }
            if (victim.hurt(this.damageSources().mobAttack(this), damage)) {
                victim.push(0.0D, 0.8D, 0.0D);
                victim.hurtMarked = true;
            }
        }
    }

    // 选择技能：由战斗目标在空闲时调用
    boolean tryStartSkill(LivingEntity target) {
        // 阶段转换优先
        if (this.pendingPhase != 0) {
            this.enterPhase(this.pendingPhase);
            return true;
        }
        int phase = this.getPhase();
        double dist = this.distanceTo(target);
        boolean sight = this.hasLineOfSight(target);
        if (phase >= 3 && this.finaleCooldown <= 0) {
            this.startSkill(SKILL_FINALE, "finale");
            return true;
        }
        if (phase >= 2 && this.summonCooldown <= 0 && this.level() instanceof ServerLevel serverLevel && this.countMinions(serverLevel) < MAX_MINIONS) {
            this.startSkill(SKILL_SUMMON, "summon");
            return true;
        }
        if (phase >= 2 && this.fieldCooldown <= 0 && this.fieldTicks <= 0 && dist < 20.0D) {
            this.startSkill(SKILL_FIELD, "field");
            return true;
        }
        if (this.webCooldown <= 0 && sight && dist > 5.0D && dist < 16.0D && this.random.nextInt(3) == 0) {
            this.startSkill(SKILL_WEB, "web");
            return true;
        }
        if (this.stormCooldown <= 0 && dist < 14.0D && this.random.nextInt(2) == 0) {
            this.startSkill(SKILL_STORM, "storm");
            return true;
        }
        if (this.huntCooldown <= 0 && dist > 8.0D && dist < 28.0D) {
            this.startSkill(SKILL_HUNT, "hunt");
            return true;
        }
        if (this.tripleCooldown <= 0 && dist < 6.5D) {
            this.startSkill(SKILL_TRIPLE, "triple_slash");
            return true;
        }
        return false;
    }

    // 裂隙场存在时，远离目标则瞬移到某块裂隙地面代替移动
    boolean tryFieldBlink(LivingEntity target) {
        if (this.fieldZones.isEmpty() || this.fieldBlinkCooldown > 0 || this.distanceTo(target) < 8.0D) {
            return false;
        }
        Vec3 zone = this.fieldZones.get(this.random.nextInt(this.fieldZones.size()));
        Vec3 spot = this.bodySpotAt(zone);
        this.fieldBlinkCooldown = 50;
        if (spot == null) {
            return false;
        }
        this.blinkTo(spot);
        this.tripleCooldown = Math.max(this.tripleCooldown, 6);
        return true;
    }

    // 目标失效时中止技能，转场和终结技需要收尾
    void abortSkill() {
        if (this.isCasting()) {
            LOGGER.debug("Rift Matriarch {} aborts skill {}", this.getId(), this.getSkill());
            if (this.getSkill() == SKILL_FINALE && this.arenaCenter != null) {
                Vec3 ground = this.groundAt(this.arenaCenter);
                if (ground != null) {
                    this.teleportTo(ground.x, ground.y, ground.z);
                }
                this.finaleCooldown = 200;
                this.finaleLines.clear();
            }
            this.setInvulnerable(false);
            this.endSkill();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 终结技滞空期间免疫
        if (this.getSkill() == SKILL_FINALE && this.getSkillTick() > 6 && this.getSkillTick() < 90 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        float dealt = amount;
        // 召唤期间背部核心暴露，伤害翻倍
        if (this.isCoreOpen()) {
            dealt = amount * 2.0F;
        }
        boolean result = super.hurt(source, dealt);
        if (result && !this.level().isClientSide() && !this.isCasting() && this.isAlive()) {
            this.triggerAnim("skill", "hurt");
        }
        return result;
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
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.RIFT_SHARD.get(), this.getX(), this.getY() + 3.0D, this.getZ(), 80, 2.0D, 2.0D, 2.0D, 0.25D);
            // 首杀：场内首次击杀的玩家额外获得螳后之镰
            for (Player player : this.playersInArena(serverLevel)) {
                CompoundTag data = player.getPersistentData();
                if (!data.getBoolean("hp_end_expansion.matriarch_first_kill")) {
                    data.putBoolean("hp_end_expansion.matriarch_first_kill", true);
                    ItemEntity scythe = new ItemEntity(serverLevel, player.getX(), player.getY() + 0.5D, player.getZ(), new ItemStack(ModItems.MATRIARCH_SCYTHE.get()));
                    scythe.setNoPickUpDelay();
                    serverLevel.addFreshEntity(scythe);
                    LOGGER.debug("Rift Matriarch first kill reward given to {}", player.getName().getString());
                }
            }
            // 场上召唤物随之崩解
            for (RiftMantisEntity mantis : serverLevel.getEntitiesOfClass(RiftMantisEntity.class, this.getBoundingBox().inflate(ARENA_RADIUS + 8.0D))) {
                if (mantis.isMinion()) {
                    mantis.kill();
                }
            }
        }
        this.fieldZones.clear();
        this.fieldTicks = 0;
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        // 死亡动画 2 秒后化为碎片消失
        if (this.deathTime >= 40 && !this.level().isClientSide() && !this.isRemoved()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ModParticles.RIFT_SPARK.get(), this.getX(), this.getY() + 2.0D, this.getZ(), 60, 2.5D, 1.5D, 2.5D, 0.1D);
            }
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMITE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ENDERMITE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.RAVAGER_STEP, 0.6F, 0.8F);
    }

    @Override
    public float getVoicePitch() {
        return 0.3F + this.random.nextFloat() * 0.1F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", this.getPhase());
        tag.putBoolean("SummonedByEgg", this.summonedByEgg);
        tag.putBoolean("PartyScaled", this.partyScaled);
        tag.putInt("FinaleLines", this.finaleLineCount);
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
        this.summonedByEgg = tag.getBoolean("SummonedByEgg");
        this.partyScaled = tag.getBoolean("PartyScaled");
        if (tag.contains("FinaleLines")) {
            this.finaleLineCount = tag.getInt("FinaleLines");
        }
        if (tag.contains("ArenaX")) {
            this.arenaCenter = new Vec3(tag.getDouble("ArenaX"), tag.getDouble("ArenaY"), tag.getDouble("ArenaZ"));
        }
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 基础控制器：死亡、移动、待机
        controllers.add(new AnimationController<>(this, "base", 5, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (state.isMoving()) {
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }));
        // 技能控制器：只播放服务端触发的动画
        controllers.add(new AnimationController<>(this, "skill", 2, state -> PlayState.STOP)
            .triggerableAnim("triple_slash", TRIPLE)
            .triggerableAnim("hunt", HUNT)
            .triggerableAnim("storm", STORM)
            .triggerableAnim("web", WEB)
            .triggerableAnim("summon", SUMMON)
            .triggerableAnim("field", FIELD)
            .triggerableAnim("finale", FINALE)
            .triggerableAnim("roar", ROAR)
            .triggerableAnim("hurt", HURT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    // 战斗目标：追击、选择并推进技能
    static final class MatriarchCombatGoal extends Goal {
        private final RiftMatriarchEntity boss;
        private int repathDelay;

        MatriarchCombatGoal(RiftMatriarchEntity boss) {
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
            this.repathDelay = 0;
        }

        @Override
        public void stop() {
            this.boss.setAggressive(false);
            this.boss.abortSkill();
            this.boss.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = this.boss.getTarget();
            if (target == null) {
                return;
            }
            // 施法中：锁定移动，推进技能
            if (this.boss.isCasting()) {
                this.boss.getNavigation().stop();
                this.boss.tickSkill(target);
                return;
            }
            this.boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.boss.tryStartSkill(target)) {
                return;
            }
            if (this.boss.tryFieldBlink(target)) {
                return;
            }
            // 追击：贴近后停步等待镰斩冷却
            double dist = this.boss.distanceTo(target);
            if (dist < 4.5D) {
                this.boss.getNavigation().stop();
                this.boss.faceTarget(target, 30.0F);
                return;
            }
            if (--this.repathDelay <= 0) {
                this.repathDelay = 6 + this.boss.getRandom().nextInt(5);
                this.boss.getNavigation().moveTo(target, 1.1D);
            }
        }
    }
}
