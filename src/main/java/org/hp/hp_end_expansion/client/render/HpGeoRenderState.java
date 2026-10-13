package org.hp.hp_end_expansion.client.render;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;

public final class HpGeoRenderState extends LivingEntityRenderState implements GeoRenderState {
    public Entity entity;
    public HpBakedModel model;
    private final Map<DataTicket<?>, Object> data=new HashMap<>();
    @Override public Map<DataTicket<?>, Object> getDataMap() { return data; }
}
