package org.hp.hp_end_expansion.client;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.Identifier;
import org.hp.hp_end_expansion.entity.tidelight.LanternJellyfishEntity;
import org.hp.hp_end_expansion.client.render.HpGeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;

public final class LanternJellyfishRenderer extends HpGeoEntityRenderer<LanternJellyfishEntity> {
    public LanternJellyfishRenderer(EntityRendererProvider.Context context) {
        super(context, new LanternJellyfishModel());
        shadowRadius = 0.25F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(LanternJellyfishEntity entity, Identifier texture, MultiBufferSource buffer, float partialTick) {
        return RenderTypes.entityTranslucent(texture);
    }
}
