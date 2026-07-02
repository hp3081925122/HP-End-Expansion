package com.hp_end_expansion;

import com.hp_end_expansion.network.ModNetwork;
import com.hp_end_expansion.registry.ModCreativeTabs;
import com.hp_end_expansion.registry.ModEntities;
import com.hp_end_expansion.registry.ModItems;
import com.hp_end_expansion.registry.ModRecipeSerializers;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(HpEndExpansion.MODID)
public class HpEndExpansion {
    // 模组基础标识和日志入口。
    public static final String MODID = "hp_end_expansion";
    public static final Logger LOGGER = LogUtils.getLogger();

    // 模组构造时注册实体、物品、网络和实体事件。
    public HpEndExpansion(IEventBus modEventBus) {
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModRecipeSerializers.register(modEventBus);
        ModNetwork.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(ModEntities::registerSpawnPlacements);
    }
}
