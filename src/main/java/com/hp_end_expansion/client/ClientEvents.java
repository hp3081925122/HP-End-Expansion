package com.hp_end_expansion.client;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.client.renderer.EnderBoxRenderer;
import com.hp_end_expansion.client.renderer.EnderSnailRenderer;
import com.hp_end_expansion.client.renderer.VoidWhaleRenderer;
import com.hp_end_expansion.network.OpenEnderBoxPayload;
import com.hp_end_expansion.network.VoidWhaleTeleportPayload;
import com.hp_end_expansion.registry.ModEntities;
import com.hp_end_expansion.world.entity.VoidWhale;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    private static final KeyMapping OPEN_ENDER_BOX = new KeyMapping("key.hp_end_expansion.open_ender_box", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.hp_end_expansion");

    // 客户端事件类只提供静态事件入口，不允许实例化。
    private ClientEvents() {
    }

    // 监听模组事件总线，注册客户端渲染器。
    @EventBusSubscriber(modid = HpEndExpansion.MODID, value = Dist.CLIENT)
    public static final class ModBusEvents {
        // 模组总线事件类只提供静态事件入口，不允许实例化。
        private ModBusEvents() {
        }

        // 注册虚空鲸的 GeckoLib 实体渲染器。
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.VOID_WHALE.get(), VoidWhaleRenderer::new);
            event.registerEntityRenderer(ModEntities.ENDER_BOX.get(), EnderBoxRenderer::new);
            event.registerEntityRenderer(ModEntities.ENDER_SNAIL.get(), EnderSnailRenderer::new);
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_ENDER_BOX);
        }
    }

    // 监听游戏事件总线，处理客户端输入。
    @EventBusSubscriber(modid = HpEndExpansion.MODID, value = Dist.CLIENT)
    public static final class GameBusEvents {
        // 游戏总线事件类只提供静态事件入口，不允许实例化。
        private GameBusEvents() {
        }

        // 骑乘虚空鲸且主手拿末影珍珠时，拦截攻击键并请求服务端传送。
        @SubscribeEvent
        public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
            // 只处理攻击键，其他交互键不参与传送逻辑。
            if (!event.isAttack()) {
                return;
            }

            // 玩家不存在或主手不是末影珍珠时不发包。
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || !minecraft.player.getMainHandItem().is(Items.ENDER_PEARL)) {
                return;
            }

            // 只有骑乘虚空鲸时才向服务端发送传送请求。
            Entity vehicle = minecraft.player.getVehicle();
            if (vehicle instanceof VoidWhale) {
                PacketDistributor.sendToServer(VoidWhaleTeleportPayload.INSTANCE);
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            while (OPEN_ENDER_BOX.consumeClick()) {
                if (Minecraft.getInstance().player != null) {
                    PacketDistributor.sendToServer(OpenEnderBoxPayload.INSTANCE);
                }
            }
        }
    }
}
