package org.hp.hp_end_expansion.entity;

import java.util.UUID;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModEntities;
import org.hp.hp_end_expansion.registry.ModParticles;

public final class VoidRayVfxEntity extends Entity {
    // 特效种类：俯冲预警线、地面冲击环、引力漩涡、星陨落点
    public static final int KIND_WARN_LINE = 0;
    public static final int KIND_SHOCK = 1;
    public static final int KIND_VORTEX = 2;
    public static final int KIND_STAR = 3;
    // 鳐王特效：黑洞、星尘地面、宽条预警、大陨石
    public static final int KIND_BLACK_HOLE = 4;
    public static final int KIND_STARDUST = 5;
    public static final int KIND_WARN_WIDE = 6;
    public static final int KIND_BIG_STAR = 7;

    // 黑洞：前摇 20 tick，生效 120 tick 后坍缩
    public static final int HOLE_ARM = 20;
    public static final int HOLE_LIFE = 141;
    // 星尘：持续 5 秒
    public static final int DUST_LIFE = 100;

    // 漩涡：前摇 24 tick 后生效 50 tick
    public static final int VORTEX_ARM = 24;
    public static final int VORTEX_LIFE = 80;
    // 星陨：预警 24 tick 后落地，余烬 10 tick
    public static final int STAR_FALL = 24;
    public static final int STAR_LIFE = 34;

