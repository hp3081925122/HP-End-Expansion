package com.hp_end_expansion.content.prismatic.item;

import com.hp_end_expansion.content.prismatic.block.PrismaticPlantBlock;
import com.hp_end_expansion.content.prismatic.block.PrismaticSaplingBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BonemealableBlock;

public class PrismMealItem extends PrismaticMaterialItem {
    // 棱壤粉是碎晶虫的可再生产物，只催熟本群系植物。
    public PrismMealItem(Properties properties) { super("prism_meal", properties); }

    // 先验证目标，再由服务端施肥并按玩家模式扣除物品。
    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PrismaticPlantBlock || state.getBlock() instanceof PrismaticSaplingBlock)) return InteractionResult.PASS;
        var plant = (BonemealableBlock) state.getBlock();
        if (!plant.isValidBonemealTarget(level, pos, state)) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            plant.performBonemeal(server, server.random, pos, state);
            context.getItemInHand().consume(1, context.getPlayer());
            level.levelEvent(1505, pos, 15);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
