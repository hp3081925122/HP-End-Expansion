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

public class  VoidWhale extends TamableAnimal implements GeoEntity {
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
    private int 驯服进度;
    private int 闪避冷却;
    private int 传送冷却;
    @Nullable
    private Vec3 巡游目标;

    // 创建虚空鲸实体，并关闭重力和视锥裁剪。
    public VoidWhale(EntityType<? extends TamableAnimal> 实体类型, Level 世界) {
        super(实体类型, 世界);
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
    public static boolean canSpawn(EntityType<VoidWhale> 实体类型, ServerLevelAccessor 世界, MobSpawnType 生成类型, BlockPos 位置, RandomSource random) {
        return 世界.getLevel().dimension() == Level.END && 位置.getY() >= 世界.getLevel().getMinBuildHeight() + 16;
    }

    // 每刻维护无重力、冷却、客户端粒子和服务端自动游动。
    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.fallDistance = 0.0F;
        // 闪避冷却逐刻减少。
        if (this.闪避冷却 > 0) {
            this.闪避冷却--;
        }
        // 传送冷却逐刻减少。
        if (this.传送冷却 > 0) {
            this.传送冷却--;
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
    public void travel(Vec3 移动输入向量) {
        // 有控制乘客时使用玩家输入控制虚空鲸。
        if (this.getControllingPassenger() instanceof Player 玩家) {
            this.travelWithRider(玩家);
            return;
        }

        // 无乘客时沿当前速度移动并逐渐减速。
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.92));
        this.calculateEntityAnimation(true);
    }

