package org.hp.hp_end_expansion.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.LivingEntity;
import org.hp.hp_end_expansion.item.TideCrossbowItem;
import org.hp.hp_end_expansion.item.TidebreakerHammerCoreItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AbstractArrow.class)
public abstract class TideCrossbowArrowDamageMixin {
    @ModifyArg(method = "onHitEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 1)
    private float hp_hammerCoreDamage(float damage) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        int bonus = Math.max(0, arrow.getPersistentData().getInt(TidebreakerHammerCoreItem.ARROW_DAMAGE_KEY));
        if (bonus == 0 || !(arrow.getOwner() instanceof LivingEntity shooter)) return damage;
        return damage + Math.min(bonus, TideCrossbowItem.capacity(shooter));
    }
}
