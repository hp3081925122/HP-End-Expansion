package com.hp_end_expansion.network;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.registry.ModItems;
import com.hp_end_expansion.world.entity.EnderBox;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenEnderBoxPayload() implements CustomPacketPayload {
    public static final OpenEnderBoxPayload INSTANCE = new OpenEnderBoxPayload();
    public static final Type<OpenEnderBoxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "open_ender_box"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenEnderBoxPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenEnderBoxPayload 载荷, IPayloadContext 上下文) {
        上下文.enqueueWork(() -> {
            if (!(上下文.player() instanceof ServerPlayer 玩家)) {
                return;
            }

            ItemStack 物品栈 = ItemStack.EMPTY;
            for (ItemStack 候选位置 : 玩家.getInventory().items) {
                if (候选位置.is(ModItems.ENDER_BOX.get())) {
                    物品栈 = 候选位置;
                    break;
                }
            }
            if (物品栈.isEmpty()) {
                return;
            }

            ItemStack 末影盒物品栈 = 物品栈;
            NonNullList<ItemStack> 物品列表 = NonNullList.withSize(EnderBox.CONTAINER_SIZE, ItemStack.EMPTY);
            末影盒物品栈.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(物品列表);
            SimpleContainer 容器 = new SimpleContainer(EnderBox.CONTAINER_SIZE) {
                @Override
                public boolean canPlaceItem(int 槽位, ItemStack 单个物品栈) {
                    return !单个物品栈.is(末影盒物品栈.getItem());
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    末影盒物品栈.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
                }
            };
            for (int 索引 = 0; 索引 < 物品列表.size(); 索引++) {
                容器.setItem(索引, 物品列表.get(索引));
            }
            玩家.openMenu(new SimpleMenuProvider((容器ID, 玩家背包, 拥有者) -> ChestMenu.sixRows(容器ID, 玩家背包, 容器), Component.translatable("item.hp_end_expansion.ender_box")));
        });
    }
}
