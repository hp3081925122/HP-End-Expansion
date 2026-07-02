package com.hp_end_expansion.world.entity;

import com.hp_end_expansion.registry.ModEntities;
import com.hp_end_expansion.registry.ModItems;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class EnderSnail extends TamableAnimal implements GeoEntity {
    // 末影蜗牛存档字段、扫描范围和成长阈值。
    private static final String CHORUS_CRYSTALS_TAG = "ChorusCrystals";
    private static final String JUVENILE_CRYSTAL_GROWTHS_TAG = "JuvenileCrystalGrowths";
    private static final int MAX_CHORUS_CRYSTALS = 10;
    private static final int JUVENILE_MOLT_THRESHOLD = 10;
    private static final int CHORUS_SEARCH_RANGE = 12;
    private static final int CONTAINER_SEARCH_RANGE = 8;
    private static final int MAX_HARVEST_BLOCKS = 96;
    // 同步壳上紫颂晶簇数量，让客户端模型能切换晶簇显示。
    private static final EntityDataAccessor<Integer> DATA_CHORUS_CRYSTALS = SynchedEntityData.defineId(EnderSnail.class, EntityDataSerializers.INT);
    // 末影蜗牛 GeckoLib 待机和爬行动画资源名。
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("animation.ender_snail.idle");
    private static final RawAnimation CRAWL_ANIMATION = RawAnimation.begin().thenLoop("animation.ender_snail.crawl");
    private static final RawAnimation TAME_ANIMATION = RawAnimation.begin().thenPlay("animation.ender_snail.tame");
    // 末影蜗牛动画缓存和幼体成长状态。
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int 幼体晶簇成长次数;

    // 创建末影蜗牛实体实例。
    public EnderSnail(EntityType<? extends TamableAnimal> 实体类型, Level 世界) {
        super(实体类型, 世界);
    }

    // 创建末影蜗牛基础属性。
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 18.0)
                .add(Attributes.MOVEMENT_SPEED, 0.12)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35);
    }

    // 定义需要同步到客户端的末影蜗牛状态。
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHORUS_CRYSTALS, 0);
    }

    // 限制末影蜗牛自然生成在末地维度内。
    public static boolean canSpawn(EntityType<EnderSnail> 实体类型, ServerLevelAccessor 世界, MobSpawnType 生成类型, BlockPos 位置, RandomSource random) {
        return 世界.getLevel().dimension() == Level.END;
    }

    // 新生成的末影蜗牛九成为幼体，一成为成年个体。
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor 世界, DifficultyInstance 难度, MobSpawnType 生成类型, @Nullable SpawnGroupData 生成组数据) {
        SpawnGroupData 生成结果 = super.finalizeSpawn(世界, 难度, 生成类型, 生成组数据);
        this.setBaby(世界.getRandom().nextInt(10) != 0);
        return 生成结果;
    }

    // 注册采集紫颂、催熟紫颂花、跟随主人、慢速闲逛和观察玩家的基础 AI。
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new HarvestMatureChorusGoal(this));
        this.goalSelector.addGoal(3, new RubChorusFlowerGoal(this));
        this.goalSelector.addGoal(4, new BreedGoal(this, 0.6));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 0.75, 6.0F, 2.0F));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.45, 100));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    // 紫颂花用于驯服后繁殖，紫颂果只走自定义驯服逻辑。
    @Override
    public boolean isFood(ItemStack 物品栈) {
        return 物品栈.is(Items.CHORUS_FLOWER);
    }

    // 处理紫颂花繁殖、紫颂果驯服和晶簇收割。
    @Override
    public InteractionResult mobInteract(Player 玩家, InteractionHand 手) {
        ItemStack 物品栈 = 玩家.getItemInHand(手);

        // 已驯服成年蜗牛只用紫颂花进入原版繁殖逻辑。
        if (物品栈.is(Items.CHORUS_FLOWER)) {
            if (this.isTame() && !this.isBaby()) {
                if (!this.level().isClientSide) {
                    if (!this.isOwnedBy(玩家)) {
                        玩家.displayClientMessage(Component.literal("[末影蜗牛调试] 紫颂花右键失败：你不是这只蜗牛的主人"), false);
                        return InteractionResult.CONSUME;
                    }
                    if (!this.canFallInLove()) {
                        玩家.displayClientMessage(Component.literal("[末影蜗牛调试] 紫颂花右键失败：蜗牛还在繁殖冷却或已经进入爱心状态"), false);
                        return InteractionResult.CONSUME;
                    }
                    物品栈.consume(1, 玩家);
                    this.setInLove(玩家);
                    玩家.displayClientMessage(Component.literal("[末影蜗牛调试] 紫颂花喂食成功：蜗牛已进入爱心繁殖状态"), false);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            if (!this.level().isClientSide) {
                玩家.displayClientMessage(Component.literal("[末影蜗牛调试] 紫颂花右键失败：蜗牛未驯服或还是幼体"), false);
            }
            return InteractionResult.PASS;
        }

        // 有紫颂晶簇时，玩家右键可收割为紫颂植株。
        int 晶簇数量 = this.getChorusCrystals();
        if (晶簇数量 > 0) {
            if (!this.level().isClientSide) {
                ItemStack 收获物 = new ItemStack(Items.CHORUS_PLANT, 晶簇数量);
                this.setChorusCrystals(0);
                if (!this.storeInNearbyContainer(收获物) && !玩家.addItem(收获物)) {
                    this.spawnAtLocation(收获物);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 未驯服时，紫颂果有概率驯服末影蜗牛。
        if (!this.isTame() && 物品栈.is(Items.CHORUS_FRUIT)) {
            if (!this.level().isClientSide) {
                物品栈.consume(1, 玩家);
                if (this.random.nextInt(3) == 0) {
                    this.tame(玩家);
                    this.setOrderedToSit(false);
                    this.triggerAnim("main", "tame");
                    this.level().broadcastEntityEvent(this, (byte)7);
                    this.setPersistenceRequired();
                } else {
                    this.level().broadcastEntityEvent(this, (byte)6);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(玩家, 手);
    }

    // 保存紫颂晶簇和幼体成长次数。
    @Override
    public void addAdditionalSaveData(CompoundTag 复合标签) {
        super.addAdditionalSaveData(复合标签);
        复合标签.putInt(CHORUS_CRYSTALS_TAG, this.getChorusCrystals());
        复合标签.putInt(JUVENILE_CRYSTAL_GROWTHS_TAG, this.幼体晶簇成长次数);
    }

    // 读取紫颂晶簇和幼体成长次数。
    @Override
    public void readAdditionalSaveData(CompoundTag 复合标签) {
        super.readAdditionalSaveData(复合标签);
        this.setChorusCrystals(复合标签.getInt(CHORUS_CRYSTALS_TAG));
        this.幼体晶簇成长次数 = 复合标签.getInt(JUVENILE_CRYSTAL_GROWTHS_TAG);
    }

    // 已驯服或壳上有晶簇的末影蜗牛需要持久化保存。
    @Override
    public boolean requiresCustomPersistence() {
        return this.isTame() || this.getChorusCrystals() > 0 || super.requiresCustomPersistence();
    }

    // 驯服末影蜗牛繁殖后生成同主人的幼体。
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel 世界, AgeableMob 另一亲代) {
        EnderSnail 幼体 = ModEntities.ENDER_SNAIL.get().create(世界);
        if (幼体 != null) {
            this.sendDebugMessage("繁殖成功，正在生成幼年末影蜗牛");
            幼体.setBaby(true);
            幼体.setTame(true, true);
            if (this.getOwnerUUID() != null) {
                幼体.setOwnerUUID(this.getOwnerUUID());
            } else if (另一亲代 instanceof EnderSnail 另一只 && 另一只.getOwnerUUID() != null) {
                幼体.setOwnerUUID(另一只.getOwnerUUID());
            }
            幼体.setOrderedToSit(false);
            幼体.setPersistenceRequired();
        }
        return 幼体;
    }

    // 只有同一主人驯服成年蜗牛可以繁殖。
    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal 另一动物) {
        if (!(另一动物 instanceof EnderSnail 另一只) || 另一只 == this) {
            return false;
        }
        return this.isTame()
                && 另一只.isTame()
                && !this.isBaby()
                && !另一只.isBaby()
                && this.getOwnerUUID() != null
                && this.getOwnerUUID().equals(另一只.getOwnerUUID())
                && this.isInLove()
                && 另一只.isInLove();
    }

    // 注册待机、爬行和驯服动画控制器。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar 控制器注册器) {
        控制器注册器.add(new AnimationController<>(this, "main", 5, 状态 -> {
            double 水平移动量 = this.getDeltaMovement().x * this.getDeltaMovement().x + this.getDeltaMovement().z * this.getDeltaMovement().z;
            状态.setAnimation(水平移动量 > 1.0E-5 ? CRAWL_ANIMATION : IDLE_ANIMATION);
            return PlayState.CONTINUE;
        }).triggerableAnim("tame", TAME_ANIMATION));
    }

    // 返回 GeckoLib 动画缓存。
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // 返回壳上可收割紫颂晶簇数量。
    public int getChorusCrystals() {
        return this.entityData.get(DATA_CHORUS_CRYSTALS);
    }

    // 设置壳上紫颂晶簇数量，并限制在有效范围内。
    private void setChorusCrystals(int 紫颂晶簇数量) {
        this.entityData.set(DATA_CHORUS_CRYSTALS, Math.max(0, Math.min(MAX_CHORUS_CRYSTALS, 紫颂晶簇数量)));
    }

    // 向附近玩家发送末影蜗牛行为调试消息。
    private void sendDebugMessage(String 消息) {
        if (this.level() instanceof ServerLevel 服务端世界) {
            for (ServerPlayer 玩家 : 服务端世界.getPlayers(玩家 -> 玩家.distanceToSqr(this) <= 1024.0D)) {
                玩家.displayClientMessage(Component.literal("[末影蜗牛调试] " + 消息), false);
            }
        }
    }

    // 末影蜗牛进食后增加紫颂晶簇，幼体累计十次后蜕变成年并掉壳。
    private void growChorusCrystal() {
        if (this.getChorusCrystals() < MAX_CHORUS_CRYSTALS) {
            this.setChorusCrystals(this.getChorusCrystals() + 1);
        }
        if (this.isBaby()) {
            this.幼体晶簇成长次数++;
            if (this.幼体晶簇成长次数 >= JUVENILE_MOLT_THRESHOLD) {
                this.幼体晶簇成长次数 = 0;
                this.setBaby(false);
                this.spawnAtLocation(new ItemStack(ModItems.ENDER_SNAIL_SHELL.get()));
                this.setPersistenceRequired();
            }
        }
    }

    // 采集整棵相连紫颂植株，并在底部尝试重新生成一朵紫颂花。
    private void harvestChorusPlant(BlockPos start) {
        if (!(this.level() instanceof ServerLevel 服务端世界)) {
            return;
        }
        this.sendDebugMessage("开始啃食并采集紫颂植株：" + start.toShortString());
        Set<BlockPos> 已访问位置 = new HashSet<>();
        ArrayDeque<BlockPos> 待检查位置 = new ArrayDeque<>();
        待检查位置.add(start.immutable());
        int 植株数量 = 0;
        BlockPos 底部位置 = start;
        while (!待检查位置.isEmpty() && 已访问位置.size() < MAX_HARVEST_BLOCKS) {
            BlockPos 当前位置 = 待检查位置.removeFirst();
            if (!已访问位置.add(当前位置)) {
                continue;
            }
            BlockState 状态 = 服务端世界.getBlockState(当前位置);
            if (!状态.is(Blocks.CHORUS_PLANT) && !状态.is(Blocks.CHORUS_FLOWER)) {
                continue;
            }
            if (当前位置.getY() < 底部位置.getY()) {
                底部位置 = 当前位置;
            }
            if (状态.is(Blocks.CHORUS_PLANT)) {
                植株数量++;
            }
            for (Direction 方向 : Direction.values()) {
                待检查位置.add(当前位置.relative(方向));
            }
        }
        for (BlockPos 位置 : 已访问位置) {
            BlockState 状态 = 服务端世界.getBlockState(位置);
            if (状态.is(Blocks.CHORUS_PLANT) || 状态.is(Blocks.CHORUS_FLOWER)) {
                服务端世界.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, 位置, Block.getId(状态));
                服务端世界.setBlock(位置, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // 只有已驯服蜗牛会把啃食紫颂后的果实交给玩家使用。
        if (植株数量 > 0 && this.isTame()) {
            ItemStack 收获物 = new ItemStack(Items.CHORUS_FRUIT, Math.min(64, 植株数量));
            if (!this.storeInNearbyContainer(收获物)) {
                this.spawnAtLocation(收获物);
            }
        }
        BlockState 新紫颂花 = Blocks.CHORUS_FLOWER.defaultBlockState().setValue(ChorusFlowerBlock.AGE, 0);
        if (服务端世界.getBlockState(底部位置.below()).is(Blocks.END_STONE) && 服务端世界.isEmptyBlock(底部位置)) {
            服务端世界.setBlock(底部位置, 新紫颂花, 3);
        } else if (this.isTame()) {
            ItemStack 紫颂花物品 = new ItemStack(Items.CHORUS_FLOWER);
            if (!this.storeInNearbyContainer(紫颂花物品)) {
                this.spawnAtLocation(紫颂花物品);
            }
        }
        this.growChorusCrystal();
    }

    // 驯服蜗牛会把产物优先放进附近容器。
    private boolean storeInNearbyContainer(ItemStack 物品栈) {
        if (!this.isTame() || 物品栈.isEmpty()) {
            return false;
        }
        BlockPos 中心位置 = this.blockPosition();
        for (BlockPos 位置 : BlockPos.betweenClosed(中心位置.offset(-CONTAINER_SEARCH_RANGE, -3, -CONTAINER_SEARCH_RANGE), 中心位置.offset(CONTAINER_SEARCH_RANGE, 3, CONTAINER_SEARCH_RANGE))) {
            BlockEntity 方块实体 = this.level().getBlockEntity(位置);
            if (方块实体 instanceof Container 容器) {
                for (int 槽位 = 0; 槽位 < 容器.getContainerSize() && !物品栈.isEmpty(); 槽位++) {
                    ItemStack 已有物品 = 容器.getItem(槽位);
                    if (已有物品.isEmpty() && 容器.canPlaceItem(槽位, 物品栈)) {
                        容器.setItem(槽位, 物品栈.copy());
                        物品栈.setCount(0);
                        容器.setChanged();
                        return true;
                    }
                    if (ItemStack.isSameItemSameComponents(已有物品, 物品栈) && 已有物品.getCount() < 容器.getMaxStackSize(已有物品) && 容器.canPlaceItem(槽位, 物品栈)) {
                        int 转移数量 = Math.min(物品栈.getCount(), 容器.getMaxStackSize(已有物品) - 已有物品.getCount());
                        已有物品.grow(转移数量);
                        物品栈.shrink(转移数量);
                        容器.setChanged();
                    }
                }
                if (物品栈.isEmpty()) {
                    return true;
                }
            }
        }
        return 物品栈.isEmpty();
    }

    // 成熟紫颂花采集目标，只间歇扫描附近小范围，避免高频全量查找。
    private static final class HarvestMatureChorusGoal extends Goal {
        private final EnderSnail 蜗牛;
        @Nullable
        private BlockPos 目标;
        private int 冷却;

        // 创建成熟紫颂采集 AI。
        private HarvestMatureChorusGoal(EnderSnail 蜗牛) {
            this.蜗牛 = 蜗牛;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        // 间歇寻找可啃食的紫颂植株或成熟紫颂花。
        @Override
        public boolean canUse() {
            if (this.冷却-- > 0 || this.蜗牛.isBaby() && this.蜗牛.幼体晶簇成长次数 >= JUVENILE_MOLT_THRESHOLD) {
                return false;
            }
            this.冷却 = 80 + this.蜗牛.getRandom().nextInt(80);
            BlockPos 中心位置 = this.蜗牛.blockPosition();
            for (BlockPos 位置 : BlockPos.betweenClosed(中心位置.offset(-CHORUS_SEARCH_RANGE, -4, -CHORUS_SEARCH_RANGE), 中心位置.offset(CHORUS_SEARCH_RANGE, 10, CHORUS_SEARCH_RANGE))) {
                BlockState 状态 = this.蜗牛.level().getBlockState(位置);
                if (状态.is(Blocks.CHORUS_PLANT) || 状态.is(Blocks.CHORUS_FLOWER) && 状态.getValue(ChorusFlowerBlock.AGE) >= ChorusFlowerBlock.DEAD_AGE) {
                    this.目标 = 位置.immutable();
                    this.蜗牛.sendDebugMessage("找到可啃食紫颂植株，准备前往：" + 位置.toShortString());
                    if (状态.is(Blocks.CHORUS_FLOWER)) {
                        this.蜗牛.sendDebugMessage("检测到紫颂花高度：Y=" + 位置.getY() + "，相对蜗牛高度差=" + (位置.getY() - 中心位置.getY()));
                    }
                    return true;
                }
            }
            return false;
        }

        // 目标仍是可啃食的紫颂植株时继续移动。
        @Override
        public boolean canContinueToUse() {
            if (this.目标 == null) {
                return false;
            }
            BlockState 状态 = this.蜗牛.level().getBlockState(this.目标);
            return 状态.is(Blocks.CHORUS_PLANT) || 状态.is(Blocks.CHORUS_FLOWER);
        }

        // 开始向成熟紫颂花移动。
        @Override
        public void start() {
            if (this.目标 != null) {
                this.蜗牛.sendDebugMessage("开始移动到紫颂采集目标：" + this.目标.toShortString());
                this.蜗牛.getNavigation().moveTo(this.目标.getX() + 0.5D, this.目标.getY(), this.目标.getZ() + 0.5D, 0.7D);
            }
        }

        // 靠近后采集整棵紫颂植株。
        @Override
        public void tick() {
            if (this.目标 == null) {
                return;
            }
            this.蜗牛.getLookControl().setLookAt(this.目标.getX() + 0.5D, this.目标.getY() + 0.5D, this.目标.getZ() + 0.5D);
            if (this.蜗牛.distanceToSqr(this.目标.getX() + 0.5D, this.目标.getY(), this.目标.getZ() + 0.5D) <= 6.25D) {
                this.蜗牛.harvestChorusPlant(this.目标);
                this.目标 = null;
                this.蜗牛.getNavigation().stop();
            } else if (this.蜗牛.getNavigation().isDone()) {
                this.蜗牛.getNavigation().moveTo(this.目标.getX() + 0.5D, this.目标.getY(), this.目标.getZ() + 0.5D, 0.7D);
            }
        }

        // 清理目标。
        @Override
        public void stop() {
            this.目标 = null;
        }
    }

    // 驯服蜗牛会去蹭未成熟紫颂花，并直接催生成紫颂树。
    private static final class RubChorusFlowerGoal extends Goal {
        private final EnderSnail 蜗牛;
        @Nullable
        private BlockPos 目标;
        private int 冷却;

        // 创建紫颂催熟 AI。
        private RubChorusFlowerGoal(EnderSnail 蜗牛) {
            this.蜗牛 = 蜗牛;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        // 驯服蜗牛间歇寻找玩家种下或附近未成熟的紫颂花。
        @Override
        public boolean canUse() {
            if (!this.蜗牛.isTame() || this.冷却-- > 0) {
                return false;
            }
            this.冷却 = 60 + this.蜗牛.getRandom().nextInt(60);
            BlockPos 中心位置 = this.蜗牛.blockPosition();
            for (BlockPos 位置 : BlockPos.betweenClosed(中心位置.offset(-CHORUS_SEARCH_RANGE, -3, -CHORUS_SEARCH_RANGE), 中心位置.offset(CHORUS_SEARCH_RANGE, 5, CHORUS_SEARCH_RANGE))) {
                BlockState 状态 = this.蜗牛.level().getBlockState(位置);
                if (状态.is(Blocks.CHORUS_FLOWER) && 状态.getValue(ChorusFlowerBlock.AGE) < ChorusFlowerBlock.DEAD_AGE) {
                    this.目标 = 位置.immutable();
                    this.蜗牛.sendDebugMessage("找到未成熟紫颂花，准备促进生长：" + 位置.toShortString());
                    this.蜗牛.sendDebugMessage("检测到紫颂花高度：Y=" + 位置.getY() + "，相对蜗牛高度差=" + (位置.getY() - 中心位置.getY()));
                    return true;
                }
            }
            return false;
        }

        // 目标仍是未成熟紫颂花时继续催熟。
        @Override
        public boolean canContinueToUse() {
            return this.目标 != null && this.蜗牛.level().getBlockState(this.目标).is(Blocks.CHORUS_FLOWER) && this.蜗牛.level().getBlockState(this.目标).getValue(ChorusFlowerBlock.AGE) < ChorusFlowerBlock.DEAD_AGE;
        }

        // 开始向紫颂花移动。
        @Override
        public void start() {
            if (this.目标 != null) {
                this.蜗牛.getNavigation().moveTo(this.目标.getX() + 0.5D, this.目标.getY(), this.目标.getZ() + 0.5D, 0.75D);
            }
        }

        // 靠近后用原版紫颂树生成逻辑催熟。
        @Override
        public void tick() {
            if (this.目标 == null) {
                return;
            }
            this.蜗牛.getLookControl().setLookAt(this.目标.getX() + 0.5D, this.目标.getY() + 0.5D, this.目标.getZ() + 0.5D);
            if (this.蜗牛.distanceToSqr(this.目标.getX() + 0.5D, this.目标.getY(), this.目标.getZ() + 0.5D) <= 4.0D && this.蜗牛.level() instanceof ServerLevel 服务端世界) {
                if (服务端世界.getBlockState(this.目标).is(Blocks.CHORUS_FLOWER) && 服务端世界.getBlockState(this.目标.below()).is(Blocks.END_STONE)) {
                    this.蜗牛.sendDebugMessage("开始促进紫颂花生成：" + this.目标.toShortString());
                    服务端世界.sendParticles(ParticleTypes.PORTAL, this.目标.getX() + 0.5D, this.目标.getY() + 0.75D, this.目标.getZ() + 0.5D, 24, 0.35D, 0.45D, 0.35D, 0.04D);
                    ChorusFlowerBlock.generatePlant(服务端世界, this.目标, 服务端世界.random, 8);
                    服务端世界.sendParticles(ParticleTypes.PORTAL, this.目标.getX() + 0.5D, this.目标.getY() + 0.75D, this.目标.getZ() + 0.5D, 32, 0.5D, 0.8D, 0.5D, 0.08D);
                } else {
                    this.蜗牛.sendDebugMessage("促进生长失败：目标不是紫颂花，或下方不是末地石：" + this.目标.toShortString());
                }
                this.目标 = null;
                this.蜗牛.getNavigation().stop();
            } else if (this.蜗牛.getNavigation().isDone()) {
                this.蜗牛.getNavigation().moveTo(this.目标.getX() + 0.5D, this.目标.getY(), this.目标.getZ() + 0.5D, 0.75D);
            }
        }

        // 清理目标。
        @Override
        public void stop() {
            this.目标 = null;
        }
    }
}
