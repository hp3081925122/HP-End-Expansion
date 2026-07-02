package com.hp_end_expansion.world.entity;

import com.hp_end_expansion.registry.ModEntities;
import com.hp_end_expansion.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class EnderFish extends Mob implements GeoEntity {
    // 召唤出的战斗杂兵末影鱼不应该在死亡时继续生成传送裂隙。
    private static final String SUPPRESS_DEATH_RIFT_TAG = "SuppressDeathRift";
    // 末影鱼移动时随机穿梭的冷却。
    private static final int PHASE_COOLDOWN = 80;
    // 掉落末影晶核碎片的概率。
    private static final float CORE_SHARD_CHANCE = 0.2F;
    // 末影鱼 GeckoLib 待机、游动和穿梭动画资源名。
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("animation.ender_fish.idle");
    private static final RawAnimation SWIM_ANIMATION = RawAnimation.begin().thenLoop("animation.ender_fish.swim");
    private static final RawAnimation PHASE_ANIMATION = RawAnimation.begin().thenPlay("animation.ender_fish.phase");
    // GeckoLib 使用实体缓存保存动画状态。
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    // 末影鱼当前的虚空游动目标。
    @Nullable
    private Vec3 wanderTarget;
    private int phaseCooldown;
    private int strikeCooldown;
    private boolean suppressDeathRift;

    // 创建小型无重力虚空生物。
    public EnderFish(EntityType<? extends EnderFish> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
    }

    // 创建末影鱼基础属性。
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.FLYING_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    // 末影鱼只自然生成在末地虚空环境中。
    public static boolean canSpawn(EntityType<EnderFish> entityType, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getLevel().dimension() == Level.END && pos.getY() > level.getLevel().getMinBuildHeight() + 16 && level.getBlockState(pos).isAir();
    }

    // 生成时随机调整朝向，让鱼群不完全同向。
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        this.setYRot(level.getRandom().nextFloat() * 360.0F);
        this.yBodyRot = this.getYRot();
        this.setYHeadRot(this.getYRot());
        return result;
    }

    // 每刻维护无重力、虚空游动和随机穿梭。
    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.fallDistance = 0.0F;
        if (this.phaseCooldown > 0) {
            this.phaseCooldown--;
        }
        if (this.strikeCooldown > 0) {
            this.strikeCooldown--;
        }
        if (this.level().isClientSide) {
            this.spawnClientParticles();
        } else if (this.isEffectiveAi()) {
            if (this.getTarget() != null && this.getTarget().isAlive()) {
                this.updateSummonedAssault(this.getTarget());
            } else {
                this.updateVoidSwimming();
            }
        }
    }

    // 末影鱼不使用地面导航，直接沿速度在空中游动。
    @Override
    public void travel(Vec3 travelVector) {
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
        this.calculateEntityAnimation(true);
    }

    // 末影鱼免疫摔落伤害。
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    // 空实现避免无重力生物触发落地摔落处理。
    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    // 死亡时掉落碎片并生成一个可见裂隙。
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // 先保留原版和父类的死亡掉落处理，避免覆盖经验或其他通用逻辑。
        super.dropCustomDeathLoot(level, source, recentlyHit);
        // 末影晶核碎片仍然按原设定概率掉落，不和裂隙生成概率绑定。
        if (this.random.nextFloat() < CORE_SHARD_CHANCE) {
            // 在死亡位置生成末影晶核碎片物品实体。
            this.spawnAtLocation(new ItemStack(ModItems.ENDER_CORE_SHARD.get()));
        }
        // 只有普通自然末影鱼才会在死亡时撕开传送裂隙。
        if (!this.suppressDeathRift) {
            // 创建裂隙实体，使用本模组注册过的裂隙类型。
            EnderRift rift = new EnderRift(ModEntities.ENDER_RIFT.get(), level);
            // 把裂隙移动到末影鱼死亡位置，并继承末影鱼死亡前的水平朝向。
            rift.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
            // 加入服务端世界；这里不再判断 recentlyHit，也不再随机，死亡必定生成。
            level.addFreshEntity(rift);
        }
    }

    // 设置基础经验奖励。
    @Override
    protected int getBaseExperienceReward() {
        return 3;
    }

    // 注册待机、游动和穿梭动画控制器。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            // 移动速度足够明显时播放游动，否则播放悬浮待机。
            state.setAnimation(this.getDeltaMovement().lengthSqr() > 1.0E-4 ? SWIM_ANIMATION : IDLE_ANIMATION);
            return PlayState.CONTINUE;
        }).triggerableAnim("phase", PHASE_ANIMATION));
    }

    // 返回 GeckoLib 动画缓存。
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // 领航者召唤出来的末影鱼会开启这个标记，防止死亡时继续生成裂隙。
    public void setSuppressDeathRift(boolean suppressDeathRift) {
        this.suppressDeathRift = suppressDeathRift;
    }

    // 末影鱼需要保存穿梭冷却，避免读档后立刻连跳。
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("PhaseCooldown", this.phaseCooldown);
        compound.putBoolean(SUPPRESS_DEATH_RIFT_TAG, this.suppressDeathRift);
    }

    // 读取穿梭冷却并恢复无重力状态。
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.phaseCooldown = compound.getInt("PhaseCooldown");
        this.suppressDeathRift = compound.getBoolean(SUPPRESS_DEATH_RIFT_TAG);
        this.setNoGravity(true);
    }

    // 服务端更新虚空游动和移动时随机穿梭。
    private void updateVoidSwimming() {
        if (this.wanderTarget == null || this.distanceToSqr(this.wanderTarget) < 2.25D || this.random.nextInt(120) == 0) {
            this.wanderTarget = this.position().add(
                    Mth.nextDouble(this.random, -10.0D, 10.0D),
                    Mth.nextDouble(this.random, -4.0D, 4.0D),
                    Mth.nextDouble(this.random, -10.0D, 10.0D)
            );
        }

        double targetY = Mth.clamp(this.wanderTarget.y, this.level().getMinBuildHeight() + 12.0D, this.level().getMaxBuildHeight() - 8.0D);
        Vec3 target = new Vec3(this.wanderTarget.x, targetY, this.wanderTarget.z);
        Vec3 direction = target.subtract(this.position());
        if (direction.lengthSqr() > 0.01D) {
            Vec3 motion = direction.normalize().scale(0.045D);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.88D).add(motion));
            this.updateRotationFromMovement(direction);
            if (this.phaseCooldown <= 0 && this.random.nextInt(180) == 0) {
                this.phaseBlink(direction.normalize());
            }
        }
    }

    // 被领航者召唤出来的末影鱼会优先追击目标，并在贴脸时进行扑咬。
    private void updateSummonedAssault(net.minecraft.world.entity.LivingEntity target) {
        // 目标失效时清空攻击状态，重新回到普通游动逻辑。
        if (!target.isAlive()) {
            this.setTarget(null);
            return;
        }

        // 让召唤鱼朝目标中心偏上位置游动，避免总是贴地追踪。
        Vec3 targetPos = target.position().add(0.0D, target.getBbHeight() * 0.45D, 0.0D);
        // 根据当前位置和目标位置计算追击方向。
        Vec3 direction = targetPos.subtract(this.position());
        if (direction.lengthSqr() <= 1.0E-4) {
            return;
        }

        // 召唤鱼追击时速度明显高于自然游动，保证能形成压迫感。
        Vec3 motion = direction.normalize().scale(0.08D);
        // 平滑叠加追击速度，避免小体型末影鱼运动过于僵硬。
        this.setDeltaMovement(this.getDeltaMovement().scale(0.84D).add(motion));
        // 根据追击方向更新朝向，让召唤鱼看起来确实在扑向目标。
        this.updateRotationFromMovement(direction);

        // 靠近目标后进行一次短冷却扑咬，并伴随小范围穿梭粒子。
        if (this.distanceToSqr(target) <= 3.24D && this.strikeCooldown <= 0) {
            // 扑咬伤害使用近战伤害来源，便于后续被护甲和附魔正常处理。
            target.hurt(this.damageSources().mobAttack(this), 3.0F);
            // 给目标一点向上的轻推，强化被鱼群啃咬的手感。
            target.push(0.0D, 0.18D, 0.0D);
            // 扑咬后发出穿梭粒子，视觉上和普通末影鱼机制保持统一。
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(), 14, 0.2D, 0.25D, 0.2D, 0.05D);
            }
            // 设置扑咬冷却，避免一小群召唤鱼一帧内连续多次命中。
            this.strikeCooldown = 18;
        }
    }

    // 末影鱼移动中短距离随机穿梭。
    private void phaseBlink(Vec3 direction) {
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        Vec3 target = this.position()
                .add(direction.scale(Mth.nextDouble(this.random, 4.0D, 8.0D)))
                .add(side.scale(Mth.nextDouble(this.random, -3.0D, 3.0D)))
                .add(0.0D, Mth.nextDouble(this.random, -2.0D, 2.0D), 0.0D);
        double y = Mth.clamp(target.y, this.level().getMinBuildHeight() + 8.0D, this.level().getMaxBuildHeight() - 8.0D);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 0.25D, this.getZ(), 18, 0.25D, 0.2D, 0.25D, 0.08D);
            this.triggerAnim("main", "phase");
            this.teleportTo(target.x, y, target.z);
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 0.25D, this.getZ(), 18, 0.25D, 0.2D, 0.25D, 0.08D);
            this.phaseCooldown = PHASE_COOLDOWN;
        }
    }

    // 根据移动向量更新鱼头朝向。
    private void updateRotationFromMovement(Vec3 movement) {
        double horizontal = movement.horizontalDistance();
        float targetYaw = Mth.wrapDegrees((float)(Mth.atan2(movement.z, movement.x) * Mth.RAD_TO_DEG) - 90.0F);
        float targetPitch = Mth.clamp(Mth.wrapDegrees((float)(-(Mth.atan2(movement.y, horizontal) * Mth.RAD_TO_DEG))), -65.0F, 65.0F);
        float yaw = Mth.rotLerp(0.35F, this.getYRot(), targetYaw);
        float pitch = Mth.rotLerp(0.35F, this.getXRot(), targetPitch);
        this.setYRot(yaw);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
        this.setXRot(pitch);
    }

    // 客户端生成小型末影粒子，让无模型占位阶段也能看见实体位置。
    private void spawnClientParticles() {
        if (this.random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.PORTAL, this.getRandomX(0.45D), this.getRandomY(), this.getRandomZ(0.45D), 0.0D, 0.01D, 0.0D);
        }
        if (this.random.nextInt(12) == 0) {
            this.level().addParticle(ParticleTypes.END_ROD, this.getRandomX(0.25D), this.getRandomY(), this.getRandomZ(0.25D), 0.0D, 0.01D, 0.0D);
        }
    }
}