    // 同步字段：种类、寿命、尺寸（线长或半径）、延迟
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(VoidRayVfxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(VoidRayVfxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(VoidRayVfxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(VoidRayVfxEntity.class, EntityDataSerializers.INT);
    // 同步字段：是否为裂空鳐放出的潮光配色特效
    private static final EntityDataAccessor<Boolean> TIDE = SynchedEntityData.defineId(VoidRayVfxEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID ownerId;
    private float damage = -1.0F;
    private float secondaryDamage = -1.0F;

    public VoidRayVfxEntity(EntityType<? extends VoidRayVfxEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    // 生成特效：delay 为本体开始前的等待 tick，life 包含 delay
    public static VoidRayVfxEntity spawn(Level level, int kind, Vec3 pos, float yaw, float size, int delay, int life, Entity owner) {
        return spawn(level, kind, pos, yaw, size, delay, life, owner, -1.0F, -1.0F);
    }

    public static VoidRayVfxEntity spawn(Level level, int kind, Vec3 pos, float yaw, float size, int delay, int life, Entity owner, float damage, float secondaryDamage) {
        VoidRayVfxEntity vfx = new VoidRayVfxEntity(ModEntities.VOID_RAY_VFX.get(), level);
        vfx.damage = damage;
        vfx.secondaryDamage = secondaryDamage;
        vfx.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        vfx.entityData.set(KIND, kind);
        vfx.entityData.set(SIZE, size);
        vfx.entityData.set(DELAY, delay);
        vfx.entityData.set(LIFE, life);
        if (owner != null) {
            vfx.ownerId = owner.getUUID();
        }
        // 裂空鳐与噬星鳐王的特效改用潮光配色
        if (owner instanceof VoidRayEntity || owner instanceof StarDevourerEntity) {
            vfx.entityData.set(TIDE, true);
        }
        level.addFreshEntity(vfx);
        return vfx;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, KIND_SHOCK);
        builder.define(LIFE, 10);
        builder.define(SIZE, 1.0F);
        builder.define(DELAY, 0);
        builder.define(TIDE, false);
    }

    public boolean isTide() {
        return this.entityData.get(TIDE);
    }

    // 按配色选择火花粒子
    private SimpleParticleType sparkParticle() {
        if (this.isTide()) {
            return ModParticles.TIDE_SPARK.get();
        }
        return ModParticles.RIFT_SPARK.get();
    }

    // 按配色选择碎片粒子
    private SimpleParticleType shardParticle() {
        if (this.isTide()) {
            return ModParticles.TIDE_SHARD.get();
        }
        return ModParticles.RIFT_SHARD.get();
    }

    public int getKind() {
        return this.entityData.get(KIND);
    }

    public int getLife() {
        return this.entityData.get(LIFE);
    }

    public float getSize() {
        return this.entityData.get(SIZE);
    }

    public int getDelay() {
        return this.entityData.get(DELAY);
    }

    // 扣除延迟后的本体时间
    public float localAge(float partialTick) {
        return this.tickCount + partialTick - this.getDelay();
    }

    @Override
    public void tick() {
        super.tick();
        int kind = this.getKind();
        if (this.level().isClientSide()) {
            this.clientTick(kind);
            return;
        }
        ServerLevel serverLevel = (ServerLevel) this.level();
        int t = this.tickCount - this.getDelay();
        if (kind == KIND_VORTEX && t >= VORTEX_ARM) {
            this.tickVortex(serverLevel, t);
        }
        if ((kind == KIND_STAR || kind == KIND_BIG_STAR) && t == STAR_FALL) {
            this.starImpact(serverLevel, kind);
        }
        if (kind == KIND_BLACK_HOLE && t >= HOLE_ARM) {
            this.tickBlackHole(serverLevel, t);
        }
        if (kind == KIND_STARDUST && t > 0 && t % 20 == 0) {
            this.tickStardust(serverLevel);
        }
        if (this.tickCount >= this.getLife()) {
            this.discard();
        }
    }

    // 客户端辅助：漩涡碎片螺旋吸入，星陨下落拖尾
    private void clientTick(int kind) {
        int t = this.tickCount - this.getDelay();
        if (kind == KIND_VORTEX && t >= VORTEX_ARM && t < this.getLife() - 4 && this.random.nextInt(2) == 0) {
            double a = this.random.nextDouble() * Math.PI * 2.0D;
            double r = this.getSize() * (0.6D + this.random.nextDouble() * 0.4D);
            double ox = Math.cos(a) * r;
            double oz = Math.sin(a) * r;
            // 切向加向心速度形成螺旋
            this.level().addParticle(this.shardParticle(), this.getX() + ox, this.getY() + 0.2D, this.getZ() + oz,
                -ox * 0.08D - oz * 0.06D, 0.02D, -oz * 0.08D + ox * 0.06D);
        }
        // 黑洞：碎片从外圈旋入
        if (kind == KIND_BLACK_HOLE && t >= HOLE_ARM && t < this.getLife() - 2) {
            double a = this.random.nextDouble() * Math.PI * 2.0D;
            double r = 4.0D + this.random.nextDouble() * 5.0D;
            double ox = Math.cos(a) * r;
            double oz = Math.sin(a) * r;
            double oy = (this.random.nextDouble() - 0.5D) * 4.0D;
            this.level().addParticle(this.shardParticle(), this.getX() + ox, this.getY() + oy, this.getZ() + oz,
                -ox * 0.07D - oz * 0.05D, -oy * 0.07D, -oz * 0.07D + ox * 0.05D);
        }
        // 星尘：地面闪烁
        if (kind == KIND_STARDUST && this.random.nextInt(3) == 0) {
            double a = this.random.nextDouble() * Math.PI * 2.0D;
            double r = this.random.nextDouble() * this.getSize();
            this.level().addParticle(this.sparkParticle(), this.getX() + Math.cos(a) * r, this.getY() + 0.1D, this.getZ() + Math.sin(a) * r, 0.0D, 0.03D, 0.0D);
        }
        if ((kind == KIND_STAR || kind == KIND_BIG_STAR) && t > STAR_FALL - 10 && t < STAR_FALL) {
            float drop = (STAR_FALL - t) / 10.0F;
            this.level().addParticle(this.sparkParticle(), this.getX(), this.getY() + drop * 14.0D + 0.5D, this.getZ(), 0.0D, 0.1D, 0.0D);
        }
    }

    // 漩涡生效：吸向圆心，圆心每 0.5 秒伤害，结束时向上弹射
    private void tickVortex(ServerLevel serverLevel, int t) {
        float radius = this.getSize();
        Entity owner = this.getOwner(serverLevel);
        AABB area = new AABB(this.position(), this.position()).inflate(radius, 2.5D, radius);
        boolean last = this.tickCount == this.getLife() - 1;
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            if (isFriend(victim)) {
                continue;
            }
            Vec3 toCenter = this.position().subtract(victim.position()).multiply(1.0D, 0.0D, 1.0D);
            double dist = toCenter.length();
            if (dist > radius) {
                continue;
            }
            if (last) {
                victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.3D, 0.0D, 0.3D).add(0.0D, 0.6D, 0.0D));
                victim.hurtMarked = true;
                continue;
            }
            if (dist > 0.3D) {
                victim.setDeltaMovement(victim.getDeltaMovement().add(toCenter.scale(0.08D / dist)));
                victim.hurtMarked = true;
            }
            if (dist < 1.5D && (t - VORTEX_ARM) % 10 == 0) {
                if (owner instanceof LivingEntity livingOwner) {
                    victim.hurt(this.damageSources().indirectMagic(this, livingOwner), this.damage >= 0 ? this.damage : 3.0F);
                } else {
                    victim.hurt(this.damageSources().magic(), this.damage >= 0 ? this.damage : 3.0F);
                }
            }
        }
        if (t == VORTEX_ARM) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 0.8F, 1.6F);
        }
        if (last) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.4F, 0.5F);
        }
    }

    // 己方与无效目标不受特效影响
    private static boolean isFriend(LivingEntity victim) {
        return victim instanceof VoidRayEntity || victim instanceof StarDevourerEntity || victim instanceof StarCoreEntity || !victim.isAlive();
    }

    // 带归属的魔法伤害
    private boolean hurtVictim(ServerLevel serverLevel, LivingEntity victim, float damage) {
        Entity owner = this.getOwner(serverLevel);
        if (owner instanceof LivingEntity livingOwner) {
            return victim.hurt(this.damageSources().indirectMagic(this, livingOwner), damage);
        }
        return victim.hurt(this.damageSources().magic(), damage);
    }

    // 黑洞生效：半径 10 吸引，核心伤害，吞噬弹射物与掉落物，结束时坍缩
    private void tickBlackHole(ServerLevel serverLevel, int t) {
        Vec3 c = this.position();
        boolean last = this.tickCount == this.getLife() - 1;
        AABB area = new AABB(c, c).inflate(10.0D);
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            if (isFriend(victim)) {
                continue;
            }
            Vec3 to = c.subtract(victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D));
            double dist = to.length();
            if (dist > 10.0D) {
                continue;
            }
            // 坍缩：半径 6 内伤害并向上击飞
            if (last) {
                if (dist <= 6.0D && this.hurtVictim(serverLevel, victim, this.secondaryDamage >= 0 ? this.secondaryDamage : 12.0F)) {
                    victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.3D, 0.0D, 0.3D).add(0.0D, 0.8D, 0.0D));
                    victim.hurtMarked = true;
                }
                continue;
            }
            // 吸力：越近越强，0.05 到 0.12
            if (dist > 0.5D) {
                double pull = 0.05D + 0.07D * (1.0D - dist / 10.0D);
                victim.setDeltaMovement(victim.getDeltaMovement().add(to.scale(pull / dist)));
                victim.hurtMarked = true;
            }
            if (dist < 2.0D && (t - HOLE_ARM) % 10 == 0) {
                this.hurtVictim(serverLevel, victim, this.damage >= 0 ? this.damage : 5.0F);
            }
        }
        // 吞噬靠近核心的弹射物与掉落物
        AABB core = new AABB(c, c).inflate(2.5D);
        for (Projectile projectile : serverLevel.getEntitiesOfClass(Projectile.class, core)) {
            if (!(projectile.getOwner() instanceof StarDevourerEntity)) {
                projectile.discard();
            }
        }
        for (ItemEntity item : serverLevel.getEntitiesOfClass(ItemEntity.class, core)) {
            item.discard();
        }
        if (t == HOLE_ARM) {
            serverLevel.playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 1.4F, 0.5F);
        }
        if (last) {
            serverLevel.sendParticles(this.shardParticle(), c.x, c.y, c.z, 40, 2.0D, 2.0D, 2.0D, 0.4D);
            serverLevel.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.6F);
        }
    }

    // 星尘地面：每秒 2 伤害
    private void tickStardust(ServerLevel serverLevel) {
        float radius = this.getSize();
        AABB area = new AABB(this.position(), this.position()).inflate(radius, 1.5D, radius);
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            if (isFriend(victim)) {
                continue;
            }
            if (victim.position().subtract(this.position()).horizontalDistanceSqr() <= radius * radius) {
                this.hurtVictim(serverLevel, victim, this.damage >= 0 ? this.damage : 2.0F);
            }
        }
    }

    // 星陨落地：范围伤害与上抛，大陨石留下星尘
    private void starImpact(ServerLevel serverLevel, int kind) {
        Entity owner = this.getOwner(serverLevel);
        float radius = this.getSize();
        AABB area = new AABB(this.position(), this.position()).inflate(radius, 2.0D, radius);
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            if (isFriend(victim)) {
                continue;
            }
            if (victim.position().subtract(this.position()).horizontalDistanceSqr() > radius * radius) {
                continue;
            }
            float damage = 8.0F;
            if (kind == KIND_BIG_STAR) {
                damage = 10.0F;
            }
            if (this.damage >= 0) damage = this.damage;
            boolean hurt = this.hurtVictim(serverLevel, victim, damage);
            if (hurt) {
                victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, 0.45D, 0.0D));
                victim.hurtMarked = true;
            }
        }
        serverLevel.sendParticles(this.shardParticle(), this.getX(), this.getY() + 0.3D, this.getZ(), 10, 0.6D, 0.2D, 0.6D, 0.22D);
        serverLevel.sendParticles(this.sparkParticle(), this.getX(), this.getY() + 0.3D, this.getZ(), 6, 0.4D, 0.3D, 0.4D, 0.1D);
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.2F, 0.7F);
        if (kind == KIND_BIG_STAR) {
            VoidRayVfxEntity dust = spawn(serverLevel, KIND_STARDUST, this.position(), 0.0F, radius, 0, DUST_LIFE, owner, this.secondaryDamage, -1.0F);
            // 鳐王已不在时星尘仍沿用陨石的配色
            dust.entityData.set(TIDE, this.isTide());
        }
    }

    private Entity getOwner(ServerLevel serverLevel) {
        if (this.ownerId == null) {
            return null;
        }
        return serverLevel.getEntity(this.ownerId);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // 预警线沿朝向延伸，其余按半径
        float size = this.getSize();
        return this.getBoundingBox().inflate(size + 1.0D, 16.0D, size + 1.0D);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
