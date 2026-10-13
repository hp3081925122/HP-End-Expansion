package org.hp.hp_end_expansion.client.render;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

public abstract class HpGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    HpBakedModel activeModel;
    public abstract Identifier getModelResource(T entity);
    public abstract Identifier getTextureResource(T entity);
    @SuppressWarnings("unchecked") private T entity(GeoRenderState state) { return (T)((HpGeoRenderState)state).entity; }
    @Override public final Identifier getModelResource(GeoRenderState state) { return getModelResource(entity(state)); }
    @Override public final Identifier getTextureResource(GeoRenderState state) { return getTextureResource(entity(state)); }
    public void setCustomAnimations(T entity,long instanceId,AnimationTest<T> test) {}
    public HpGeoModel<T> getAnimationProcessor() { return this; }
    public HpBone getBone(String name) { return activeModel.bone(name); }
}
