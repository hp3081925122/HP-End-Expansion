package org.hp.hp_end_expansion.item;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.hp.hp_end_expansion.client.StarCoreChestplateRenderer;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** 星核胸甲：逐星兽的星核余烬做成的胸甲，受击时有概率把余烬反溅到攻击者身上。 */
public final class StarCoreChestplateItem extends ArmorItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.star_core_chestplate.idle");
    private static final float SPLASH_CHANCE = 0.25F, SPLASH_DAMAGE = 3;
    private static final int SPLASH_FIRE_SECONDS = 3;
    private static final double SPLASH_RANGE = 6;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StarCoreChestplateItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, Type.CHESTPLATE, properties);
    }

    // 反溅伤害记为荆棘，两个穿着星核胸甲的人互砍时不会来回反弹
    public static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity wearer = event.getEntity();
        DamageSource source = event.getSource();
        if (event.getNewDamage() <= 0 || !(wearer.level() instanceof ServerLevel level)) return;
        if (!(wearer.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof StarCoreChestplateItem)) return;
        if (source.is(DamageTypes.THORNS) || source.is(DamageTypeTags.BYPASSES_ARMOR)) return;
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == wearer || !attacker.isAlive()) return;
        if (attacker.distanceToSqr(wearer) > SPLASH_RANGE * SPLASH_RANGE || wearer.getRandom().nextFloat() >= SPLASH_CHANCE) return;

        attacker.hurt(level.damageSources().thorns(wearer), SPLASH_DAMAGE);
        if (!attacker.fireImmune()) attacker.igniteForSeconds(SPLASH_FIRE_SECONDS);
        Vec3 from = wearer.position().add(0, wearer.getBbHeight() * 0.65, 0);
        Vec3 dir = attacker.position().add(0, attacker.getBbHeight() * 0.5, 0).subtract(from).normalize();
        for (int i = 0; i < 14; i++) {
            Vec3 v = dir.scale(0.35 + wearer.getRandom().nextDouble() * 0.3)
                .add(wearer.getRandom().nextGaussian() * 0.08, wearer.getRandom().nextDouble() * 0.12, wearer.getRandom().nextGaussian() * 0.08);
            level.sendParticles(ModParticles.STAR_EMBER.get(), from.x, from.y, from.z, 0, v.x, v.y, v.z, 1);
        }
        level.playSound(null, wearer.getX(), wearer.getY(), wearer.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.5F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.star_core_chestplate").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private StarCoreChestplateRenderer renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack,
                                                                                @Nullable EquipmentSlot equipmentSlot, @Nullable HumanoidModel<T> original) {
                if (renderer == null) renderer = new StarCoreChestplateRenderer();
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
