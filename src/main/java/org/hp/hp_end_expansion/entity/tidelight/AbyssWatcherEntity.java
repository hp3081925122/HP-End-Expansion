package org.hp.hp_end_expansion.entity.tidelight;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * 深渊守望者：潮光礁海的漂浮精英怪。按 Boss 的结构设计（读招、阶段、破绽、终结技），数值保持精英级。
 * 技能时间轴与 animations/abyss_watcher.animation.json 一一对应（1 tick = 0.05 秒）。
 */
public final class AbyssWatcherEntity extends Monster implements GeoEntity {
    public static final byte NONE = 0, BITE = 1, SWEEP = 2, VOLLEY = 3, GAZE = 4, MAELSTROM = 5, ROAR = 6, STUN = 7;
    private static final int[] DURATION = {0, 40, 40, 50, 80, 130, 40, 60};
    private static final String[] ANIM = {"", "bite", "sweep", "volley", "gaze", "maelstrom", "roar", "stun"};

    // ① 深渊冲咬
    public static final int BITE_DASH = 14, BITE_SNAP = 22;
    private static final float BITE_DAMAGE = 10;
    // ② 鳍刃回旋
    public static final int SWEEP_START = 18, SWEEP_END = 26;
    private static final double SWEEP_RADIUS = 5.5;
    private static final float SWEEP_DAMAGE = 8;
    // ③ 晶脊齐射：第 20/24/28 tick 各射出两枚晶核，晶刺的飞行、预警和伤害由 AbyssVfxEntity 处理
    private static final int[] VOLLEY_MARKS = {20, 24, 28};
    private static final int SPIKE_DELAY = AbyssVfxEntity.SPIKE_ERUPT;
    // ④ 深渊凝视
    public static final int GAZE_FIRE = 30, GAZE_END = 70;
    public static final double GAZE_LENGTH = 20;
    private static final float GAZE_DAMAGE = 3, GAZE_TURN = 2.2F;
    // ⑤ 渊潮漩涡（终结技）
    public static final int MAEL_RISE = 30, MAEL_DIVE = 90, MAEL_SLAM = 104;
    private static final double MAEL_PULL_RADIUS = 10, MAEL_SLAM_RADIUS = 4.5;
    private static final float MAEL_SLAM_DAMAGE = 14, MAEL_INTERRUPT = 24;
    // ⑥ 咆哮：转二阶段
    public static final int ROAR_BLAST = 8;

    private static final int BITE_CD = 100, SWEEP_CD = 140, VOLLEY_CD = 160, GAZE_CD = 260, MAEL_CD = 800, GLOBAL_CD = 24;
    private static final double HOVER = 2.6, TRACK_RANGE = 32;

