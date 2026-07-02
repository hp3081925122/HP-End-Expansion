package com.hp_end_expansion.world.item;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class PetBagItem extends Item {
    // 创建只能单个堆叠的宠物袋。
    public PetBagItem(Properties 属性) {
        super(属性.stacksTo(1));
    }

    // 右键自己的已驯服生物时，把宠物实体保存到物品组件中。
    @Override
    public InteractionResult interactLivingEntity(ItemStack 物品栈, Player 玩家, LivingEntity 目标, InteractionHand 手) {
        // 已经装有宠物的袋子不再收容其他实体。
        if (物品栈.has(DataComponents.ENTITY_DATA)) {
            return InteractionResult.FAIL;
        }

        // 只允许收容属于当前玩家的已驯服宠物，避免误收其他生物。
        if (!(目标 instanceof TamableAnimal 可驯服动物) || !可驯服动物.isTame() || !可驯服动物.isOwnedBy(玩家) || 目标.isPassenger() || 目标.isVehicle()) {
            return InteractionResult.PASS;
        }

        // 服务端保存完整实体数据，然后移除世界中的原实体。
        if (!玩家.level().isClientSide) {
            CompoundTag 实体标签 = new CompoundTag();
            if (!目标.saveAsPassenger(实体标签)) {
                return InteractionResult.FAIL;
            }
            物品栈.set(DataComponents.ENTITY_DATA, CustomData.of(实体标签));
            目标.discard();
        }
        return InteractionResult.sidedSuccess(玩家.level().isClientSide);
    }

    // 潜行右键方块时，把宠物袋里的宠物重新放回世界。
    @Override
    public InteractionResult useOn(UseOnContext 上下文) {
        Player 玩家 = 上下文.getPlayer();
        CustomData 实体数据 = 上下文.getItemInHand().get(DataComponents.ENTITY_DATA);
        // 空袋子或非潜行使用时，不拦截普通方块交互。
        if (玩家 == null || 实体数据 == null || !玩家.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        Level 世界 = 上下文.getLevel();
        // 服务端从组件恢复实体，并放在点击方块的相邻位置。
        if (!世界.isClientSide) {
            Entity 实体 = EntityType.create(实体数据.copyTag(), 世界).orElse(null);
            if (实体 == null) {
                return InteractionResult.FAIL;
            }
            实体.moveTo(上下文.getClickedPos().relative(上下文.getClickedFace()).getBottomCenter(), 上下文.getRotation(), 0.0F);
            if (!世界.noCollision(实体)) {
                return InteractionResult.FAIL;
            }
            世界.addFreshEntity(实体);
            上下文.getItemInHand().remove(DataComponents.ENTITY_DATA);
        }
        return InteractionResult.sidedSuccess(世界.isClientSide);
    }

    // 显示宠物袋当前是否装有宠物。
    @Override
    public void appendHoverText(ItemStack 物品栈, TooltipContext 上下文, List<Component> 提示列表, TooltipFlag 提示标志) {
        CustomData 实体数据 = 物品栈.get(DataComponents.ENTITY_DATA);
        if (实体数据 == null) {
            提示列表.add(Component.translatable("item.hp_end_expansion.pet_bag.empty"));
            return;
        }

        // 从实体数据里读取实体 id，方便玩家区分袋子内容。
        String 实体ID = 实体数据.copyTag().getString("id");
        提示列表.add(Component.translatable("item.hp_end_expansion.pet_bag.contains", 实体ID));
    }
}
