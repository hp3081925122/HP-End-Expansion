package org.hp.hp_end_expansion.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.hp.hp_end_expansion.client.AbyssSpineBeamFx;

public record AbyssSpineBeamPayload(Vec3 from, Vec3 to, int face) {
    public static void encode(AbyssSpineBeamPayload payload, FriendlyByteBuf buf) {
        buf.writeDouble(payload.from.x).writeDouble(payload.from.y).writeDouble(payload.from.z);
        buf.writeDouble(payload.to.x).writeDouble(payload.to.y).writeDouble(payload.to.z);
        buf.writeByte(payload.face);
    }
    public static AbyssSpineBeamPayload decode(FriendlyByteBuf buf) {
        return new AbyssSpineBeamPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readByte());
    }
    public static void handle(AbyssSpineBeamPayload payload, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> AbyssSpineBeamFx.spawn(payload.from, payload.to, payload.face)));
        context.get().setPacketHandled(true);
    }
}
