package org.hp.hp_end_expansion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.hp.hp_end_expansion.entity.RiftMatriarchEntity;
import org.hp.hp_end_expansion.registry.ModEntities;

public final class RiftEggItem extends Item {
    public RiftEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.rift_egg").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        // 仅末地可召唤
        if (level.dimension() != Level.END) {
            if (!level.isClientSide() && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("message.hp_end_expansion.rift_egg.wrong_dimension"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        // 在点击方块上方召唤螳后
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        RiftMatriarchEntity boss = ModEntities.RIFT_MATRIARCH.get().spawn(serverLevel, pos, MobSpawnType.TRIGGERED);
        if (boss == null) {
            return InteractionResult.FAIL;
        }
        boss.setSummonedByEgg(true);
        serverLevel.playSound(null, pos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0F, 1.2F);
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
