package org.hp.hp_end_expansion.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.VoidRayEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

public final class VoidRayModel extends GeoModel<VoidRayEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "geo/void_ray.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/void_ray.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "animations/void_ray.animation.json");

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getModelResource(VoidRayEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getModelResource(VoidRayEntity entity, @Nullable GeoRenderer<VoidRayEntity> renderer) {
        return MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureResource(VoidRayEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getTextureResource(VoidRayEntity entity, @Nullable GeoRenderer<VoidRayEntity> renderer) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(VoidRayEntity entity) {
        return ANIMATION;
    }
}
