package com.hp_end_expansion.content.prismatic.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class PrismaticMaterialItem extends Item {
    // 短名称决定物品自己的用途提示，不保存额外全局状态。
    private final String id;

    // 材料、钥匙和护符共用稳定的说明入口。
    public PrismaticMaterialItem(String id, Properties properties) { super(properties); this.id = id; }

    // 所有用途文本可本地化，帮助玩家在游戏内理解采集与探索链。
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.hp_end_expansion.prismatic_" + id));
    }

    // Boss 护符提供八秒抗性和三十秒冷却，冷却由原版同步到客户端。
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!id.equals("refraction_charm")) return super.use(level, player, hand);
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 0));
            player.getCooldowns().addCooldown(this, 600);
            player.displayClientMessage(Component.translatable("message.hp_end_expansion.prismatic_charm_active"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
