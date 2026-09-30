package org.hp.hp_end_expansion;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.registry.ModEntities;
import org.slf4j.Logger;
import org.hp.hp_end_expansion.registry.ModItems;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.ModStarwreck;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import org.hp.hp_end_expansion.registry.ModTidelight;
import org.hp.hp_end_expansion.registry.TidelightEntities;
import org.hp.hp_end_expansion.worldgen.tidelight.TidelightWorldgen;
import org.hp.hp_end_expansion.worldgen.StarwreckConfig;
import org.hp.hp_end_expansion.worldgen.StarwreckWorldgen;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

@Mod(Hp_end_expansion.MODID)
public final class Hp_end_expansion {
    public static final String MODID = "hp_end_expansion";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hp_end_expansion"))
            .icon(() -> ModItems.RIFT_MANTIS_SPAWN_EGG.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                ModStarwreck.BLOCK_ITEMS.forEach(item -> output.accept(item.get()));
                ModTidelight.ITEMS.forEach(item -> output.accept(item.get()));
                StarwreckEntities.ITEMS.forEach(item -> output.accept(item.get()));
                output.accept(ModStarwreck.STAR_CRYSTAL_SHARD.get());
                output.accept(ModItems.END_MOTE_SPAWN_EGG.get());
                output.accept(ModItems.RIFT_MANTIS_SPAWN_EGG.get());
                output.accept(ModItems.RIFT_MATRIARCH_SPAWN_EGG.get());
                output.accept(ModItems.VOID_RAY_SPAWN_EGG.get());
                output.accept(ModItems.STAR_DEVOURER_SPAWN_EGG.get());
                output.accept(ModItems.RIFT_CARAPACE.get());
                output.accept(ModItems.RIFT_SICKLE.get());
                output.accept(ModItems.VOID_CORE.get());
                output.accept(ModItems.RAY_MEMBRANE.get());
                output.accept(ModItems.RIFT_EGG.get());
                output.accept(ModItems.STAR_BEACON.get());
                output.accept(ModItems.DEVOURER_WINGS.get());
                output.accept(ModItems.MATRIARCH_SCYTHE.get());
                output.accept(ModItems.END_DUST.get());
            })
            .build()
    );

    public Hp_end_expansion(IEventBus modEventBus, ModContainer container) {
        LOGGER.debug("Using default ordering for creative tab hp_end_expansion:main");
        ModEntities.register(modEventBus);
        ModStarwreck.register(modEventBus);
        StarwreckEntities.register(modEventBus);
        StarwreckWorldgen.register(modEventBus);
        ModTidelight.register(modEventBus);
        TidelightEntities.register(modEventBus);
        TidelightWorldgen.register(modEventBus);
        container.registerConfig(ModConfig.Type.COMMON, StarwreckConfig.SPEC);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
