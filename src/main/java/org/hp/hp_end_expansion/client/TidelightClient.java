package org.hp.hp_end_expansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.*;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.item.TideCrossbowItem;
import org.hp.hp_end_expansion.network.TideCrossbowShootPayload;
import org.hp.hp_end_expansion.registry.*;
import org.hp.hp_end_expansion.worldgen.tidelight.TidelightWorldgen;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class TidelightClient {
    private static boolean attackHandled;

    @SubscribeEvent public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isAttack() || minecraft.player == null || minecraft.screen != null
            || minecraft.player.isSpectator() || TideCrossbowItem.heldHand(minecraft.player) == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!attackHandled) {
            attackHandled = true;
            ClientPacketDistributor.sendToServer(TideCrossbowShootPayload.INSTANCE);
        }
    }

    @SubscribeEvent public static void attackRelease(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !minecraft.options.keyAttack.isDown()) attackHandled = false;
    }


    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModTidelight.TIDE_MOTE.get(), sprites -> new TidelightParticle.Provider(sprites, false));
        event.registerSpriteSet(ModTidelight.TIDE_BUBBLE.get(), sprites -> new TidelightParticle.Provider(sprites, true));
    }
    @SubscribeEvent public static void music(SelectMusicEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.level.dimension() != Level.END) return;
        if (event.getOriginalMusic().equals(Musics.END_BOSS) || event.getMusic() == null || !event.getMusic().equals(event.getOriginalMusic())) return;
        if (minecraft.level.getBiome(minecraft.player.blockPosition()).is(TidelightWorldgen.BIOME)) event.setMusic(new Music(ModTidelight.MUSIC, 600, 2400, true));
    }
}
