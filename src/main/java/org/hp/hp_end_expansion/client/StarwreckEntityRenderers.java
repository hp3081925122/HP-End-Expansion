package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.starwreck.EmberBeetleEntity;
import org.hp.hp_end_expansion.entity.starwreck.FallingStarEntity;
import org.hp.hp_end_expansion.entity.starwreck.MeteorTortoiseEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarBearerEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarCallerEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarMartyrEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarChaserEntity;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.cache.GeckoLibCache;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 星骸荒原三种生物共用：geo / 贴图 / 动画按实体 ID 取，发光部位由 *_glowmask 贴图给出
@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class StarwreckEntityRenderers {
    static {
        // 万象灾厄的动画是自己的格式，但放在 animations/ 下。GeckoLib 扫到后解析失败会中断整批加载，后面的动画文件全部丢失。
        GeckoLibCache.registerNamespaceExclusion("myriad_calamity");
    }

    private StarwreckEntityRenderers() {}

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StarwreckEntities.EMBER_MOTH.get(), context -> new Renderer<>(context, "ember_moth", 0.2F));
        event.registerEntityRenderer(StarwreckEntities.EMBER_BEETLE.get(), context -> new Renderer<>(context, "ember_beetle", 0.35F));
        event.registerEntityRenderer(StarwreckEntities.METEOR_TORTOISE.get(), context -> new Renderer<>(context, "meteor_tortoise", 0.8F));
        event.registerEntityRenderer(StarwreckEntities.STAR_CHASER.get(), context -> {
            Renderer<StarChaserEntity> renderer = new Renderer<>(context, "star_chaser", 0.9F);
            renderer.withScale(1.5F);
            return renderer;
        });
        event.registerEntityRenderer(StarwreckEntities.STAR_CALLER.get(), context -> new Renderer<>(context, "star_caller", 0.45F));
        event.registerEntityRenderer(StarwreckEntities.STAR_MARTYR.get(), context -> new Renderer<>(context, "star_martyr", 0.4F));
        event.registerEntityRenderer(StarwreckEntities.STAR_BEARER.get(), context -> new Renderer<>(context, "star_bearer", 0.7F));
        event.registerEntityRenderer(StarwreckEntities.STAR_FLAIL.get(), StarFlailRenderer::new);
        // 余烬地面只有粒子，没有模型
        event.registerEntityRenderer(StarwreckEntities.EMBER_GROUND.get(), NoopRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.STAR_RIFT.get(), StarRiftRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.FALLING_STAR.get(), FallingStarRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.STAR_IMPACT.get(), StarImpactRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.STAR_MARK.get(), StarMarkRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.BEARER_VFX.get(), BearerVfxRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.STAR_SHARD.get(), StarShardRenderer::new);
    }

    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.STAR_DEBRIS.get(), sprites -> new StarDebrisParticle.Provider(sprites, false));
        event.registerSpriteSet(ModParticles.STAR_ASH.get(), sprites -> new StarDebrisParticle.Provider(sprites, true));
        event.registerSpriteSet(ModParticles.BEARER_FLAME.get(), sprites -> new BearerParticle.Provider(sprites, BearerParticle.Kind.FLAME));
        event.registerSpriteSet(ModParticles.BEARER_RAGE.get(), sprites -> new BearerParticle.Provider(sprites, BearerParticle.Kind.RAGE));
        event.registerSpriteSet(ModParticles.BEARER_SHARD.get(), sprites -> new BearerParticle.Provider(sprites, BearerParticle.Kind.SHARD));
        event.registerSpriteSet(ModParticles.BEARER_VOID.get(), sprites -> new BearerParticle.Provider(sprites, BearerParticle.Kind.VOID));
        event.registerSpriteSet(ModParticles.BEARER_GLINT.get(), sprites -> new BearerParticle.Provider(sprites, BearerParticle.Kind.GLINT));
    }

    private static final class Renderer<T extends Mob & GeoEntity> extends GeoEntityRenderer<T> {
        private static final String[] VFX_BONES = {"head", "jaw", "core", "comet_3", "leg_front_right_paw"};
        private static final String[] BEARER_BONES = {"star", "star_broken", "chest", "forearm_right"};

        Renderer(EntityRendererProvider.Context context, String name, float shadow) {
            super(context, new Model<>(name));
            shadowRadius = shadow;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                                        @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            Matrix4f baseTranslations = entityRenderTranslations;
            super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
            if (animatable instanceof StarChaserEntity) {
                if (isReRender) {
                    entityRenderTranslations = baseTranslations;
                } else {
                    for (String name : VFX_BONES)
                        model.getBone(name).ifPresent(bone -> bone.setTrackingMatrices(true));
                }
            } else if (animatable instanceof StarBearerEntity && !isReRender) {
                for (String name : BEARER_BONES)
                    model.getBone(name).ifPresent(bone -> bone.setTrackingMatrices(true));
            }
        }

        // 甲虫、逐星兽和坠星教团的死亡由动画表现，不再叠加原版侧翻
        @Override protected float getDeathMaxRotation(T animatable) {
            return animatable instanceof EmberBeetleEntity || animatable instanceof StarChaserEntity || animatable instanceof StarCallerEntity
                || animatable instanceof StarMartyrEntity || animatable instanceof StarBearerEntity ? 0 : super.getDeathMaxRotation(animatable);
        }

        @Override public void renderFinal(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                                          float partialTick, int packedLight, int packedOverlay, int colour) {
            super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);
            if (animatable instanceof StarChaserEntity chaser && !chaser.isInvisible())
                StarChaserVfx.render(chaser, model, poseStack, bufferSource, partialTick);
            if (animatable instanceof StarBearerEntity bearer) {
                String boneName = bearer.isStarBroken() ? "star_broken" : "star";
                model.getBone(boneName).ifPresent(bone -> {
                    Vector3f local = bone.getLocalSpaceMatrix().transformPosition(new Vector3f());
                    bearer.starAnchor = bearer.getPosition(partialTick).add(local.x, local.y, local.z);
                    if (bearer.isDeadOrDying()) return;
                    // 圣星常亮一团白金圣辉；反扑和抛星的前摇里越聚越亮。碎星后只剩一点暗红余光
                    float age = bearer.tickCount + partialTick;
                    float charge = bearer.chargeUp(partialTick);
                    float breathe = 0.92F + 0.08F * (float) Math.sin(age * 0.25);
                    Vec3 at = new Vec3(local.x, local.y - 0.1, local.z);
                    if (bearer.isStarBroken())
                        BearerVfxRenderer.glow(poseStack, bufferSource, entityRenderDispatcher.cameraOrientation(), at, 0.45F * breathe, 0, 0.35F, 0.12F, 0.06F);
                    else BearerVfxRenderer.glow(poseStack, bufferSource, entityRenderDispatcher.cameraOrientation(), at, (0.95F + 1.3F * charge) * breathe, age * 2,
                        0.5F + 0.6F * charge, 0.38F + 0.6F * charge, 0.18F + 0.6F * charge);
                });
                // 胸口星晶在 chest 骨骼轴心上方 0.5、前方 0.32；拳心在前臂轴心（肘）下方 0.62
                Vec3 chest = model.getBone("chest").map(bone -> toVec(bone.getLocalSpaceMatrix().transformPosition(new Vector3f(0, 0.5F, -0.32F)))).orElse(null);
                Vec3 fist = model.getBone("forearm_right").map(bone -> toVec(bone.getLocalSpaceMatrix().transformPosition(new Vector3f(0, -0.62F, 0)))).orElse(null);
                Vec3 origin = bearer.getPosition(partialTick);
                bearer.chestAnchor = chest == null ? null : origin.add(chest);
                bearer.fistAnchor = fist == null ? null : origin.add(fist);
                if (!bearer.isDeadOrDying())
                    BearerVfxRenderer.negative(bearer, poseStack, bufferSource, partialTick, entityRenderDispatcher.cameraOrientation(),
                        entityRenderDispatcher.camera.getPosition(), chest, fist);
            }
        }

        private static Vec3 toVec(Vector3f v) { return new Vec3(v.x, v.y, v.z); }
    }

    private static final class Model<T extends Mob & GeoEntity> extends GeoModel<T> {
        private static final String[] CRYSTAL_BONES = {"crystal_1", "crystal_2", "crystal_3", "crystal_4"};
        private final ResourceLocation model, texture, overload, animation;

        Model(String name) {
            model = id("geo/" + name + ".geo.json");
            texture = id("textures/entity/" + name + ".png");
            overload = id("textures/entity/" + name + "_overload.png");
            animation = id("animations/" + name + ".animation.json");
        }

        private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, path); }
        @Override public ResourceLocation getModelResource(T animatable) { return model; }
        // 逐星兽过载后换成胸核裸露、裂缝加宽的贴图，发光层自动取 *_overload_glowmask
        @Override public ResourceLocation getTextureResource(T animatable) {
            return animatable instanceof StarChaserEntity chaser && chaser.isOverloaded() ? overload : texture;
        }
        @Override public ResourceLocation getAnimationResource(T animatable) { return animation; }

        @Override public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> state) {
            super.setCustomAnimations(animatable, instanceId, state);
            if (animatable instanceof MeteorTortoiseEntity tortoise) {
                int left = tortoise.getCrystals();
                for (int i = 0; i < CRYSTAL_BONES.length; i++) {
                    GeoBone bone = getAnimationProcessor().getBone(CRYSTAL_BONES[i]);
                    if (bone != null) bone.setHidden(i >= left);
                }
            }
            if (animatable instanceof StarBearerEntity bearer) {
                GeoBone whole = getAnimationProcessor().getBone("star");
                GeoBone broken = getAnimationProcessor().getBone("star_broken");
                if (whole != null) whole.setHidden(bearer.isStarBroken());
                if (broken != null) broken.setHidden(!bearer.isStarBroken());
            }
        }
    }
}
