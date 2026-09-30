package org.hp.hp_end_expansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.ModStarwreck;
import org.hp.hp_end_expansion.worldgen.StarwreckWorldgen;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class StarwreckMusic {
    @SubscribeEvent
    public static void selectMusic(SelectMusicEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.level.dimension() != Level.END) return;
        if (event.getOriginalMusic().equals(Musics.END_BOSS) || event.getMusic() == null || !event.getMusic().equals(event.getOriginalMusic())) return;
        if (minecraft.level.getBiome(minecraft.player.blockPosition()).is(StarwreckWorldgen.BIOME)) {
            event.setMusic(new Music(ModStarwreck.MUSIC, 600, 2400, true));
        }
    }
}
