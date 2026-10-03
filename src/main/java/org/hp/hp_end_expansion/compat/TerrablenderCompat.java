package org.hp.hp_end_expansion.compat;

import java.lang.reflect.Method;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.worldgen.StarwreckConfig;
import org.hp.hp_end_expansion.worldgen.StarwreckWorldgen;
import org.hp.hp_end_expansion.worldgen.tidelight.TidelightWorldgen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TerrablenderCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(Hp_end_expansion.MODID + "/terrablender");
    private static final int BIOME_WEIGHT = 5;
    private static volatile Boolean terrablenderPresent;
    private static boolean starwreckRegistered;
    private static boolean tidelightRegistered;

    private TerrablenderCompat() {
    }

    public static boolean isLoaded() {
        Boolean present = terrablenderPresent;
        if (present != null) {
            return present;
        }
        try {
            Class.forName("terrablender.api.EndBiomeRegistry", false, TerrablenderCompat.class.getClassLoader());
            terrablenderPresent = true;
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            terrablenderPresent = false;
            return false;
        }
    }

    public static void registerListener() {
        if (isLoaded()) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, TerrablenderCompat::registerBiomes);
        }
    }

    private static void registerBiomes(ServerAboutToStartEvent event) {
        if (!isLoaded() || (!StarwreckConfig.enabled() && !StarwreckConfig.tidelightEnabled())) return;
        try {
            Class<?> registry = Class.forName("terrablender.api.EndBiomeRegistry", false, TerrablenderCompat.class.getClassLoader());
            Method highlands = registry.getMethod("registerHighlandsBiome", ResourceKey.class, int.class);
            Method midlands = registry.getMethod("registerMidlandsBiome", ResourceKey.class, int.class);
            Method edge = registry.getMethod("registerEdgeBiome", ResourceKey.class, int.class);
            if (StarwreckConfig.enabled() && !starwreckRegistered) {
                register(highlands, StarwreckWorldgen.BIOME);
                register(midlands, StarwreckWorldgen.BIOME);
                register(edge, StarwreckWorldgen.BIOME);
                starwreckRegistered = true;
            }
            if (StarwreckConfig.tidelightEnabled() && !tidelightRegistered) {
                register(highlands, TidelightWorldgen.BIOME);
                register(midlands, TidelightWorldgen.BIOME);
                register(edge, TidelightWorldgen.BIOME);
                tidelightRegistered = true;
            }
            LOGGER.info("TerraBlender End biome compatibility registered for HP End Expansion");
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.error("Failed to register HP End Expansion biomes with TerraBlender", exception);
        }
    }

    private static void register(Method method, ResourceKey<Biome> biome) throws ReflectiveOperationException {
        method.invoke(null, biome, BIOME_WEIGHT);
    }
}
