package org.hp.hp_end_expansion.client;

import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.*;
import net.minecraftforge.network.PacketDistributor;
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
            org.hp.hp_end_expansion.network.ModNetwork.CHANNEL.sendToServer(TideCrossbowShootPayload.INSTANCE);
        }
    }

    @SubscribeEvent public static void attackRelease(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !minecraft.options.keyAttack.isDown()) attackHandled = false;
    }
    public static void crossbow(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var bow = ModTidelight.TIDE_CROSSBOW.get();
            ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) -> {
                if (entity == null || !entity.isUsingItem() || entity.getUseItem() != stack) return 0.0F;
                return (float) (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / CrossbowItem.getChargeDuration(stack);
            });
            ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("charged"), (stack, level, entity, seed) ->
                CrossbowItem.isCharged(stack) && (entity == null || !entity.isUsingItem() || entity.getUseItem() != stack) ? 1.0F : 0.0F);
            ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("firework"), (stack, level, entity, seed) -> {
                return CrossbowItem.containsChargedProjectile(stack, Items.FIREWORK_ROCKET) ? 1.0F : 0.0F;
            });
        });
    }

    public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModTidelight.TIDE_MOTE.get(), sprites -> new TidelightParticle.Provider(sprites, false));
        event.registerSpriteSet(ModTidelight.TIDE_BUBBLE.get(), sprites -> new TidelightParticle.Provider(sprites, true));
        com.mojang.logging.LogUtils.getLogger().debug("Registered tide_mote and tide_bubble particle providers");
    }
    @EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class TidelightRegistration {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { crossbow(event); }
        @SubscribeEvent public static void registerParticles(RegisterParticleProvidersEvent event) { particles(event); }
    }
}
