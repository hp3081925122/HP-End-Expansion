package org.hp.hp_end_expansion.client;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.StarDevourerEntity;
import com.geckolib.animation.state.AnimationTest;
import org.hp.hp_end_expansion.client.render.HpBone;
import org.hp.hp_end_expansion.client.render.HpGeoModel;

public final class StarDevourerModel extends HpGeoModel<StarDevourerEntity> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/star_devourer");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/star_devourer.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/star_devourer");

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getModelResource(StarDevourerEntity entity) {
        return MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getTextureResource(StarDevourerEntity entity) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(StarDevourerEntity entity) {
        return ANIMATION;
    }

    // 核心骨骼名
    private static final String[] CORE_NAMES = {"core_left", "core_center", "core_right"};

    @Override
    public void setCustomAnimations(StarDevourerEntity animatable, long instanceId, AnimationTest<StarDevourerEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        // 核心碎裂后隐藏晶体骨骼、显示碎裂骨骼
        for (int i = 0; i < 3; i++) {
            boolean broken = animatable.isCoreBroken(i);
            HpBone crystal = this.getAnimationProcessor().getBone(CORE_NAMES[i] + "_crystal");
            HpBone shards = this.getAnimationProcessor().getBone(CORE_NAMES[i] + "_broken");
            if (crystal != null) {
                crystal.setHidden(broken);
            }
            if (shards != null) {
                shards.setHidden(!broken);
            }
        }
    }
}
