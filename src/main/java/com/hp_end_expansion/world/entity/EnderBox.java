package com.hp_end_expansion.world.entity;

import com.hp_end_expansion.registry.ModItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class EnderBox extends TamableAnimal implements Container, GeoEntity {
    // 末影盒容器容量和存档字段名。
    public static final int CONTAINER_SIZE = 54;
    private static final String ITEMS_TAG = "Items";
    private static final String INITIAL_LOOT_GRANTED_TAG = "InitialLootGranted";
    // 末影盒 GeckoLib 动画资源名。
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("animation.ender_box.idle");
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("animation.ender_box.walk");
    private static final RawAnimation OPEN_ANIMATION = RawAnimation.begin().thenPlay("animation.ender_box.open");
    private static final RawAnimation TAME_ANIMATION = RawAnimation.begin().thenPlay("animation.ender_box.tame");
    private static final RawAnimation PACK_ANIMATION = RawAnimation.begin().thenPlay("animation.ender_box.pack");
    // 末影盒内部物品、动画缓存和初始战利品状态。
    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean initialLootGranted;

    // 创建末影盒实体实例。
    public EnderBox(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    // 创建末影盒基础属性。
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    // 注册跟随主人、随机闲逛和观察玩家的基础 AI。
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0, 6.0F, 2.0F));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.8, 80));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    // 紫颂果作为末影盒的驯服食物。
    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.CHORUS_FRUIT);
    }

    // 处理紫颂果驯服、主人开箱和主人收纳为物品。
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 未驯服时吃紫颂果完成驯服，并给一次末地城宝箱战利品。
        if (!this.isTame() && stack.is(Items.CHORUS_FRUIT)) {
            if (!this.level().isClientSide) {
                stack.consume(1, player);
                this.tame(player);
                this.setOrderedToSit(false);
                // 驯服成功时只生成一次初始战利品。
                if (!this.initialLootGranted && this.level() instanceof ServerLevel serverLevel) {
                    LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.END_CITY_TREASURE);
                    LootParams lootParams = new LootParams.Builder(serverLevel)
                            .withParameter(LootContextParams.ORIGIN, this.position())
                            .withOptionalParameter(LootContextParams.THIS_ENTITY, this)
                            .create(LootContextParamSets.CHEST);
                    ObjectArrayList<ItemStack> generatedLoot = lootTable.getRandomItems(lootParams, this.random);
                    generatedLoot.removeIf(ItemStack::isEmpty);
                    if (!generatedLoot.isEmpty()) {
                        this.setItem(this.random.nextInt(this.items.size()), generatedLoot.get(this.random.nextInt(generatedLoot.size())).copy());
                    }
                    this.initialLootGranted = true;
                }
                this.triggerAnim("main", "tame");
                this.level().broadcastEntityEvent(this, (byte)7);
                this.setPersistenceRequired();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 已驯服且属于当前玩家时，右键开箱，潜行右键收纳成物品。
        if (this.isTame() && this.isOwnedBy(player)) {
            if (!this.level().isClientSide) {
                if (player.isShiftKeyDown()) {
                    this.triggerAnim("main", "pack");
                    ItemStack enderBoxStack = new ItemStack(ModItems.ENDER_BOX.get());
                    enderBoxStack.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(this.items));
                    this.spawnAtLocation(enderBoxStack);
                    this.discard();
                } else {
                    this.triggerAnim("main", "open");
                    player.openMenu(new SimpleMenuProvider((containerId, inventory, owner) -> ChestMenu.sixRows(containerId, inventory, this), this.getDisplayName()));
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    // 末影盒当前免疫普通伤害，避免作为容器宠物被误伤打碎。
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    // 末影盒不会因为离玩家太远而自然消失。
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // 末影盒需要持久化，保证容器内容不会因卸载丢失。
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // 末影盒当前不繁殖。
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    // 末影盒当前不允许交配。
    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal otherAnimal) {
        return false;
    }

    // 保存容器内容和初始战利品状态。
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        ContainerHelper.saveAllItems(compound, this.items, this.registryAccess());
        compound.putBoolean(INITIAL_LOOT_GRANTED_TAG, this.initialLootGranted);
    }

    // 读取容器内容和初始战利品状态。
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        for (int i = 0; i < this.items.size(); i++) {
            this.items.set(i, ItemStack.EMPTY);
        }
        if (compound.contains(ITEMS_TAG)) {
            ContainerHelper.loadAllItems(compound, this.items, this.registryAccess());
        }
        this.initialLootGranted = compound.getBoolean(INITIAL_LOOT_GRANTED_TAG);
    }

    // 注册待机、行走和触发动画控制器。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            double horizontalMovement = this.getDeltaMovement().x * this.getDeltaMovement().x + this.getDeltaMovement().z * this.getDeltaMovement().z;
            state.setAnimation(horizontalMovement > 1.0E-5 ? WALK_ANIMATION : IDLE_ANIMATION);
            return PlayState.CONTINUE;
        }).triggerableAnim("open", OPEN_ANIMATION).triggerableAnim("tame", TAME_ANIMATION).triggerableAnim("pack", PACK_ANIMATION));
    }

    // 返回 GeckoLib 动画缓存。
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // 返回末影盒容器容量。
    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    // 判断末影盒容器是否为空。
    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // 读取指定槽位物品。
    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.items.size() ? this.items.get(slot) : ItemStack.EMPTY;
    }

    // 从指定槽位移除指定数量物品。
    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = ContainerHelper.removeItem(this.items, slot, amount);
        if (!stack.isEmpty()) {
            this.setChanged();
        }
        return stack;
    }

    // 不触发更新地移除指定槽位物品。
    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot >= 0 && slot < this.items.size()) {
            ItemStack stack = this.items.get(slot);
            this.items.set(slot, ItemStack.EMPTY);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    // 设置指定槽位物品并限制最大堆叠。
    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        this.setChanged();
    }

    // 容器变更由实体存档负责，这里保留接口实现。
    @Override
    public void setChanged() {
    }

    // 玩家必须靠近且实体存活时才能继续使用容器。
    @Override
    public boolean stillValid(Player player) {
        return this.isAlive() && this.distanceToSqr(player) <= 64.0;
    }

    // 清空末影盒容器内容。
    @Override
    public void clearContent() {
        this.items.clear();
    }
}
