package org.hp.hp_end_expansion.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public final class RiftParticles {
    private RiftParticles() {
    }

    // 裂隙火花：全亮、按寿命切换帧并逐渐缩小
    public static final class Spark extends TextureSheetParticle {
        private final SpriteSet sprites;

        Spark(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
            super(level, x, y, z, xd, yd, zd);
            this.sprites = sprites;
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.friction = 0.86F;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.lifetime = 8 + this.random.nextInt(6);
            this.quadSize = 0.12F + this.random.nextFloat() * 0.08F;
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.setSpriteFromAge(this.sprites);
        }

        @Override
        public float getQuadSize(float partialTick) {
            float life = (this.age + partialTick) / this.lifetime;
            return this.quadSize * (1.0F - life * 0.5F);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 15728880;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // 裂隙碎片：随机造型、受重力下落、旋转并在末段淡出
    public static final class Shard extends TextureSheetParticle {
        private final float spin;

        Shard(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
            super(level, x, y, z, xd, yd, zd);
            this.xd = xd + (this.random.nextDouble() - 0.5D) * 0.08D;
            this.yd = yd + 0.12D + this.random.nextDouble() * 0.1D;
            this.zd = zd + (this.random.nextDouble() - 0.5D) * 0.08D;
            this.friction = 0.94F;
            this.gravity = 1.1F;
            this.lifetime = 16 + this.random.nextInt(12);
            this.quadSize = 0.1F + this.random.nextFloat() * 0.08F;
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            this.spin = (this.random.nextFloat() - 0.5F) * 0.5F;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.oRoll = this.roll;
            if (!this.onGround) {
                this.roll += this.spin;
            }
            float life = (float) this.age / this.lifetime;
            if (life > 0.7F) {
                this.alpha = 1.0F - (life - 0.7F) / 0.3F;
            }
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 15728880;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // 火花粒子工厂
    public record SparkProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Spark createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new Spark(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }

    // 碎片粒子工厂
    public record ShardProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Shard createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new Shard(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
