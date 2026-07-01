package com.hp_end_expansion.client.model;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.entity.VoidWhale;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class VoidWhaleModel extends GeoModel<VoidWhale> {
    // 虚空鲸模型、贴图和动画资源路径。
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "geo/void_whale.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "textures/entity/void_whale.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "animations/void_whale.animation.json");

    // 返回 GeckoLib 读取的模型文件。
    @Override
    public ResourceLocation getModelResource(VoidWhale animatable) {
        return MODEL;
    }

    // 返回 GeckoLib 读取的实体贴图。
    @Override
    public ResourceLocation getTextureResource(VoidWhale animatable) {
        return TEXTURE;
    }

    // 返回 GeckoLib 读取的动画文件。
    @Override
    public ResourceLocation getAnimationResource(VoidWhale animatable) {
        return ANIMATION;
    }
}