    private static final EntityDataAccessor<Byte> SKILL = SynchedEntityData.defineId(AbyssWatcherEntity.class, EntityDataSerializers.BYTE);
    // 每次起技能 +1，同一个技能连放两次（连咬）时客户端也能重置动画和特效计时
    private static final EntityDataAccessor<Integer> SKILL_SEQ = SynchedEntityData.defineId(AbyssWatcherEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(AbyssWatcherEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> BEAM_YAW = SynchedEntityData.defineId(AbyssWatcherEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_PITCH = SynchedEntityData.defineId(AbyssWatcherEntity.class, EntityDataSerializers.FLOAT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.abyss_watcher.idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop("animation.abyss_watcher.move");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.abyss_watcher.death");
    private static final RawAnimation[] SKILL_ANIMS = new RawAnimation[ANIM.length];
    static {
        for (int i = 1; i < ANIM.length; i++) SKILL_ANIMS[i] = RawAnimation.begin().thenPlay("animation.abyss_watcher." + ANIM[i]);
    }

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int skillTicks, globalCd, biteCd, sweepCd, volleyCd, gazeCd, maelCd;
    private float skillYaw;
    private Vec3 biteDirection = new Vec3(0, 0, 1);
    private boolean biteChained, biteHit;
    private final java.util.Set<Integer> sweepHit = new java.util.HashSet<>();
    private Vec3 maelCenter = Vec3.ZERO;
    private float maelDamage;
    private int clientSkillStart, animResetSeen = -1;

    public AbyssWatcherEntity(EntityType<? extends AbyssWatcherEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        xpReward = 60;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 160.0)
            .add(Attributes.ARMOR, 8.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
            .add(Attributes.MOVEMENT_SPEED, 0.2)
            .add(Attributes.FLYING_SPEED, 0.2)
            .add(Attributes.FOLLOW_RANGE, TRACK_RANGE)
            .add(Attributes.ATTACK_DAMAGE, BITE_DAMAGE);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKILL, NONE);
        builder.define(SKILL_SEQ, 0);
        builder.define(ENRAGED, false);
        builder.define(BEAM_YAW, 0F);
        builder.define(BEAM_PITCH, 0F);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SKILL.equals(key) || SKILL_SEQ.equals(key)) clientSkillStart = tickCount;
    }

    public byte getSkill() { return entityData.get(SKILL); }
    public boolean isEnraged() { return entityData.get(ENRAGED); }
    public float getSkillAge(float partialTick) { return tickCount - clientSkillStart + partialTick; }
    public Vec3 beamDirection() { return Vec3.directionFromRotation(entityData.get(BEAM_PITCH), entityData.get(BEAM_YAW)); }

    public static boolean canHit(Entity e) {
        return e instanceof LivingEntity l && l.isAlive() && !(e instanceof AbyssWatcherEntity)
            && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---------------- 受伤与阶段 ----------------

    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof AbyssWatcherEntity) return false;
        byte skill = getSkill();
        if (skill == MAELSTROM && skillTicks < MAEL_DIVE) amount *= 0.6F;   // 升空盘旋时减伤
        if (skill == STUN) amount *= 1.3F;                                     // 破绽：被打断后受到的伤害提高
        boolean hit = super.hurt(source, amount);
        if (!hit || level().isClientSide || !isAlive()) return hit;
        if (skill == MAELSTROM && skillTicks >= MAEL_RISE && skillTicks < MAEL_DIVE) {
            maelDamage += amount;
            if (maelDamage >= MAEL_INTERRUPT) {
                playSound(SoundEvents.SHIELD_BREAK, 2F, 0.5F);
                startSkill(STUN);
            }
        }
        return hit;
    }

    // ---------------- 主循环 ----------------

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (biteCd > 0) biteCd--;
        if (sweepCd > 0) sweepCd--;
        if (volleyCd > 0) volleyCd--;
        if (gazeCd > 0) gazeCd--;
        if (maelCd > 0) maelCd--;

        LivingEntity target = getTarget();
        if (target != null && (!canHit(target) || distanceToSqr(target) > TRACK_RANGE * TRACK_RANGE)) { setTarget(null); target = null; }

        if (!isEnraged() && getHealth() < getMaxHealth() * 0.5F && getSkill() == NONE) {
            entityData.set(ENRAGED, true);
            maelCd = 0;
            startSkill(ROAR);
        }
        if (getSkill() != NONE) { tickSkill(target); return; }
        if (globalCd > 0) globalCd--;

        if (target == null) { hover(position().add(Math.sin(tickCount * 0.01) * 0.5, 0, Math.cos(tickCount * 0.01) * 0.5), 0.03); return; }
        faceTowards(target, 6);
        if (globalCd <= 0 && level().getDifficulty() != Difficulty.PEACEFUL && chooseSkill(target)) return;
        // 平时在目标身边 6-9 格处盘旋，保持悬浮高度
        Vec3 away = horizontalAway(position(), target.position());
        double dist = horizontalDist(target);
        double want = dist > 9 ? 6 : dist < 5 ? 8 : dist;
        Vec3 orbit = rotateY(away, 0.25).scale(want);
        hover(target.position().add(orbit), isEnraged() ? 0.07 : 0.055);
    }

