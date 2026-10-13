package org.hp.hp_end_expansion.client;

import net.minecraft.resources.Identifier;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.TidelampWyrmEntity;
import org.hp.hp_end_expansion.client.render.HpGeoModel;

public final class TidelampWyrmModel extends HpGeoModel<TidelampWyrmEntity> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/tidelamp_wyrm");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/tidelamp_wyrm.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/tidelamp_wyrm");

    @Override
    public Identifier getModelResource(TidelampWyrmEntity entity) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(TidelampWyrmEntity entity) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(TidelampWyrmEntity entity) {
        return ANIMATION;
    }
}
