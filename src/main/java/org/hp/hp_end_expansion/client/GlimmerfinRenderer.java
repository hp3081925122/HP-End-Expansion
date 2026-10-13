package org.hp.hp_end_expansion.client;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.GlimmerfinEntity;
import org.hp.hp_end_expansion.registry.ModEntities;
import org.hp.hp_end_expansion.client.render.HpGeoModel;
import org.hp.hp_end_expansion.client.render.HpGeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT)
public final class GlimmerfinRenderer extends HpGeoEntityRenderer<GlimmerfinEntity> {
    public GlimmerfinRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        shadowRadius = 0.2F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    // 鱼鳍为半透明薄膜，使用半透明渲染保留贴图透明度
    @Override
    public RenderType getRenderType(GlimmerfinEntity entity, Identifier texture, MultiBufferSource buffer, float partialTick) {
        return RenderTypes.entityTranslucent(texture);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GLIMMERFIN.get(), GlimmerfinRenderer::new);
    }

    private static final class Model extends HpGeoModel<GlimmerfinEntity> {
        @Override
        public Identifier getModelResource(GlimmerfinEntity entity) {
            return Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/glimmerfin");
        }

        @Override
        public Identifier getTextureResource(GlimmerfinEntity entity) {
            return Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "textures/entity/glimmerfin.png");
        }

        @Override
        public Identifier getAnimationResource(GlimmerfinEntity entity) {
            return Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "entity/glimmerfin");
        }
    }
}
