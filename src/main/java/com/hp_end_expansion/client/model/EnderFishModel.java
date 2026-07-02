package com.hp_end_expansion.client.model;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.entity.EnderFish;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class EnderFishModel extends GeoModel<EnderFish> {
    // 末影鱼 GeckoLib 模型、贴图和动画资源路径。
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "geo/ender_fish.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "textures/entity/ender_fish.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "animations/ender_fish.animation.json");

    // 返回 GeckoLib 读取的模型文件。
    @Override
    public ResourceLocation getModelResource(EnderFish animatable) {
        return MODEL;
    }

    // 返回 GeckoLib 读取的实体贴图。
    @Override
    public ResourceLocation getTextureResource(EnderFish animatable) {
        return TEXTURE;
    }

    // 返回 GeckoLib 读取的动画文件。
    @Override
    public ResourceLocation getAnimationResource(EnderFish animatable) {
        return ANIMATION;
    }
}
