package com.hp_end_expansion.event;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.item.EnderSnailShellArmorData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = HpEndExpansion.MODID)
public final class CommonEvents {
    // 死亡保护的随机传送半径和每个蜗牛壳回复的最大生命比例。
    private static final int SHELL_TELEPORT_RADIUS = 20;
    private static final float HEAL_PER_SHELL = 0.15F;
    // 人形盔甲槽位列表。
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // 通用事件类只提供静态事件入口，不允许实例化。
    private CommonEvents() {
    }

    // 玩家死亡时消耗带壳盔甲上的所有蜗牛壳层数，随机传送到附近安全位置并回血。
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent 事件) {
        if (!(事件.getEntity() instanceof ServerPlayer 玩家)) {
            return;
        }
        int 蜗牛壳数量 = 0;
        for (EquipmentSlot 槽位 : ARMOR_SLOTS) {
            蜗牛壳数量 += EnderSnailShellArmorData.getShellCount(玩家.getItemBySlot(槽位));
        }
        if (蜗牛壳数量 <= 0 || !(玩家.level() instanceof ServerLevel 服务端世界)) {
            return;
        }

        BlockPos 安全位置 = null;
        BlockPos 玩家位置 = 玩家.blockPosition();
        for (int 索引 = 0; 索引 < 64; 索引++) {
            int 横坐标 = 玩家位置.getX() + 玩家.getRandom().nextInt(SHELL_TELEPORT_RADIUS * 2 + 1) - SHELL_TELEPORT_RADIUS;
            int 纵坐标 = 玩家位置.getZ() + 玩家.getRandom().nextInt(SHELL_TELEPORT_RADIUS * 2 + 1) - SHELL_TELEPORT_RADIUS;
            int 目标Y = Mth.clamp(玩家位置.getY() + 玩家.getRandom().nextInt(17) - 8, 服务端世界.getMinBuildHeight() + 1, 服务端世界.getMaxBuildHeight() - 2);
            BlockPos 候选位置 = new BlockPos(横坐标, 目标Y, 纵坐标);
            while (候选位置.getY() > 服务端世界.getMinBuildHeight() + 1 && 服务端世界.getBlockState(候选位置.below()).isAir()) {
                候选位置 = 候选位置.below();
            }
            BlockState 脚部状态 = 服务端世界.getBlockState(候选位置);
            BlockState 头部状态 = 服务端世界.getBlockState(候选位置.above());
            if (!服务端世界.getBlockState(候选位置.below()).blocksMotion() || !脚部状态.getCollisionShape(服务端世界, 候选位置).isEmpty() || !头部状态.getCollisionShape(服务端世界, 候选位置.above()).isEmpty() || 服务端世界.containsAnyLiquid(玩家.getBoundingBox().move(候选位置.getBottomCenter().subtract(玩家.position())))) {
                continue;
            }
            玩家.teleportTo(候选位置.getX() + 0.5D, 候选位置.getY(), 候选位置.getZ() + 0.5D);
            if (服务端世界.noCollision(玩家) && !服务端世界.containsAnyLiquid(玩家.getBoundingBox())) {
                安全位置 = 候选位置;
                break;
            }
            玩家.teleportTo(玩家位置.getX() + 0.5D, 玩家位置.getY(), 玩家位置.getZ() + 0.5D);
        }
        if (安全位置 == null) {
            return;
        }

        for (EquipmentSlot 槽位 : ARMOR_SLOTS) {
            EnderSnailShellArmorData.setShellCount(玩家.getItemBySlot(槽位), 0);
        }
        事件.setCanceled(true);
        玩家.setHealth(Math.max(1.0F, 玩家.getMaxHealth() * Math.min(1.0F, 蜗牛壳数量 * HEAL_PER_SHELL)));
        玩家.fallDistance = 0.0F;
        玩家.displayClientMessage(Component.translatable("message.hp_end_expansion.ender_snail_shell_saved", 蜗牛壳数量), true);
    }

    // 在带壳盔甲 tooltip 上显示当前蜗牛壳层数。
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent 事件) {
        ItemStack 物品栈 = 事件.getItemStack();
        if (!(物品栈.getItem() instanceof ArmorItem)) {
            return;
        }
        int 蜗牛壳数量 = EnderSnailShellArmorData.getShellCount(物品栈);
        if (蜗牛壳数量 > 0) {
            事件.getToolTip().add(Component.translatable("item.hp_end_expansion.ender_snail_shell.armor_shells", 蜗牛壳数量));
        }
    }
}