    private boolean chooseSkill(LivingEntity target) {
        double d = horizontalDist(target);
        boolean sight = hasLineOfSight(target);
        double attackDistance = target.getEyePosition().distanceTo(mouth());
        boolean sweepHeight = target.getBoundingBox().maxY >= getY() - 2
                && target.getBoundingBox().minY <= getY() + 5;
        if (isEnraged() && maelCd <= 0 && d <= 16) { startSkill(MAELSTROM); return true; }
        if (d <= 5.5 && sweepHeight && sweepCd <= 0) { startSkill(SWEEP); return true; }
        if (isEnraged() && gazeCd <= 0 && sight && attackDistance <= 18) { startSkill(GAZE); return true; }
        if (attackDistance <= 10 && biteCd <= 0 && sight) { startSkill(BITE); return true; }
        if (attackDistance <= 22 && volleyCd <= 0 && sight) { startSkill(VOLLEY); return true; }
        return false;
    }

    private void startSkill(byte skill) {
        entityData.set(SKILL, skill);
        entityData.set(SKILL_SEQ, entityData.get(SKILL_SEQ) + 1);
        skillTicks = 0;
        LivingEntity t = getTarget();
        skillYaw = t != null ? yawTo(t) : getYRot();
        if (skill == BITE) biteDirection = t != null ? t.getEyePosition().subtract(mouth()).normalize() : forward();
        if (Boolean.getBoolean("hp_end_expansion.debugAbyssHeading") && t != null) {
            com.mojang.logging.LogUtils.getLogger().info("Abyss watcher attack: skill={}, mouthY={}, targetY={}, biteDirection={}", skill, mouth().y, t.getEyeY(), biteDirection);
        }
        float cdScale = isEnraged() ? 0.7F : 1F;
        switch (skill) {
            case BITE -> { biteCd = (int) (BITE_CD * cdScale); biteHit = false; playSound(SoundEvents.ELDER_GUARDIAN_AMBIENT, 1.4F, 1.3F); }
            case SWEEP -> { sweepCd = (int) (SWEEP_CD * cdScale); sweepHit.clear(); }
            case VOLLEY -> { volleyCd = (int) (VOLLEY_CD * cdScale); playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2F, 0.7F); }
            case GAZE -> {
                gazeCd = (int) (GAZE_CD * cdScale);
                if (t != null) aimBeamAt(t.getEyePosition(), 180);
                playSound(SoundEvents.BEACON_ACTIVATE, 2F, 1.6F);
            }
            case MAELSTROM -> {
                maelCd = MAEL_CD;
                maelDamage = 0;
                Vec3 c = t != null ? t.position() : position();
                maelCenter = new Vec3(c.x, groundY(c), c.z);
                playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 2F, 0.6F);
            }
            case ROAR -> playSound(SoundEvents.ELDER_GUARDIAN_HURT, 3F, 0.5F);
            case STUN -> setDeltaMovement(Vec3.ZERO);
            default -> {}
        }
    }

    private void endSkill() {
        byte skill = getSkill();
        // 二阶段的冲咬会接一次连咬
        if (skill == BITE && isEnraged() && !biteChained && getTarget() != null) {
            biteChained = true;
            startSkill(BITE);
            biteCd = (int) (BITE_CD * 0.7F);
            return;
        }
        biteChained = false;
        entityData.set(SKILL, NONE);
        skillTicks = 0;
        globalCd = isEnraged() ? GLOBAL_CD / 2 : GLOBAL_CD;
    }

    private void tickSkill(@Nullable LivingEntity target) {
        skillTicks++;
        byte skill = getSkill();
        int trackUntil = switch (skill) { case BITE -> BITE_DASH - 2; case SWEEP -> 14; case VOLLEY -> 30; case GAZE -> GAZE_END; case MAELSTROM -> MAEL_DIVE; default -> 0; };
        if (target != null && skillTicks <= trackUntil && skill != GAZE) skillYaw = Mth.approachDegrees(skillYaw, yawTo(target), 8);
        if (skill == BITE && target != null && skillTicks <= trackUntil) biteDirection = target.getEyePosition().subtract(mouth()).normalize();
        if (skill == GAZE && target != null) skillYaw = Mth.approachDegrees(skillYaw, yawTo(target), GAZE_TURN);
        setYRot(skillYaw);
        yBodyRot = skillYaw;
        yHeadRot = skillYaw;
        switch (skill) {
            case BITE -> tickBite();
            case SWEEP -> tickSweep();
            case VOLLEY -> tickVolley(target);
            case GAZE -> tickGaze(target);
            case MAELSTROM -> tickMaelstrom();
            case ROAR -> tickRoar();
            default -> setDeltaMovement(getDeltaMovement().scale(0.8));
        }
        if (getSkill() == skill && skillTicks >= DURATION[skill]) endSkill();
    }

    // ---------------- ① 深渊冲咬 ----------------
    // 0-14 后仰张嘴、眼睛亮起（读招），14-22 沿锁定方向冲刺约 7 格，第 22 tick 合嘴结算，之后僵直 18 tick
    private void tickBite() {
        if (skillTicks < BITE_DASH) {
            setDeltaMovement(forward().scale(-0.06).add(0, holdHeight() * 0.1, 0));
            return;
        }
        if (skillTicks <= BITE_SNAP) {
            setDeltaMovement(biteDirection.scale(0.95));
            if (!biteHit) {
                Vec3 m = mouth();
                for (LivingEntity v : near(m, 2.4, 2.4)) {
                    if (v.hurt(damageSources().mobAttack(this), BITE_DAMAGE)) {
                        Vec3 f = forward();
                        v.knockback(1.0, -f.x, -f.z);
                        biteHit = true;
                    }
                }
                if (skillTicks == BITE_SNAP || biteHit) {
                    // 合嘴：嘴前爆闪 + 一圈冲击环
                    playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.6F, 0.6F);
                    if (level() instanceof ServerLevel s) {
                        AbyssVfxEntity.spawn(s, this, AbyssVfxEntity.BURST, m, 1.3F, m);
                        AbyssVfxEntity.spawn(s, this, AbyssVfxEntity.SHOCKWAVE, m.add(0, -0.6, 0), 2.6F, m);
                    }
                    biteHit = true;
                }
            }
            return;
        }
        setDeltaMovement(getDeltaMovement().scale(0.7));
    }

    // ---------------- ② 鳍刃回旋 ----------------
    // 0-16 侧身收鳍、晶脉发亮，18-26 整体回旋一圈：半径 5.5 内 8 伤害并向外击飞
    private void tickSweep() {
        setDeltaMovement(getDeltaMovement().scale(0.6).add(0, holdHeight() * 0.1, 0));
        if (skillTicks < SWEEP_START || skillTicks > SWEEP_END) return;
        if (skillTicks == SWEEP_START) playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2F, 0.5F);
        for (LivingEntity v : near(position().add(0, 1.5, 0), SWEEP_RADIUS, 3.5)) {
            if (!sweepHit.add(v.getId())) continue;
            if (v.hurt(damageSources().mobAttack(this), SWEEP_DAMAGE)) {
                Vec3 out = horizontalAway(v.position(), position());
                v.knockback(1.5, -out.x, -out.z);
                v.setDeltaMovement(v.getDeltaMovement().add(0, 0.35, 0));
                v.hurtMarked = true;
            }
        }
    }

    // ---------------- ③ 晶脊齐射 ----------------
    // 0-20 拱背、背晶充能；第 20/24/28 tick 各射出两枚晶核，落点地面先亮 16 tick 预警圈，随后晶刺破土 6 伤害并顶起
    private void tickVolley(@Nullable LivingEntity target) {
        setDeltaMovement(getDeltaMovement().scale(0.7).add(0, holdHeight() * 0.1, 0));
        if (!(level() instanceof ServerLevel s)) return;
        for (int k = 0; k < VOLLEY_MARKS.length; k++) {
            if (skillTicks != VOLLEY_MARKS[k] || target == null) continue;
            playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 2F, 0.6F + k * 0.2F);
            for (int j = 0; j < 2; j++) {
                // 第一枚打目标预判位置，第二枚打目标周围 2.5 格的随机点，逼玩家走位
                Vec3 lead = target.position().add(target.getDeltaMovement().scale(SPIKE_DELAY * 0.6));
                Vec3 at = j == 0 ? lead : target.position().add(rotateY(new Vec3(0, 0, 2.5), random.nextDouble() * Math.PI * 2));
                at = new Vec3(at.x, groundY(at), at.z);
                Vec3 from = local((j == 0 ? -0.6 : 0.6), 4.2, 1.2 - k * 1.4);
                AbyssVfxEntity.spawn(s, this, AbyssVfxEntity.SPIKE, at, 1F, from);
            }
        }
    }

    // ---------------- ④ 深渊凝视（二阶段） ----------------
    // 0-30 眼睛和口中聚光，地面画出瞄准线；30-70 从口中射出 20 格光束，每 tick 最多转 2.2°，横向跑动可以甩开；
    // 光束被方块截断，每 4 tick 3 伤害并附加缓慢
    private void tickGaze(@Nullable LivingEntity target) {
        setDeltaMovement(getDeltaMovement().scale(0.6).add(0, holdHeight() * 0.1, 0));
        if (target != null && skillTicks < GAZE_END) aimBeamAt(target.getEyePosition(), skillTicks < GAZE_FIRE ? 6 : GAZE_TURN);
        if (skillTicks < GAZE_FIRE || skillTicks >= GAZE_END) return;
        Vec3 from = mouth();
        Vec3 dir = beamDirection();
        double len = beamLength(level(), from, dir);
        if (skillTicks == GAZE_FIRE) playSound(SoundEvents.GUARDIAN_ATTACK, 3F, 0.6F);
        Vec3 end = from.add(dir.scale(len));
        if (skillTicks % 4 != 0) return;
        AABB box = new AABB(from, end).inflate(1);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, box, AbyssWatcherEntity::canHit)) {
            if (distanceToSegment(v.getBoundingBox().getCenter(), from, end) > 0.8 + v.getBbWidth() * 0.5) continue;
            v.invulnerableTime = 0;
            if (v.hurt(damageSources().indirectMagic(this, this), GAZE_DAMAGE))
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
        }
    }

    // ---------------- ⑤ 渊潮漩涡（二阶段终结技） ----------------
    // 0-30 升到目标脚下中心点上方 7 格（期间减伤 40%）；30-90 绕中心盘旋，10 格内的生物被拉向中心，每 10 tick 外圈喷出间歇泉；
    // 这段时间累计受到 24 伤害会被打下来眩晕 3 秒（受伤 +30%）；90-104 俯冲，第 104 tick 砸地 14 伤害并推出潮汐水墙
    private void tickMaelstrom() {
        if (!(level() instanceof ServerLevel s)) return;
        if (skillTicks < MAEL_DIVE) {
            double ang = skillTicks * 0.09;
            double r = skillTicks < MAEL_RISE ? 4 * skillTicks / (double) MAEL_RISE : 4;
            Vec3 want = maelCenter.add(Math.cos(ang) * r, 7, Math.sin(ang) * r);
            setDeltaMovement(want.subtract(position()).scale(0.15));
            if (skillTicks >= MAEL_RISE) {
                if (skillTicks == MAEL_RISE) {
                    playSound(SoundEvents.CONDUIT_ACTIVATE, 3F, 0.5F);
                    AbyssVfxEntity.spawn(s, this, AbyssVfxEntity.VORTEX, maelCenter, (float) MAEL_PULL_RADIUS, maelCenter);
                }
                for (LivingEntity v : near(maelCenter, MAEL_PULL_RADIUS, 6)) {
                    Vec3 in = maelCenter.subtract(v.position()).multiply(1, 0, 1);
                    if (in.lengthSqr() < 1) continue;
                    v.setDeltaMovement(v.getDeltaMovement().add(in.normalize().scale(0.055)));
                    v.hurtMarked = true;
                }
                // 漩涡外圈每 10 tick 从空中落下一枚晶核，砸出晶刺丛
                if (skillTicks % 10 == 0) {
                    double a = random.nextDouble() * Math.PI * 2, rr = 4 + random.nextDouble() * 5;
                    Vec3 g = maelCenter.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
                    AbyssVfxEntity.spawn(s, this, AbyssVfxEntity.SPIKE, new Vec3(g.x, groundY(g), g.z), 1F, mouth());
                }
            }
            return;
        }
        if (skillTicks < MAEL_SLAM) {
            Vec3 want = maelCenter.add(0, 1.2, 0);
            setDeltaMovement(want.subtract(position()).scale(0.35));
            skillYaw = Mth.approachDegrees(skillYaw, yawToPos(maelCenter), 20);
            return;
        }
        if (skillTicks == MAEL_SLAM) {
            for (LivingEntity v : near(maelCenter, MAEL_SLAM_RADIUS, 3)) {
                if (v.hurt(damageSources().mobAttack(this), MAEL_SLAM_DAMAGE)) {
                    Vec3 out = horizontalAway(v.position(), maelCenter);
                    v.knockback(1.6, -out.x, -out.z);
                    v.setDeltaMovement(v.getDeltaMovement().add(0, 0.5, 0));
                    v.hurtMarked = true;
                }
            }
            // 外圈冲击波：7 格内 5 伤害（内圈已吃到砸地的不重复结算）
            for (LivingEntity v : near(maelCenter, 7, 3)) {
                if (v.position().subtract(maelCenter).horizontalDistance() <= MAEL_SLAM_RADIUS) continue;
                if (v.hurt(damageSources().mobAttack(this), 5)) {
                    Vec3 out = horizontalAway(v.position(), maelCenter);
                    v.knockback(1.2, -out.x, -out.z);
                    v.hurtMarked = true;
                }
            }
            AbyssVfxEntity.spawn(s, this, AbyssVfxEntity.SHOCKWAVE, maelCenter, 7.5F, maelCenter);
            AbyssVfxEntity.spawn(s, this, AbyssVfxEntity.SHOCKWAVE, maelCenter, 4.5F, maelCenter);
            AbyssVfxEntity.spawn(s, this, AbyssVfxEntity.BURST, maelCenter.add(0, 1.2, 0), 3.2F, maelCenter);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.5F, 0.6F);
        }
        setDeltaMovement(getDeltaMovement().scale(0.6).add(0, holdHeight() * 0.06, 0));
    }

    // ---------------- ⑥ 咆哮（进入二阶段） ----------------
    private void tickRoar() {
        setDeltaMovement(getDeltaMovement().scale(0.6));
        if (skillTicks != ROAR_BLAST || !(level() instanceof ServerLevel s)) return;
        for (LivingEntity v : near(position(), 7, 4)) {
            Vec3 out = horizontalAway(v.position(), position());
            v.knockback(1.4, -out.x, -out.z);
            v.hurtMarked = true;
        }
        AbyssVfxEntity.spawn(s, this, AbyssVfxEntity.SHOCKWAVE, new Vec3(getX(), groundY(position()), getZ()), 7F, position());
    }

    // ---------------- 移动 ----------------

    @Override public void travel(Vec3 input) {
        if (isDeadOrDying()) { setDeltaMovement(getDeltaMovement().add(0, -0.04, 0)); }
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.91));
        calculateEntityAnimation(false);
    }

    /** 朝 at 的水平位置飞，同时保持离地 HOVER 格。 */
    private void hover(Vec3 at, double speed) {
        Vec3 d = at.subtract(position()).multiply(1, 0, 1);
        Vec3 h = d.lengthSqr() > 1 ? d.normalize().scale(speed) : d.scale(speed);
        setDeltaMovement(getDeltaMovement().add(h.x, holdHeight() * 0.04, h.z));
        if (d.horizontalDistanceSqr() > 1.0E-6 && getTarget() == null) {
            float moveYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
            faceTowardsYaw(moveYaw, 4);
            if (Boolean.getBoolean("hp_end_expansion.debugAbyssHeading") && tickCount % 20 == 0) {
                com.mojang.logging.LogUtils.getLogger().info("Abyss watcher heading: id={}, bodyYaw={}, moveYaw={}, velocity={}", getId(), yBodyRot, moveYaw, getDeltaMovement());
            }
        }
    }

    /** 当前高度距悬浮高度的差（正 = 需要上升），限制在 ±1。 */
    private double holdHeight() {
        double want = groundY(position()) + HOVER;
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            want = Math.max(groundY(position()) + 0.15, target.getEyeY() - 3.5);
        }
        return Mth.clamp(want - getY(), -1, 1);
    }

    private double groundY(Vec3 at) {
        BlockPos top = level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
        int y = top.getY();
        // 在洞穴/礁柱下时以脚下向下第一格实心方块为准
        if (y > at.y + 1) {
            HitResult hit = level().clip(new ClipContext(at.add(0, 1, 0), at.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            return hit.getType() == HitResult.Type.MISS ? at.y - 24 : hit.getLocation().y;
        }
        return y;
    }

    private void faceTowards(Entity e, float step) { faceTowardsYaw(yawTo(e), step); }
    private void faceTowardsYaw(float yaw, float step) {
        setYRot(Mth.approachDegrees(getYRot(), yaw, step));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    // ---------------- 工具 ----------------

    private void aimBeamAt(Vec3 at, float maxStep) {
        Vec3 d = at.subtract(mouth());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        entityData.set(BEAM_YAW, Mth.approachDegrees(entityData.get(BEAM_YAW), yaw, maxStep));
        entityData.set(BEAM_PITCH, Mth.approachDegrees(entityData.get(BEAM_PITCH), Mth.clamp(pitch, -89, 89), maxStep));
    }

    public static double beamLength(Level level, Vec3 from, Vec3 dir) {
        HitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(GAZE_LENGTH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? GAZE_LENGTH : hit.getLocation().distanceTo(from);
    }

    /** 嘴前端（模型 vfx_mouth 骨骼的静止位置：前 3.1 格、高 2.6 格，含渲染上抬 0.45）。 */
    public Vec3 mouth() { return local(0, 3.5, 3.1); }

    private Vec3 forward() {
        double y = Math.toRadians(skillYaw);
        return new Vec3(-Math.sin(y), 0, Math.cos(y));
    }

    private Vec3 local(double right, double up, double fwd) {
        double y = Math.toRadians(skillYaw);
        double sin = Math.sin(y), cos = Math.cos(y);
        return position().add(-sin * fwd - cos * right, up, cos * fwd - sin * right);
    }

    private float yawTo(Entity e) { return yawToPos(e.position()); }
    private float yawToPos(Vec3 p) { return (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90; }
    private double horizontalDist(Entity e) { return Math.sqrt(e.position().subtract(position()).horizontalDistanceSqr()); }

    private List<LivingEntity> near(Vec3 at, double radius, double height) {
        AABB box = new AABB(at.x - radius, at.y - height, at.z - radius, at.x + radius, at.y + height, at.z + radius);
        return level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && canHit(e)
            && e.position().subtract(at).horizontalDistanceSqr() <= (radius + e.getBbWidth() * 0.5) * (radius + e.getBbWidth() * 0.5));
    }

    private static Vec3 horizontalAway(Vec3 from, Vec3 center) {
        Vec3 d = from.subtract(center).multiply(1, 0, 1);
        return d.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private static Vec3 rotateY(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / Math.max(ab.lengthSqr(), 1.0E-6), 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    private static DustParticleOptions crystalDust() { return new DustParticleOptions(new Vector3f(0.45F, 0.95F, 0.95F), 1.4F); }

    private static void ring(ServerLevel s, Vec3 c, double r, int n, net.minecraft.core.particles.ParticleOptions p) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            s.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    // ---------------- 其他 ----------------

    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ELDER_GUARDIAN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ELDER_GUARDIAN_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ELDER_GUARDIAN_DEATH; }
    @Override public int getAmbientSoundInterval() { return 240; }
    @Override protected float getSoundVolume() { return 1.6F; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Enraged", isEnraged());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ENRAGED, tag.getBoolean("Enraged"));
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }

    // ---------------- 动画 ----------------

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 6, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            if (getSkill() != NONE) return PlayState.STOP;
            return state.setAndContinue(getDeltaMovement().horizontalDistanceSqr() > 0.003 ? MOVE : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "skill", 0, state -> {
            byte skill = getSkill();
            if (isDeadOrDying() || skill == NONE) return PlayState.STOP;
            if (animResetSeen != clientSkillStart) {
                animResetSeen = clientSkillStart;
                state.getController().forceAnimationReset();
            }
            return state.setAndContinue(SKILL_ANIMS[skill]);
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
