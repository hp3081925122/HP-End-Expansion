package org.hp.hp_end_expansion.client;

import net.minecraft.resources.ResourceLocation;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.LanternJellyfishEntity;
import software.bernie.geckolib.model.GeoModel;

public final class LanternJellyfishModel extends GeoModel<LanternJellyfishEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "geo/lantern_jellyfish.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/lantern_jellyfish.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "animations/lantern_jellyfish.animation.json");

    @Override
    public ResourceLocation getModelResource(LanternJellyfishEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LanternJellyfishEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(LanternJellyfishEntity entity) {
        return ANIMATION;
    }
}
