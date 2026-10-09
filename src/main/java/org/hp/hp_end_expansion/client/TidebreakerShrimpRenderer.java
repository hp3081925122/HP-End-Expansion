package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.tidelight.TidebreakerShrimpEntity;
import org.hp.hp_end_expansion.registry.ModEntities;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@EventBusSubscriber(modid=Hp_end_expansion.MODID,value=Dist.CLIENT)
public final class TidebreakerShrimpRenderer extends GeoEntityRenderer<TidebreakerShrimpEntity> {
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID,path);}
    private static ResourceLocation fx(String name){return id("textures/entity/tidebreaker_"+name+".png");}
    private static final ResourceLocation STREAK=fx("streak"),ARC=fx("arc"),BUBBLE=fx("bubble"),SHARD=fx("shard"),GUARD=fx("guard"),IMPACT=fx("impact");
    public TidebreakerShrimpRenderer(EntityRendererProvider.Context c){super(c,new Model());shadowRadius=.65F;}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(ModEntities.TIDEBREAKER_SHRIMP.get(),TidebreakerShrimpRenderer::new);e.registerEntityRenderer(ModEntities.TIDEBREAKER_SHRIMP_TEST.get(),TidebreakerShrimpRenderer::new);}
    @Override protected float getDeathMaxRotation(TidebreakerShrimpEntity e){return 0;}
    @Override public boolean shouldRender(TidebreakerShrimpEntity e,Frustum f,double x,double y,double z){return e.shouldRender(x,y,z)&&f.isVisible(e.getBoundingBox().inflate(e.skill()==0?2:9));}
    @Override public void render(TidebreakerShrimpEntity e,float yaw,float pt,PoseStack ps,MultiBufferSource b,int light){
        super.render(e,yaw,pt,ps,b,light);
        if(e.isDeadOrDying())return;
        Vec3 pos=e.getPosition(pt),cam=entityRenderDispatcher.camera.getPosition().subtract(pos);float age=e.skillAge(pt);PoseStack.Pose p=ps.last();
        if(e.skill()==TidebreakerShrimpEntity.COMBO){
            for(int side=0;side<2;side++){
                int hit=side==0?9:15;float t=age-hit;Vec3 fist=e.fist(side,age);
                if(t<0&&t>=-6)disc(p,b,ARC,fist,e.forward(),.18+.12*(1+t/6),.4F);
                if(t>=0&&t<6){Vec3 end=e.impact(side).subtract(pos);float fade=1-t/6;line(p,b,fist,end,cam,.22,fade);burst(p,b,end,cam,t,.75F);}
            }
        }else if(e.skill()==TidebreakerShrimpEntity.DASH){
            Vec3 fist=e.fist(1,age);
            if(age<12){float charge=Mth.clamp(age/12,0,1);disc(p,b,ARC,fist,e.forward(),.55-.25*charge,.3F+.5F*charge);}
            if(age>=12&&age<20){float fade=age<=17?1:(20-age)/3;line(p,b,fist.subtract(e.forward().scale(1.7)),fist.add(e.forward().scale(.8)),cam,.3,fade);disc(p,b,ARC,fist,e.forward(),.45,fade);}
        }else if(e.skill()==TidebreakerShrimpEntity.BUBBLE&&age<14&&age>=3){
            // 蓄力空泡与发射点一致，位于双拳拳面中点
            Vec3 fist=e.fist(0,age).add(e.fist(1,age)).scale(.5);float q=(age-3)/11;AbyssFx.billboard(p,AbyssFx.glow(b,BUBBLE),fist,cam.subtract(fist),.1+.2*q,age*.12,.4F+.5F*q);
            disc(p,b,ARC,fist,e.forward(),.5-.25*q,.4F);
        }else if(e.skill()==TidebreakerShrimpEntity.GUARD&&age>=3&&age<=15){
            Vec3 center=e.fist(0,age).add(e.fist(1,age)).scale(.5).add(e.forward().scale(.18));
            float strength=.45F+.35F*Mth.clamp(1-e.guardHitAge(pt)/6,0,1);disc(p,b,GUARD,center,e.forward(),.62,strength);
            if(e.guardHitAge(pt)<6)burst(p,b,center,cam,e.guardHitAge(pt),.65F);
        }
        if(e.bubbleState()!=0){
            Vec3 center=e.bubblePoint(pt).subtract(pos);
            if(e.bubbleState()==1){AbyssFx.billboard(p,AbyssFx.glow(b,BUBBLE),center,cam.subtract(center),.25,age*.1,1);disc(p,b,ARC,center,e.forward(),.3,.7F);}
            else burst(p,b,center,cam,e.burstAge(pt),1.1F);
        }
    }
    private static void disc(PoseStack.Pose p,MultiBufferSource b,ResourceLocation tex,Vec3 center,Vec3 axis,double radius,float alpha){
        Vec3 right=axis.cross(new Vec3(0,1,0)).normalize().scale(radius),up=right.normalize().cross(axis).normalize().scale(radius);
        AbyssFx.quad(p,AbyssFx.glow(b,tex),center.subtract(right).subtract(up),center.add(right).subtract(up),center.add(right).add(up),center.subtract(right).add(up),0,0,1,1,alpha);
    }
    private static void line(PoseStack.Pose p,MultiBufferSource b,Vec3 a,Vec3 end,Vec3 cam,double width,float alpha){
        Vec3 side=end.subtract(a).normalize().cross(cam.subtract(a).normalize()).normalize().scale(width);
        AbyssFx.quad(p,AbyssFx.glow(b,STREAK),a.subtract(side),end.subtract(side),end.add(side),a.add(side),0,0,1,1,alpha);
    }
    private static void burst(PoseStack.Pose p,MultiBufferSource b,Vec3 c,Vec3 cam,float age,float radius){
        float fade=Mth.clamp(1-age/7,0,1);if(fade<=0)return;
        AbyssFx.billboard(p,AbyssFx.glow(b,IMPACT),c,cam.subtract(c),radius*(.45+.55*Math.min(1,age/2)),age*.04,fade);
        AbyssFx.billboard(p,AbyssFx.glow(b,ARC),c,cam.subtract(c),radius*(.65+age*.09),-age*.07,fade*.8F);
        for(int i=0;i<7;i++){double angle=i*2.399;Vec3 shard=c.add(Math.cos(angle)*age*.1*radius,Math.sin(angle)*age*.09*radius-age*age*.003,Math.sin(angle*2)*age*.08*radius);AbyssFx.billboard(p,AbyssFx.glow(b,SHARD),shard,cam.subtract(shard),.09*radius,angle,fade);}
    }
    private static final class Model extends GeoModel<TidebreakerShrimpEntity>{
        private static String prefix(TidebreakerShrimpEntity e){return e.getType()==ModEntities.TIDEBREAKER_SHRIMP_TEST.get()?"tidebreaker_shrimp_test":"tidebreaker_shrimp";}
        @Override public ResourceLocation getModelResource(TidebreakerShrimpEntity e){return id("geo/"+prefix(e)+".geo.json");}
        @Override public ResourceLocation getTextureResource(TidebreakerShrimpEntity e){return id("textures/entity/"+prefix(e)+".png");}
        @Override public ResourceLocation getAnimationResource(TidebreakerShrimpEntity e){return id("animations/"+prefix(e)+".animation.json");}
    }
}
