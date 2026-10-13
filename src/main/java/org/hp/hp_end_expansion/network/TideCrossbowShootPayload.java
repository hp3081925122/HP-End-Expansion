package org.hp.hp_end_expansion.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.item.TideCrossbowItem;

@EventBusSubscriber(modid = Hp_end_expansion.MODID)
public record TideCrossbowShootPayload() implements CustomPacketPayload {
    public static final TideCrossbowShootPayload INSTANCE = new TideCrossbowShootPayload();
    public static final Type<TideCrossbowShootPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "tide_crossbow_shoot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TideCrossbowShootPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<TideCrossbowShootPayload> type() {
        return TYPE;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
            Player player = context.player();
            InteractionHand hand = TideCrossbowItem.heldHand(player);
            if (hand != null && player.getItemInHand(hand).getItem() instanceof TideCrossbowItem crossbow) crossbow.shootLoaded(player, hand);
        });
    }
}
