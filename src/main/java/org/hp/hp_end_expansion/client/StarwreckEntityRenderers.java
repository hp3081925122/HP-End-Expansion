package org.hp.hp_end_expansion.client;

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
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 星骸荒原三种生物共用：geo / 贴图 / 动画按实体 ID 取，发光部位由 *_glowmask 贴图给出
@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class StarwreckEntityRenderers {
    private StarwreckEntityRenderers() {}

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StarwreckEntities.EMBER_MOTH.get(), context -> new Renderer<>(context, "ember_moth", 0.2F));
        event.registerEntityRenderer(StarwreckEntities.EMBER_BEETLE.get(), context -> new Renderer<>(context, "ember_beetle", 0.35F));
        event.registerEntityRenderer(StarwreckEntities.METEOR_TORTOISE.get(), context -> new Renderer<>(context, "meteor_tortoise", 0.8F));
        // 余烬地面只有粒子，没有模型
        event.registerEntityRenderer(StarwreckEntities.EMBER_GROUND.get(), NoopRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.STAR_RIFT.get(), StarRiftRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.FALLING_STAR.get(), FallingStarRenderer::new);
        event.registerEntityRenderer(StarwreckEntities.STAR_IMPACT.get(), StarImpactRenderer::new);
    }

    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.STAR_DEBRIS.get(), sprites -> new StarDebrisParticle.Provider(sprites, false));
        event.registerSpriteSet(ModParticles.STAR_ASH.get(), sprites -> new StarDebrisParticle.Provider(sprites, true));
    }

    private static final class Renderer<T extends Mob & GeoEntity> extends GeoEntityRenderer<T> {
        Renderer(EntityRendererProvider.Context context, String name, float shadow) {
            super(context, new Model<>(name));
            shadowRadius = shadow;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        // 甲虫的死亡由鞘翅张开的动画表现，不再叠加原版侧翻
        @Override protected float getDeathMaxRotation(T animatable) {
            return animatable instanceof EmberBeetleEntity ? 0 : super.getDeathMaxRotation(animatable);
        }
    }

    private static final class Model<T extends Mob & GeoEntity> extends GeoModel<T> {
        private static final String[] CRYSTAL_BONES = {"crystal_1", "crystal_2", "crystal_3", "crystal_4"};
        private final ResourceLocation model, texture, animation;

        Model(String name) {
            model = id("geo/" + name + ".geo.json");
            texture = id("textures/entity/" + name + ".png");
            animation = id("animations/" + name + ".animation.json");
        }

        private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, path); }
        @Override public ResourceLocation getModelResource(T animatable) { return model; }
        @Override public ResourceLocation getTextureResource(T animatable) { return texture; }
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
        }
    }
}
