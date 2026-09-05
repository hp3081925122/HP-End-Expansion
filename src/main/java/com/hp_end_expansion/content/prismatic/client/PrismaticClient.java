package com.hp_end_expansion.content.prismatic.client;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.content.prismatic.PrismaticEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 只在客户端加载此事件订阅器，公共实体注册器不引用客户端类。
@EventBusSubscriber(modid = HpEndExpansion.MODID, value = Dist.CLIENT)
public final class PrismaticClient {
    private PrismaticClient() { }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PrismaticEntities.SHARDLING.get(), context -> new PrismaticEntityRenderer<>(context, "shardling", 0.3F));
        event.registerEntityRenderer(PrismaticEntities.PRISM_HARE.get(), context -> new PrismaticEntityRenderer<>(context, "prism_hare", 0.3F));
        event.registerEntityRenderer(PrismaticEntities.FACET_RAM.get(), context -> new PrismaticEntityRenderer<>(context, "facet_ram", 0.55F));
        event.registerEntityRenderer(PrismaticEntities.DUSK_MOTH.get(), context -> new PrismaticEntityRenderer<>(context, "dusk_moth", 0.2F));
        event.registerEntityRenderer(PrismaticEntities.GLASS_STALKER.get(), context -> new PrismaticEntityRenderer<>(context, "glass_stalker", 0.6F));
        event.registerEntityRenderer(PrismaticEntities.NEEDLE_SPITTER.get(), context -> new PrismaticEntityRenderer<>(context, "needle_spitter", 0.5F));
        event.registerEntityRenderer(PrismaticEntities.SHARDBACK.get(), context -> new PrismaticEntityRenderer<>(context, "shardback", 0.65F));
        event.registerEntityRenderer(PrismaticEntities.GLARE_WISP.get(), context -> new PrismaticEntityRenderer<>(context, "glare_wisp", 0.3F));
        event.registerEntityRenderer(PrismaticEntities.FAULT_WARDEN.get(), context -> new PrismaticEntityRenderer<>(context, "fault_warden", 1.2F));
        event.registerEntityRenderer(PrismaticEntities.MIRROR_HUNTRESS.get(), context -> new PrismaticEntityRenderer<>(context, "mirror_huntress", 0.7F));
        event.registerEntityRenderer(PrismaticEntities.PARALLAX_REGENT.get(), context -> new PrismaticEntityRenderer<>(context, "parallax_regent", 1.5F));
        event.registerEntityRenderer(PrismaticEntities.CRYSTAL_NEEDLE.get(), context -> new ThrownItemRenderer<>(context, 0.7F, true));
    }
}
