package org.hp.hp_end_expansion.client;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.RiftMatriarchEntity;
import org.hp.hp_end_expansion.client.render.HpGeoModel;

public final class RiftMatriarchModel extends HpGeoModel<RiftMatriarchEntity> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/rift_matriarch");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/rift_matriarch.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/rift_matriarch");

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getModelResource(RiftMatriarchEntity entity) {
        return MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getTextureResource(RiftMatriarchEntity entity) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(RiftMatriarchEntity entity) {
        return ANIMATION;
    }
}
