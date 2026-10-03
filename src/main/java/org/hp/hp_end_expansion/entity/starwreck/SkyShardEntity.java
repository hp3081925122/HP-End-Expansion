package org.hp.hp_end_expansion.entity.starwreck;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.StarwreckEntities;

/**
 * 天幕坠片（设计文档 4.4、4.6）。实体从生成起就站在落点：前 {@link #warn()} tick 地上只有一片越来越深的夜空暗影，
 * 最后 {@link #DROP} tick 碎片从高处插下来，落地那一拍结算伤害。之后竖插在地上 {@link #stand()} tick，期间挡住注视；
 * 裂天那一串碎片的 stand 为 0，落地就碎。伤害由裂天之主创建时传入，不读配置。
 */
public class SkyShardEntity extends Entity {
    public static final int DROP = 10, SHATTER = 8;
    private static final EntityDataAccessor<Integer> WARN = SynchedEntityData.defineId(SkyShardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAND = SynchedEntityData.defineId(SkyShardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SkyShardEntity.class, EntityDataSerializers.FLOAT);
    @Nullable private SkyrenderEntity owner;
    private float damage;

    public SkyShardEntity(EntityType<? extends SkyShardEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static void spawn(ServerLevel level, SkyrenderEntity owner, Vec3 at, float damage, int warn, int stand, float radius) {
        SkyShardEntity shard = StarwreckEntities.SKY_SHARD.get().create(level);
        if (shard == null) return;
        // 伤害和时长都由本体在释放时给定
        shard.owner = owner;
        shard.damage = damage;
        shard.entityData.set(WARN, warn);
        shard.entityData.set(STAND, stand);
        shard.entityData.set(RADIUS, radius);
        shard.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
        level.addFreshEntity(shard);
    }

    public int warn() { return entityData.get(WARN); }
    public int stand() { return entityData.get(STAND); }
    public float radius() { return entityData.get(RADIUS); }
    public boolean landed() { return tickCount >= warn(); }

    /** 插在地上时挡视线的体积，和画面上的碎片对齐；落地就碎的那种不挡。 */
    @Nullable public AABB blocker() {
        if (!landed() || stand() <= 0) return null;
        return new AABB(getX() - 0.75, getY(), getZ() - 0.75, getX() + 0.75, getY() + 3, getZ() + 0.75);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WARN, 24).define(STAND, 160).define(RADIUS, 1.5F);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        int warn = warn();
        // 落地：半径内贴地的生物受伤，裂天之主自己不算
        if (tickCount == warn) {
            float r = radius();
            DamageSource source = damageSources().magic();
            if (owner != null) source = damageSources().mobAttack(owner);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(r, 2.5, r),
                e -> e.isAlive() && !(e instanceof SkyrenderEntity) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
                double dx = e.getX() - getX(), dz = e.getZ() - getZ();
                if (dx * dx + dz * dz > r * r || e.getY() < getY() - 1) continue;
                e.hurt(source, damage);
            }
            sl.sendParticles(ModParticles.SKY_MOTE.get(), getX(), getY() + 0.3, getZ(), 24, r * 0.5, 0.2, r * 0.5, 0.08);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 2, 0.5F);
        }
        // 碎掉：插地的到时碎，落地就碎的在落地后几拍里碎
        int life = warn + Math.max(SHATTER, stand());
        if (tickCount >= life) {
            sl.sendParticles(ModParticles.SKY_MOTE.get(), getX(), getY() + 1.5, getZ(), 18, 0.5, 1.0, 0.5, 0.04);
            if (stand() > 0) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.5F, 0.6F);
            discard();
        }
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 160 * 160; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(2, 20, 2); }
}
