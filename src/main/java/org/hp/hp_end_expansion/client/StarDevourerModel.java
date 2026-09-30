package org.hp.hp_end_expansion.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.StarDevourerEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

public final class StarDevourerModel extends GeoModel<StarDevourerEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "geo/star_devourer.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/star_devourer.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "animations/star_devourer.animation.json");

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getModelResource(StarDevourerEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getModelResource(StarDevourerEntity entity, @Nullable GeoRenderer<StarDevourerEntity> renderer) {
        return MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureResource(StarDevourerEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getTextureResource(StarDevourerEntity entity, @Nullable GeoRenderer<StarDevourerEntity> renderer) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(StarDevourerEntity entity) {
        return ANIMATION;
    }

    // 核心骨骼名
    private static final String[] CORE_NAMES = {"core_left", "core_center", "core_right"};

    @Override
    public void setCustomAnimations(StarDevourerEntity animatable, long instanceId, AnimationState<StarDevourerEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        // 核心碎裂后隐藏晶体骨骼、显示碎裂骨骼
        for (int i = 0; i < 3; i++) {
            boolean broken = animatable.isCoreBroken(i);
            GeoBone crystal = this.getAnimationProcessor().getBone(CORE_NAMES[i] + "_crystal");
            GeoBone shards = this.getAnimationProcessor().getBone(CORE_NAMES[i] + "_broken");
            if (crystal != null) {
                crystal.setHidden(broken);
            }
            if (shards != null) {
                shards.setHidden(!broken);
            }
        }
    }
}
