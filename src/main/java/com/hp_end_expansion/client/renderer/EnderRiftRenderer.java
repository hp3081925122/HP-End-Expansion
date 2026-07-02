package com.hp_end_expansion.client.renderer;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.world.entity.EnderRift;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public class EnderRiftRenderer extends EntityRenderer<EnderRift> {
    // 裂隙使用一张透明贴图画成交叉的发光入口。
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "textures/entity/ender_rift.png");

    // 创建裂隙渲染器，并关闭地面阴影。
    public EnderRiftRenderer(EntityRendererProvider.Context context) {
        // 调用原版实体渲染器构造，取得渲染调度器和字体上下文。
        super(context);
        // 裂隙本身是悬浮入口，不需要实体阴影压在地面上。
        this.shadowRadius = 0.0F;
    }

    // 渲染两张交叉透明面片，让任意角度都能看到裂隙。
    @Override
    public void render(EnderRift entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // 保存当前矩阵，避免裂隙缩放和旋转影响后续渲染。
        poseStack.pushPose();
        // 让裂隙中心抬到实体碰撞箱中段，视觉上更像一个入口。
        poseStack.translate(0.0D, 0.7D, 0.0D);
        // 根据游戏刻数计算轻微脉冲，避免裂隙像静态告示牌。
        float age = entity.tickCount + partialTick;
        // 使用正弦波控制大小变化，形成末影能量收缩感。
        float pulse = 0.95F + Mth.sin(age * 0.16F) * 0.08F;
        // 让两张面片缓慢旋转，强化裂隙是实体而不是单张粒子。
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 2.0F));
        // 按脉冲缩放宽度和高度，保持中心位置不变。
        poseStack.scale(pulse, 1.0F + (pulse - 0.95F) * 0.6F, pulse);
        // 使用发光透明实体渲染层，使贴图在末地暗处也能读清。
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        // 取得当前矩阵，后续顶点都写入这个局部坐标空间。
        Matrix4f matrix = poseStack.last().pose();
        // 第一张面片朝南北方向展开。
        addQuad(vertexConsumer, matrix, -0.65F, -0.7F, 0.0F, 0.65F, 0.7F, 0.0F);
        // 第二张面片朝东西方向展开，和第一张组成十字可见面。
        addQuad(vertexConsumer, matrix, 0.0F, -0.7F, -0.65F, 0.0F, 0.7F, 0.65F);
        // 恢复矩阵，保证后面的名称牌等渲染不受影响。
        poseStack.popPose();
        // 保留父类处理名称牌等基础实体渲染逻辑。
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    // 向缓冲区写入一张双面的透明矩形。
    private static void addQuad(VertexConsumer vertexConsumer, Matrix4f matrix, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        // 第一面按顺时针写入，用完整贴图覆盖矩形。
        vertexConsumer.addVertex(matrix, minX, minY, minZ).setColor(255, 255, 255, 220).setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
        vertexConsumer.addVertex(matrix, maxX, minY, maxZ).setColor(255, 255, 255, 220).setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
        vertexConsumer.addVertex(matrix, maxX, maxY, maxZ).setColor(255, 255, 255, 220).setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
        vertexConsumer.addVertex(matrix, minX, maxY, minZ).setColor(255, 255, 255, 220).setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
        // 第二面反向写入，避免从背面看不到裂隙。
        vertexConsumer.addVertex(matrix, minX, maxY, minZ).setColor(255, 255, 255, 220).setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, -1.0F, 0.0F);
        vertexConsumer.addVertex(matrix, maxX, maxY, maxZ).setColor(255, 255, 255, 220).setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, -1.0F, 0.0F);
        vertexConsumer.addVertex(matrix, maxX, minY, maxZ).setColor(255, 255, 255, 220).setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, -1.0F, 0.0F);
        vertexConsumer.addVertex(matrix, minX, minY, minZ).setColor(255, 255, 255, 220).setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, -1.0F, 0.0F);
    }

    // 返回裂隙实体使用的透明贴图。
    @Override
    public ResourceLocation getTextureLocation(EnderRift entity) {
        // 让实体渲染系统绑定裂隙专用贴图。
        return TEXTURE;
    }
}
