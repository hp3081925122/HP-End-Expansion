package org.hp.hp_end_expansion.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.item.StarBowItem;

public final class StarChargeParticle extends TextureSheetParticle {
    private static final int STREAM = 0;
    private static final int RING = 1;
    private static final int PULSE = 2;
    private static final int CORE = 3;
    private static final int CONSTELLATION = 4;
    /** 裂天之角的蓄力光环：第 n 次蓄满套上第 n 道，钉在矢尖上，越外圈越大，相邻两道反向转。 */
    private static final int HALO = 5;

    private final SpriteSet sprites;
    private final int ownerId;
    private final int mode;
    private final int halo;
    private final float angle0;
    private final float radius;
    private final float depth;
    private final float spin;
    private final float charge;
    private float previousRight;
    private float previousUp;
    private float previousForward;
    private float localRight;
    private float localUp;
    private float localForward;

    private StarChargeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        ownerId = (int) xd;
        mode = (int) yd;
        float fraction = (float) (yd - mode);
        charge = Mth.clamp((float) zd, 0.0F, 1.0F);
        float scale = StarBowChargeFx.sizeScale(level.getEntity(ownerId));
        gravity = 0;
        hasPhysics = false;
        this.xd = this.yd = this.zd = 0;
        halo = mode == HALO ? Math.round(fraction * 10) : 0;
        switch (mode) {
            case HALO -> {
                angle0 = 0;
                radius = 0;
                depth = 0.03F;
                spin = (halo % 2 == 0 ? -1 : 1) * (0.05F + 0.02F * halo);
                lifetime = 20 * 60;
                // 第一人称其它粒子缩到 0.2 倍，光环和箭头差不多大才看得出是一圈，单独按 0.55 倍
                quadSize = (0.055F + 0.027F * (halo - 1)) * (scale < 1 ? 0.55F : scale);
                setSprite(sprites.get(3, 3));
            }
            case RING -> {
                angle0 = fraction * Mth.TWO_PI;
                radius = 0.13F + charge * 0.1F;
                depth = 0.02F;
                spin = 0.18F + charge * 0.12F;
                lifetime = charge >= 1.0F ? 28 : 14;
                quadSize = 0.027F * scale;
                setSprite(sprites.get(1, 3));
            }
            case PULSE -> {
                angle0 = fraction * Mth.TWO_PI;
                radius = 0.38F;
                depth = 0.04F;
                spin = 0.2F;
                lifetime = 9;
                quadSize = 0.047F * scale;
                setSprite(sprites.get(2, 3));
            }
            case CORE -> {
                angle0 = 0;
                radius = 0;
                depth = 0.04F;
                spin = 0;
                lifetime = 7;
                quadSize = 0.062F * scale;
                setSprite(sprites.get(2, 3));
            }
            case CONSTELLATION -> {
                angle0 = fraction * Mth.TWO_PI;
                radius = 0.23F;
                depth = 0.05F;
                spin = 0.08F;
                lifetime = 1200;
                quadSize = 0.031F * scale;
                setSprite(sprites.get(1, 3));
            }
            default -> {
                angle0 = fraction * Mth.TWO_PI;
                radius = 0.17F + random.nextFloat() * 0.13F;
                depth = -0.28F - random.nextFloat() * 0.12F;
                spin = (random.nextBoolean() ? 1 : -1) * (0.18F + charge * 0.18F);
                lifetime = 9 + random.nextInt(5);
                quadSize = (0.021F + random.nextFloat() * 0.012F) * scale;
                // 汇聚光点只用小四芒星：圆形光点（star_charge_0）按用户要求不再用
                setSprite(sprites.get(1, 3));
            }
        }
        roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        updateLocal(0);
        previousRight = localRight;
        previousUp = localUp;
        previousForward = localForward;
    }

    private void updateLocal(int age) {
        float progress = Math.min(1.0F, age / (float) lifetime);
        switch (mode) {
            case HALO -> {
                localRight = 0;
                localUp = 0;
                localForward = depth;
            }
            case RING -> {
                float angle = angle0 + spin * age;
                localRight = Mth.cos(angle) * radius;
                localUp = Mth.sin(angle) * radius * 0.5F;
                localForward = depth + Mth.sin(angle * 2.0F) * 0.08F;
            }
            case PULSE -> {
                float eased = progress * progress * (3.0F - 2.0F * progress);
                float angle = angle0 + spin * age;
                float currentRadius = 0.02F + radius * eased;
                localRight = Mth.cos(angle) * currentRadius;
                localUp = Mth.sin(angle) * currentRadius;
                localForward = depth + Mth.sin(angle * 2.0F) * 0.03F;
            }
            case CORE -> {
                localRight = 0;
                localUp = 0;
                localForward = depth + Mth.sin(age * 0.8F) * 0.015F;
            }
            case CONSTELLATION -> {
                float angle = angle0 + spin * age;
                localRight = Mth.cos(angle) * radius;
                localUp = Mth.sin(angle) * radius * 0.78F;
                localForward = depth + Mth.sin(angle) * 0.11F;
            }
            default -> {
                float eased = progress * progress * (3.0F - 2.0F * progress);
                float angle = angle0 + spin * age + eased * 0.8F;
                float currentRadius = radius * (1.0F - eased) + 0.015F;
                localRight = Mth.cos(angle) * currentRadius;
                localUp = Mth.sin(angle) * currentRadius * 0.7F;
                localForward = depth + eased * 0.34F + Mth.sin(angle * 1.7F) * 0.025F;
            }
        }
    }

    private LivingEntity owner() {
        Entity entity = level.getEntity(ownerId);
        if (entity instanceof LivingEntity living && living.isAlive() && living.isUsingItem()
            && living.getUseItem().getItem() instanceof StarBowItem) return living;
        return null;
    }

    @Override public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        LivingEntity owner = owner();
        if (owner == null || age++ >= lifetime || ((mode == RING || mode == CONSTELLATION) && !StarBowChargeFx.full(owner))
            || (mode == HALO && StarBowChargeFx.stage(owner) < halo)) {
            remove();
            return;
        }
        previousRight = localRight;
        previousUp = localUp;
        previousForward = localForward;
        updateLocal(age);
        roll += mode == STREAM ? spin * 0.7F : mode == HALO ? spin : 0.12F;
        Vec3 world = StarBowChargeFx.toWorld(owner, 1, localRight, localUp, localForward);
        x = world.x;
        y = world.y;
        z = world.z;
    }

    @Override public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        LivingEntity owner = owner();
        if (owner == null) return;
        Vec3 world = StarBowChargeFx.toWorld(owner, partialTick,
            Mth.lerp(partialTick, previousRight, localRight),
            Mth.lerp(partialTick, previousUp, localUp),
            Mth.lerp(partialTick, previousForward, localForward));
        double x0 = x;
        double y0 = y;
        double z0 = z;
        double xo0 = xo;
        double yo0 = yo;
        double zo0 = zo;
        x = xo = world.x;
        y = yo = world.y;
        z = zo = world.z;
        super.render(buffer, camera, partialTick);
        x = x0;
        y = y0;
        z = z0;
        xo = xo0;
        yo = yo0;
        zo = zo0;
    }

    @Override public float getQuadSize(float partialTick) {
        float progress = Math.min(1.0F, (age + partialTick) / lifetime);
        return switch (mode) {
            // 光环：套上那一下从大收到常态，之后轻轻呼吸
            case HALO -> {
                float a = age + partialTick;
                yield quadSize * (a < 5 ? 1.5F - 0.1F * a : 1.0F + 0.04F * Mth.sin(a * 0.3F + halo));
            }
            case PULSE -> quadSize * (1.0F - progress * progress);
            case CORE -> quadSize * (0.8F + 0.25F * Mth.sin((age + partialTick) * 0.85F));
            case RING, CONSTELLATION -> quadSize * (0.85F + 0.15F * Mth.sin((age + partialTick) * 0.65F + angle0));
            default -> quadSize * (progress < 0.2F ? progress / 0.2F : 1.0F - progress * 0.45F);
        };
    }

    @Override protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                 double xd, double yd, double zd) {
            return new StarChargeParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
