package com.hp_end_expansion.world.item;

import com.hp_end_expansion.world.EndShipTeleportation;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EnderCoreItem extends Item {
    // 绑定数据中的字段名。
    private static final String BOUND_DIMENSION_TAG = "BoundDimension";
    private static final String BOUND_X_TAG = "BoundX";
    private static final String BOUND_Y_TAG = "BoundY";
    private static final String BOUND_Z_TAG = "BoundZ";

    // 创建只能单个堆叠的末影晶核。
    public EnderCoreItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // 潜行右键绑定当前位置。
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            CompoundTag tag = new CompoundTag();
            tag.putString(BOUND_DIMENSION_TAG, level.dimension().location().toString());
            tag.putDouble(BOUND_X_TAG, player.getX());
            tag.putDouble(BOUND_Y_TAG, player.getY());
            tag.putDouble(BOUND_Z_TAG, player.getZ());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            player.displayClientMessage(Component.translatable("message.hp_end_expansion.ender_core_bound"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // 显示末影晶核是否已绑定位置。
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BoundLocation boundLocation = readBoundLocation(stack);
        if (boundLocation == null) {
            tooltip.add(Component.translatable("item.hp_end_expansion.ender_core.unbound"));
            return;
        }
        tooltip.add(Component.translatable("item.hp_end_expansion.ender_core.bound", boundLocation.dimension().location().toString(), (int)boundLocation.position().x, (int)boundLocation.position().y, (int)boundLocation.position().z));
    }

    // 左键攻击生物时，把目标传送到绑定位置。
    public static boolean teleportTargetToBoundLocation(ItemStack stack, Player player, Entity target) {
        BoundLocation boundLocation = readBoundLocation(stack);
        if (boundLocation == null || !(target instanceof LivingEntity livingEntity) || target instanceof Player) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        boolean teleported = EndShipTeleportation.teleportToBoundPosition(serverLevel, livingEntity, boundLocation.dimension(), boundLocation.position());
        if (teleported) {
            player.getCooldowns().addCooldown(stack.getItem(), 40);
            player.displayClientMessage(Component.translatable("message.hp_end_expansion.ender_core_sent"), true);
        }
        return teleported;
    }

    // 从物品组件中读取绑定位置。
    @Nullable
    private static BoundLocation readBoundLocation(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(BOUND_DIMENSION_TAG) || !tag.contains(BOUND_X_TAG) || !tag.contains(BOUND_Y_TAG) || !tag.contains(BOUND_Z_TAG)) {
            return null;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(BOUND_DIMENSION_TAG));
        if (dimensionId == null) {
            return null;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        Vec3 position = new Vec3(tag.getDouble(BOUND_X_TAG), tag.getDouble(BOUND_Y_TAG), tag.getDouble(BOUND_Z_TAG));
        return new BoundLocation(dimension, position);
    }

    // 末影晶核绑定目标。
    private record BoundLocation(ResourceKey<Level> dimension, Vec3 position) {
    }
}
