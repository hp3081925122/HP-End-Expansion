package org.hp.hp_end_expansion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

// 鳐王之翼：鞘翅，滑翔中潜行可向视线方向冲刺，冷却 6 秒
public final class DevourerWingsItem extends ElytraItem {
    private static final int DASH_COOLDOWN = 120;

    public DevourerWingsItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.devourer_wings").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        boolean result = super.elytraFlightTick(stack, entity, flightTicks);
        // 潜行冲刺
        if (!entity.level().isClientSide() && entity instanceof Player player && player.isShiftKeyDown()
            && !player.getCooldowns().isOnCooldown(this)) {
            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(player.getDeltaMovement().add(look.scale(1.6D)));
            player.hurtMarked = true;
            player.getCooldowns().addCooldown(this, DASH_COOLDOWN);
            // 冲刺特效与音效
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.5D, player.getZ(), 20, 0.4D, 0.4D, 0.4D, 0.1D);
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.5F, 0.6F);
        }
        return result;
    }
}
