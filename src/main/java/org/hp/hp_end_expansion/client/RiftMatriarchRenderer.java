package org.hp.hp_end_expansion.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.hp.hp_end_expansion.entity.RiftMatriarchEntity;
import org.hp.hp_end_expansion.client.render.HpGeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;

public final class RiftMatriarchRenderer extends HpGeoEntityRenderer<RiftMatriarchEntity> {
    public RiftMatriarchRenderer(EntityRendererProvider.Context context) {
        super(context, new RiftMatriarchModel());
        this.shadowRadius = 1.75F;
        this.withScale(2.5F);
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
