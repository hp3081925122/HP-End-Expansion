package com.hp_end_expansion.client.renderer;

import com.hp_end_expansion.client.model.EnderBoxModel;
import com.hp_end_expansion.world.entity.EnderBox;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class EnderBoxRenderer extends GeoEntityRenderer<EnderBox> {
    // 初始化末影盒 GeckoLib 渲染器和阴影大小。
    public EnderBoxRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new EnderBoxModel());
        this.shadowRadius = 0.5F;
    }
}
