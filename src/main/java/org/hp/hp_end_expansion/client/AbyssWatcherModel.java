package org.hp.hp_end_expansion.client;

import net.minecraft.resources.ResourceLocation;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.AbyssWatcherEntity;
import software.bernie.geckolib.model.GeoModel;

public final class AbyssWatcherModel extends GeoModel<AbyssWatcherEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "geo/abyss_watcher.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/abyss_watcher.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "animations/abyss_watcher.animation.json");

    @Override
    public ResourceLocation getModelResource(AbyssWatcherEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(AbyssWatcherEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(AbyssWatcherEntity entity) {
        return ANIMATION;
    }
}
