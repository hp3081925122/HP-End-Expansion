package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.hp.hp_end_expansion.entity.tidelight.PearlHermitCrabEntity;
import org.hp.hp_end_expansion.entity.tidelight.ReefEelEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class TidelightEntityRenderer<T extends Mob & GeoEntity> extends GeoEntityRenderer<T> {
    public TidelightEntityRenderer(EntityRendererProvider.Context context, String name, float shadow) {
        super(context, new Model<>(name)); shadowRadius = shadow; addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    // 幼年寄居蟹按一半大小绘制
    @Override public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        boolean baby = entity.isBaby();
        if (baby) { poseStack.pushPose(); poseStack.scale(0.5F, 0.5F, 0.5F); }
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
        if (baby) poseStack.popPose();
    }

    private static final class Model<T extends Mob & GeoEntity> extends GeoModel<T> {
        private static final String[] EEL_SEGMENTS = {"seg1_follow", "seg2_follow", "seg3_follow", "seg4_follow", "seg5_follow"};
        private static final float EEL_FOLLOW_LIMIT = 35F;
        private static final int EEL_FOLLOW_DELAY = 2;
        private final String name;
        private final ResourceLocation model, texture, animation, altTexture;
        private static final ResourceLocation FALLBACK_MODEL = id("geo/end_mote.geo.json");
        private static final ResourceLocation FALLBACK_TEXTURE = id("textures/entity/end_mote.png");
        private static final ResourceLocation FALLBACK_ANIMATION = id("animations/end_mote.animation.json");
        private Model(String name) {
            this.name = name; model = id("geo/" + name + ".geo.json"); texture = id("textures/entity/" + name + ".png"); animation = id("animations/" + name + ".animation.json");
            // 寄居蟹刷走珍珠后换暗壳贴图，礁鳗预警时换斑点全亮的贴图；两者与底图共用 UV，各自带发光遮罩
            altTexture = name.equals("pearl_hermit_crab") ? id("textures/entity/pearl_hermit_crab_dark.png") : name.equals("reef_eel") ? id("textures/entity/reef_eel_warning.png") : null;
        }
        private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("hp_end_expansion", name); }
        @Override public ResourceLocation getModelResource(T entity) { return GeckoLibCache.getBakedModels().containsKey(model) ? model : FALLBACK_MODEL; }
        @Override public ResourceLocation getTextureResource(T entity) {
            if (!getModelResource(entity).equals(model)) return FALLBACK_TEXTURE;
            if (entity instanceof PearlHermitCrabEntity crab && !crab.hasPearl()) return altTexture;
            if (entity instanceof ReefEelEntity eel && eel.isWarning()) return altTexture;
            return texture;
        }
        @Override public ResourceLocation getAnimationResource(T entity) { return GeckoLibCache.getBakedAnimations().containsKey(animation) ? animation : FALLBACK_ANIMATION; }
        @Override public RenderType getRenderType(T entity, ResourceLocation texture) { return name.equals("lantern_jellyfish") ? RenderType.entityTranslucent(texture) : super.getRenderType(entity, texture); }

        @Override public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> state) {
            super.setCustomAnimations(animatable, instanceId, state);
            if (animatable instanceof PearlHermitCrabEntity crab) {
                GeoBone pearl = getAnimationProcessor().getBone("pearl");
                if (pearl != null) pearl.setHidden(!crab.hasPearl());
            } else if (animatable instanceof ReefEelEntity eel) {
                animateEel(eel, state);
            }
        }

        // 弹簧跟随：第 k 节的朝向取 k×2 tick 前的身体朝向，写在不参与动画的 *_follow 骨骼上，每帧直接赋值不会累积。
        // 俯仰写在 body 上，只在游动或攻击时生效，盘踞时保持平躺。
        private void animateEel(ReefEelEntity eel, AnimationState<T> state) {
            float partial = state.getPartialTick();
            float previous = Mth.rotLerp(partial, eel.yBodyRotO, eel.yBodyRot);
            for (int k = 0; k < EEL_SEGMENTS.length; k++) {
                GeoBone bone = getAnimationProcessor().getBone(EEL_SEGMENTS[k]);
                float past = eel.trailYaw((k + 1) * EEL_FOLLOW_DELAY, partial);
                float relative = Mth.clamp(Mth.wrapDegrees(previous - past), -EEL_FOLLOW_LIMIT, EEL_FOLLOW_LIMIT);
                if (bone != null) bone.setRotY(relative * Mth.DEG_TO_RAD);
                previous = past;
            }
            GeoBone body = getAnimationProcessor().getBone("body");
            if (body != null) {
                boolean active = eel.attackPhase() != 0 || state.isMoving();
                float pitch = active ? Mth.clamp(Mth.lerp(partial, eel.xRotO, eel.getXRot()), -50F, 50F) * 0.8F : 0F;
                // GeckoLib 骨骼 +X 为抬头，实体 xRot 为正是低头，所以取反
                body.setRotX(-pitch * Mth.DEG_TO_RAD);
            }
        }
    }
}
