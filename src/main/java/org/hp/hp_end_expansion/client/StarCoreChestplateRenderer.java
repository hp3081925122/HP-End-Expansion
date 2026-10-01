package org.hp.hp_end_expansion.client;

import org.hp.hp_end_expansion.item.StarCoreChestplateItem;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 资源按物品 ID 取：geo/armor、textures/armor（含 _glowmask）、animations/armor 下的 star_core_chestplate
public final class StarCoreChestplateRenderer extends GeoArmorRenderer<StarCoreChestplateItem> {
    public StarCoreChestplateRenderer() {
        super(StarwreckEntities.STAR_CORE_CHESTPLATE.get());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
