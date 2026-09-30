package org.hp.hp_end_expansion.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.ModEntities;
import org.hp.hp_end_expansion.registry.ModParticles;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class RiftMantisClientRegistration {
    private RiftMantisClientRegistration() {
    }

    // 实体渲染器
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.RIFT_MANTIS.get(), RiftMantisRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFT_MATRIARCH.get(), RiftMatriarchRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_RAY.get(), VoidRayRenderer::new);
        event.registerEntityRenderer(ModEntities.STAR_DEVOURER.get(), StarDevourerRenderer::new);
        event.registerEntityRenderer(ModEntities.STAR_CORE.get(), StarCoreRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_RAY_VFX.get(), VoidRayVfxRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFT_VFX.get(), RiftVfxRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFT_BLADE.get(), RiftBladeRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFT_FISSURE.get(), RiftFissureRenderer::new);
        event.registerEntityRenderer(ModEntities.END_MOTE.get(), EndMoteRenderer::new);
    }

    // 粒子工厂
    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.STAR_EMBER.get(), StarEmberParticle.Provider::new);
        event.registerSpriteSet(ModParticles.RIFT_SPARK.get(), RiftParticles.SparkProvider::new);
        event.registerSpriteSet(ModParticles.RIFT_SHARD.get(), RiftParticles.ShardProvider::new);
    }
}
