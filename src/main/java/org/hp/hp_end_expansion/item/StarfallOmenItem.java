package org.hp.hp_end_expansion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.hp.hp_end_expansion.entity.starwreck.StarRain;
import org.hp.hp_end_expansion.worldgen.StarwreckWorldgen;

/** 坠星兆石：在星骸荒原右键，于身边落下一场星雨。 */
public final class StarfallOmenItem extends Item {
    public StarfallOmenItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.hp_end_expansion.starfall_omen").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (!server.getBiome(player.blockPosition()).is(StarwreckWorldgen.BIOME)) {
            player.sendOverlayMessage(Component.translatable("message.hp_end_expansion.starfall_omen.wrong_biome"));
            return InteractionResult.FAIL;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.FAIL;
        StarRain.Result result = StarRain.summonNear(server, serverPlayer);
        if (result == StarRain.Result.BUSY) {
            player.sendOverlayMessage(Component.translatable("message.hp_end_expansion.starfall_omen.busy"));
            return InteractionResult.FAIL;
        }
        if (result != StarRain.Result.STARTED) {
            player.sendOverlayMessage(Component.translatable("message.hp_end_expansion.starfall_omen.no_site"));
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        return use(context.getLevel(), player, context.getHand());
    }
}
