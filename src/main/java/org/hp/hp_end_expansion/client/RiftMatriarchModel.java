package org.hp.hp_end_expansion.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.RiftMatriarchEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

public final class RiftMatriarchModel extends GeoModel<RiftMatriarchEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "geo/rift_matriarch.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/rift_matriarch.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "animations/rift_matriarch.animation.json");

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getModelResource(RiftMatriarchEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getModelResource(RiftMatriarchEntity entity, @Nullable GeoRenderer<RiftMatriarchEntity> renderer) {
        return MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureResource(RiftMatriarchEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getTextureResource(RiftMatriarchEntity entity, @Nullable GeoRenderer<RiftMatriarchEntity> renderer) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RiftMatriarchEntity entity) {
        return ANIMATION;
    }
}
