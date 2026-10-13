package org.hp.hp_end_expansion.network;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class ModNetwork {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "main"), () -> "1", "1"::equals, "1"::equals);
    public static void register() {
        CHANNEL.registerMessage(0, TideCrossbowShootPayload.class, TideCrossbowShootPayload::encode,
            TideCrossbowShootPayload::decode, TideCrossbowShootPayload::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, AbyssSpineBeamPayload.class, AbyssSpineBeamPayload::encode,
            AbyssSpineBeamPayload::decode, AbyssSpineBeamPayload::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void sendBeam(ServerLevel level, AbyssSpineBeamPayload payload) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
            payload.from().x, payload.from().y, payload.from().z, 64.0D, level.dimension())), payload);
    }
}
