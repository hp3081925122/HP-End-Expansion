package org.hp.hp_end_expansion.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Quaternionf;

public final class HpGuiGraphics {
    private final GuiGraphicsExtractor graphics;
    private int color=-1;
    private boolean glow;
    public HpGuiGraphics(GuiGraphicsExtractor graphics) { this.graphics=graphics; }
    public int guiWidth() { return graphics.guiWidth(); }
    public int guiHeight() { return graphics.guiHeight(); }
    public void setColor(float r,float g,float b,float a) { color=ARGB.colorFromFloat(a,r,g,b); }
    public void setGlow(boolean value) { glow=value; }
    public void fill(int x0,int y0,int x1,int y1,int color) { graphics.fill(x0,y0,x1,y1,color); }
    public void drawString(Font font,Component text,int x,int y,int color,boolean shadow) { graphics.text(font,text,x,y,color,shadow); }
    public void blit(Identifier texture,int x,int y,float u,float v,int width,int height,int tw,int th) { blit(texture,x,y,width,height,u,v,width,height,tw,th); }
    public void blit(Identifier texture,int x,int y,int width,int height,float u,float v,int sw,int sh,int tw,int th) {
        graphics.blit(glow?HpSkyDraw.GUI_GLOW:RenderPipelines.GUI_TEXTURED,texture,x,y,u,v,width,height,sw,sh,tw,th,color);
    }
    public Pose pose() { return new Pose(); }
    public final class Pose {
        public void pushPose() { graphics.pose().pushMatrix(); }
        public void popPose() { graphics.pose().popMatrix(); }
        public void translate(float x,float y,float z) { graphics.pose().translate(x,y); }
        public void scale(float x,float y,float z) { graphics.pose().scale(x,y); }
        public void mulPose(Quaternionf rotation) { graphics.pose().rotate(2F*(float)Math.atan2(rotation.z,rotation.w)); }
    }
}
