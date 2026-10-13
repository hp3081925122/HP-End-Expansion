package org.hp.hp_end_expansion.client;

import net.minecraft.resources.Identifier;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.LanternJellyfishEntity;
import org.hp.hp_end_expansion.client.render.HpGeoModel;

public final class LanternJellyfishModel extends HpGeoModel<LanternJellyfishEntity> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/lantern_jellyfish");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/lantern_jellyfish.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/lantern_jellyfish");

    @Override
    public Identifier getModelResource(LanternJellyfishEntity entity) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(LanternJellyfishEntity entity) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(LanternJellyfishEntity entity) {
        return ANIMATION;
    }
}
