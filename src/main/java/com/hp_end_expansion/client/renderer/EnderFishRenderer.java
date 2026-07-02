package com.hp_end_expansion.client.renderer;

import com.hp_end_expansion.client.model.EnderFishModel;
import com.hp_end_expansion.world.entity.EnderFish;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class EnderFishRenderer extends GeoEntityRenderer<EnderFish> {
    // 初始化末影鱼 GeckoLib 渲染器和阴影大小。
    public EnderFishRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new EnderFishModel());
        this.withScale(0.62F);
        this.shadowRadius = 0.22F;
    }

    // 让模型跟随实体俯仰，游动时能朝向上下目标。
    @Override
    protected void applyRotations(EnderFish animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        poseStack.mulPose(Axis.XP.rotationDegrees(animatable.getXRot()));
    }
}
