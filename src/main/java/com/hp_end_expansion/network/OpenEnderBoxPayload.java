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

    public static void handle(OpenEnderBoxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ItemStack stack = ItemStack.EMPTY;
            for (ItemStack candidate : player.getInventory().items) {
                if (candidate.is(ModItems.ENDER_BOX.get())) {
                    stack = candidate;
                    break;
                }
            }
            if (stack.isEmpty()) {
                return;
            }

            ItemStack enderBoxStack = stack;
            NonNullList<ItemStack> items = NonNullList.withSize(EnderBox.CONTAINER_SIZE, ItemStack.EMPTY);
            enderBoxStack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
            SimpleContainer container = new SimpleContainer(EnderBox.CONTAINER_SIZE) {
                @Override
                public boolean canPlaceItem(int slot, ItemStack itemStack) {
                    return !itemStack.is(enderBoxStack.getItem());
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    enderBoxStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
                }
            };
            for (int i = 0; i < items.size(); i++) {
                container.setItem(i, items.get(i));
            }
            player.openMenu(new SimpleMenuProvider((containerId, inventory, owner) -> ChestMenu.sixRows(containerId, inventory, container), Component.translatable("item.hp_end_expansion.ender_box")));
        });
    }
}
