package com.hp_end_expansion.event;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.item.EnderCoreItem;
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
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
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
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int shellCount = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            shellCount += EnderSnailShellArmorData.getShellCount(player.getItemBySlot(slot));
        }
        if (shellCount <= 0 || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos safePos = null;
        BlockPos playerPos = player.blockPosition();
        for (int i = 0; i < 64; i++) {
            int x = playerPos.getX() + player.getRandom().nextInt(SHELL_TELEPORT_RADIUS * 2 + 1) - SHELL_TELEPORT_RADIUS;
            int z = playerPos.getZ() + player.getRandom().nextInt(SHELL_TELEPORT_RADIUS * 2 + 1) - SHELL_TELEPORT_RADIUS;
            int y = Mth.clamp(playerPos.getY() + player.getRandom().nextInt(17) - 8, serverLevel.getMinBuildHeight() + 1, serverLevel.getMaxBuildHeight() - 2);
            BlockPos candidate = new BlockPos(x, y, z);
            while (candidate.getY() > serverLevel.getMinBuildHeight() + 1 && serverLevel.getBlockState(candidate.below()).isAir()) {
                candidate = candidate.below();
            }
            BlockState feet = serverLevel.getBlockState(candidate);
            BlockState head = serverLevel.getBlockState(candidate.above());
            if (!serverLevel.getBlockState(candidate.below()).blocksMotion() || !feet.getCollisionShape(serverLevel, candidate).isEmpty() || !head.getCollisionShape(serverLevel, candidate.above()).isEmpty() || serverLevel.containsAnyLiquid(player.getBoundingBox().move(candidate.getBottomCenter().subtract(player.position())))) {
                continue;
            }
            player.teleportTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
            if (serverLevel.noCollision(player) && !serverLevel.containsAnyLiquid(player.getBoundingBox())) {
                safePos = candidate;
                break;
            }
            player.teleportTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
        }
        if (safePos == null) {
            return;
        }

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            EnderSnailShellArmorData.setShellCount(player.getItemBySlot(slot), 0);
        }
        event.setCanceled(true);
        player.setHealth(Math.max(1.0F, player.getMaxHealth() * Math.min(1.0F, shellCount * HEAL_PER_SHELL)));
        player.fallDistance = 0.0F;
        player.displayClientMessage(Component.translatable("message.hp_end_expansion.ender_snail_shell_saved", shellCount), true);
    }

    // 在带壳盔甲 tooltip 上显示当前蜗牛壳层数。
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ArmorItem)) {
            return;
        }
        int shellCount = EnderSnailShellArmorData.getShellCount(stack);
        if (shellCount > 0) {
            event.getToolTip().add(Component.translatable("item.hp_end_expansion.ender_snail_shell.armor_shells", shellCount));
        }
    }

    // 玩家左键攻击生物时，如果主手是已绑定末影晶核，则把目标传送到绑定位置。
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        ItemStack stack = event.getEntity().getMainHandItem();
        if (stack.getItem() instanceof EnderCoreItem && EnderCoreItem.teleportTargetToBoundLocation(stack, event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }
}
