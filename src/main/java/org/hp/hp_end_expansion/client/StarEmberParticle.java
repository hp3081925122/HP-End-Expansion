package org.hp.hp_end_expansion.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

public final class StarEmberParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private StarEmberParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = 35 + random.nextInt(25);
        this.quadSize = 0.045F + random.nextFloat() * 0.03F;
        this.xd = (random.nextDouble() - 0.5) * 0.012;
        this.yd = 0.015 + random.nextDouble() * 0.015;
        this.zd = (random.nextDouble() - 0.5) * 0.012;
        this.hasPhysics = false;
        this.friction = 1;
        this.setSpriteFromAge(sprites);
    }
    @Override
    public void tick() { super.tick(); this.setSpriteFromAge(sprites); }
    @Override
    protected int getLightColor(float partialTick) { return 15728880; }
    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_OPAQUE; }
    // 传入的速度叠加在自身的随机上飘速度上；方块环境火星传 0，死亡爆散、冲击波等传初速度
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            StarEmberParticle particle = new StarEmberParticle(level, x, y, z, sprites);
            if (xd != 0 || yd != 0 || zd != 0) {
                particle.xd += xd; particle.yd += yd; particle.zd += zd;
                particle.friction = 0.9F;
            }
            return particle;
        }
    }
}
