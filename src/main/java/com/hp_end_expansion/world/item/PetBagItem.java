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
    public PetBagItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // 右键自己的已驯服生物时，把宠物实体保存到物品组件中。
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // 已经装有宠物的袋子不再收容其他实体。
        if (stack.has(DataComponents.ENTITY_DATA)) {
            return InteractionResult.FAIL;
        }

        // 只允许收容属于当前玩家的已驯服宠物，避免误收其他生物。
        if (!(target instanceof TamableAnimal tamableAnimal) || !tamableAnimal.isTame() || !tamableAnimal.isOwnedBy(player) || target.isPassenger() || target.isVehicle()) {
            return InteractionResult.PASS;
        }

        // 服务端保存完整实体数据，然后移除世界中的原实体。
        if (!player.level().isClientSide) {
            CompoundTag entityTag = new CompoundTag();
            if (!target.saveAsPassenger(entityTag)) {
                return InteractionResult.FAIL;
            }
            stack.set(DataComponents.ENTITY_DATA, CustomData.of(entityTag));
            target.discard();
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    // 潜行右键方块时，把宠物袋里的宠物重新放回世界。
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        CustomData entityData = context.getItemInHand().get(DataComponents.ENTITY_DATA);
        // 空袋子或非潜行使用时，不拦截普通方块交互。
        if (player == null || entityData == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        // 服务端从组件恢复实体，并放在点击方块的相邻位置。
        if (!level.isClientSide) {
            Entity entity = EntityType.create(entityData.copyTag(), level).orElse(null);
            if (entity == null) {
                return InteractionResult.FAIL;
            }
            entity.moveTo(context.getClickedPos().relative(context.getClickedFace()).getBottomCenter(), context.getRotation(), 0.0F);
            if (!level.noCollision(entity)) {
                return InteractionResult.FAIL;
            }
            level.addFreshEntity(entity);
            context.getItemInHand().remove(DataComponents.ENTITY_DATA);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // 显示宠物袋当前是否装有宠物。
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CustomData entityData = stack.get(DataComponents.ENTITY_DATA);
        if (entityData == null) {
            tooltip.add(Component.translatable("item.hp_end_expansion.pet_bag.empty"));
            return;
        }

        // 从实体数据里读取实体 id，方便玩家区分袋子内容。
        String entityId = entityData.copyTag().getString("id");
        tooltip.add(Component.translatable("item.hp_end_expansion.pet_bag.contains", entityId));
    }
}
