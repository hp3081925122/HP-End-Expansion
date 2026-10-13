package org.hp.hp_end_expansion.client;

import org.hp.hp_end_expansion.item.StarCoreChestplateItem;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;

// 资源按物品 ID 取：geo/armor、textures/armor（含 _glowmask）、animations/armor 下的 star_core_chestplate
public final class StarCoreChestplateRenderer extends GeoArmorRenderer<StarCoreChestplateItem, StarCoreChestplateRenderer.State> {
    public StarCoreChestplateRenderer() {
        super(StarwreckEntities.STAR_CORE_CHESTPLATE.get());
        withRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
    public static final class State extends net.minecraft.client.renderer.entity.state.HumanoidRenderState implements com.geckolib.renderer.base.GeoRenderState {
        private final java.util.Map<com.geckolib.constant.dataticket.DataTicket<?>,Object> data=new java.util.HashMap<>();
        @Override public java.util.Map<com.geckolib.constant.dataticket.DataTicket<?>,Object> getDataMap() { return data; }
    }
    @Override public State createRenderState(StarCoreChestplateItem item, com.geckolib.renderer.GeoArmorRenderer.RenderData related) { return new State(); }
}
