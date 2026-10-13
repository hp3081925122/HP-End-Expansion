package org.hp.hp_end_expansion.validation;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.client.StarCoreChestplateRenderer;
import org.hp.hp_end_expansion.client.render.HpGeoRenderState;

@EventBusSubscriber(modid=Hp_end_expansion.MODID,value=Dist.CLIENT)
public final class MigrationClientTests {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (!Boolean.getBoolean("hp_end_expansion.clientValidation")) return;
        event.enqueueWork(()-> {
            var ticket=DataTicket.create("hp_end_expansion:render_state_validation",Object.class);
            for(GeoRenderState state:new GeoRenderState[]{new HpGeoRenderState(),new StarCoreChestplateRenderer.State()}) {
                Object marker=new Object();
                state.addGeckolibData(ticket,marker);
                if(state.getGeckolibData(ticket)!=marker || !state.hasGeckolibData(ticket) || state.getDataMap().get(ticket)!=marker)
                    throw new IllegalStateException("Gecko render-state data map mismatch: "+state.getClass().getName());
                state.getDataMap().remove(ticket);
                if(state.getGeckolibData(ticket)!=null || state.hasGeckolibData(ticket))
                    throw new IllegalStateException("Gecko render-state data removal mismatch: "+state.getClass().getName());
            }
            LogUtils.getLogger().info("Client render-state validation passed: entity and armor data tickets use the same map");
        });
    }
}
