package org.hp.hp_end_expansion.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.hp.hp_end_expansion.entity.RiftMantisEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class RiftMantisRenderer extends GeoEntityRenderer<RiftMantisEntity> {
    public RiftMantisRenderer(EntityRendererProvider.Context context) {
        super(context, new RiftMantisModel());
        this.shadowRadius = 1.05F;
        // 精英体型：模型整体放大 1.5 倍
        this.withScale(1.5F);
        // 眼部与刃缘自发光层，读取 rift_mantis_glowmask.png
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(RiftMantisEntity animatable) {
        // 死亡倒伏由骨骼动画完成，禁用原版侧翻
        return 0.0F;
    }
}
