package org.hp.hp_end_expansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

public final class TidelightParticle extends TextureSheetParticle {
    private static final double MOTE_RISE = 0.25;
    private static final double BUBBLE_RISE = 0.3;
    private final SpriteSet sprites;
    private final double phase;
    private final boolean bubble;
    private TidelightParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, boolean bubble) {
        super(level, x, y, z); this.sprites = sprites; this.bubble = bubble;
        lifetime = bubble ? 18 + random.nextInt(12) : 50 + random.nextInt(35);
        quadSize = bubble ? 0.055F : 0.025F + random.nextFloat() * 0.025F;
        yd = bubble ? 0.025 : 0.012; phase = random.nextDouble() * Math.PI * 2;
        hasPhysics = false; friction = 1; setSpriteFromAge(sprites);
    }
    @Override public void tick() {
        xd = Math.sin(age * 0.12 + phase) * 0.006; zd = Math.cos(age * 0.1 + phase) * 0.006;
        var player = Minecraft.getInstance().player;
        yd = player != null && player.getXRot() < -45F ? (bubble ? BUBBLE_RISE : MOTE_RISE) : (bubble ? 0.025 : 0.012);
        super.tick();
        if (removed) return;
        int remaining = lifetime - age;
        int frame = bubble ? (remaining <= 4 ? 2 : age < lifetime / 3 ? 0 : 1) : Math.min(3, age * 4 / lifetime);
        setSprite(sprites.get(frame, bubble ? 2 : 3));
        setAlpha(Math.min(1.0F, remaining / (bubble ? 4.0F : 12.0F)));
    }
    @Override protected int getLightColor(float partialTick) { return 15728880; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    public record Provider(SpriteSet sprites, boolean bubble) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) { return new TidelightParticle(level, x, y, z, sprites, bubble); }
    }
}
