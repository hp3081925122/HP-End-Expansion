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
    private final NonNullList<ItemStack> 物品列表 = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean 已给予初始战利品;

    // 创建末影盒实体实例。
    public EnderBox(EntityType<? extends TamableAnimal> 实体类型, Level 世界) {
        super(实体类型, 世界);
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
    public boolean isFood(ItemStack 物品栈) {
        return 物品栈.is(Items.CHORUS_FRUIT);
    }

    // 处理紫颂果驯服、主人开箱和主人收纳为物品。
    @Override
    public InteractionResult mobInteract(Player 玩家, InteractionHand 手) {
        ItemStack 物品栈 = 玩家.getItemInHand(手);
        // 未驯服时吃紫颂果完成驯服，并给一次末地城宝箱战利品。
        if (!this.isTame() && 物品栈.is(Items.CHORUS_FRUIT)) {
            if (!this.level().isClientSide) {
                物品栈.consume(1, 玩家);
                this.tame(玩家);
                this.setOrderedToSit(false);
                // 驯服成功时只生成一次初始战利品。
                if (!this.已给予初始战利品 && this.level() instanceof ServerLevel 服务端世界) {
                    LootTable 战利品表 = 服务端世界.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.END_CITY_TREASURE);
                    LootParams 战利品参数 = new LootParams.Builder(服务端世界)
                            .withParameter(LootContextParams.ORIGIN, this.position())
                            .withOptionalParameter(LootContextParams.THIS_ENTITY, this)
                            .create(LootContextParamSets.CHEST);
                    ObjectArrayList<ItemStack> 生成战利品 = 战利品表.getRandomItems(战利品参数, this.random);
                    生成战利品.removeIf(ItemStack::isEmpty);
                    if (!生成战利品.isEmpty()) {
                        this.setItem(this.random.nextInt(this.物品列表.size()), 生成战利品.get(this.random.nextInt(生成战利品.size())).copy());
                    }
                    this.已给予初始战利品 = true;
                }
                this.triggerAnim("main", "tame");
                this.level().broadcastEntityEvent(this, (byte)7);
                this.setPersistenceRequired();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 已驯服且属于当前玩家时，右键开箱，潜行右键收纳成物品。
        if (this.isTame() && this.isOwnedBy(玩家)) {
            if (!this.level().isClientSide) {
                if (玩家.isShiftKeyDown()) {
                    this.triggerAnim("main", "pack");
                    ItemStack 末影盒物品栈 = new ItemStack(ModItems.ENDER_BOX.get());
                    末影盒物品栈.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(this.物品列表));
                    this.spawnAtLocation(末影盒物品栈);
                    this.discard();
                } else {
                    this.triggerAnim("main", "open");
                    玩家.openMenu(new SimpleMenuProvider((容器ID, 玩家背包, 拥有者) -> ChestMenu.sixRows(容器ID, 玩家背包, this), this.getDisplayName()));
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(玩家, 手);
    }

    // 末影盒当前免疫普通伤害，避免作为容器宠物被误伤打碎。
    @Override
    public boolean hurt(DamageSource 伤害来源, float 伤害量) {
        return false;
    }

    // 末影盒不会因为离玩家太远而自然消失。
    @Override
    public boolean removeWhenFarAway(double 最近玩家距离) {
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
    public AgeableMob getBreedOffspring(ServerLevel 世界, AgeableMob 另一亲代) {
        return null;
    }

    // 末影盒当前不允许交配。
    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal 另一动物) {
        return false;
    }

    // 保存容器内容和初始战利品状态。
    @Override
    public void addAdditionalSaveData(CompoundTag 复合标签) {
        super.addAdditionalSaveData(复合标签);
        ContainerHelper.saveAllItems(复合标签, this.物品列表, this.registryAccess());
        复合标签.putBoolean(INITIAL_LOOT_GRANTED_TAG, this.已给予初始战利品);
    }

    // 读取容器内容和初始战利品状态。
    @Override
    public void readAdditionalSaveData(CompoundTag 复合标签) {
        super.readAdditionalSaveData(复合标签);
        for (int 索引 = 0; 索引 < this.物品列表.size(); 索引++) {
            this.物品列表.set(索引, ItemStack.EMPTY);
        }
        if (复合标签.contains(ITEMS_TAG)) {
            ContainerHelper.loadAllItems(复合标签, this.物品列表, this.registryAccess());
        }
        this.已给予初始战利品 = 复合标签.getBoolean(INITIAL_LOOT_GRANTED_TAG);
    }

    // 注册待机、行走和触发动画控制器。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar 控制器注册器) {
        控制器注册器.add(new AnimationController<>(this, "main", 5, 状态 -> {
            double 水平移动量 = this.getDeltaMovement().x * this.getDeltaMovement().x + this.getDeltaMovement().z * this.getDeltaMovement().z;
            状态.setAnimation(水平移动量 > 1.0E-5 ? WALK_ANIMATION : IDLE_ANIMATION);
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
        for (ItemStack 物品栈 : this.物品列表) {
            if (!物品栈.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // 读取指定槽位物品。
    @Override
    public ItemStack getItem(int 槽位) {
        return 槽位 >= 0 && 槽位 < this.物品列表.size() ? this.物品列表.get(槽位) : ItemStack.EMPTY;
    }

    // 从指定槽位移除指定数量物品。
    @Override
    public ItemStack removeItem(int 槽位, int 伤害量) {
        ItemStack 物品栈 = ContainerHelper.removeItem(this.物品列表, 槽位, 伤害量);
        if (!物品栈.isEmpty()) {
            this.setChanged();
        }
        return 物品栈;
    }

    // 不触发更新地移除指定槽位物品。
    @Override
    public ItemStack removeItemNoUpdate(int 槽位) {
        if (槽位 >= 0 && 槽位 < this.物品列表.size()) {
            ItemStack 物品栈 = this.物品列表.get(槽位);
            this.物品列表.set(槽位, ItemStack.EMPTY);
            return 物品栈;
        }
        return ItemStack.EMPTY;
    }

    // 设置指定槽位物品并限制最大堆叠。
    @Override
    public void setItem(int 槽位, ItemStack 物品栈) {
        this.物品列表.set(槽位, 物品栈);
        物品栈.limitSize(this.getMaxStackSize(物品栈));
        this.setChanged();
    }

    // 容器变更由实体存档负责，这里保留接口实现。
    @Override
    public void setChanged() {
    }

    // 玩家必须靠近且实体存活时才能继续使用容器。
    @Override
    public boolean stillValid(Player 玩家) {
        return this.isAlive() && this.distanceToSqr(玩家) <= 64.0;
    }

    // 清空末影盒容器内容。
    @Override
    public void clearContent() {
        this.物品列表.clear();
    }
}
