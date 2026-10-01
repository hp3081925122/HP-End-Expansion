package org.hp.hp_end_expansion.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 负星者的粒子。
 * <ul>
 *   <li>圣焰：4 帧火舌，按年龄从旺到熄播放，上飘时左右轻摆；狂暴版整体染成赤金。</li>
 *   <li>碎晶：金色玻璃碎片，受重力、落地弹一下再滑开，翻滚时一闪一闪。</li>
 * </ul>
 */
public final class BearerParticle extends TextureSheetParticle {
    public enum Kind { FLAME, RAGE, SHARD, VOID, GLINT }

    private final Kind kind;
    private final SpriteSet sprites;
    private final float spin, sway;
    private boolean bounced;

    private BearerParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        sway = random.nextFloat() * Mth.TWO_PI;
        if (kind == Kind.VOID || kind == Kind.GLINT) {
            // 负光：不受重力，速度按 0.88 衰减，生成时给的初速正好把它带到目标点（吸入用）
            pickSprite(sprites);
            gravity = 0;
            friction = kind == Kind.VOID ? 0.88F : 0.82F;
            hasPhysics = false;
            lifetime = kind == Kind.VOID ? 10 + random.nextInt(7) : 6 + random.nextInt(5);
            quadSize = kind == Kind.VOID ? 0.08F + random.nextFloat() * 0.08F : 0.1F + random.nextFloat() * 0.1F;
            spin = (random.nextFloat() - 0.5F) * 0.5F;
        } else if (kind == Kind.SHARD) {
            pickSprite(sprites);
            gravity = 0.85F;
            friction = 0.97F;
            lifetime = 30 + random.nextInt(20);
            quadSize = 0.07F + random.nextFloat() * 0.07F;
            spin = (random.nextFloat() - 0.5F) * 0.9F;
        } else {
            setSpriteFromAge(sprites);
            gravity = -0.015F;
            friction = 0.9F;
            hasPhysics = false;
            lifetime = 12 + random.nextInt(10);
            quadSize = 0.12F + random.nextFloat() * 0.1F;
            spin = 0;
            if (kind == Kind.RAGE) setColor(1.0F, 0.42F, 0.3F);
        }
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
    }

    @Override public void tick() {
        super.tick();
        oRoll = roll;
        if (kind == Kind.VOID) {
            roll += spin;
            return;
        }
        if (kind == Kind.GLINT) {
            // 星芒一闪一闪：两帧大小芒交替
            setSprite(sprites.get(age % 3 == 0 ? 1 : 0, 1));
            return;
        }
        if (kind == Kind.SHARD) {
            if (onGround && !bounced) {
                // 落地弹一下：竖直速度反向打四折，水平补回原版落地吃掉的那部分
                bounced = true;
                yd = 0.12 + random.nextDouble() * 0.06;
                xd *= 1.4;
                zd *= 1.4;
            }
            roll += onGround ? spin * 0.3F : spin;
            // 翻滚时每隔几拍反一下光
            alpha = age / 2 % 3 == 0 ? 1F : 0.8F;
            return;
        }
        setSpriteFromAge(sprites);
        xd += Math.sin(age * 0.5 + sway) * 0.004;
        zd += Math.cos(age * 0.5 + sway) * 0.004;
    }

    @Override public float getQuadSize(float partialTick) {
        float t = (age + partialTick) / lifetime;
        if (kind == Kind.VOID) return quadSize * (t < 0.2F ? 0.5F + t * 2.5F : 1 - (t - 0.2F) * 1.1F);
        if (kind == Kind.GLINT) return quadSize * (1 - t * t);
        if (kind == Kind.SHARD) return quadSize * (t > 0.8F ? (1 - t) * 5 : 1);
        return quadSize * (t < 0.15F ? t / 0.15F : 1 - (t - 0.15F) * 0.6F);
    }

    @Override protected int getLightColor(float partialTick) { return LightTexture.FULL_BRIGHT; }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new BearerParticle(level, x, y, z, xd, yd, zd, sprites, kind);
        }
    }
}
