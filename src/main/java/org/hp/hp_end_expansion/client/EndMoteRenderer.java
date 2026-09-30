package org.hp.hp_end_expansion.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.hp.hp_end_expansion.entity.EndMoteEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class EndMoteRenderer extends GeoEntityRenderer<EndMoteEntity> {

    public EndMoteRenderer(EntityRendererProvider.Context context) {
        super(context, new EndMoteModel());
        this.shadowRadius = 0.15F;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
