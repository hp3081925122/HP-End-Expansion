package com.hp_end_expansion.world.item;

import com.hp_end_expansion.world.EndShipTeleportation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class EndShipCompassItem extends Item {
    // 创建只能单个堆叠的末地船罗盘。
    public EndShipCompassItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // 右键消耗罗盘，把玩家传送到最近的末地船目的地。
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.fail(stack);
        }
        boolean teleported = EndShipTeleportation.teleportToNearestEndShip(serverLevel, player);
        if (!teleported) {
            player.displayClientMessage(Component.translatable("message.hp_end_expansion.end_ship_not_found"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.hp_end_expansion.end_ship_compass_used"), true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
