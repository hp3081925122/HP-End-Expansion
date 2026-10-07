package org.hp.hp_end_expansion.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;
import org.hp.hp_end_expansion.item.DevourerWingsItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerFallFlyingMixin {
    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void hp_tryStartFallFlying(CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        if (!player.onGround() && !player.isFallFlying() && !player.isInWater() && !player.hasEffect(MobEffects.LEVITATION)) {
            ItemStack wings = DevourerWingsItem.getFlightStack(player, net.minecraft.world.entity.EquipmentSlot.CHEST);
            if (wings.getItem() instanceof DevourerWingsItem && wings.canElytraFly(player)) {
                player.startFallFlying();
                cir.setReturnValue(true);
            }
        }
    }
}
