package org.hp.hp_end_expansion.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.hp.hp_end_expansion.item.DevourerWingsItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityFallFlyingMixin {
    @Redirect(
        method = "updateFallFlying",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private ItemStack hp_getFlightStack(LivingEntity entity, EquipmentSlot slot) {
        return DevourerWingsItem.getFlightStack(entity, slot);
    }
}
