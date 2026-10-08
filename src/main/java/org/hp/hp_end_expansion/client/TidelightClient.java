package org.hp.hp_end_expansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
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
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.*;
import org.hp.hp_end_expansion.worldgen.tidelight.TidelightWorldgen;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class TidelightClient {
    @SubscribeEvent public static void crossbow(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var bow = ModTidelight.TIDE_CROSSBOW.get();
            ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) -> {
                if (entity == null || CrossbowItem.isCharged(stack)) return 0.0F;
                return (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / CrossbowItem.getChargeDuration(stack, entity);
            });
            ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
            ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("charged"), (stack, level, entity, seed) ->
                CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
            ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("firework"), (stack, level, entity, seed) -> {
                ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
                return charged != null && charged.contains(Items.FIREWORK_ROCKET) ? 1.0F : 0.0F;
            });
        });
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
