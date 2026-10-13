package org.hp.hp_end_expansion.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@EventBusSubscriber(modid=Hp_end_expansion.MODID,value=Dist.CLIENT)
public final class HpSkyDraw {
    private record Key(boolean textured,boolean additive,VertexFormat.Mode mode) {}
    private static final Map<Key,RenderPipeline> PIPELINES=new HashMap<>();
    public static final RenderPipeline GUI_GLOW=RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID,"pipeline/gui_glow"))
        .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING)).build();
    private static boolean additive;
    static Identifier texture;
    static final Vector4f color=new Vector4f(1,1,1,1);
    static {
        for(boolean textured:new boolean[]{false,true}) for(boolean glow:new boolean[]{false,true})
            for(VertexFormat.Mode mode:new VertexFormat.Mode[]{VertexFormat.Mode.TRIANGLES,VertexFormat.Mode.QUADS}) {
                var builder=RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID,"pipeline/sky_"+textured+"_"+glow+"_"+mode.name().toLowerCase(java.util.Locale.ROOT)))
                    .withVertexShader(textured?"core/position_tex_color":"core/position_color")
                    .withFragmentShader(textured?"core/position_tex_color":"core/position_color")
                    .withVertexFormat(textured?DefaultVertexFormat.POSITION_TEX_COLOR:DefaultVertexFormat.POSITION_COLOR,mode)
                    .withColorTargetState(new ColorTargetState(glow?BlendFunction.LIGHTNING:BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS,false)).withCull(false);
                if(textured) builder.withSampler("Sampler0");
                PIPELINES.put(new Key(textured,glow,mode),builder.build());
            }
    }
    @SubscribeEvent public static void register(RegisterRenderPipelinesEvent event) { PIPELINES.values().forEach(event::registerPipeline);event.registerPipeline(GUI_GLOW); }
    static RenderPipeline pipeline(MeshData.DrawState state) { return PIPELINES.get(new Key(state.format()==DefaultVertexFormat.POSITION_TEX_COLOR,additive,state.mode())); }
    public static void normal() { additive=false; }
    public static void additive() { additive=true; }
    public static void colored() { texture=null; }
    public static void textured() {}
    public static void texture(Identifier value) { texture=value; }
    public static void color(float r,float g,float b,float a) { color.set(r,g,b,a); }
    public static void draw(MeshData mesh) { try(HpSkyMesh buffer=new HpSkyMesh()) { buffer.upload(mesh); buffer.draw(new Matrix4f()); } }
}
