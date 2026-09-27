package org.hp.hp_end_expansion.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.RiftMantisEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

public final class RiftMantisModel extends GeoModel<RiftMantisEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "geo/rift_mantis.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/rift_mantis.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "animations/rift_mantis.animation.json");

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getModelResource(RiftMantisEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getModelResource(RiftMantisEntity entity, @Nullable GeoRenderer<RiftMantisEntity> renderer) {
        return MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureResource(RiftMantisEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getTextureResource(RiftMantisEntity entity, @Nullable GeoRenderer<RiftMantisEntity> renderer) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RiftMantisEntity entity) {
        return ANIMATION;
    }
}
