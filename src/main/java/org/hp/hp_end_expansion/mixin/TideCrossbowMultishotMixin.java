package org.hp.hp_end_expansion.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.hp.hp_end_expansion.item.TideCrossbowItem;
import org.hp.hp_end_expansion.item.TideRemnantClawItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentHelper.class)
public abstract class TideCrossbowMultishotMixin {
    @Inject(method = "processProjectileCount", at = @At("RETURN"), cancellable = true)
    private static void hp_projectileCount(ServerLevel level, ItemStack weapon, Entity shooter, int baseCount, CallbackInfoReturnable<Integer> result) {
        if (!(weapon.getItem() instanceof TideCrossbowItem) || !(shooter instanceof LivingEntity living)
            || result.getReturnValueI() <= 0 || !TideRemnantClawItem.isEquipped(living)) return;
        result.setReturnValue(result.getReturnValueI() + TideCrossbowItem.capacity(living) / 2);
    }

    @Inject(method = "processProjectileSpread", at = @At("RETURN"), cancellable = true)
    private static void hp_projectileSpread(ServerLevel level, ItemStack weapon, Entity shooter, float baseSpread, CallbackInfoReturnable<Float> result) {
        if (weapon.getItem() instanceof TideCrossbowItem) result.setReturnValue(Math.max(10.0F, result.getReturnValueF()));
    }
}
