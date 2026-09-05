package com.hp_end_expansion.content.prismatic.entity;

import com.hp_end_expansion.content.prismatic.PrismaticContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

// 生态生物共用繁殖和产物存档；冷却只在实体加载时推进。
public abstract class PrismaticAnimal extends Animal implements PrismaticAnimated {
    private static final EntityDataAccessor<Integer> PRODUCT_STAGE = SynchedEntityData.defineId(PrismaticAnimal.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int productCooldown;
    private int productReadyTicks;
    private boolean productFed;

    protected PrismaticAnimal(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    // 物种覆写食物和产物，普通棱兔只有繁殖能力。
    protected String foodId() { return "crystal_bud"; }
    protected String productId() { return ""; }
    protected int productionDelay() { return 200; }
    protected int productionCooldown() { return 2400; }
    protected boolean usesGroundWander() { return true; }
    protected boolean performingAction() { return false; }

    // 只同步状态切换，不发送每刻倒计时；二表示产物已经成熟。
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PRODUCT_STAGE, 0);
    }

    public int productStage() { return entityData.get(PRODUCT_STAGE); }

    // 喂食和自主觅食共享一个生产槽，外部无法绕过冷却获得额外产物。
    protected boolean beginProduction() {
        if (isBaby() || productId().isEmpty() || productFed || productCooldown > 0) { return false; }
        productFed = true;
        productReadyTicks = productionDelay();
        entityData.set(PRODUCT_STAGE, 1);
        setPersistenceRequired();
        triggerAnim("main", "special");
        return true;
    }

    public int productCooldownTicks() { return productCooldown; }
    public int productReadyTicks() { return productReadyTicks; }
    public boolean hasPendingProduct() { return productFed; }

    // 复用原版繁殖、引诱、跟随与受伤逃跑，不消耗自然植被。
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        if (!(this instanceof FacetRam)) {
            goalSelector.addGoal(1, new PanicGoal(this, 1.5));
        }
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        goalSelector.addGoal(3, new TemptGoal(this, 1.2, this::isFood, false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        if (usesGroundWander()) { goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0)); }
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // 飞行属性也注册给同组生物，只有暮翅蛾的移动控制器会使用。
    public static AttributeSupplier.Builder attributes(double health, double speed, double damage) {
        return Animal.createMobAttributes().add(Attributes.MAX_HEALTH, health)
                .add(Attributes.MOVEMENT_SPEED, speed).add(Attributes.FLYING_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, damage).add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(PrismaticContent.item(foodId()));
    }

    // 玩家互动优先处理成熟产物；喂食同时沿用原版繁殖规则。
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!productId().isEmpty() && !isBaby() && stack.isEmpty() && productFed) {
            if (!level().isClientSide) {
                if (productReadyTicks > 0) {
                    player.displayClientMessage(Component.translatable("message.hp_end_expansion.prismatic_product_growing", (productReadyTicks + 19) / 20), true);
                } else {
                    ItemStack product = new ItemStack(PrismaticContent.item(productId()));
                    if (!player.addItem(product)) { player.drop(product, false); }
                    productFed = false;
                    productCooldown = productionCooldown();
                    entityData.set(PRODUCT_STAGE, 0);
                    setPersistenceRequired();
                    triggerAnim("main", "special");
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isFood(stack)) {
            if (level().isClientSide) { return InteractionResult.CONSUME; }
            int countBefore = stack.getCount();
            InteractionResult result = super.mobInteract(player, hand);
            if (beginProduction()) {
                if (stack.getCount() == countBefore) { usePlayerItem(player, hand, stack); }
                return InteractionResult.SUCCESS;
            }
            if (result.consumesAction()) { setPersistenceRequired(); }
            return result;
        }
        return super.mobInteract(player, hand);
    }

    // 产物计时仅服务端更新，重新加载时不会清零。
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive()) {
            if (productCooldown > 0) { productCooldown--; }
            if (productReadyTicks > 0) { productReadyTicks--; }
            entityData.set(PRODUCT_STAGE, productFed ? productReadyTicks > 0 ? 1 : 2 : 0);
        } else if (level().isClientSide && productStage() == 1 && tickCount % 30 == 0) {
            level().addParticle(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.75, getZ(), 0, 0.01, 0);
        }
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        PrismaticAnimal child = (PrismaticAnimal) getType().create(level);
        if (child != null) { child.setPersistenceRequired(); }
        return child;
    }

    // 动画只在伤害生效时触发一次，死亡改用完整死亡动作。
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);
        if (damaged && !level().isClientSide && isAlive() && !performingAction()) { triggerAnim("main", "hurt"); }
        return damaged;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) { triggerAnim("main", "death"); }
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("PrismaticProductCooldown", productCooldown);
        tag.putInt("PrismaticProductReadyTicks", productReadyTicks);
        tag.putBoolean("PrismaticProductFed", productFed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        productCooldown = Math.max(0, tag.getInt("PrismaticProductCooldown"));
        productReadyTicks = Math.max(0, tag.getInt("PrismaticProductReadyTicks"));
        productFed = tag.getBoolean("PrismaticProductFed");
        entityData.set(PRODUCT_STAGE, productFed ? productReadyTicks > 0 ? 1 : 2 : 0);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
