package org.hp.hp_end_expansion.mixin;

import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.hp.hp_end_expansion.item.TideCrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class TideCrossbowEquipmentMixin {
    @Inject(method = "collectEquipmentChanges", at = @At("RETURN"))
    private void hp_restoreCrossbowCapacity(CallbackInfoReturnable<Map<EquipmentSlot, ItemStack>> result) {
        if (result.getReturnValue() != null) TideCrossbowItem.restoreCapacityModifier((LivingEntity) (Object) this);
    }
}
