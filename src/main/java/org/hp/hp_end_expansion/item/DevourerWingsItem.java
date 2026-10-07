package org.hp.hp_end_expansion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModItems;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class DevourerWingsItem extends Item implements ICurioItem {
    private static final int DASH_COOLDOWN = 60;

    public DevourerWingsItem(Properties properties) {
        super(properties);
    }

    public static ItemStack getFlightStack(LivingEntity entity, EquipmentSlot slot) {
        ItemStack equipped = entity.getItemBySlot(slot);
        if (slot != EquipmentSlot.CHEST || equipped.canElytraFly(entity)) return equipped;
        if (!(entity instanceof Player player)) return equipped;
        return CuriosApi.getCuriosInventory(player)
            .flatMap(handler -> handler.findFirstCurio(ModItems.DEVOURER_WINGS.get()))
            .filter(result -> result.slotContext().identifier().equals("back"))
            .map(result -> result.stack())
            .orElse(equipped);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.devourer_wings.flight").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.devourer_wings").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        if (!entity.level().isClientSide()) {
            int nextFlightTick = flightTicks + 1;
            if (nextFlightTick % 10 == 0) {
                entity.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ELYTRA_GLIDE);
            }
        }
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
        return true;
    }
}
