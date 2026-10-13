package org.hp.hp_end_expansion.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.LobsterEliteEntity;
import org.hp.hp_end_expansion.registry.ModEntities;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class LobsterEliteRenderer extends GeoEntityRenderer<LobsterEliteEntity> {
    private static final ResourceLocation GEO = id("geo/lobster_elite.geo.json");
    private static final ResourceLocation TEXTURE = id("textures/entity/lobster_elite.png");
    private static final ResourceLocation ANIMATION = id("animations/lobster_elite.animation.json");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, path);
    }

    public LobsterEliteRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        shadowRadius = 1.2F;
    }

    // 注册龙虾精英渲染器
    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.LOBSTER_ELITE.get(), LobsterEliteRenderer::new);
    }

    // 模型、贴图、动画资源路径
    private static final class Model extends GeoModel<LobsterEliteEntity> {
        @Override public ResourceLocation getModelResource(LobsterEliteEntity entity) { return GEO; }
        @Override public ResourceLocation getTextureResource(LobsterEliteEntity entity) { return TEXTURE; }
        @Override public ResourceLocation getAnimationResource(LobsterEliteEntity entity) { return ANIMATION; }
    }
}
