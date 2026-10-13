package org.hp.hp_end_expansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.world.level.Level;
import org.hp.hp_end_expansion.entity.starwreck.SkyrenderEntity;
import org.hp.hp_end_expansion.registry.ModStarwreck;
import org.hp.hp_end_expansion.registry.ModTidelight;
import org.hp.hp_end_expansion.worldgen.StarwreckWorldgen;
import org.hp.hp_end_expansion.worldgen.tidelight.TidelightWorldgen;

public final class StarwreckMusic {
    private static Music starwreck;
    private static Music tidelight;

    public static Music selectMusic(Music original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.level.dimension() != Level.END
            || original == null || original.equals(Musics.END_BOSS)) return original;
        if (!minecraft.level.getEntitiesOfClass(SkyrenderEntity.class, minecraft.player.getBoundingBox().inflate(96.0),
            net.minecraft.world.entity.Entity::isAlive).isEmpty()) return Musics.END_BOSS;
        if (minecraft.level.getBiome(minecraft.player.blockPosition()).is(StarwreckWorldgen.BIOME)) {
            if (starwreck == null) starwreck = new Music(Holder.direct(ModStarwreck.MUSIC.get()), 600, 2400, true);
            return starwreck;
        }
        if (minecraft.level.getBiome(minecraft.player.blockPosition()).is(TidelightWorldgen.BIOME)) {
            if (tidelight == null) tidelight = new Music(Holder.direct(ModTidelight.MUSIC.get()), 600, 2400, true);
            return tidelight;
        }
        return original;
    }
}
