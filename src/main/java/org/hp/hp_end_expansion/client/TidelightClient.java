package org.hp.hp_end_expansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.*;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.*;
import org.hp.hp_end_expansion.worldgen.tidelight.TidelightWorldgen;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class TidelightClient {
    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModTidelight.TIDE_MOTE.get(), sprites -> new TidelightParticle.Provider(sprites, false));
        event.registerSpriteSet(ModTidelight.TIDE_BUBBLE.get(), sprites -> new TidelightParticle.Provider(sprites, true));
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TidelightEntities.LANTERN_JELLYFISH.get(), context -> new TidelightEntityRenderer<>(context, "lantern_jellyfish", 0.25F));
        event.registerEntityRenderer(TidelightEntities.PEARL_HERMIT_CRAB.get(), context -> new TidelightEntityRenderer<>(context, "pearl_hermit_crab", 0.2F));
        event.registerEntityRenderer(TidelightEntities.REEF_EEL.get(), context -> new TidelightEntityRenderer<>(context, "reef_eel", 0.3F));
        event.registerEntityRenderer(TidelightEntities.REEF_CRYSTAL_BEAST.get(), ReefCrystalBeastRenderer::new);
    }
    @SubscribeEvent public static void music(SelectMusicEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.level.dimension() != Level.END) return;
        if (event.getOriginalMusic().equals(Musics.END_BOSS) || event.getMusic() == null || !event.getMusic().equals(event.getOriginalMusic())) return;
        if (minecraft.level.getBiome(minecraft.player.blockPosition()).is(TidelightWorldgen.BIOME)) event.setMusic(new Music(ModTidelight.MUSIC, 600, 2400, true));
    }
}
