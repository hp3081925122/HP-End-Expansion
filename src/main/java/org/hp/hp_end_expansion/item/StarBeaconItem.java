package org.hp.hp_end_expansion.item;

import com.mojang.logging.LogUtils;
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
import org.hp.hp_end_expansion.entity.StarDevourerEntity;
import org.hp.hp_end_expansion.registry.ModEntities;
import org.slf4j.Logger;

public final class StarBeaconItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public StarBeaconItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.star_beacon").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        // 仅末地可召唤
        if (level.dimension() != Level.END) {
            LOGGER.debug("Star Beacon use rejected outside the End: {}", level.dimension());
            if (!level.isClientSide() && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("message.hp_end_expansion.star_beacon.wrong_dimension"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        // 在点击方块上方 12 格召唤鳐王
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace()).above(12);
        StarDevourerEntity boss = ModEntities.STAR_DEVOURER.get().spawn(serverLevel, pos, MobSpawnType.TRIGGERED);
        if (boss == null) {
            LOGGER.debug("Star Beacon failed to summon Star Devourer at {}", pos);
            return InteractionResult.FAIL;
        }
        boss.setSummonedByBeacon(true);
        serverLevel.playSound(null, pos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.7F);
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
