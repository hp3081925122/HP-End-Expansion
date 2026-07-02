package com.hp_end_expansion.client.renderer;

import com.hp_end_expansion.client.model.EnderSnailModel;
import com.hp_end_expansion.world.entity.EnderSnail;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class EnderSnailRenderer extends GeoEntityRenderer<EnderSnail> {
    // 初始化末影蜗牛 GeckoLib 渲染器和阴影大小。
    public EnderSnailRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new EnderSnailModel());
        this.shadowRadius = 0.45F;
    }
}
