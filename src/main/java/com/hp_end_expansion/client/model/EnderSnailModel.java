package com.hp_end_expansion.client.model;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.entity.EnderSnail;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class EnderSnailModel extends GeoModel<EnderSnail> {
    // 末影蜗牛 GeckoLib 模型、贴图和动画资源路径。
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "geo/ender_snail.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "textures/entity/ender_snail.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "animations/ender_snail.animation.json");

    // 返回 GeckoLib 读取的模型文件。
    @Override
    public ResourceLocation getModelResource(EnderSnail animatable) {
        return MODEL;
    }

    // 返回 GeckoLib 读取的实体贴图。
    @Override
    public ResourceLocation getTextureResource(EnderSnail animatable) {
        return TEXTURE;
    }

    // 返回 GeckoLib 读取的动画文件。
    @Override
    public ResourceLocation getAnimationResource(EnderSnail animatable) {
        return ANIMATION;
    }

    // 按晶簇状态隐藏或显示壳上的晶簇骨骼。
    @Override
    public void setCustomAnimations(EnderSnail animatable, long instanceId, AnimationState<EnderSnail> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        this.getBone("crystals").ifPresent(crystals -> crystals.setHidden(animatable.getChorusCrystals() <= 0));
    }
}