    // 处理末影珍珠驯服和主人直接骑乘。
    @Override
    public InteractionResult mobInteract(Player 玩家, InteractionHand 手) {
        ItemStack 物品栈 = 玩家.getItemInHand(手);
        // 未驯服时，末影珍珠会推进驯服进度。
        if (!this.isTame() && 物品栈.is(Items.ENDER_PEARL)) {
            if (!this.level().isClientSide) {
                物品栈.consume(1, 玩家);
                this.驯服进度++;
                // 达到固定进度或满足随机成功时完成驯服。
                if (this.驯服进度 >= 5 || this.驯服进度 >= 3 && this.random.nextInt(3) == 0) {
                    this.tame(玩家);
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
        if (this.isTame() && this.isOwnedBy(玩家)) {
            if (!this.level().isClientSide) {
                玩家.startRiding(this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(玩家, 手);
    }

    // 虚空鲸受到普通伤害时闪避并免疫本次伤害。
    @Override
    public boolean hurt(DamageSource 伤害来源, float 伤害量) {
        // 绕过无敌的伤害仍按原版处理。
        if (伤害来源.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(伤害来源, 伤害量);
        }

        // 服务端触发闪避动画和位移。
        if (!this.level().isClientSide && this.闪避冷却 <= 0) {
            this.闪避冷却 = 60;
            this.triggerAnim("main", "dodge");
            this.dodgeAway(伤害来源.getSourcePosition());
        }

        return false;
    }

    // 死亡时掉落少量末影珍珠。
    @Override
    protected void dropCustomDeathLoot(ServerLevel 世界, DamageSource 伤害来源, boolean 最近被玩家攻击) {
        super.dropCustomDeathLoot(世界, 伤害来源, 最近被玩家攻击);
        this.spawnAtLocation(new ItemStack(Items.ENDER_PEARL, 1 + this.random.nextInt(3)));
    }

    // 设置基础经验奖励。
    @Override
    protected int getBaseExperienceReward() {
        return 8;
    }

    // 末影珍珠作为驯服食物。
    @Override
    public boolean isFood(ItemStack 物品栈) {
        return 物品栈.is(Items.ENDER_PEARL);
    }

    // 虚空鲸当前不繁殖。
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel 世界, AgeableMob 另一亲代) {
        return null;
    }

    // 虚空鲸当前不允许交配。
    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal 另一动物) {
        return false;
    }

    // 虚空鲸不会因离玩家太远自动消失。
    @Override
    public boolean removeWhenFarAway(double 最近玩家距离) {
        return false;
    }

    // 已驯服虚空鲸需要自定义持久化。
    @Override
    public boolean requiresCustomPersistence() {
        return this.isTame() || super.requiresCustomPersistence();
    }

    // 虚空鲸免疫摔落伤害。
    @Override
    public boolean causeFallDamage(float 摔落距离, float 倍率, DamageSource 伤害来源) {
        return false;
    }

    // 空实现避免飞行实体触发落地摔落逻辑。
    @Override
    protected void checkFallDamage(double 目标Y, boolean 在地面, BlockState 状态, BlockPos 位置) {
    }

    // 返回当前控制乘客。
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity 乘客 = this.getFirstPassenger();
        return 乘客 instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    // 只允许主人玩家作为唯一乘客。
    @Override
    protected boolean canAddPassenger(Entity 乘客) {
        return this.getPassengers().isEmpty() && 乘客 instanceof Player 玩家 && this.isTame() && this.isOwnedBy(玩家);
    }

    // 设置乘客在虚空鲸背上的位置。
    @Override
    protected void positionRider(Entity 乘客, Entity.MoveFunction 位置回调) {
        if (this.hasPassenger(乘客)) {
            位置回调.accept(乘客, this.getX(), this.getY() + 9.0, this.getZ());
        }
    }

    // 保存驯服进度。
    @Override
    public void addAdditionalSaveData(CompoundTag 复合标签) {
        super.addAdditionalSaveData(复合标签);
        复合标签.putInt(TAME_PROGRESS_TAG, this.驯服进度);
    }

    // 读取驯服进度并恢复无重力状态。
    @Override
    public void readAdditionalSaveData(CompoundTag 复合标签) {
        super.readAdditionalSaveData(复合标签);
        this.驯服进度 = 复合标签.getInt(TAME_PROGRESS_TAG);
        this.setNoGravity(true);
    }

    // 注册主动画控制器和可触发的闪避、传送动画。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar 控制器注册器) {
        控制器注册器.add(new AnimationController<>(this, "main", 5, 状态 -> {
            // 移动或被骑乘时播放游动动画，否则播放待机动画。
            状态.setAnimation(this.isVehicle() || this.getDeltaMovement().lengthSqr() > 0.01 ? SWIM_ANIMATION : IDLE_ANIMATION);
            return PlayState.CONTINUE;
        }).triggerableAnim("dodge", DODGE_ANIMATION).triggerableAnim("teleport", TELEPORT_ANIMATION));
    }

    // 返回 GeckoLib 动画缓存。
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // 玩家骑乘时消耗主手全部末影珍珠并按水平视线远距离传送。
    public void tryTeleportFromRider(ServerPlayer 玩家) {
        // 冷却中、未骑乘、未驯服或非主人时不允许传送。
        if (this.传送冷却 > 0 || 玩家.getVehicle() != this || !this.isTame() || !this.isOwnedBy(玩家)) {
            return;
        }

        // 主手必须持有末影珍珠。
        ItemStack 物品栈 = 玩家.getMainHandItem();
        if (!物品栈.is(Items.ENDER_PEARL) || 物品栈.isEmpty()) {
            return;
        }

        // 每颗末影珍珠提供 200 格传送距离。
        int 珍珠数量 = 物品栈.getCount();
        double 距离 = 珍珠数量 * 200.0;
        Vec3 视线 = 玩家.getLookAngle();
        Vec3 水平视线 = new Vec3(视线.x, 0.0, 视线.z);
        if (水平视线.lengthSqr() < 1.0E-4) {
            水平视线 = Vec3.directionFromRotation(0.0F, 玩家.getYRot());
        }
        Vec3 目标 = this.position().add(水平视线.normalize().scale(距离));
        ServerLevel 服务端世界 = 玩家.serverLevel();
        double 目标Y = Mth.clamp(this.getY(), 服务端世界.getMinBuildHeight() + 2.0, 服务端世界.getMaxBuildHeight() - 2.0);

        // 在传送前后生成传送粒子并触发动画。
        this.sendPortalParticles(服务端世界, this.position(), 80);
        this.teleportTo(目标.x, 目标Y, 目标.z);
        玩家.teleportTo(目标.x, 目标.y, 目标.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.传送冷却 = 40;
        this.triggerAnim("main", "teleport");
        this.sendPortalParticles(服务端世界, this.position(), 120);

        // 创造模式不消耗末影珍珠。
        if (!玩家.getAbilities().instabuild) {
            物品栈.shrink(珍珠数量);
        }
    }

    // 根据玩家输入控制虚空鲸移动。
    private void travelWithRider(Player 玩家) {
        // 手持末影珍珠骑乘时，虚空鲸获得额外移动倍率。
        boolean 持有珍珠 = 玩家.getMainHandItem().is(Items.ENDER_PEARL) || 玩家.getOffhandItem().is(Items.ENDER_PEARL);
        double 珍珠速度倍率 = 持有珍珠 ? 1.5 : 1.0;

        // 服务端每秒有 2% 概率吃掉骑乘玩家手上的一颗末影珍珠。
        if (!this.level().isClientSide && 持有珍珠 && this.tickCount % 20 == 0 && this.random.nextFloat() < 0.02F && !玩家.getAbilities().instabuild) {
            ItemStack 珍珠物品栈 = 玩家.getMainHandItem().is(Items.ENDER_PEARL) ? 玩家.getMainHandItem() : 玩家.getOffhandItem();
            珍珠物品栈.shrink(1);
        }

        // 获取玩家视线、侧向、前进、横移和下降输入。
        Vec3 视线 = 玩家.getLookAngle().normalize();
        Vec3 侧向 = new Vec3(视线.z, 0.0, -视线.x).normalize();
        double 前进输入 = 玩家.zza;
        double 横移输入 = 玩家.xxa;
        double 下降输入 = 玩家.isShiftKeyDown() ? -RIDER_DESCEND_SPEED : 0.0;
        Vec3 期望移动 = 视线.scale(前进输入 * RIDER_FORWARD_SPEED * 珍珠速度倍率).add(侧向.scale(横移输入 * RIDER_STRAFE_SPEED * 珍珠速度倍率)).add(0.0, 下降输入 * 珍珠速度倍率, 0.0);

        // 有移动输入时朝向移动方向，没有移动输入时朝向玩家视线方向。
        if (期望移动.lengthSqr() > 1.0E-4) {
            this.updateRotationFromMovement(期望移动, "rider");
        } else {
            this.setYRot(Mth.wrapDegrees(玩家.getYRot()));
            this.setYBodyRot(this.getYRot());
            this.setYHeadRot(this.getYRot());
            this.setXRot(玩家.getXRot() * 0.5F);
            this.debugMovement("rider_look", 视线, this.getYRot(), this.getXRot());
        }
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        // 平滑叠加期望速度，避免骑乘时瞬间变向。
        this.setDeltaMovement(this.getDeltaMovement().scale(0.55).add(期望移动.scale(0.45)));
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.calculateEntityAnimation(true);
    }

    // 服务端自动游动逻辑，包含随机巡游和末影珍珠吸引。
    private void updateWanderMovement() {
        Player 被吸引玩家 = this.findPearlHolder();
        LivingEntity 拥有者 = this.getOwner();
        boolean 需要返回主人 = this.需要返回主人(拥有者);
        // 附近有手持末影珍珠的玩家时，目标改为玩家眼部附近。
        if (需要返回主人) {
            this.巡游目标 = 拥有者.getEyePosition().add(0.0, -0.6, 0.0);
        } else if (被吸引玩家 != null) {
            this.巡游目标 = 被吸引玩家.getEyePosition().add(0.0, -0.6, 0.0);
        }

        // 没有目标、接近目标或随机刷新时选择新的巡游目标。
        if (this.巡游目标 == null || this.distanceToSqr(this.巡游目标) < 9.0 || this.random.nextInt(160) == 0) {
            this.巡游目标 = this.position().add(
                    Mth.nextDouble(this.random, -24.0, 24.0),
                    Mth.nextDouble(this.random, -8.0, 8.0),
                    Mth.nextDouble(this.random, -24.0, 24.0)
            );
        }
        this.巡游目标 = this.clampTargetToOwnerRange(this.巡游目标, 拥有者);

        // 限制目标高度，避免游到世界高度边界外。
        double 目标Y = Mth.clamp(this.巡游目标.y, this.level().getMinBuildHeight() + 8.0, this.level().getMaxBuildHeight() - 8.0);
        Vec3 目标 = new Vec3(this.巡游目标.x, 目标Y, this.巡游目标.z);
        Vec3 方向 = 目标.subtract(this.position());
        // 根据驯服状态和吸引状态选择速度，并朝目标方向移动。
        if (方向.lengthSqr() > 0.01) {
            double 速度 = 需要返回主人 || 被吸引玩家 != null ? PEARL_ATTRACT_SPEED : this.isTame() ? TAME_WANDER_SPEED : WILD_WANDER_SPEED;
            Vec3 移动量 = 方向.normalize().scale(速度);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9).add(移动量));
            this.updateRotationFromMovement(方向, 需要返回主人 ? "owner_return" : 被吸引玩家 != null ? "pearl_attract" : "wander");
        }
    }

    // 已驯服虚空鲸超过主人半径时优先返回主人附近。
    private boolean 需要返回主人(@Nullable LivingEntity 拥有者) {
        return this.isTame() && 拥有者 != null && 拥有者.level() == this.level() && this.distanceToSqr(拥有者) > OWNER_STAY_RANGE * OWNER_STAY_RANGE;
    }

    // 已驯服虚空鲸的自动巡游目标不能超出主人附近。
    private Vec3 clampTargetToOwnerRange(Vec3 目标, @Nullable LivingEntity 拥有者) {
        if (!this.isTame() || 拥有者 == null || 拥有者.level() != this.level()) {
            return 目标;
        }

        Vec3 主人位置 = 拥有者.position();
        Vec3 偏移 = 目标.subtract(主人位置);
        if (偏移.lengthSqr() <= OWNER_TARGET_RANGE * OWNER_TARGET_RANGE) {
            return 目标;
        }

        return 主人位置.add(偏移.normalize().scale(OWNER_TARGET_RANGE));
    }

    // 查找范围内最近的手持末影珍珠玩家。
    @Nullable
    private Player findPearlHolder() {
        // 只在服务端查找玩家。
        if (!(this.level() instanceof ServerLevel 服务端世界)) {
            return null;
        }

        // 遍历范围内主手或副手持有末影珍珠的非旁观玩家。
        double 范围平方 = PEARL_ATTRACT_RANGE * PEARL_ATTRACT_RANGE;
        Player 最近玩家 = null;
        double 最近距离 = 范围平方;
        for (ServerPlayer 玩家 : 服务端世界.getPlayers(玩家 -> !玩家.isSpectator() && (玩家.getMainHandItem().is(Items.ENDER_PEARL) || 玩家.getOffhandItem().is(Items.ENDER_PEARL)))) {
            double 距离 = this.distanceToSqr(玩家);
            // 记录最近的符合条件玩家。
            if (距离 < 最近距离) {
                最近玩家 = 玩家;
                最近距离 = 距离;
            }
        }
        return 最近玩家;
    }

    // 根据移动向量平滑更新 yaw、身体朝向、头部朝向和俯仰角。
    private void updateRotationFromMovement(Vec3 移动向量, String 伤害来源) {
        if (移动向量.lengthSqr() > 1.0E-4) {
            // 从三维移动向量计算水平朝向和垂直俯仰。
            double 水平距离 = 移动向量.horizontalDistance();
            float 目标偏航 = Mth.wrapDegrees((float)(Mth.atan2(移动向量.z, 移动向量.x) * Mth.RAD_TO_DEG) - 90.0F);
            float 目标俯仰 = Mth.clamp(Mth.wrapDegrees((float)(-(Mth.atan2(移动向量.y, 水平距离) * Mth.RAD_TO_DEG))), -75.0F, 75.0F);
            // 用插值减少朝向突变。
            float 偏航 = Mth.rotLerp(0.35F, this.getYRot(), 目标偏航);
            float 俯仰 = Mth.rotLerp(0.35F, this.getXRot(), 目标俯仰);

            // 同步实体、身体、头部和俯仰角。
            this.setYRot(偏航);
            this.setYBodyRot(偏航);
            this.setYHeadRot(偏航);
            this.setXRot(俯仰);
            this.debugMovement(伤害来源, 移动向量, 目标偏航, 目标俯仰);
        }
    }

    // 默认关闭的低频移动调试日志。
    private void debugMovement(String 伤害来源, Vec3 移动向量, float 目标偏航, float 目标俯仰) {
        if (DEBUG_MOVEMENT && this.tickCount % 40 == 0) {
            HpEndExpansion.LOGGER.debug("Void whale movement source={} yaw={} bodyYaw={} headYaw={} pitch={} targetYaw={} targetPitch={} motion=({}, {}, {})", 伤害来源, this.getYRot(), this.yBodyRot, this.yHeadRot, this.getXRot(), 目标偏航, 目标俯仰, 移动向量.x, 移动向量.y, 移动向量.z);
        }
    }

    // 受击时向伤害来源反方向闪避。
    private void dodgeAway(@Nullable Vec3 来源位置) {
        Vec3 远离方向 = 来源位置 == null ? this.getLookAngle().reverse() : this.position().subtract(来源位置);
        // 来源过近或无来源时，随机生成一个闪避方向。
        if (远离方向.lengthSqr() < 0.01) {
            远离方向 = new Vec3(this.random.nextDouble() - 0.5, 0.25, this.random.nextDouble() - 0.5);
        }
        // 计算闪避目标，并限制在世界高度内。
        Vec3 目标 = this.position().add(远离方向.normalize().scale(12.0)).add(0.0, Mth.nextDouble(this.random, -3.0, 5.0), 0.0);
        double 目标Y = Mth.clamp(目标.y, this.level().getMinBuildHeight() + 3.0, this.level().getMaxBuildHeight() - 3.0);
        // 服务端执行闪避传送并生成粒子。
        if (this.level() instanceof ServerLevel 服务端世界) {
            this.sendPortalParticles(服务端世界, this.position(), 40);
            this.teleportTo(目标.x, 目标Y, 目标.z);
            this.sendPortalParticles(服务端世界, this.position(), 60);
        }
    }

    // 向指定位置发送传送粒子。
    private void sendPortalParticles(ServerLevel 世界, Vec3 位置向量, int 数量) {
        世界.sendParticles(ParticleTypes.PORTAL, 位置向量.x, 位置向量.y + 1.0, 位置向量.z, 数量, 1.6, 0.9, 1.6, 0.25);
        世界.sendParticles(ParticleTypes.REVERSE_PORTAL, 位置向量.x, 位置向量.y + 1.0, 位置向量.z, 数量 / 2, 1.2, 0.7, 1.2, 0.12);
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
