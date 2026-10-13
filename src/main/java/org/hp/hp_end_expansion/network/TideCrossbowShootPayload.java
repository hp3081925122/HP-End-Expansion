package org.hp.hp_end_expansion.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;
import org.hp.hp_end_expansion.item.TideCrossbowItem;

public record TideCrossbowShootPayload() {
    public static final TideCrossbowShootPayload INSTANCE = new TideCrossbowShootPayload();
    public static void encode(TideCrossbowShootPayload payload, FriendlyByteBuf buf) {}
    public static TideCrossbowShootPayload decode(FriendlyByteBuf buf) { return INSTANCE; }
    public static void handle(TideCrossbowShootPayload payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            InteractionHand hand = TideCrossbowItem.heldHand(player);
            if (hand != null && player.getItemInHand(hand).getItem() instanceof TideCrossbowItem crossbow) crossbow.shootLoaded(player, hand);
        });
        ctx.setPacketHandled(true);
    }
}
