package org.hp.hp_end_expansion.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.hp.hp_end_expansion.entity.tidelight.LanternJellyfishEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class LanternJellyfishRenderer extends GeoEntityRenderer<LanternJellyfishEntity> {
    public LanternJellyfishRenderer(EntityRendererProvider.Context context) {
        super(context, new LanternJellyfishModel());
        shadowRadius = 0.25F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(LanternJellyfishEntity entity, ResourceLocation texture, MultiBufferSource buffer, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }
}
