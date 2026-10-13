package org.hp.hp_end_expansion.client;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.RiftMantisEntity;
import org.hp.hp_end_expansion.client.render.HpGeoModel;

public final class RiftMantisModel extends HpGeoModel<RiftMantisEntity> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/rift_mantis");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/rift_mantis.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/rift_mantis");

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getModelResource(RiftMantisEntity entity) {
        return MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getTextureResource(RiftMantisEntity entity) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(RiftMantisEntity entity) {
        return ANIMATION;
    }
}
