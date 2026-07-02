package com.hp_end_expansion.client.renderer;

import com.hp_end_expansion.client.model.EnderNavigatorModel;
import com.hp_end_expansion.world.entity.EnderNavigator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class EnderNavigatorRenderer extends GeoEntityRenderer<EnderNavigator> {
    // 初始化末影领航者 GeckoLib 渲染器、缩放和自发光层。
    public EnderNavigatorRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new EnderNavigatorModel());
        this.withScale(2.04F);
        this.shadowRadius = 0.84F;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    // 让飞行中的领航者整体跟随实体俯仰，突袭和升降时更有压迫感。
    @Override
    protected void applyRotations(EnderNavigator animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        poseStack.mulPose(Axis.XP.rotationDegrees(animatable.getXRot()));
    }
}
