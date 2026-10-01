package org.hp.hp_end_expansion.entity;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.config.CombatConfigs;
import org.joml.Vector3f;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class VoidRayEntity extends Monster implements GeoEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 动画定义
    private static final RawAnimation IDLE_FLY = RawAnimation.begin().thenLoop("animation.void_ray.idle_fly");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.void_ray.glide");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.void_ray.death");
    private static final RawAnimation DIVE = RawAnimation.begin().thenPlay("animation.void_ray.dive_start").thenLoop("animation.void_ray.dive_loop");
    private static final RawAnimation DIVE_END = RawAnimation.begin().thenPlay("animation.void_ray.dive_end");
    private static final RawAnimation BEAM = RawAnimation.begin().thenPlay("animation.void_ray.beam_start").thenLoop("animation.void_ray.beam_loop");
    private static final RawAnimation BEAM_END = RawAnimation.begin().thenPlay("animation.void_ray.beam_end");
    private static final RawAnimation VORTEX = RawAnimation.begin().thenPlay("animation.void_ray.vortex_cast");
    private static final RawAnimation STARFALL = RawAnimation.begin().thenPlay("animation.void_ray.brood_burst");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.void_ray.hurt");

    // 技能编号
    public static final int SKILL_NONE = 0;
    public static final int SKILL_DIVE = 1;
    public static final int SKILL_BEAM = 2;
    public static final int SKILL_VORTEX = 3;
    public static final int SKILL_STARFALL = 4;

    // 俯冲时序：前摇、俯冲、收招
    public static final int DIVE_WINDUP = 16;
    public static final int DIVE_STRIKE_END = 28;
    public static final int DIVE_RECOVER_END = 44;
    // 射线时序：蓄力、引导、过热
    public static final int BEAM_CHARGE = 20;
    public static final int BEAM_STOP = 60;
    public static final int BEAM_END_TICK = 72;
    // 星陨时序：背部裂开时发射
    public static final int STAR_LAUNCH = 18;
    public static final int STAR_END = 30;
    // 核心相对实体脚底的高度
    public static final double CORE_HEIGHT = 0.85D;

    // 同步字段：技能、技能 tick、射线终点、第二阶段
    private static final EntityDataAccessor<Integer> SKILL = SynchedEntityData.defineId(VoidRayEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKILL_TICK = SynchedEntityData.defineId(VoidRayEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> BEAM_POINT = SynchedEntityData.defineId(VoidRayEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(VoidRayEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    // 客户端俯冲拖尾历史点
    public final ArrayDeque<Vec3> trail = new ArrayDeque<>();

    // 技能冷却
    private int diveCooldown = 40;
    private int beamCooldown = 80;
    private int vortexCooldown = 120;
    private int starCooldown;
    private int globalCooldown = 20;
    // 俯冲路径：起点、锁定落点、拉升终点与已命中目标
    private Vec3 diveFrom;
    private Vec3 diveLow;
    private Vec3 diveTo;
    private final Set<Integer> diveHits = new HashSet<>();
    // 失去目标计时，用于回血
    private int idleTicks;
    // 鳐王护卫：只会俯冲，无掉落无经验
    private boolean minion;

    public VoidRayEntity(EntityType<? extends VoidRayEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 40;
        this.setNoGravity(true);
        this.moveControl = new VoidRayMoveControl(this);
    }

    // 精英怪属性
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 130.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.5D)
            .add(Attributes.FLYING_SPEED, 0.5D)
            .add(Attributes.ATTACK_DAMAGE, 10.0D)
            .add(Attributes.ARMOR, 6.0D)
            .add(Attributes.FOLLOW_RANGE, 48.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKILL, SKILL_NONE);
        builder.define(SKILL_TICK, 0);
        builder.define(BEAM_POINT, new Vector3f());
        builder.define(ENRAGED, false);
    }

    @Override
    protected void registerGoals() {
        // 行为：战斗优先，其次盘旋游荡
        this.goalSelector.addGoal(1, new VoidRayCombatGoal(this));
        this.goalSelector.addGoal(5, new VoidRayWanderGoal(this));
        // 受击后反击
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, VoidRayEntity.class, StarDevourerEntity.class));
    }

    public void makeMinion(float health) {
        this.minion = true;
        this.xpReward = 0;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        this.setHealth(health);
        this.setPersistenceRequired();
    }

    public boolean isMinion() {
        return this.minion;
    }

    @Override
    protected boolean shouldDropLoot() {
        return !this.minion && super.shouldDropLoot();
    }

    public int getSkill() {
        return this.entityData.get(SKILL);
    }

    public int getSkillTick() {
        return this.entityData.get(SKILL_TICK);
    }

    public boolean isCasting() {
        return this.getSkill() != SKILL_NONE;
    }

    public boolean isEnraged() {
        return this.entityData.get(ENRAGED);
    }

    public Vec3 getBeamPoint() {
        Vector3f v = this.entityData.get(BEAM_POINT);
        return new Vec3(v.x, v.y, v.z);
    }

    // 射线是否处于引导阶段
    public boolean isBeamFiring() {
        int t = this.getSkillTick();
        return this.getSkill() == SKILL_BEAM && t >= BEAM_CHARGE && t < BEAM_STOP;
    }

    // 俯冲是否处于冲刺阶段
    public boolean isDiveStriking() {
        int t = this.getSkillTick();
        return this.getSkill() == SKILL_DIVE && t >= DIVE_WINDUP && t < DIVE_STRIKE_END + 4;
    }

    private void startSkill(int skill, String anim) {
        this.entityData.set(SKILL, skill);
        this.entityData.set(SKILL_TICK, 0);
        this.getNavigation().stop();
        this.triggerAnim("skill", anim);
        LOGGER.debug("Void Ray {} starts skill {} ({})", this.getId(), skill, anim);
    }

    private void endSkill() {
        this.entityData.set(SKILL, SKILL_NONE);
        this.entityData.set(SKILL_TICK, 0);
        // 技能间隔，第二阶段缩短
        this.globalCooldown = this.scaleCooldown(20);
    }

    // 第二阶段冷却乘 0.75
    private int scaleCooldown(int ticks) {
        if (this.isEnraged()) {
            return Math.round(ticks * 0.75F);
        }
        return ticks;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.clientEffects();
            return;
        }
        // 冷却计时
        if (this.diveCooldown > 0) {
            this.diveCooldown--;
        }
        if (this.beamCooldown > 0) {
            this.beamCooldown--;
        }
        if (this.vortexCooldown > 0) {
            this.vortexCooldown--;
        }
        if (this.starCooldown > 0) {
            this.starCooldown--;
        }
        if (this.globalCooldown > 0) {
            this.globalCooldown--;
        }
    }

    // 客户端：俯冲拖尾、射线蓄力火花、过热烟雾、星陨背部火花
    private void clientEffects() {
        if (this.isDiveStriking()) {
            this.trail.addFirst(this.position());
            while (this.trail.size() > 12) {
                this.trail.removeLast();
            }
        } else if (!this.trail.isEmpty()) {
            this.trail.removeLast();
        }
        int skill = this.getSkill();
        int t = this.getSkillTick();
        // 射线蓄力：火花从周围吸入核心
        if (skill == SKILL_BEAM && t < BEAM_CHARGE && this.random.nextInt(2) == 0) {
            double a = this.random.nextDouble() * Math.PI * 2.0D;
            double r = 1.4D + this.random.nextDouble() * 0.6D;
            double ox = Math.cos(a) * r;
            double oz = Math.sin(a) * r;
            double oy = (this.random.nextDouble() - 0.5D) * 0.8D;
            this.level().addParticle(ModParticles.RIFT_SPARK.get(), this.getX() + ox, this.getY() + CORE_HEIGHT + oy, this.getZ() + oz,
                -ox * 0.15D, -oy * 0.15D, -oz * 0.15D);
        }
        // 射线过热：核心冒烟
        if (skill == SKILL_BEAM && t >= BEAM_STOP && this.random.nextInt(2) == 0) {
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, this.getX(), this.getY() + CORE_HEIGHT - 0.2D, this.getZ(),
                0.0D, -0.03D, 0.0D);
        }
        // 星陨蓄力：背部裂缝溢出火花
        if (skill == SKILL_STARFALL && t > 10 && t < STAR_LAUNCH + 4) {
            this.level().addParticle(ModParticles.RIFT_SPARK.get(), this.getX() + (this.random.nextDouble() - 0.5D) * 0.8D, this.getY() + 1.3D,
                this.getZ() + (this.random.nextDouble() - 0.5D) * 0.8D, 0.0D, 0.25D, 0.0D);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // 首次低于 50% 进入第二阶段
        if (!this.isEnraged() && this.getHealth() < this.getMaxHealth() * 0.5F) {
            this.entityData.set(ENRAGED, true);
            this.starCooldown = 0;
            LOGGER.debug("Void Ray {} enters phase 2", this.getId());
        }
        // 失去目标 5 秒后缓慢回血
        if (this.getTarget() == null) {
            this.idleTicks++;
            if (this.idleTicks > 100 && this.tickCount % 10 == 0 && this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0F);
            }
        } else {
            this.idleTicks = 0;
        }
    }

    // 朝向目标，限速转身
    void faceTarget(LivingEntity target, float maxTurn) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float turned = Mth.approachDegrees(this.getYRot(), yaw, maxTurn);
        this.setYRot(turned);
        this.yBodyRot = turned;
        this.yHeadRot = turned;
    }

    // 地面高度：向下最多探测 24 格，找不到返回 null
    Vec3 groundBelow(Vec3 from) {
        Vec3 to = from.subtract(0.0D, 24.0D, 0.0D);
        BlockHitResult hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        return hit.getLocation();
    }

    // 技能逐 tick 推进
    void tickSkill(LivingEntity target) {
        int skill = this.getSkill();
        int t = this.getSkillTick() + 1;
        this.entityData.set(SKILL_TICK, t);
        if (skill == SKILL_DIVE) {
            this.tickDive(target, t);
        } else if (skill == SKILL_BEAM) {
            this.tickBeam(target, t);
        } else if (skill == SKILL_VORTEX) {
            this.tickVortex(target, t);
        } else if (skill == SKILL_STARFALL) {
            this.tickStarfall(target, t);
        }
    }

    // 俯冲穿刺：前摇锁定路径，二次贝塞尔俯冲，收招拉升
    private void tickDive(LivingEntity target, int t) {
        if (t < DIVE_WINDUP) {
            this.faceTarget(target, 12.0F);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.6D).add(0.0D, 0.015D, 0.0D));
        }
        if (t == 1) {
            // 预警线：从目标脚下沿俯冲方向延伸
            Vec3 dir = target.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
            if (dir.lengthSqr() < 1.0E-3D) {
                dir = this.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
            }
            dir = dir.normalize();
            Vec3 ground = this.groundBelow(target.position().add(0.0D, 0.5D, 0.0D));
            if (ground == null) {
                ground = target.position();
            }
            Vec3 lineStart = ground.subtract(dir.scale(3.0D));
            float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
            VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_WARN_LINE, lineStart, yaw, 10.0F, 0, DIVE_WINDUP + 2, this);
            this.diveFrom = null;
            this.diveLow = ground.add(0.0D, 0.6D, 0.0D);
            this.diveTo = ground.add(dir.scale(8.0D)).add(0.0D, 5.0D, 0.0D);
            this.diveHits.clear();
            this.playSound(SoundEvents.PHANTOM_SWOOP, 1.6F, 0.6F);
        }
        if (t == DIVE_WINDUP) {
            // 前摇结束：路径锁定
            this.diveFrom = this.position();
            this.playSound(SoundEvents.PHANTOM_FLAP, 2.0F, 0.5F);
        }
        if (t >= DIVE_WINDUP && t <= DIVE_STRIKE_END && this.diveFrom != null) {
            float p = (float) (t - DIVE_WINDUP + 1) / (DIVE_STRIKE_END - DIVE_WINDUP + 1);
            Vec3 next = bezier(this.diveFrom, this.diveLow, this.diveTo, p);
            Vec3 motion = next.subtract(this.position());
            this.setDeltaMovement(motion);
            if (motion.horizontalDistanceSqr() > 1.0E-4D) {
                float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F;
                this.setYRot(yaw);
                this.yBodyRot = yaw;
                this.yHeadRot = yaw;
            }
            this.diveHitCheck();
            // 最低点冲击环
            if (t == DIVE_WINDUP + 6) {
                VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_SHOCK, this.diveLow.subtract(0.0D, 0.55D, 0.0D), 0.0F, 4.0F, 0, 12, this);
                this.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 1.4F, 0.6F);
            }
        }
        if (t == DIVE_STRIKE_END + 1) {
            this.triggerAnim("skill", "dive_end");
        }
        if (t > DIVE_STRIKE_END) {
            // 收招：减速滑行并缓慢拉升，这是反击窗口
            this.setDeltaMovement(this.getDeltaMovement().scale(0.8D).add(0.0D, 0.03D, 0.0D));
        }
        if (t >= DIVE_RECOVER_END) {
            this.diveCooldown = this.scaleCooldown(120);
            this.endSkill();
        }
    }

    private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, float p) {
        float q = 1.0F - p;
        return a.scale(q * q).add(b.scale(2.0F * q * p)).add(c.scale(p * p));
    }

    // 俯冲判定：沿路径半径 1.5，同一目标只命中一次
    private void diveHitCheck() {
        AABB area = this.getBoundingBox().inflate(1.5D);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (victim == this || victim instanceof VoidRayEntity || victim instanceof StarDevourerEntity || !victim.isAlive() || this.diveHits.contains(victim.getId())) {
                continue;
            }
            this.diveHits.add(victim.getId());
            if (victim.hurt(this.damageSources().mobAttack(this), CombatConfigs.VOID_RAY.damage("diveDamage"))) {
                // 只向上击飞，避免把玩家推下虚空
                victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.2D, 0.0D, 0.2D).add(0.0D, 0.6D, 0.0D));
                victim.hurtMarked = true;
                LOGGER.debug("Void Ray {} dive hit {}", this.getId(), victim.getName().getString());
            }
        }
    }

    // 虚空射线：蓄力后限速追踪，被方块阻挡，收招过热
    private void tickBeam(LivingEntity target, int t) {
        this.faceTarget(target, 6.0F);
        this.setDeltaMovement(this.getDeltaMovement().scale(0.7D));
        Vec3 core = this.position().add(0.0D, CORE_HEIGHT, 0.0D);
        if (t == 1) {
            // 初始瞄准点：目标前方偏移，给玩家反应空间
            Vec3 start = target.position().add(target.getDeltaMovement().scale(-6.0D));
            this.entityData.set(BEAM_POINT, start.toVector3f());
            this.playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.6F);
        }
        if (t == BEAM_CHARGE) {
            this.playSound(SoundEvents.GUARDIAN_ATTACK, 2.0F, 0.6F);
        }
        if (t >= BEAM_CHARGE && t < BEAM_STOP) {
            // 瞄准方向每 tick 最多转 4°
            Vec3 current = this.getBeamPoint().subtract(core).normalize();
            Vec3 wanted = target.getBoundingBox().getCenter().subtract(core).normalize();
            double cos = Mth.clamp(current.dot(wanted), -1.0D, 1.0D);
            double angle = Math.acos(cos);
            double maxStep = 4.0D * Mth.DEG_TO_RAD;
            Vec3 dir = wanted;
            if (angle > maxStep) {
                double k = maxStep / angle;
                dir = current.scale(1.0D - k).add(wanted.scale(k)).normalize();
            }
            Vec3 far = core.add(dir.scale(28.0D));
            BlockHitResult block = this.level().clip(new ClipContext(core, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 end = far;
            if (block.getType() != HitResult.Type.MISS) {
                end = block.getLocation();
            }
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(this.level(), this, core, end,
                new AABB(core, end).inflate(1.0D), e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof VoidRayEntity), 0.3F);
            if (entityHit != null) {
                end = entityHit.getLocation();
                // 每 0.5 秒结算一次，举盾减半
                if ((t - BEAM_CHARGE) % 10 == 0 && entityHit.getEntity() instanceof LivingEntity victim) {
                    float damage = CombatConfigs.VOID_RAY.damage("beamDamage");
                    if (victim.isBlocking()) {
                        damage = CombatConfigs.VOID_RAY.damage("beamBlockedDamage");
                    }
                    victim.hurt(this.damageSources().indirectMagic(this, this), damage);
                }
            }
            this.entityData.set(BEAM_POINT, end.toVector3f());
            // 落点灼烧火花
            if (t % 3 == 0 && this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ModParticles.RIFT_SPARK.get(), end.x, end.y, end.z, 2, 0.15D, 0.15D, 0.15D, 0.06D);
            }
        }
        if (t == BEAM_STOP) {
            this.triggerAnim("skill", "beam_end");
            this.playSound(SoundEvents.BEACON_DEACTIVATE, 2.0F, 1.4F);
        }
        if (t >= BEAM_END_TICK) {
            this.beamCooldown = this.scaleCooldown(180);
            this.endSkill();
        }
    }

    // 引力漩涡：在目标脚下生成符文圈，由特效实体负责吸附与伤害
    private void tickVortex(LivingEntity target, int t) {
        this.faceTarget(target, 10.0F);
        this.setDeltaMovement(this.getDeltaMovement().scale(0.7D));
        if (t == 8) {
            Vec3 center = this.findVortexCenter(target);
            if (center != null) {
                VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_VORTEX, center, 0.0F, 3.5F, 0, VoidRayVfxEntity.VORTEX_LIFE, this, CombatConfigs.VOID_RAY.damage("vortexDamage"), -1.0F);
                this.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 2.0F, 0.6F);
                // 漩涡结束时俯冲刚好可用
                this.diveCooldown = Math.min(this.diveCooldown, VoidRayVfxEntity.VORTEX_LIFE - 20);
            } else {
                LOGGER.debug("Void Ray {} vortex cancelled: no safe ground", this.getId());
            }
        }
        if (t >= 28) {
            this.vortexCooldown = this.scaleCooldown(240);
            this.endSkill();
        }
    }

    // 安全落点：圆心与半径 3 的八个方向下方都必须有实心地面
    Vec3 findVortexCenter(LivingEntity target) {
        Vec3 ground = this.groundBelow(target.position().add(0.0D, 0.5D, 0.0D));
        if (ground == null) {
            return null;
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0D;
            Vec3 probe = ground.add(Math.cos(a) * 3.0D, 1.0D, Math.sin(a) * 3.0D);
            Vec3 below = this.groundBelow(probe);
            if (below == null || ground.y - below.y > 6.0D) {
                return null;
            }
        }
        return ground;
    }

    // 星陨：背部裂开后，陨星依次砸向目标周围的预警点
    private void tickStarfall(LivingEntity target, int t) {
        this.faceTarget(target, 8.0F);
        this.setDeltaMovement(this.getDeltaMovement().scale(0.6D));
        if (t == 2) {
            this.playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0F, 0.6F);
        }
        if (t == STAR_LAUNCH) {
            int count = 5;
            if (this.isEnraged()) {
                count = 7;
            }
            int placed = 0;
            for (int i = 0; i < count; i++) {
                Vec3 spot;
                if (i == 0) {
                    spot = target.position();
                } else {
                    double a = this.random.nextDouble() * Math.PI * 2.0D;
                    double r = 2.5D + this.random.nextDouble() * 3.5D;
                    spot = target.position().add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
                }
                Vec3 ground = this.groundBelow(spot.add(0.0D, 3.0D, 0.0D));
                if (ground == null) {
                    continue;
                }
                VoidRayVfxEntity.spawn(this.level(), VoidRayVfxEntity.KIND_STAR, ground, 0.0F, 2.0F, placed * 4, VoidRayVfxEntity.STAR_LIFE + placed * 4, this, CombatConfigs.VOID_RAY.damage("starfallDamage"), -1.0F);
                placed++;
            }
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ModParticles.RIFT_SHARD.get(), this.getX(), this.getY() + 1.2D, this.getZ(), 14, 0.5D, 0.2D, 0.5D, 0.25D);
            }
            this.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 2.0F, 0.5F);
            LOGGER.debug("Void Ray {} starfall placed {} markers", this.getId(), placed);
        }
        if (t >= STAR_END) {
            this.starCooldown = 400;
            this.endSkill();
        }
    }

    // 选择技能：第二阶段优先星陨
    boolean tryStartSkill(LivingEntity target) {
        if (this.globalCooldown > 0) {
            return false;
        }
        double dist = this.distanceTo(target);
        double hDist = Math.sqrt(this.distanceToSqr(target.getX(), this.getY(), target.getZ()));
        boolean sight = this.hasLineOfSight(target);
        // 护卫只使用俯冲
        if (this.minion) {
            if (this.diveCooldown <= 0 && sight && hDist > 5.0D && hDist < 22.0D) {
                this.startSkill(SKILL_DIVE, "dive");
                return true;
            }
            return false;
        }
        if (this.isEnraged() && this.starCooldown <= 0 && dist < 24.0D) {
            this.startSkill(SKILL_STARFALL, "starfall");
            return true;
        }
        if (this.diveCooldown <= 0 && sight && hDist > 6.0D && hDist < 20.0D) {
            this.startSkill(SKILL_DIVE, "dive");
            return true;
        }
        if (this.beamCooldown <= 0 && sight && dist > 8.0D && dist < 28.0D) {
            this.startSkill(SKILL_BEAM, "beam");
            return true;
        }
        if (this.vortexCooldown <= 0 && target.onGround() && dist < 24.0D && this.findVortexCenter(target) != null) {
            this.startSkill(SKILL_VORTEX, "vortex");
            return true;
        }
        return false;
    }

    // 目标失效时中止技能并停止技能动画
    void abortSkill() {
        if (this.isCasting()) {
            LOGGER.debug("Void Ray {} aborts skill {}", this.getId(), this.getSkill());
            this.entityData.set(SKILL, SKILL_NONE);
            this.entityData.set(SKILL_TICK, 0);
            this.stopTriggeredAnim("skill", null);
        }
    }

    // 巡航高度：第二阶段降低 2 格
    double cruiseHeight() {
        if (this.isEnraged()) {
            return 6.0D;
        }
        return 8.0D;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float finalAmount = amount;
        // 弱点：来自下方的弹射物打中腹部核心
        Entity direct = source.getDirectEntity();
        if (source.is(DamageTypeTags.IS_PROJECTILE) && direct != null && direct.getY() < this.getY() + 0.4D) {
            finalAmount = finalAmount * 1.5F;
        }
        // 射线过热窗口
        if (this.getSkill() == SKILL_BEAM && this.getSkillTick() >= BEAM_STOP) {
            finalAmount = finalAmount * 1.3F;
        }
        boolean result = super.hurt(source, finalAmount);
        if (result && !this.level().isClientSide() && source.getEntity() instanceof Player player) {
            LOGGER.debug("Void Ray {} was damaged by player {}; retaliation target is available", this.getId(), player.getGameProfile().getName());
        }
        if (result && !this.level().isClientSide() && !this.isCasting() && this.isAlive()) {
            this.triggerAnim("skill", "hurt");
        }
        return result;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.abortSkill();
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        // 死亡动画 1.5 秒后碎裂消失
        if (this.deathTime >= 30 && !this.level().isClientSide() && !this.isRemoved()) {
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ModParticles.RIFT_SHARD.get(), this.getX(), this.getY() + 0.6D, this.getZ(), 30, 1.4D, 0.4D, 1.4D, 0.2D);
                serverLevel.sendParticles(ModParticles.RIFT_SPARK.get(), this.getX(), this.getY() + CORE_HEIGHT, this.getZ(), 16, 0.4D, 0.4D, 0.4D, 0.15D);
            }
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
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
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.45F + this.random.nextFloat() * 0.1F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Enraged", this.isEnraged());
        tag.putBoolean("Minion", this.minion);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(ENRAGED, tag.getBoolean("Enraged"));
        this.minion = tag.getBoolean("Minion");
        if (this.minion) {
            this.xpReward = 0;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 基础控制器：死亡、滑翔、悬停拍翼
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
            .triggerableAnim("dive_end", DIVE_END)
            .triggerableAnim("beam", BEAM)
            .triggerableAnim("beam_end", BEAM_END)
            .triggerableAnim("vortex", VORTEX)
            .triggerableAnim("starfall", STARFALL)
            .triggerableAnim("hurt", HURT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    // 飞行移动：向目标点加速，技能期间由技能直接控制速度
    static final class VoidRayMoveControl extends MoveControl {
        private final VoidRayEntity ray;

        VoidRayMoveControl(VoidRayEntity ray) {
            super(ray);
            this.ray = ray;
        }

        @Override
        public void tick() {
            if (this.ray.isCasting() || this.operation != Operation.MOVE_TO) {
                return;
            }
            Vec3 delta = new Vec3(this.wantedX - this.ray.getX(), this.wantedY - this.ray.getY(), this.wantedZ - this.ray.getZ());
            double dist = delta.length();
            if (dist < 1.0D) {
                this.operation = Operation.WAIT;
                this.ray.setDeltaMovement(this.ray.getDeltaMovement().scale(0.6D));
                return;
            }
            double speed = this.speedModifier * this.ray.getAttributeValue(Attributes.FLYING_SPEED) * 0.08D;
            this.ray.setDeltaMovement(this.ray.getDeltaMovement().add(delta.scale(speed / dist)));
            // 没有目标时朝向移动方向
            if (this.ray.getTarget() == null) {
                Vec3 motion = this.ray.getDeltaMovement();
                if (motion.horizontalDistanceSqr() > 1.0E-3D) {
                    float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F;
                    float turned = Mth.approachDegrees(this.ray.getYRot(), yaw, 8.0F);
                    this.ray.setYRot(turned);
                    this.ray.yBodyRot = turned;
                    this.ray.yHeadRot = turned;
                }
            }
        }
    }

    // 战斗：绕目标盘旋并释放技能
    static final class VoidRayCombatGoal extends Goal {
        private final VoidRayEntity ray;
        private float orbitAngle;

        VoidRayCombatGoal(VoidRayEntity ray) {
            this.ray = ray;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.ray.getTarget();
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
            this.ray.setAggressive(true);
            LivingEntity target = this.ray.getTarget();
            if (target != null) {
                this.orbitAngle = (float) Mth.atan2(this.ray.getZ() - target.getZ(), this.ray.getX() - target.getX());
            }
        }

        @Override
        public void stop() {
            this.ray.setAggressive(false);
            this.ray.abortSkill();
        }

        @Override
        public void tick() {
            LivingEntity target = this.ray.getTarget();
            if (target == null) {
                return;
            }
            if (this.ray.isCasting()) {
                this.ray.tickSkill(target);
                return;
            }
            this.ray.faceTarget(target, 10.0F);
            if (this.ray.tryStartSkill(target)) {
                return;
            }
            // 盘旋：半径 10，高度保持在目标上方
            this.orbitAngle += 0.025F;
            double x = target.getX() + Math.cos(this.orbitAngle) * 10.0D;
            double z = target.getZ() + Math.sin(this.orbitAngle) * 10.0D;
            double y = target.getY() + this.ray.cruiseHeight();
            this.ray.getMoveControl().setWantedPosition(x, y, z, 1.0D);
        }
    }

    // 游荡：在附近高空随机选点，下方是虚空时保持当前高度
    static final class VoidRayWanderGoal extends Goal {
        private final VoidRayEntity ray;

        VoidRayWanderGoal(VoidRayEntity ray) {
            this.ray = ray;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.ray.getTarget() == null && !this.ray.getMoveControl().hasWanted() && this.ray.getRandom().nextInt(20) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            double x = this.ray.getX() + (this.ray.getRandom().nextDouble() - 0.5D) * 24.0D;
            double z = this.ray.getZ() + (this.ray.getRandom().nextDouble() - 0.5D) * 24.0D;
            int ground = this.ray.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            double y = this.ray.getY();
            if (ground > this.ray.level().getMinBuildHeight() + 1) {
                y = ground + 8.0D + this.ray.getRandom().nextDouble() * 6.0D;
            }
            this.ray.getMoveControl().setWantedPosition(x, y, z, 0.6D);
        }
    }
}
