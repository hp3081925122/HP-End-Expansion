package com.hp_end_expansion.network;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.entity.VoidWhale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record VoidWhaleTeleportPayload() implements CustomPacketPayload {
    // 无字段负载使用单例和 unit 编解码器。
    public static final VoidWhaleTeleportPayload INSTANCE = new VoidWhaleTeleportPayload();
    public static final Type<VoidWhaleTeleportPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "void_whale_teleport"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoidWhaleTeleportPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    // 返回当前自定义负载类型。
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 服务端收到传送请求后，只允许当前骑乘的虚空鲸处理。
    public static void handle(VoidWhaleTeleportPayload 载荷, IPayloadContext 上下文) {
        上下文.enqueueWork(() -> {
            // 网络上下文玩家必须是服务端玩家。
            if (上下文.player() instanceof ServerPlayer 玩家) {
                Entity 载具 = 玩家.getVehicle();
                // 玩家当前载具必须是虚空鲸。
                if (载具 instanceof VoidWhale voidWhale) {
                    voidWhale.tryTeleportFromRider(玩家);
                }
            }
        });
    }
}
