package org.hp.hp_end_expansion.client;

import net.minecraft.resources.ResourceLocation;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.TidelampWyrmEntity;
import software.bernie.geckolib.model.GeoModel;

public final class TidelampWyrmModel extends GeoModel<TidelampWyrmEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "geo/tidelamp_wyrm.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/tidelamp_wyrm.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "animations/tidelamp_wyrm.animation.json");

    @Override
    public ResourceLocation getModelResource(TidelampWyrmEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(TidelampWyrmEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(TidelampWyrmEntity entity) {
        return ANIMATION;
    }
}
