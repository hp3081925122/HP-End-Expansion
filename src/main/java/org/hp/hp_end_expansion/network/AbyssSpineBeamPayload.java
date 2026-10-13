package org.hp.hp_end_expansion.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.client.AbyssSpineBeamFx;

@EventBusSubscriber(modid = Hp_end_expansion.MODID)
public record AbyssSpineBeamPayload(Vec3 from, Vec3 to, int face) implements CustomPacketPayload {
    public static final Type<AbyssSpineBeamPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "abyss_spine_beam"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AbyssSpineBeamPayload> STREAM_CODEC = StreamCodec.of(
        // 写入起点、终点和受击面
        (buf, payload) -> {
            buf.writeDouble(payload.from.x).writeDouble(payload.from.y).writeDouble(payload.from.z);
            buf.writeDouble(payload.to.x).writeDouble(payload.to.y).writeDouble(payload.to.z);
            buf.writeByte(payload.face);
        },
        // 按相同顺序读取
        buf -> new AbyssSpineBeamPayload(
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            buf.readByte())
    );

    @Override
    public Type<AbyssSpineBeamPayload> type() {
        return TYPE;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // 客户端收到后生成激光特效
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> AbyssSpineBeamFx.spawn(payload.from, payload.to, payload.face));
    }
}
