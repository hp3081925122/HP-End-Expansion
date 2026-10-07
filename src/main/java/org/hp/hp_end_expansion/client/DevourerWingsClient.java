package org.hp.hp_end_expansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.item.DevourerWingsItem;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class DevourerWingsClient {
    private DevourerWingsClient() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.input == null || !minecraft.player.input.jumping
            || minecraft.player.getAbilities().flying || minecraft.player.isFallFlying()) return;
        if (!(DevourerWingsItem.getFlightStack(minecraft.player, net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()
            instanceof DevourerWingsItem)) return;
        if (minecraft.player.tryToStartFallFlying()) {
            minecraft.player.connection.send(
                new ServerboundPlayerCommandPacket(minecraft.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
            );
        }
    }
}
