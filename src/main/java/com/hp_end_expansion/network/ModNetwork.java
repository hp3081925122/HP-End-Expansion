package com.hp_end_expansion.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    // 当前网络协议版本。
    private static final String PROTOCOL_VERSION = "1";

    // 网络注册类只提供静态入口，不允许实例化。
    private ModNetwork() {
    }

    // 注册网络负载处理事件。
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::registerPayloads);
    }

    // 注册客户端发往服务端的虚空鲸传送负载。
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(VoidWhaleTeleportPayload.TYPE, VoidWhaleTeleportPayload.STREAM_CODEC, VoidWhaleTeleportPayload::handle);
        registrar.playToServer(OpenEnderBoxPayload.TYPE, OpenEnderBoxPayload.STREAM_CODEC, OpenEnderBoxPayload::handle);
    }
}
