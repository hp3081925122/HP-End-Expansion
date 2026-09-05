package com.hp_end_expansion.content.prismatic.client;

import com.hp_end_expansion.content.prismatic.entity.MirrorHuntress;
import com.hp_end_expansion.content.prismatic.entity.PrismaticAnimated;
import com.hp_end_expansion.content.prismatic.entity.PrismaticMonster;
import com.hp_end_expansion.content.prismatic.entity.PrismaticAnimal;
import com.hp_end_expansion.HpEndExpansion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

// 模型保持原始比例，光束、扇线与地面环由同步技能状态绘制稳定几何。
public final class PrismaticEntityRenderer<T extends LivingEntity & PrismaticAnimated> extends GeoEntityRenderer<T> {
    public PrismaticEntityRenderer(EntityRendererProvider.Context context, String id, float shadow) {
        super(context, new PrismaticEntityModel<>(id));
        shadowRadius = shadow;
        if (id.equals("shardling") || id.equals("facet_ram") || id.equals("dusk_moth")) {
            ResourceLocation mask = ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "textures/entity/prismatic/" + id + "_glowmask.png");
            addRenderLayer(new GeoRenderLayer<>(this) {
                @Override
                public void render(PoseStack poses, T entity, BakedGeoModel model, RenderType type, MultiBufferSource buffers,
                        VertexConsumer original, float partialTick, int light, int overlay) {
                    if (entity instanceof PrismaticAnimal animal && animal.productStage() == 2 && !entity.isInvisible()) {
                        RenderType glow = RenderType.eyes(mask);
                        getRenderer().reRender(model, poses, buffers, entity, glow, buffers.getBuffer(glow), partialTick, LightTexture.FULL_BRIGHT, overlay, -1);
                    }
                }
            });
        }
    }

    // 使用 GeckoLib 最终渲染扩展点，避免几何继承模型骨骼的旋转缩放。
    @Override
    public void renderFinal(PoseStack poses, T entity, BakedGeoModel model, MultiBufferSource buffers,
            VertexConsumer original, float partialTick, int light, int overlay, int colour) {
        super.renderFinal(poses, entity, model, buffers, original, partialTick, light, overlay, colour);
        if (!(entity instanceof PrismaticMonster monster) || monster.currentAttack() == PrismaticMonster.Attack.NONE || !monster.isAlive()) { return; }
        float age = monster.attackAge() + partialTick;
        boolean windup = age < monster.windupTicks();
        if (!windup && age > monster.windupTicks() + 8) { return; }
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        Matrix4f pose = poses.last().pose();
        float alpha = windup ? 0.5F : 0.85F * (1.0F - (age - monster.windupTicks()) / 9.0F);
        Vec3 localOrigin = monster.attackOrigin().subtract(monster.position());
        switch (monster.currentAttack()) {
            case BEAM -> {
                Vec3 end = monster.beamEnd().subtract(monster.position());
                beam(vertices, pose, localOrigin, end, windup ? 0.065F : 0.42F, alpha);
                if (!windup) { beam(vertices, pose, localOrigin, end, 0.14F, 0.95F); }
            }
            case NEEDLES -> {
                int count = monster instanceof MirrorHuntress ? 3 : 1;
                for (int i = 0; i < count; i++) {
                    Vec3 direction = Vec3.directionFromRotation(monster.attackPitch(), monster.attackYaw() + (i - (count - 1) * 0.5F) * 16.0F);
                    Vec3 worldStart = monster.attackOrigin();
                    Vec3 far = worldStart.add(direction.scale(12.0));
                    Vec3 end = entity.level().clip(new ClipContext(worldStart, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getLocation();
                    beam(vertices, pose, localOrigin, end.subtract(monster.position()), 0.045F, alpha * 0.7F);
                }
            }
            case CHARGE -> {
                Vec3 start = new Vec3(0, 0.08, 0);
                Vec3 direction = Vec3.directionFromRotation(0, monster.attackYaw());
                Vec3 worldStart = monster.position().add(start);
                Vec3 end = entity.level().clip(new ClipContext(worldStart, worldStart.add(direction.scale(7.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getLocation();
                beam(vertices, pose, start, end.subtract(monster.position()), windup ? 0.10F : 0.22F, alpha);
            }
            case PULSE, STRIKE -> {
                double halfAngle = monster.currentAttack() == PrismaticMonster.Attack.STRIKE ? Math.acos(0.1) : Math.PI;
                ring(vertices, pose, monster.attackRadius(), monster.attackYaw(), halfAngle, windup ? 0.10 : 0.30, alpha);
            }
            default -> { }
        }
    }

    // 双交叉长条在多个观察角度都可见，使用原版无纹理自发光透明管线。
    private static void beam(VertexConsumer vertices, Matrix4f pose, Vec3 start, Vec3 end, float width, float alpha) {
        Vec3 direction = end.subtract(start).normalize();
        Vec3 side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 0.001) { side = new Vec3(1, 0, 0); }
        side = side.normalize().scale(width);
        Vec3 up = direction.cross(side).normalize().scale(width);
        quad(vertices, pose, start.add(side), end.add(side), end.subtract(side), start.subtract(side), alpha);
        quad(vertices, pose, start.add(up), end.add(up), end.subtract(up), start.subtract(up), alpha);
    }

    // 圆环使用固定四十八段上限，横扫只绘制锁定前方扇弧。
    private static void ring(VertexConsumer vertices, Matrix4f pose, double radius, float yaw, double halfAngle, double width, float alpha) {
        int steps = 48;
        double center = Math.toRadians(yaw);
        for (int i = 0; i < steps; i++) {
            double a = center - halfAngle + i * halfAngle * 2.0 / steps;
            double b = center - halfAngle + (i + 1) * halfAngle * 2.0 / steps;
            quad(vertices, pose, point(a, radius - width), point(a, radius + width), point(b, radius + width), point(b, radius - width), alpha);
        }
        if (halfAngle < Math.PI) {
            beam(vertices, pose, new Vec3(0, 0.07, 0), point(center - halfAngle, radius), 0.04F, alpha);
            beam(vertices, pose, new Vec3(0, 0.07, 0), point(center + halfAngle, radius), 0.04F, alpha);
        }
    }

    private static Vec3 point(double angle, double radius) { return new Vec3(-Math.sin(angle) * radius, 0.07, Math.cos(angle) * radius); }

    // 洋红危险边缘配合暖白中心，几何形状本身也能提示攻击范围。
    private static void quad(VertexConsumer vertices, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float alpha) {
        vertices.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(1.0F, 0.35F, 0.72F, alpha);
        vertices.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(1.0F, 0.82F, 0.90F, alpha);
        vertices.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(1.0F, 0.82F, 0.90F, alpha);
        vertices.addVertex(pose, (float) d.x, (float) d.y, (float) d.z).setColor(1.0F, 0.35F, 0.72F, alpha);
    }
}
