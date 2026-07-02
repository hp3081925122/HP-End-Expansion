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
    public EnderBoxItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // 潜行右键方块时，把末影盒物品重新实体化。
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        // 只有玩家潜行使用时才执行实体化，普通右键仍交给 use 开箱。
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        // 服务端创建实体，并把物品中的容器内容转移到实体容器。
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        if (!level.isClientSide) {
            EnderBox enderBox = ModEntities.ENDER_BOX.get().create(level);
            if (enderBox == null) {
                return InteractionResult.FAIL;
            }

            // 拷贝物品组件中的 54 格容器内容。
            NonNullList<ItemStack> items = NonNullList.withSize(EnderBox.CONTAINER_SIZE, ItemStack.EMPTY);
            stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
            for (int i = 0; i < items.size(); i++) {
                enderBox.setItem(i, items.get(i).copy());
            }
            // 重新实体化的末影盒归当前玩家所有，并放在点击面的旁边。
            enderBox.setTame(true, true);
            enderBox.setOwnerUUID(player.getUUID());
            enderBox.setOrderedToSit(false);
            enderBox.moveTo(context.getClickedPos().relative(context.getClickedFace()).getBottomCenter(), context.getRotation(), 0.0F);
            if (!level.noCollision(enderBox)) {
                return InteractionResult.FAIL;
            }

            // 成功生成实体后，非创造模式消耗物品。
            level.addFreshEntity(enderBox);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // 手持末影盒右键空气时直接打开容器。
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 服务端创建临时容器，并把变更同步回物品组件。
        if (!level.isClientSide) {
            NonNullList<ItemStack> items = NonNullList.withSize(EnderBox.CONTAINER_SIZE, ItemStack.EMPTY);
            stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
            SimpleContainer container = new SimpleContainer(EnderBox.CONTAINER_SIZE) {
                @Override
                public boolean canPlaceItem(int slot, ItemStack itemStack) {
                    return !itemStack.is(stack.getItem());
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
                }
            };
            // 把物品内保存的内容填进临时容器。
            for (int i = 0; i < items.size(); i++) {
                container.setItem(i, items.get(i));
            }
            player.openMenu(new SimpleMenuProvider((containerId, inventory, owner) -> ChestMenu.sixRows(containerId, inventory, container), stack.getHoverName()));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // 显示末影盒当前保存的物品数量。
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int used = 0;
        for (ItemStack itemStack : stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems()) {
            used += itemStack.getCount();
        }
        tooltip.add(Component.translatable("item.hp_end_expansion.ender_box.contents", used));
    }
}
