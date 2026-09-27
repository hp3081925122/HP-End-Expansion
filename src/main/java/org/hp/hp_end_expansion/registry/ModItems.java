package org.hp.hp_end_expansion.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hp_end_expansion.MODID);
    public static final DeferredItem<SpawnEggItem> RIFT_MANTIS_SPAWN_EGG = ITEMS.register(
        "rift_mantis_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.RIFT_MANTIS, 0x30283E, 0xB26DE3, new Item.Properties())
    );

    private ModItems() {
    }
}
