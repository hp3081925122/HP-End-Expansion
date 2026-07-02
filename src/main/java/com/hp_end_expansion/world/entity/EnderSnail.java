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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
    private int juvenileCrystalGrowths;

    // 创建末影蜗牛实体实例。
    public EnderSnail(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
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
    public static boolean canSpawn(EntityType<EnderSnail> entityType, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getLevel().dimension() == Level.END;
    }

    // 新生成的末影蜗牛九成为幼体，一成为成年个体。
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        this.setBaby(level.getRandom().nextInt(10) != 0);
        return result;
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
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.CHORUS_FLOWER);
    }

    // 处理紫颂花繁殖、紫颂果驯服和晶簇收割。
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 已驯服成年蜗牛只用紫颂花进入原版繁殖逻辑。
        if (stack.is(Items.CHORUS_FLOWER)) {
            if (this.isTame() && !this.isBaby()) {
                if (!this.level().isClientSide) {
                    if (!this.isOwnedBy(player)) {
                        return InteractionResult.CONSUME;
                    }
                    if (!this.canFallInLove()) {
                        return InteractionResult.CONSUME;
                    }
                    stack.consume(1, player);
                    this.setInLove(player);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            return InteractionResult.PASS;
        }

        // 有紫颂晶簇时，玩家右键可收割为紫颂植株。
        int crystals = this.getChorusCrystals();
        if (crystals > 0) {
            if (!this.level().isClientSide) {
                ItemStack harvest = new ItemStack(Items.CHORUS_PLANT, crystals);
                this.setChorusCrystals(0);
                if (!this.storeInNearbyContainer(harvest) && !player.addItem(harvest)) {
                    this.spawnAtLocation(harvest);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 未驯服时，紫颂果有概率驯服末影蜗牛。
        if (!this.isTame() && stack.is(Items.CHORUS_FRUIT)) {
            if (!this.level().isClientSide) {
                stack.consume(1, player);
                if (this.random.nextInt(3) == 0) {
                    this.tame(player);
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

        return super.mobInteract(player, hand);
    }

    // 保存紫颂晶簇和幼体成长次数。
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt(CHORUS_CRYSTALS_TAG, this.getChorusCrystals());
        compound.putInt(JUVENILE_CRYSTAL_GROWTHS_TAG, this.juvenileCrystalGrowths);
    }

    // 读取紫颂晶簇和幼体成长次数。
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setChorusCrystals(compound.getInt(CHORUS_CRYSTALS_TAG));
        this.juvenileCrystalGrowths = compound.getInt(JUVENILE_CRYSTAL_GROWTHS_TAG);
    }

    // 已驯服或壳上有晶簇的末影蜗牛需要持久化保存。
    @Override
    public boolean requiresCustomPersistence() {
        return this.isTame() || this.getChorusCrystals() > 0 || super.requiresCustomPersistence();
    }

    // 驯服末影蜗牛繁殖后生成同主人的幼体。
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        EnderSnail child = ModEntities.ENDER_SNAIL.get().create(level);
        if (child != null) {
            child.setBaby(true);
            child.setTame(true, true);
            if (this.getOwnerUUID() != null) {
                child.setOwnerUUID(this.getOwnerUUID());
            } else if (otherParent instanceof EnderSnail other && other.getOwnerUUID() != null) {
                child.setOwnerUUID(other.getOwnerUUID());
            }
            child.setOrderedToSit(false);
            child.setPersistenceRequired();
        }
        return child;
    }

    // 只有同一主人驯服成年蜗牛可以繁殖。
    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal otherAnimal) {
        if (!(otherAnimal instanceof EnderSnail other) || other == this) {
            return false;
        }
        return this.isTame()
                && other.isTame()
                && !this.isBaby()
                && !other.isBaby()
                && this.getOwnerUUID() != null
                && this.getOwnerUUID().equals(other.getOwnerUUID())
                && this.isInLove()
                && other.isInLove();
    }

    // 注册待机、爬行和驯服动画控制器。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            double horizontalMovement = this.getDeltaMovement().x * this.getDeltaMovement().x + this.getDeltaMovement().z * this.getDeltaMovement().z;
            state.setAnimation(horizontalMovement > 1.0E-5 ? CRAWL_ANIMATION : IDLE_ANIMATION);
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
    private void setChorusCrystals(int chorusCrystals) {
        this.entityData.set(DATA_CHORUS_CRYSTALS, Math.max(0, Math.min(MAX_CHORUS_CRYSTALS, chorusCrystals)));
    }

    // 末影蜗牛进食后增加紫颂晶簇，幼体累计十次后蜕变成年并掉壳。
    private void growChorusCrystal() {
        if (this.getChorusCrystals() < MAX_CHORUS_CRYSTALS) {
            this.setChorusCrystals(this.getChorusCrystals() + 1);
        }
        if (this.isBaby()) {
            this.juvenileCrystalGrowths++;
            if (this.juvenileCrystalGrowths >= JUVENILE_MOLT_THRESHOLD) {
                this.juvenileCrystalGrowths = 0;
                this.setBaby(false);
                this.spawnAtLocation(new ItemStack(ModItems.ENDER_SNAIL_SHELL.get()));
                this.setPersistenceRequired();
            }
        }
    }

    // 采集整棵相连紫颂植株，并在底部尝试重新生成一朵紫颂花。
    private void harvestChorusPlant(BlockPos start) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        int plantCount = 0;
        BlockPos basePos = start;
        while (!queue.isEmpty() && visited.size() < MAX_HARVEST_BLOCKS) {
            BlockPos current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            BlockState state = serverLevel.getBlockState(current);
            if (!state.is(Blocks.CHORUS_PLANT) && !state.is(Blocks.CHORUS_FLOWER)) {
                continue;
            }
            if (current.getY() < basePos.getY()) {
                basePos = current;
            }
            if (state.is(Blocks.CHORUS_PLANT)) {
                plantCount++;
            }
            for (Direction direction : Direction.values()) {
                queue.add(current.relative(direction));
            }
        }
        for (BlockPos pos : visited) {
            BlockState state = serverLevel.getBlockState(pos);
            if (state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER)) {
                serverLevel.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
                serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // 只有已驯服蜗牛会把啃食紫颂后的果实交给玩家使用。
        if (plantCount > 0 && this.isTame()) {
            ItemStack harvest = new ItemStack(Items.CHORUS_FRUIT, Math.min(64, plantCount));
            if (!this.storeInNearbyContainer(harvest)) {
                this.spawnAtLocation(harvest);
            }
        }
        BlockState newFlower = Blocks.CHORUS_FLOWER.defaultBlockState().setValue(ChorusFlowerBlock.AGE, 0);
        if (serverLevel.getBlockState(basePos.below()).is(Blocks.END_STONE) && serverLevel.isEmptyBlock(basePos)) {
            serverLevel.setBlock(basePos, newFlower, 3);
        } else if (this.isTame()) {
            ItemStack flower = new ItemStack(Items.CHORUS_FLOWER);
            if (!this.storeInNearbyContainer(flower)) {
                this.spawnAtLocation(flower);
            }
        }
        this.growChorusCrystal();
    }

    // 驯服蜗牛会把产物优先放进附近容器。
    private boolean storeInNearbyContainer(ItemStack stack) {
        if (!this.isTame() || stack.isEmpty()) {
            return false;
        }
        BlockPos center = this.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-CONTAINER_SEARCH_RANGE, -3, -CONTAINER_SEARCH_RANGE), center.offset(CONTAINER_SEARCH_RANGE, 3, CONTAINER_SEARCH_RANGE))) {
            BlockEntity blockEntity = this.level().getBlockEntity(pos);
            if (blockEntity instanceof Container container) {
                for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
                    ItemStack existing = container.getItem(slot);
                    if (existing.isEmpty() && container.canPlaceItem(slot, stack)) {
                        container.setItem(slot, stack.copy());
                        stack.setCount(0);
                        container.setChanged();
                        return true;
                    }
                    if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < container.getMaxStackSize(existing) && container.canPlaceItem(slot, stack)) {
                        int moved = Math.min(stack.getCount(), container.getMaxStackSize(existing) - existing.getCount());
                        existing.grow(moved);
                        stack.shrink(moved);
                        container.setChanged();
                    }
                }
                if (stack.isEmpty()) {
                    return true;
                }
            }
        }
        return stack.isEmpty();
    }

    // 成熟紫颂花采集目标，只间歇扫描附近小范围，避免高频全量查找。
    private static final class HarvestMatureChorusGoal extends Goal {
        private final EnderSnail snail;
        @Nullable
        private BlockPos target;
        private int cooldown;

        // 创建成熟紫颂采集 AI。
        private HarvestMatureChorusGoal(EnderSnail snail) {
            this.snail = snail;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        // 间歇寻找可啃食的紫颂植株或成熟紫颂花。
        @Override
        public boolean canUse() {
            if (this.cooldown-- > 0 || this.snail.isBaby() && this.snail.juvenileCrystalGrowths >= JUVENILE_MOLT_THRESHOLD) {
                return false;
            }
            this.cooldown = 80 + this.snail.getRandom().nextInt(80);
            BlockPos center = this.snail.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-CHORUS_SEARCH_RANGE, -4, -CHORUS_SEARCH_RANGE), center.offset(CHORUS_SEARCH_RANGE, 10, CHORUS_SEARCH_RANGE))) {
                BlockState state = this.snail.level().getBlockState(pos);
                if (state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER) && state.getValue(ChorusFlowerBlock.AGE) >= ChorusFlowerBlock.DEAD_AGE) {
                    this.target = pos.immutable();
                    return true;
                }
            }
            return false;
        }

        // 目标仍是可啃食的紫颂植株时继续移动。
        @Override
        public boolean canContinueToUse() {
            if (this.target == null) {
                return false;
            }
            BlockState state = this.snail.level().getBlockState(this.target);
            return state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER);
        }

        // 开始向成熟紫颂花移动。
        @Override
        public void start() {
            if (this.target != null) {
                this.snail.getNavigation().moveTo(this.target.getX() + 0.5D, this.target.getY(), this.target.getZ() + 0.5D, 0.7D);
            }
        }

        // 靠近后采集整棵紫颂植株。
        @Override
        public void tick() {
            if (this.target == null) {
                return;
            }
            this.snail.getLookControl().setLookAt(this.target.getX() + 0.5D, this.target.getY() + 0.5D, this.target.getZ() + 0.5D);
            if (this.snail.distanceToSqr(this.target.getX() + 0.5D, this.target.getY(), this.target.getZ() + 0.5D) <= 6.25D) {
                this.snail.harvestChorusPlant(this.target);
                this.target = null;
                this.snail.getNavigation().stop();
            } else if (this.snail.getNavigation().isDone()) {
                this.snail.getNavigation().moveTo(this.target.getX() + 0.5D, this.target.getY(), this.target.getZ() + 0.5D, 0.7D);
            }
        }

        // 清理目标。
        @Override
        public void stop() {
            this.target = null;
        }
    }

    // 驯服蜗牛会去蹭未成熟紫颂花，并直接催生成紫颂树。
    private static final class RubChorusFlowerGoal extends Goal {
        private final EnderSnail snail;
        @Nullable
        private BlockPos target;
        private int cooldown;

        // 创建紫颂催熟 AI。
        private RubChorusFlowerGoal(EnderSnail snail) {
            this.snail = snail;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        // 驯服蜗牛间歇寻找玩家种下或附近未成熟的紫颂花。
        @Override
        public boolean canUse() {
            if (!this.snail.isTame() || this.cooldown-- > 0) {
                return false;
            }
            this.cooldown = 60 + this.snail.getRandom().nextInt(60);
            BlockPos center = this.snail.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-CHORUS_SEARCH_RANGE, -3, -CHORUS_SEARCH_RANGE), center.offset(CHORUS_SEARCH_RANGE, 5, CHORUS_SEARCH_RANGE))) {
                BlockState state = this.snail.level().getBlockState(pos);
                if (state.is(Blocks.CHORUS_FLOWER) && state.getValue(ChorusFlowerBlock.AGE) < ChorusFlowerBlock.DEAD_AGE) {
                    this.target = pos.immutable();
                    return true;
                }
            }
            return false;
        }

        // 目标仍是未成熟紫颂花时继续催熟。
        @Override
        public boolean canContinueToUse() {
            return this.target != null && this.snail.level().getBlockState(this.target).is(Blocks.CHORUS_FLOWER) && this.snail.level().getBlockState(this.target).getValue(ChorusFlowerBlock.AGE) < ChorusFlowerBlock.DEAD_AGE;
        }

        // 开始向紫颂花移动。
        @Override
        public void start() {
            if (this.target != null) {
                this.snail.getNavigation().moveTo(this.target.getX() + 0.5D, this.target.getY(), this.target.getZ() + 0.5D, 0.75D);
            }
        }

        // 靠近后用原版紫颂树生成逻辑催熟。
        @Override
        public void tick() {
            if (this.target == null) {
                return;
            }
            this.snail.getLookControl().setLookAt(this.target.getX() + 0.5D, this.target.getY() + 0.5D, this.target.getZ() + 0.5D);
            if (this.snail.distanceToSqr(this.target.getX() + 0.5D, this.target.getY(), this.target.getZ() + 0.5D) <= 4.0D && this.snail.level() instanceof ServerLevel serverLevel) {
                if (serverLevel.getBlockState(this.target).is(Blocks.CHORUS_FLOWER) && serverLevel.getBlockState(this.target.below()).is(Blocks.END_STONE)) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL, this.target.getX() + 0.5D, this.target.getY() + 0.75D, this.target.getZ() + 0.5D, 24, 0.35D, 0.45D, 0.35D, 0.04D);
                    ChorusFlowerBlock.generatePlant(serverLevel, this.target, serverLevel.random, 8);
                    serverLevel.sendParticles(ParticleTypes.PORTAL, this.target.getX() + 0.5D, this.target.getY() + 0.75D, this.target.getZ() + 0.5D, 32, 0.5D, 0.8D, 0.5D, 0.08D);
                }
                this.target = null;
                this.snail.getNavigation().stop();
            } else if (this.snail.getNavigation().isDone()) {
                this.snail.getNavigation().moveTo(this.target.getX() + 0.5D, this.target.getY(), this.target.getZ() + 0.5D, 0.75D);
            }
        }

        // 清理目标。
        @Override
        public void stop() {
            this.target = null;
        }
    }
}
