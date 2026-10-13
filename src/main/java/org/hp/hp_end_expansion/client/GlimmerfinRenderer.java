package org.hp.hp_end_expansion.client;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.GlimmerfinEntity;
import org.hp.hp_end_expansion.registry.ModEntities;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class GlimmerfinRenderer extends GeoEntityRenderer<GlimmerfinEntity> {
    public GlimmerfinRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        shadowRadius = 0.2F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    // 鱼鳍为半透明薄膜，使用半透明渲染保留贴图透明度
    @Override
    public RenderType getRenderType(GlimmerfinEntity entity, ResourceLocation texture, MultiBufferSource buffer, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GLIMMERFIN.get(), GlimmerfinRenderer::new);
    }

    private static final class Model extends GeoModel<GlimmerfinEntity> {
        @Override
        public ResourceLocation getModelResource(GlimmerfinEntity entity) {
            return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "geo/glimmerfin.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(GlimmerfinEntity entity) {
            return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/glimmerfin.png");
        }

        @Override
        public ResourceLocation getAnimationResource(GlimmerfinEntity entity) {
            return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "animations/glimmerfin.animation.json");
        }
    }
}
