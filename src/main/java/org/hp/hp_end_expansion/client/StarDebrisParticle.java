package org.hp.hp_end_expansion.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** 星雨碎屑。碎石受重力、落地后继续滚一段并随滚动翻转；星灰几乎不落，慢慢飘散缩小。 */
public final class StarDebrisParticle extends TextureSheetParticle {
    private final boolean ash;
    private final float spin;

    private StarDebrisParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, boolean ash) {
        super(level, x, y, z);
        this.ash = ash;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        pickSprite(sprites);
        if (ash) {
            gravity = 0.03F;
            friction = 0.96F;
            lifetime = 30 + random.nextInt(30);
            quadSize = 0.05F + random.nextFloat() * 0.05F;
            hasPhysics = false;
        } else {
            gravity = 0.9F;
            friction = 0.98F;
            lifetime = 45 + random.nextInt(30);
            quadSize = 0.09F + random.nextFloat() * 0.08F;
        }
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        spin = (random.nextFloat() - 0.5F) * 0.6F;
    }

    @Override public void tick() {
        super.tick();
        oRoll = roll;
        if (onGround) {
            // 原版落地每拍只剩 0.7 的水平速度，补回一部分让碎石真的滚开
            xd *= 1.28;
            zd *= 1.28;
            roll += spin * (float) Math.sqrt(xd * xd + zd * zd) * 8;
        } else {
            roll += spin;
        }
    }

    @Override public float getQuadSize(float partialTick) {
        float life = (age + partialTick) / lifetime;
        return quadSize * (life > 0.75F ? 1 - (life - 0.75F) * 4 : 1);
    }

    // 碎石带着余热，至少保留一半方块光
    @Override protected int getLightColor(float partialTick) {
        int base = super.getLightColor(partialTick);
        return ash ? base : (base & 0xFFFF0000) | Math.max(base & 0xFFFF, 0xA0);
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_OPAQUE; }

    public record Provider(SpriteSet sprites, boolean ash) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new StarDebrisParticle(level, x, y, z, xd, yd, zd, sprites, ash);
        }
    }
}
