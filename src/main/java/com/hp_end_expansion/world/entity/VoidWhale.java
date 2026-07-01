package com.hp_end_expansion.world.entity;

import com.hp_end_expansion.HpEndExpansion;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

public class VoidWhale extends TamableAnimal implements GeoEntity {
    // 存档中记录驯服进度的字段名。
    private static final String TAME_PROGRESS_TAG = "TameProgress";
    // 手持末影珍珠吸引虚空鲸的范围。
    private static final double PEARL_ATTRACT_RANGE = 48.0;
    private static final double OWNER_STAY_RANGE = 48.0;
    private static final double OWNER_TARGET_RANGE = 40.0;
    // 未驯服、已驯服、被珍珠吸引时的自动游动速度。
    private static final double WILD_WANDER_SPEED = 0.035;
    private static final double TAME_WANDER_SPEED = 0.07;
    private static final double PEARL_ATTRACT_SPEED = 0.09;
    // 玩家骑乘时的前进、横移和下降速度。
    private static final double RIDER_FORWARD_SPEED = 3;
    private static final double RIDER_STRAFE_SPEED = 2;
    private static final double RIDER_DESCEND_SPEED = 2;
    // 默认关闭的移动朝向调试开关。
    private static final boolean DEBUG_MOVEMENT = false;
    // GeckoLib 动画资源名。
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("animation.void_whale.idle");
    private static final RawAnimation SWIM_ANIMATION = RawAnimation.begin().thenLoop("animation.void_whale.swim");
    private static final RawAnimation DODGE_ANIMATION = RawAnimation.begin().thenPlay("animation.void_whale.dodge");
    private static final RawAnimation TELEPORT_ANIMATION = RawAnimation.begin().thenPlay("animation.void_whale.teleport");
    // GeckoLib 动画缓存和实体状态。
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int tameProgress;
    private int dodgeCooldown;
    private int teleportCooldown;
    @Nullable
    private Vec3 wanderTarget;

    // 创建虚空鲸实体，并关闭重力和视锥裁剪。
    public VoidWhale(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.noCulling = true;
    }

