package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.slf4j.Logger;

@EventBusSubscriber(modid = Hp_end_expansion.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class StarfallRenderTypes extends RenderType {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, RenderType> ADDITIVE_TYPES = new HashMap<>();
    private static ShaderInstance additiveShader;

    private StarfallRenderTypes() {
        super("starfall_additive", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {});
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
            ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "starfall_additive"), DefaultVertexFormat.NEW_ENTITY), shader -> {
                additiveShader = shader;
                LOGGER.debug("Starfall additive shader loaded with ONE + ONE blending");
            });
    }

    public static RenderType additive(ResourceLocation texture) {
        return ADDITIVE_TYPES.computeIfAbsent(texture, key -> create("starfall_additive", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                .setShaderState(new ShaderStateShard(() -> additiveShader))
                .setTextureState(new TextureStateShard(key, false, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false)));
    }
}
