package com.hp_end_expansion.client.model;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.entity.EnderBox;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class EnderBoxModel extends GeoModel<EnderBox> {
    // 末影盒 GeckoLib 模型、贴图和动画资源路径。
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "geo/ender_box.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "textures/entity/ender_box.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "animations/ender_box.animation.json");

    // 返回 GeckoLib 读取的模型文件。
    @Override
    public ResourceLocation getModelResource(EnderBox animatable) {
        return MODEL;
    }

    // 返回 GeckoLib 读取的实体贴图。
    @Override
    public ResourceLocation getTextureResource(EnderBox animatable) {
        return TEXTURE;
    }

    // 返回 GeckoLib 读取的动画文件。
    @Override
    public ResourceLocation getAnimationResource(EnderBox animatable) {
        return ANIMATION;
    }
}
