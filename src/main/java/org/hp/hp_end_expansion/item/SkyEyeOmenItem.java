package org.hp.hp_end_expansion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.starwreck.SkyrenderEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarRain;
import org.hp.hp_end_expansion.worldgen.StarwreckWorldgen;

public final class SkyEyeOmenItem extends Item {
    public SkyEyeOmenItem(Properties properties) { super(properties); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.sky_eye_omen").withStyle(ChatFormatting.GRAY));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(stack);
        // 只能在星骸荒原使用；同维度只有一只，星雨期间不行
        if (!server.getBiome(player.blockPosition()).is(StarwreckWorldgen.BIOME)) return fail(player, stack, "wrong_biome");
        if (SkyrenderEntity.present(server)) return fail(player, stack, "present");
        if (StarRain.active(server)) return fail(player, stack, "starfall");
        Vec3 center = player.position();
        double radius = 28.0D;
        if (SkyrenderEntity.summon(server, center, radius, player) == null) return fail(player, stack, "no_room");
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    private static InteractionResultHolder<ItemStack> fail(Player player, ItemStack stack, String key) {
        player.displayClientMessage(Component.translatable("message.hp_end_expansion.sky_eye_omen." + key), true);
        return InteractionResultHolder.fail(stack);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        return use(context.getLevel(), player, context.getHand()).getResult();
    }
}
