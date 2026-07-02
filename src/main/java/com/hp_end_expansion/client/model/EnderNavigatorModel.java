package com.hp_end_expansion.client.model;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.entity.EnderNavigator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class EnderNavigatorModel extends GeoModel<EnderNavigator> {
    // 末影领航者 GeckoLib 模型、贴图和动画资源路径。
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "geo/ender_navigator.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "textures/entity/ender_navigator.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "animations/ender_navigator.animation.json");

    // 返回 GeckoLib 读取的模型文件。
    @Override
    public ResourceLocation getModelResource(EnderNavigator animatable) {
        return MODEL;
    }

    // 返回 GeckoLib 读取的实体贴图。
    @Override
    public ResourceLocation getTextureResource(EnderNavigator animatable) {
        return TEXTURE;
    }

    // 返回 GeckoLib 读取的动画文件。
    @Override
    public ResourceLocation getAnimationResource(EnderNavigator animatable) {
        return ANIMATION;
    }

    // 让头部和面具会跟着目标轻微转向，飞行时观感更灵活。
    @Override
    public void setCustomAnimations(EnderNavigator animatable, long instanceId, AnimationState<EnderNavigator> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone head = this.getAnimationProcessor().getBone("head");
        GeoBone mask = this.getAnimationProcessor().getBone("mask");
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);

        if (head != null) {
            // 把头部水平偏转限制住，避免高速盘旋时面具扭得太夸张。
            head.setRotY(Mth.clamp(entityData.netHeadYaw(), -22.5F, 22.5F) * Mth.DEG_TO_RAD);
            // 给头部一点上下追踪，强化施法注视感。
            head.setRotX(Mth.clamp(entityData.headPitch(), -18.0F, 18.0F) * Mth.DEG_TO_RAD);
        }

        if (mask != null) {
            // 面具略微跟随头部，保持头冠和面罩的整体感。
            mask.setRotY(Mth.clamp(entityData.netHeadYaw(), -16.0F, 16.0F) * Mth.DEG_TO_RAD);
            mask.setRotX(Mth.clamp(entityData.headPitch(), -12.0F, 12.0F) * Mth.DEG_TO_RAD);
        }
    }
}
