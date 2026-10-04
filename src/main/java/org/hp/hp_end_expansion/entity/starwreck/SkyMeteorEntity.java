package org.hp.hp_end_expansion.entity.starwreck;

import javax.annotation.Nullable;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.StarwreckEntities;

/**
 * 天陨的画面（设计文档 skyrender_meteor_ultimate.md）：站在坑中心的坑底，只负责画天裂、巨星、落点暗影、冲击波和焦痕。
 * 判定和结算全在裂天之主身上，这里的计时和本体的 {@link SkyrenderEntity#METEOR} 状态同一拍开始。
 */
public class SkyMeteorEntity extends Entity {
    public static final int LIFE = SkyrenderEntity.METEOR_LEN + SkyrenderEntity.METEOR_REST + 40;
    private static final EntityDataAccessor<Float> STAR_RADIUS = SynchedEntityData.defineId(SkyMeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ARENA_RADIUS = SynchedEntityData.defineId(SkyMeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FROM_YAW = SynchedEntityData.defineId(SkyMeteorEntity.class, EntityDataSerializers.FLOAT);

    public SkyMeteorEntity(EntityType<? extends SkyMeteorEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** fromYaw：天裂所在的水平方向（atan2(z, x)，度），取玩家看向坑心时的身后。 */
    static void spawn(ServerLevel level, Vec3 at, float starRadius, float arenaRadius, float fromYaw) {
        SkyMeteorEntity e = StarwreckEntities.SKY_METEOR.get().create(level);
        if (e == null) return;
        e.entityData.set(STAR_RADIUS, starRadius);
        e.entityData.set(ARENA_RADIUS, arenaRadius);
        e.entityData.set(FROM_YAW, fromYaw);
        e.moveTo(at.x, at.y, at.z, 0, 0);
        level.addFreshEntity(e);
    }

    public float starRadius() { return entityData.get(STAR_RADIUS); }
    public float arenaRadius() { return entityData.get(ARENA_RADIUS); }

    /** 客户端自己的天裂位置：取这台客户端第一次画它时，镜头看向坑心的身后，让每个玩家都看见星正面朝自己压下来。 */
    @Nullable private Vec3 viewSky;

    /** 只在客户端渲染时调用。镜头站在坑心附近（水平 6 格内）时沿用服务端给的方向。 */
    public void lockViewDirection(Vec3 camera) {
        if (viewSky != null) return;
        Vec3 d = new Vec3(getX() - camera.x, 0, getZ() - camera.z);
        if (d.lengthSqr() < 36) {
            viewSky = serverSkyPoint();
            return;
        }
        d = d.normalize();
        viewSky = new Vec3(d.x * SkyrenderEntity.METEOR_RANGE, SkyrenderEntity.METEOR_HEIGHT, d.z * SkyrenderEntity.METEOR_RANGE);
    }

    /** 天裂中心，相对坑底中心。 */
    public Vec3 skyPoint() {
        return viewSky != null ? viewSky : serverSkyPoint();
    }

    private Vec3 serverSkyPoint() {
        double yaw = Math.toRadians(entityData.get(FROM_YAW));
        return new Vec3(Math.cos(yaw) * SkyrenderEntity.METEOR_RANGE, SkyrenderEntity.METEOR_HEIGHT, Math.sin(yaw) * SkyrenderEntity.METEOR_RANGE);
    }

    /** 坠星段的下落进度 0..1。 */
    public static float drop(float t) {
        return Math.max(0, Math.min(1, (t - SkyrenderEntity.METEOR_DROP) / (float) (SkyrenderEntity.METEOR_HIT - SkyrenderEntity.METEOR_DROP)));
    }

    /** 星起步时藏在天旋涡深处：星心在旋涡口后面多少格。 */
    public static final float START_DEPTH = 80;
    /** 天旋涡口的半径（格），陨石雨从这一圈里落下来。 */
    public static final float VORTEX_RADIUS = 230;

    /** 落点：星心停在 0.9 个半径高。 */
    private Vec3 impactPoint() { return new Vec3(0, starRadius() * 0.9, 0); }

    /** 星的前进方向：旋涡口指向落点。 */
    public Vec3 lead() { return impactPoint().subtract(skyPoint()).normalize(); }

    /** 下落进度：一开始就看得出在动，越往后越快，最后一秒猛地砸下来。 */
    public static float fall(float k) { return 0.45F * k + 0.55F * k * k * k; }

    /** 星心沿直线从旋涡深处走到落点。星半径约 1.1 个星半径，最后两拍已经犁进地里。 */
    public Vec3 starPos(float t) {
        Vec3 start = skyPoint().subtract(lead().scale(START_DEPTH));
        return start.add(impactPoint().subtract(start).scale(fall(drop(t))));
    }

    /** 星心冲出旋涡口那一拍（旋涡口炸亮、燃成火环、陨石雨开始）。只和星半径有关，每台客户端算出来一样。 */
    public float breakTick() {
        double f = START_DEPTH / (impactPoint().subtract(skyPoint()).length() + START_DEPTH), lo = 0, hi = 1;
        for (int i = 0; i < 20; i++) {
            double m = (lo + hi) * 0.5;
            if (fall((float) m) < f) lo = m;
            else hi = m;
        }
        return SkyrenderEntity.METEOR_DROP + (float) lo * (SkyrenderEntity.METEOR_HIT - SkyrenderEntity.METEOR_DROP);
    }

    // ---------- 陨石雨：渲染和落地粒子共用同一套参数 ----------
    public static final int RAIN = 40;

    /** 0..1 的固定伪随机：同一只实体每帧、每拍取到的一样。 */
    public static float noise(int seed, int i, int salt) {
        int h = seed * 0x9E3779B1 + i * 0x85EBCA6B + salt * 0xC2B2AE35;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / (float) (1 << 24);
    }

    /** 垂直于 axis 的一组正交基。 */
    public static Vec3[] basis(Vec3 axis) {
        Vec3 u = axis.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1.0E-6) u = axis.cross(new Vec3(1, 0, 0));
        u = u.normalize();
        return new Vec3[]{u, axis.cross(u).normalize()};
    }

    /** 第 i 颗从冲出旋涡后 2 拍开始，到落地前 8 拍为止，陆续落下。 */
    public float rainStart(int i) {
        float from = breakTick() + 2, to = SkyrenderEntity.METEOR_HIT - 8;
        return from + (to - from) * (i + 0.6F * noise(getId(), i, 1)) / RAIN;
    }

    public float rainTime(int i) { return 14 + 8 * noise(getId(), i, 2); }

    public float rainSize(int i) { return 2.5F + 2.5F * noise(getId(), i, 7); }

    /** 起点：旋涡口那一圈里。 */
    public Vec3 rainFrom(int i) {
        Vec3[] uv = basis(lead());
        double a = Math.PI * 2 * noise(getId(), i, 3), r = VORTEX_RADIUS * (0.5 + 0.45 * noise(getId(), i, 4));
        return skyPoint().add(uv[0].scale(Math.cos(a) * r)).add(uv[1].scale(Math.sin(a) * r));
    }

    /** 落点：坑里和坑外一圈。 */
    public Vec3 rainTo(int i) {
        double a = Math.PI * 2 * noise(getId(), i, 5), r = arenaRadius() * (0.25 + 1.6 * noise(getId(), i, 6));
        return new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r);
    }

    /** 第 i 颗在进度 p（0..1）时的位置：越落越快。 */
    public Vec3 rainPos(int i, float p) {
        float e = 0.35F * p + 0.65F * p * p;
        Vec3 from = rainFrom(i);
        return from.add(rainTo(i).subtract(from).scale(e));
    }

    // ---------- 余烬：落地后坑底裂开的熔岩缝，一格一格贴着方块网格 ----------
    /** 熔岩缝的走向：坑半径为 1 的坐标，手摆的主干从坑心往外放射，再分几道叉。 */
    private static final float[][] CRACKS = {
        {0, 0, 0.35F, 0.1F, 0.7F, 0.05F, 1.05F, 0.2F}, {0, 0, -0.2F, 0.35F, -0.3F, 0.7F, -0.55F, 1.0F},
        {0, 0, -0.4F, -0.1F, -0.75F, -0.3F, -1.1F, -0.25F}, {0, 0, 0.1F, -0.4F, 0.35F, -0.7F, 0.4F, -1.05F},
        {0, 0, 0.3F, 0.45F, 0.55F, 0.8F, 0.85F, 0.9F}, {0, 0, -0.3F, -0.45F, -0.45F, -0.85F},
        {0.7F, 0.05F, 0.8F, -0.3F, 1.0F, -0.45F}, {-0.3F, 0.7F, -0.7F, 0.65F, -0.95F, 0.5F}, {0.35F, -0.7F, 0.7F, -0.75F},
        {-0.75F, -0.3F, -0.85F, -0.65F}, {0.55F, 0.8F, 0.3F, 1.0F}, {0.45F, 0.25F, 0.2F, 0.48F}, {-0.48F, 0.05F, -0.3F, 0.4F},
        {0.2F, -0.25F, 0.55F, -0.35F}};
    @Nullable private int[][] cracks;

    /** 熔岩缝占的方块格：{x, z, 离坑心的距离 × 10}。靠近坑心的主干两格宽，坑心一小片整个烧穿。 */
    public int[][] crackCells() {
        if (cracks != null) return cracks;
        float arena = arenaRadius();
        java.util.Map<Long, int[]> cells = new java.util.LinkedHashMap<>();
        java.util.function.BiConsumer<Integer, Integer> put = (x, z) -> cells.putIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL),
            new int[]{x, z, (int) (Math.sqrt((x + 0.5) * (x + 0.5) + (z + 0.5) * (z + 0.5)) * 10)});
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (x * x + z * z <= 9) put.accept(x, z);
        for (float[] line : CRACKS) {
            for (int s = 0; s + 3 < line.length; s += 2) {
                double x0 = line[s] * arena, z0 = line[s + 1] * arena, x1 = line[s + 2] * arena, z1 = line[s + 3] * arena;
                double len = Math.hypot(x1 - x0, z1 - z0);
                for (double d = 0; d <= len; d += 0.4) {
                    double x = x0 + (x1 - x0) * d / len, z = z0 + (z1 - z0) * d / len;
                    put.accept((int) Math.floor(x), (int) Math.floor(z));
                    if (Math.hypot(x, z) < arena * 0.4) put.accept((int) Math.floor(x - (z1 - z0) / len), (int) Math.floor(z + (x1 - x0) / len));
                }
            }
        }
        cracks = cells.values().toArray(new int[0][]);
        return cracks;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STAR_RADIUS, 14F).define(ARENA_RADIUS, 28F).define(FROM_YAW, 0F);
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (tickCount >= LIFE) discard();
            return;
        }
        // 星冲出旋涡口那一拍，天上再炸一声
        float out = breakTick();
        if (tickCount == (int) Math.ceil(out)) level().playLocalSound(getX(), getY() + 12, getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 8, 0.38F, false);
        rainLandings();
        impactBurst();
        // 冲出来以后星的背后一路掉火星，越快越密
        if (tickCount > out + 4 && tickCount < SkyrenderEntity.METEOR_HIT) {
            Vec3 at = position().add(starPos(tickCount));
            Vec3 back = lead().scale(-1);
            float r = starRadius(), k = drop(tickCount);
            int n = 2 + (int) (k * 6);
            for (int i = 0; i < n; i++) {
                Vec3 p = at.add(back.scale(r * (0.6 + random.nextDouble() * 0.5)))
                    .add((random.nextDouble() - 0.5) * r * 1.2, (random.nextDouble() - 0.5) * r * 1.2, (random.nextDouble() - 0.5) * r * 1.2);
                Vec3 v = back.scale(0.2 + random.nextDouble() * 0.3);
                level().addParticle(ModParticles.STAR_EMBER.get(), true, p.x, p.y, p.z, v.x, v.y, v.z);
            }
        }
        // 最后一秒半：星压下来的气浪把坑底的烟尘往外推，地上的碎块被掀起来
        int left = SkyrenderEntity.METEOR_HIT - tickCount;
        if (left > 0 && left <= 30) {
            float q = 1 - left / 30F, arena = arenaRadius();
            BlockState ground = level().getBlockState(blockPosition().below());
            if (ground.isAir()) ground = Blocks.END_STONE.defaultBlockState();
            int smoke = 3 + (int) (q * q * 14);
            for (int i = 0; i < smoke; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = arena * (0.15 + random.nextDouble() * 0.85);
                double sp = 0.25 + q * 0.55 + random.nextDouble() * 0.2;
                level().addParticle(ParticleTypes.LARGE_SMOKE, true, getX() + Math.cos(a) * r, getY() + 0.3, getZ() + Math.sin(a) * r,
                    Math.cos(a) * sp, 0.02 + random.nextDouble() * 0.05, Math.sin(a) * sp);
            }
            int bits = 2 + (int) (q * 8);
            for (int i = 0; i < bits; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = arena * Math.sqrt(random.nextDouble());
                level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), true, getX() + Math.cos(a) * r, getY() + 0.2,
                    getZ() + Math.sin(a) * r, Math.cos(a) * 0.1, 0.35 + q * 0.5 + random.nextDouble() * 0.3, Math.sin(a) * 0.1);
            }
        }
    }

    /** 陨石雨落地：原版的爆炸、火焰、岩浆和地面碎块粒子，隔一颗响一声。只在客户端跑。 */
    private void rainLandings() {
        BlockState ground = groundBlock();
        for (int i = 0; i < RAIN; i++) {
            if (tickCount != (int) Math.ceil(rainStart(i) + rainTime(i))) continue;
            Vec3 p = position().add(rainTo(i));
            float size = rainSize(i);
            level().addParticle(ParticleTypes.EXPLOSION, true, p.x, p.y + 1, p.z, 0, 0, 0);
            for (int j = 0; j < 10; j++) {
                double a = random.nextDouble() * Math.PI * 2, sp = 0.15 + random.nextDouble() * 0.3;
                level().addParticle(ParticleTypes.FLAME, true, p.x, p.y + 0.5, p.z, Math.cos(a) * sp, 0.2 + random.nextDouble() * 0.4, Math.sin(a) * sp);
                level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), true, p.x, p.y + 0.3, p.z,
                    Math.cos(a) * sp, 0.4 + random.nextDouble() * 0.5, Math.sin(a) * sp);
            }
            for (int j = 0; j < 3; j++) level().addParticle(ParticleTypes.LAVA, true, p.x, p.y + 0.5, p.z, 0, 0, 0);
            for (int j = 0; j < 4; j++) level().addParticle(ParticleTypes.LARGE_SMOKE, true, p.x + (random.nextDouble() - 0.5) * size * 2, p.y + 1,
                p.z + (random.nextDouble() - 0.5) * size * 2, 0, 0.08, 0);
            if ((i & 1) == 0) level().playLocalSound(p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3, 0.7F + 0.3F * noise(getId(), i, 9), false);
        }
    }

    /** 主星落地后的原版粒子：两团大爆炸，火焰和烟往外推；之后熔岩缝上一直冒火星、岩浆点和烟。 */
    private void impactBurst() {
        int since = tickCount - SkyrenderEntity.METEOR_HIT;
        if (since < 0) return;
        float arena = arenaRadius();
        if (since == 0 || since == 14) {
            for (int j = 0; j < 3; j++) level().addParticle(ParticleTypes.EXPLOSION_EMITTER, true, getX() + (random.nextDouble() - 0.5) * 8, getY() + 2,
                getZ() + (random.nextDouble() - 0.5) * 8, 0, 0, 0);
        }
        if (since < 20) {
            float q = 1 - since / 20F;
            for (int j = 0; j < (int) (30 * q); j++) {
                double a = random.nextDouble() * Math.PI * 2, sp = (0.6 + random.nextDouble() * 1.2) * q;
                level().addParticle(j % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, true, getX() + Math.cos(a) * 3, getY() + 0.5 + random.nextDouble() * 3,
                    getZ() + Math.sin(a) * 3, Math.cos(a) * sp, 0.05 + random.nextDouble() * 0.25, Math.sin(a) * sp);
            }
            for (int j = 0; j < (int) (6 * q); j++) level().addParticle(ParticleTypes.LAVA, true, getX(), getY() + 1, getZ(), 0, 0, 0);
        }
        if (since >= 8 && since < LIFE - SkyrenderEntity.METEOR_HIT - 30) {
            int[][] cells = crackCells();
            float reach = arena * 1.2F * Math.min(1, (since - 4) / 24F);
            for (int j = 0; j < 4; j++) {
                int[] c = cells[random.nextInt(cells.length)];
                if (c[2] > reach * 10) continue;
                double x = getX() + c[0] + random.nextDouble(), z = getZ() + c[1] + random.nextDouble();
                level().addParticle(j == 0 && random.nextInt(3) == 0 ? ParticleTypes.LAVA : j == 1 ? ParticleTypes.SMOKE : ParticleTypes.SMALL_FLAME,
                    true, x, getY() + 0.1, z, 0, 0.03 + random.nextDouble() * 0.05, 0);
            }
        }
    }

    private BlockState groundBlock() {
        BlockState ground = level().getBlockState(blockPosition().below());
        return ground.isAir() ? Blocks.END_STONE.defaultBlockState() : ground;
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 256 * 256; }

    /** 星从坑外的天裂里斜着落进坑心，天裂更高，地上的暗影铺满整个坑。 */
    @Override public AABB getBoundingBoxForCulling() {
        double r = Math.max(arenaRadius() + 8, starRadius() * 3);
        Vec3 sky = position().add(skyPoint());
        double s = VORTEX_RADIUS + START_DEPTH + 60;
        return new AABB(getX() - r, getY() - 2, getZ() - r, getX() + r, getY() + r, getZ() + r)
            .minmax(new AABB(sky.x - s, sky.y - s, sky.z - s, sky.x + s, sky.y + s, sky.z + s));
    }
}
