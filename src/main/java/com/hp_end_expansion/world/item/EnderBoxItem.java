package com.hp_end_expansion.world.item;

import com.hp_end_expansion.registry.ModEntities;
import com.hp_end_expansion.world.entity.EnderBox;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

public class EnderBoxItem extends Item {
    // 创建不可堆叠的末影盒物品。
    public EnderBoxItem(Properties 属性) {
        super(属性.stacksTo(1));
    }

    // 潜行右键方块时，把末影盒物品重新实体化。
    @Override
    public InteractionResult useOn(UseOnContext 上下文) {
        Player 玩家 = 上下文.getPlayer();
        // 只有玩家潜行使用时才执行实体化，普通右键仍交给 use 开箱。
        if (玩家 == null || !玩家.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        // 服务端创建实体，并把物品中的容器内容转移到实体容器。
        ItemStack 物品栈 = 上下文.getItemInHand();
        Level 世界 = 上下文.getLevel();
        if (!世界.isClientSide) {
            EnderBox enderBox = ModEntities.ENDER_BOX.get().create(世界);
            if (enderBox == null) {
                return InteractionResult.FAIL;
            }

            // 拷贝物品组件中的 54 格容器内容。
            NonNullList<ItemStack> 物品列表 = NonNullList.withSize(EnderBox.CONTAINER_SIZE, ItemStack.EMPTY);
            物品栈.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(物品列表);
            for (int 索引 = 0; 索引 < 物品列表.size(); 索引++) {
                enderBox.setItem(索引, 物品列表.get(索引).copy());
            }
            // 重新实体化的末影盒归当前玩家所有，并放在点击面的旁边。
            enderBox.setTame(true, true);
            enderBox.setOwnerUUID(玩家.getUUID());
            enderBox.setOrderedToSit(false);
            enderBox.moveTo(上下文.getClickedPos().relative(上下文.getClickedFace()).getBottomCenter(), 上下文.getRotation(), 0.0F);
            if (!世界.noCollision(enderBox)) {
                return InteractionResult.FAIL;
            }

            // 成功生成实体后，非创造模式消耗物品。
            世界.addFreshEntity(enderBox);
            if (!玩家.getAbilities().instabuild) {
                物品栈.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(世界.isClientSide);
    }

    // 手持末影盒右键空气时直接打开容器。
    @Override
    public InteractionResultHolder<ItemStack> use(Level 世界, Player 玩家, InteractionHand 手) {
        ItemStack 物品栈 = 玩家.getItemInHand(手);
        // 服务端创建临时容器，并把变更同步回物品组件。
        if (!世界.isClientSide) {
            NonNullList<ItemStack> 物品列表 = NonNullList.withSize(EnderBox.CONTAINER_SIZE, ItemStack.EMPTY);
            物品栈.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(物品列表);
            SimpleContainer 容器 = new SimpleContainer(EnderBox.CONTAINER_SIZE) {
                @Override
                public boolean canPlaceItem(int 槽位, ItemStack 单个物品栈) {
                    return !单个物品栈.is(物品栈.getItem());
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    物品栈.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
                }
            };
            // 把物品内保存的内容填进临时容器。
            for (int 索引 = 0; 索引 < 物品列表.size(); 索引++) {
                容器.setItem(索引, 物品列表.get(索引));
            }
            玩家.openMenu(new SimpleMenuProvider((容器ID, 玩家背包, 拥有者) -> ChestMenu.sixRows(容器ID, 玩家背包, 容器), 物品栈.getHoverName()));
        }
        return InteractionResultHolder.sidedSuccess(物品栈, 世界.isClientSide);
    }

    // 显示末影盒当前保存的物品数量。
    @Override
    public void appendHoverText(ItemStack 物品栈, TooltipContext 上下文, List<Component> 提示列表, TooltipFlag 提示标志) {
        int 已用数量 = 0;
        for (ItemStack 单个物品栈 : 物品栈.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems()) {
            已用数量 += 单个物品栈.getCount();
        }
        提示列表.add(Component.translatable("item.hp_end_expansion.ender_box.contents", 已用数量));
    }
}
