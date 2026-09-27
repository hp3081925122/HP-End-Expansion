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
            .displayItems((parameters, output) -> output.accept(ModItems.RIFT_MANTIS_SPAWN_EGG.get()))
            .build()
    );

    public Hp_end_expansion(IEventBus modEventBus) {
        LOGGER.debug("Using default ordering for creative tab hp_end_expansion:main");
        ModEntities.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
