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

    /** 星起步时星心在天裂后面多少个星半径：整团藏在裂缝后面，先探出一小块，再整个挤出来。 */
    public static final float START_BACK = 1.2F;

    /** 落点：星心停在 0.9 个半径高。 */
    private Vec3 impactPoint() { return new Vec3(0, starRadius() * 0.9, 0); }

    /** 星的前进方向：天裂指向落点。 */
    public Vec3 lead() { return impactPoint().subtract(skyPoint()).normalize(); }

    /**
     * 星心沿直线从天裂后面走到落点：一开始就看得出在动，越往后越快，最后一秒猛地砸下来。
     * 星岩半径约 1.1 个星半径，最后两拍岩底已经犁进地里。
     */
    public Vec3 starPos(float t) {
        float k = drop(t), fall = 0.25F * k + 0.75F * k * k * k;
        Vec3 start = skyPoint().subtract(lead().scale(START_BACK * starRadius()));
        return start.add(impactPoint().subtract(start).scale(fall));
    }

    /** 星心正好穿过天裂的那一拍（裂口炸开一圈气浪、天裂再响一声）。只和星半径有关，每台客户端算出来一样。 */
    public float breakTick() {
        double back = START_BACK * starRadius(), f = back / (impactPoint().subtract(skyPoint()).length() + back), lo = 0, hi = 1;
        for (int i = 0; i < 20; i++) {
            double m = (lo + hi) * 0.5;
            if (0.25 * m + 0.75 * m * m * m < f) lo = m;
            else hi = m;
        }
        return SkyrenderEntity.METEOR_DROP + (float) lo * (SkyrenderEntity.METEOR_HIT - SkyrenderEntity.METEOR_DROP);
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
        // 星心挤出天裂那一拍，裂口再炸一声
        float out = breakTick();
        if (tickCount == (int) Math.ceil(out)) level().playLocalSound(getX(), getY() + 12, getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 8, 0.38F, false);
        // 挤出来以后星的背后一路掉火星，越快越密
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
        double s = starRadius() * (3 + START_BACK) + 40;
        return new AABB(getX() - r, getY() - 2, getZ() - r, getX() + r, getY() + r, getZ() + r)
            .minmax(new AABB(sky.x - s, sky.y - s, sky.z - s, sky.x + s, sky.y + s, sky.z + s));
    }
}
