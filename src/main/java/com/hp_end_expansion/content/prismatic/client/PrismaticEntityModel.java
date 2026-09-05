package com.hp_end_expansion.content.prismatic.client;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.content.prismatic.entity.PrismaticAnimated;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

// 渲染器构造时固定资源位置，渲染期间不访问文件系统或注册表。
public final class PrismaticEntityModel<T extends PrismaticAnimated> extends GeoModel<T> {
    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animation;

    public PrismaticEntityModel(String id) {
        model = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "geo/prismatic/" + id + ".geo.json");
        texture = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "textures/entity/prismatic/" + id + ".png");
        animation = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "animations/prismatic/" + id + ".animation.json");
    }

    @Override public ResourceLocation getModelResource(T animatable) { return model; }
    @Override public ResourceLocation getTextureResource(T animatable) { return texture; }
    @Override public ResourceLocation getAnimationResource(T animatable) { return animation; }
}