    // 创建虚空鲸的基础属性。
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FLYING_SPEED, 0.9)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7);
    }

    // 限制虚空鲸自然生成在末地较高位置。
    public static boolean canSpawn(EntityType<VoidWhale> entityType, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getLevel().dimension() == Level.END && pos.getY() >= level.getLevel().getMinBuildHeight() + 16;
    }

    // 每刻维护无重力、冷却、客户端粒子和服务端自动游动。
    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.fallDistance = 0.0F;
        // 闪避冷却逐刻减少。
        if (this.dodgeCooldown > 0) {
            this.dodgeCooldown--;
        }
        // 传送冷却逐刻减少。
        if (this.teleportCooldown > 0) {
            this.teleportCooldown--;
        }
        // 客户端只生成视觉粒子，服务端负责自动移动。
        if (this.level().isClientSide) {
            this.spawnClientParticles();
        } else if (!this.isVehicle() && this.isEffectiveAi()) {
            this.updateWanderMovement();
        }
    }

    // 自定义飞行移动；被玩家骑乘时交给骑乘控制。
    @Override
    public void travel(Vec3 travelVector) {
        // 有控制乘客时使用玩家输入控制虚空鲸。
        if (this.getControllingPassenger() instanceof Player player) {
            this.travelWithRider(player);
            return;
        }

        // 无乘客时沿当前速度移动并逐渐减速。
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.92));
        this.calculateEntityAnimation(true);
    }

    // 处理末影珍珠驯服和主人直接骑乘。
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 未驯服时，末影珍珠会推进驯服进度。
        if (!this.isTame() && stack.is(Items.ENDER_PEARL)) {
            if (!this.level().isClientSide) {
                stack.consume(1, player);
                this.tameProgress++;
                // 达到固定进度或满足随机成功时完成驯服。
                if (this.tameProgress >= 5 || this.tameProgress >= 3 && this.random.nextInt(3) == 0) {
                    this.tame(player);
                    this.setOrderedToSit(false);
                    this.level().broadcastEntityEvent(this, (byte)7);
                    this.setPersistenceRequired();
                } else {
                    this.level().broadcastEntityEvent(this, (byte)6);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 已驯服且属于当前玩家时，直接交互开始骑乘。
        if (this.isTame() && this.isOwnedBy(player)) {
            if (!this.level().isClientSide) {
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    // 虚空鲸受到普通伤害时闪避并免疫本次伤害。
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 绕过无敌的伤害仍按原版处理。
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }

        // 服务端触发闪避动画和位移。
        if (!this.level().isClientSide && this.dodgeCooldown <= 0) {
            this.dodgeCooldown = 60;
            this.triggerAnim("main", "dodge");
            this.dodgeAway(source.getSourcePosition());
        }

        return false;
    }

    // 死亡时掉落少量末影珍珠。
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        this.spawnAtLocation(new ItemStack(Items.ENDER_PEARL, 1 + this.random.nextInt(3)));
    }

    // 设置基础经验奖励。
    @Override
    protected int getBaseExperienceReward() {
        return 8;
    }

    // 末影珍珠作为驯服食物。
    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.ENDER_PEARL);
    }

    // 虚空鲸当前不繁殖。
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    // 虚空鲸当前不允许交配。
    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal otherAnimal) {
        return false;
    }

    // 虚空鲸不会因离玩家太远自动消失。
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // 已驯服虚空鲸需要自定义持久化。
    @Override
    public boolean requiresCustomPersistence() {
        return this.isTame() || super.requiresCustomPersistence();
    }

    // 虚空鲸免疫摔落伤害。
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    // 空实现避免飞行实体触发落地摔落逻辑。
    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    // 返回当前控制乘客。
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity passenger = this.getFirstPassenger();
        return passenger instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    // 只允许主人玩家作为唯一乘客。
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player player && this.isTame() && this.isOwnedBy(player);
    }

    // 设置乘客在虚空鲸背上的位置。
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (this.hasPassenger(passenger)) {
            callback.accept(passenger, this.getX(), this.getY() + 9.0, this.getZ());
        }
    }

    // 保存驯服进度。
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt(TAME_PROGRESS_TAG, this.tameProgress);
    }

    // 读取驯服进度并恢复无重力状态。
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.tameProgress = compound.getInt(TAME_PROGRESS_TAG);
        this.setNoGravity(true);
    }

    // 注册主动画控制器和可触发的闪避、传送动画。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            // 移动或被骑乘时播放游动动画，否则播放待机动画。
            state.setAnimation(this.isVehicle() || this.getDeltaMovement().lengthSqr() > 0.01 ? SWIM_ANIMATION : IDLE_ANIMATION);
            return PlayState.CONTINUE;
        }).triggerableAnim("dodge", DODGE_ANIMATION).triggerableAnim("teleport", TELEPORT_ANIMATION));
    }

    // 返回 GeckoLib 动画缓存。
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // 玩家骑乘时消耗主手全部末影珍珠并按水平视线远距离传送。
    public void tryTeleportFromRider(ServerPlayer player) {
        // 冷却中、未骑乘、未驯服或非主人时不允许传送。
        if (this.teleportCooldown > 0 || player.getVehicle() != this || !this.isTame() || !this.isOwnedBy(player)) {
            return;
        }

        // 主手必须持有末影珍珠。
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(Items.ENDER_PEARL) || stack.isEmpty()) {
            return;
        }

        // 每颗末影珍珠提供 200 格传送距离。
        int pearls = stack.getCount();
        double distance = pearls * 200.0;
        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-4) {
            horizontalLook = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        Vec3 target = this.position().add(horizontalLook.normalize().scale(distance));
        ServerLevel serverLevel = player.serverLevel();
        double y = Mth.clamp(this.getY(), serverLevel.getMinBuildHeight() + 2.0, serverLevel.getMaxBuildHeight() - 2.0);

        // 在传送前后生成传送粒子并触发动画。
        this.sendPortalParticles(serverLevel, this.position(), 80);
        this.teleportTo(target.x, y, target.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.teleportCooldown = 40;
        this.triggerAnim("main", "teleport");
        this.sendPortalParticles(serverLevel, this.position(), 120);

        // 创造模式不消耗末影珍珠。
        if (!player.getAbilities().instabuild) {
            stack.shrink(pearls);
        }
    }

    // 根据玩家输入控制虚空鲸移动。
    private void travelWithRider(Player player) {
        // 获取玩家视线、侧向、前进、横移和下降输入。
        Vec3 look = player.getLookAngle().normalize();
        Vec3 side = new Vec3(look.z, 0.0, -look.x).normalize();
        double forward = player.zza;
        double strafe = player.xxa;
        double descent = player.isShiftKeyDown() ? -RIDER_DESCEND_SPEED : 0.0;
        Vec3 desired = look.scale(forward * RIDER_FORWARD_SPEED).add(side.scale(strafe * RIDER_STRAFE_SPEED)).add(0.0, descent, 0.0);

        // 有移动输入时朝向移动方向，没有移动输入时朝向玩家视线方向。
        if (desired.lengthSqr() > 1.0E-4) {
            this.updateRotationFromMovement(desired, "rider");
        } else {
            this.setYRot(Mth.wrapDegrees(player.getYRot()));
            this.setYBodyRot(this.getYRot());
            this.setYHeadRot(this.getYRot());
            this.setXRot(player.getXRot() * 0.5F);
            this.debugMovement("rider_look", look, this.getYRot(), this.getXRot());
        }
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        // 平滑叠加期望速度，避免骑乘时瞬间变向。
        this.setDeltaMovement(this.getDeltaMovement().scale(0.55).add(desired.scale(0.45)));
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.calculateEntityAnimation(true);
    }

    // 服务端自动游动逻辑，包含随机巡游和末影珍珠吸引。
    private void updateWanderMovement() {
        Player attractedPlayer = this.findPearlHolder();
        LivingEntity owner = this.getOwner();
        boolean shouldReturnToOwner = this.shouldReturnToOwner(owner);
        // 附近有手持末影珍珠的玩家时，目标改为玩家眼部附近。
        if (shouldReturnToOwner) {
            this.wanderTarget = owner.getEyePosition().add(0.0, -0.6, 0.0);
        } else if (attractedPlayer != null) {
            this.wanderTarget = attractedPlayer.getEyePosition().add(0.0, -0.6, 0.0);
        }

        // 没有目标、接近目标或随机刷新时选择新的巡游目标。
        if (this.wanderTarget == null || this.distanceToSqr(this.wanderTarget) < 9.0 || this.random.nextInt(160) == 0) {
            this.wanderTarget = this.position().add(
                    Mth.nextDouble(this.random, -24.0, 24.0),
                    Mth.nextDouble(this.random, -8.0, 8.0),
                    Mth.nextDouble(this.random, -24.0, 24.0)
            );
        }
        this.wanderTarget = this.clampTargetToOwnerRange(this.wanderTarget, owner);

        // 限制目标高度，避免游到世界高度边界外。
        double y = Mth.clamp(this.wanderTarget.y, this.level().getMinBuildHeight() + 8.0, this.level().getMaxBuildHeight() - 8.0);
        Vec3 target = new Vec3(this.wanderTarget.x, y, this.wanderTarget.z);
        Vec3 direction = target.subtract(this.position());
        // 根据驯服状态和吸引状态选择速度，并朝目标方向移动。
        if (direction.lengthSqr() > 0.01) {
            double speed = shouldReturnToOwner || attractedPlayer != null ? PEARL_ATTRACT_SPEED : this.isTame() ? TAME_WANDER_SPEED : WILD_WANDER_SPEED;
            Vec3 motion = direction.normalize().scale(speed);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9).add(motion));
            this.updateRotationFromMovement(direction, shouldReturnToOwner ? "owner_return" : attractedPlayer != null ? "pearl_attract" : "wander");
        }
    }

    // 已驯服虚空鲸超过主人半径时优先返回主人附近。
    private boolean shouldReturnToOwner(@Nullable LivingEntity owner) {
        return this.isTame() && owner != null && owner.level() == this.level() && this.distanceToSqr(owner) > OWNER_STAY_RANGE * OWNER_STAY_RANGE;
    }

    // 已驯服虚空鲸的自动巡游目标不能超出主人附近。
    private Vec3 clampTargetToOwnerRange(Vec3 target, @Nullable LivingEntity owner) {
        if (!this.isTame() || owner == null || owner.level() != this.level()) {
            return target;
        }

        Vec3 ownerPosition = owner.position();
        Vec3 offset = target.subtract(ownerPosition);
        if (offset.lengthSqr() <= OWNER_TARGET_RANGE * OWNER_TARGET_RANGE) {
            return target;
        }

        return ownerPosition.add(offset.normalize().scale(OWNER_TARGET_RANGE));
    }

    // 查找范围内最近的手持末影珍珠玩家。
    @Nullable
    private Player findPearlHolder() {
        // 只在服务端查找玩家。
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        // 遍历范围内主手或副手持有末影珍珠的非旁观玩家。
        double rangeSqr = PEARL_ATTRACT_RANGE * PEARL_ATTRACT_RANGE;
        Player closest = null;
        double closestDistance = rangeSqr;
        for (ServerPlayer player : serverLevel.getPlayers(player -> !player.isSpectator() && (player.getMainHandItem().is(Items.ENDER_PEARL) || player.getOffhandItem().is(Items.ENDER_PEARL)))) {
            double distance = this.distanceToSqr(player);
            // 记录最近的符合条件玩家。
            if (distance < closestDistance) {
                closest = player;
                closestDistance = distance;
            }
        }
        return closest;
    }

    // 根据移动向量平滑更新 yaw、身体朝向、头部朝向和俯仰角。
    private void updateRotationFromMovement(Vec3 movement, String source) {
        if (movement.lengthSqr() > 1.0E-4) {
            // 从三维移动向量计算水平朝向和垂直俯仰。
            double horizontalDistance = movement.horizontalDistance();
            float targetYaw = Mth.wrapDegrees((float)(Mth.atan2(movement.z, movement.x) * Mth.RAD_TO_DEG) - 90.0F);
            float targetPitch = Mth.clamp(Mth.wrapDegrees((float)(-(Mth.atan2(movement.y, horizontalDistance) * Mth.RAD_TO_DEG))), -75.0F, 75.0F);
            // 用插值减少朝向突变。
            float yaw = Mth.rotLerp(0.35F, this.getYRot(), targetYaw);
            float pitch = Mth.rotLerp(0.35F, this.getXRot(), targetPitch);

            // 同步实体、身体、头部和俯仰角。
            this.setYRot(yaw);
            this.setYBodyRot(yaw);
            this.setYHeadRot(yaw);
            this.setXRot(pitch);
            this.debugMovement(source, movement, targetYaw, targetPitch);
        }
    }

    // 默认关闭的低频移动调试日志。
    private void debugMovement(String source, Vec3 movement, float targetYaw, float targetPitch) {
        if (DEBUG_MOVEMENT && this.tickCount % 40 == 0) {
            HpEndExpansion.LOGGER.debug("Void whale movement source={} yaw={} bodyYaw={} headYaw={} pitch={} targetYaw={} targetPitch={} motion=({}, {}, {})", source, this.getYRot(), this.yBodyRot, this.yHeadRot, this.getXRot(), targetYaw, targetPitch, movement.x, movement.y, movement.z);
        }
    }

    // 受击时向伤害来源反方向闪避。
    private void dodgeAway(@Nullable Vec3 sourcePosition) {
        Vec3 away = sourcePosition == null ? this.getLookAngle().reverse() : this.position().subtract(sourcePosition);
        // 来源过近或无来源时，随机生成一个闪避方向。
        if (away.lengthSqr() < 0.01) {
            away = new Vec3(this.random.nextDouble() - 0.5, 0.25, this.random.nextDouble() - 0.5);
        }
        // 计算闪避目标，并限制在世界高度内。
        Vec3 target = this.position().add(away.normalize().scale(12.0)).add(0.0, Mth.nextDouble(this.random, -3.0, 5.0), 0.0);
        double y = Mth.clamp(target.y, this.level().getMinBuildHeight() + 3.0, this.level().getMaxBuildHeight() - 3.0);
        // 服务端执行闪避传送并生成粒子。
        if (this.level() instanceof ServerLevel serverLevel) {
            this.sendPortalParticles(serverLevel, this.position(), 40);
            this.teleportTo(target.x, y, target.z);
            this.sendPortalParticles(serverLevel, this.position(), 60);
        }
    }

    // 向指定位置发送传送粒子。
    private void sendPortalParticles(ServerLevel level, Vec3 position, int count) {
        level.sendParticles(ParticleTypes.PORTAL, position.x, position.y + 1.0, position.z, count, 1.6, 0.9, 1.6, 0.25);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, position.x, position.y + 1.0, position.z, count / 2, 1.2, 0.7, 1.2, 0.12);
    }

    // 客户端生成虚空鲸身上的环境粒子。
    private void spawnClientParticles() {
        // 高频生成紫色传送门粒子。
        if (this.random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.PORTAL, this.getRandomX(4.4), this.getRandomY(), this.getRandomZ(4.4), 0.0, 0.02, 0.0);
        }
        // 低频生成末地烛光粒子。
        if (this.random.nextInt(10) == 0) {
            this.level().addParticle(ParticleTypes.END_ROD, this.getRandomX(3.4), this.getRandomY(), this.getRandomZ(3.4), 0.0, 0.01, 0.0);
        }
    }
}
