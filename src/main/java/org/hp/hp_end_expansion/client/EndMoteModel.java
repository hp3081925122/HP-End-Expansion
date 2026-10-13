package org.hp.hp_end_expansion.client;

import net.minecraft.resources.Identifier;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.EndMoteEntity;
import org.hp.hp_end_expansion.client.render.HpGeoModel;

public final class EndMoteModel extends HpGeoModel<EndMoteEntity> {

    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/end_mote");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/end_mote.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/end_mote");

    @Override
    public Identifier getModelResource(EndMoteEntity entity) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(EndMoteEntity entity) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(EndMoteEntity entity) {
        return ANIMATION;
    }
}
