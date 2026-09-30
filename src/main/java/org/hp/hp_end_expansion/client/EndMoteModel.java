package org.hp.hp_end_expansion.client;

import net.minecraft.resources.ResourceLocation;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.EndMoteEntity;
import software.bernie.geckolib.model.GeoModel;

public final class EndMoteModel extends GeoModel<EndMoteEntity> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "geo/end_mote.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/end_mote.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "animations/end_mote.animation.json");

    @Override
    public ResourceLocation getModelResource(EndMoteEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(EndMoteEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(EndMoteEntity entity) {
        return ANIMATION;
    }
}
