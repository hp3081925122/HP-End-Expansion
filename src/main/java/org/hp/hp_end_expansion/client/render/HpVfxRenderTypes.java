package org.hp.hp_end_expansion.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;

@EventBusSubscriber(modid=Hp_end_expansion.MODID,value=Dist.CLIENT)
public final class HpVfxRenderTypes {
    private static final RenderPipeline ADDITIVE_PIPELINE=RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID,"pipeline/vfx_additive"))
        .withVertexShader("core/entity")
        .withFragmentShader(Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID,"core/vfx_additive"))
        .withShaderDefine("EMISSIVE")
        .withShaderDefine("NO_OVERLAY")
        .withShaderDefine("NO_CARDINAL_LIGHTING")
        .withSampler("Sampler0")
        .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.ADDITIVE),ColorTargetState.WRITE_COLOR))
        .withVertexFormat(DefaultVertexFormat.ENTITY,VertexFormat.Mode.QUADS)
        .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL,false))
        .build();
    private static final Function<Identifier,RenderType> ADDITIVE=Util.memoize(texture->RenderType.create(
        "hp_vfx_additive",RenderSetup.builder(ADDITIVE_PIPELINE).withTexture("Sampler0",texture).sortOnUpload().createRenderSetup()));

    public static RenderType additive(Identifier texture) { return ADDITIVE.apply(texture); }

    @SubscribeEvent public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(ADDITIVE_PIPELINE);
        LogUtils.getLogger().debug("VFX additive pipeline registered: blend={}, writeMask={}, emissive=true",
            ADDITIVE_PIPELINE.getColorTargetState().blendFunction().orElseThrow(),ADDITIVE_PIPELINE.getColorTargetState().writeMask());
    }
}
