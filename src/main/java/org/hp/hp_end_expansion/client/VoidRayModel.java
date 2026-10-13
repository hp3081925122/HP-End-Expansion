package org.hp.hp_end_expansion.client;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.VoidRayEntity;
import org.hp.hp_end_expansion.client.render.HpGeoModel;

public final class VoidRayModel extends HpGeoModel<VoidRayEntity> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/void_ray");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/void_ray.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/void_ray");

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getModelResource(VoidRayEntity entity) {
        return MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getTextureResource(VoidRayEntity entity) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(VoidRayEntity entity) {
        return ANIMATION;
    }
}
