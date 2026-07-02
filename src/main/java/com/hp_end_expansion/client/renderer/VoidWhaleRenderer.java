package com.hp_end_expansion.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.hp_end_expansion.client.model.VoidWhaleModel;
import com.hp_end_expansion.world.entity.VoidWhale;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class VoidWhaleRenderer extends GeoEntityRenderer<VoidWhale> {
    // 初始化虚空鲸渲染器，并放大显示尺寸和阴影范围。
    public VoidWhaleRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new VoidWhaleModel());
        this.withScale(6.15F);
        this.shadowRadius = 8.4F;
    }

    // 应用实体朝向，并额外修正模型自身朝向轴和俯仰角。
    @Override
    protected void applyRotations(VoidWhale animatable, PoseStack poseStack, float 存在刻数, float 旋转偏航, float 局部刻, float nativeScale) {
        super.applyRotations(animatable, poseStack, 存在刻数, 旋转偏航, 局部刻, nativeScale);
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(animatable.getXRot()));
    }
}
