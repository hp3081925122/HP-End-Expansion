package org.hp.hp_end_expansion.entity.starwreck;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.registry.ModParticles;
import org.hp.hp_end_expansion.registry.ModStarwreck;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * 陨壳龟（设计文档 9.3）。中立：平时趴着或慢慢挪、啃星苔芽。
 * 被生物打到后：缩壳 3 秒（伤害 ×0.4）→ 探头踏地，第 14 tick 震出半径 4 格的余烬冲击波 → 追击攻击者，对方离开 12 格后平息。
 * 追击中目标贴身时每 10 秒再踏一次。镐右击龟壳敲下一簇星晶，不激怒它；敲完的晶簇每 5 分钟长回一簇，不繁殖。
 */
public final class MeteorTortoiseEntity extends PathfinderMob implements GeoEntity {
    public static final int MAX_CRYSTALS = 4;
    public static final int REGROW_TICKS = 6000;
    public static final int SHELL_TICKS = 60;
    public static final int STOMP_TICKS = 24;
    public static final int STOMP_IMPACT = 14;
    public static final double STOMP_RADIUS = 4;
    public static final float STOMP_DAMAGE = 6;
    public static final double CALM_DISTANCE = 12;
    private static final int STOMP_COOLDOWN = 200;
    public static final byte CALM = 0, SHELLED = 1, STOMPING = 2, ANGRY = 3;

    // 壳上剩余的晶簇数量，客户端据此隐藏 crystal_(n+1) ～ crystal_4 骨骼
    private static final EntityDataAccessor<Integer> CRYSTALS = SynchedEntityData.defineId(MeteorTortoiseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(MeteorTortoiseEntity.class, EntityDataSerializers.BYTE);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.meteor_tortoise.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.meteor_tortoise.walk");
    private static final RawAnimation RETRACT = RawAnimation.begin().thenPlayAndHold("animation.meteor_tortoise.retract");
    private static final RawAnimation STOMP = RawAnimation.begin().thenPlay("animation.meteor_tortoise.stomp");
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private int modeTicks;
    private int regrowTicks;
    private int stompCooldown;

    public MeteorTortoiseEntity(EntityType<? extends MeteorTortoiseEntity> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.ARMOR, 10).add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
            .add(Attributes.MOVEMENT_SPEED, 0.12).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.FOLLOW_RANGE, 16).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CRYSTALS, MAX_CRYSTALS);
        builder.define(MODE, CALM);
    }

    public int getCrystals() { return entityData.get(CRYSTALS); }
    public void setCrystals(int count) { entityData.set(CRYSTALS, Mth.clamp(count, 0, MAX_CRYSTALS)); }
    public byte getMode() { return entityData.get(MODE); }
    private void setMode(byte mode) { entityData.set(MODE, mode); modeTicks = 0; }
    public boolean isShelled() { return getMode() == SHELLED; }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new HoldStillGoal());
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.6, true) {
            @Override public boolean canUse() { return getMode() == ANGRY && super.canUse(); }
            @Override public boolean canContinueToUse() { return getMode() == ANGRY && super.canContinueToUse(); }
        });
        goalSelector.addGoal(4, new GrazeGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7, 0.002F) {
            @Override public boolean canUse() { return getMode() == CALM && super.canUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8, 0.02F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (isShelled() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) amount *= 0.4F;
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive() && source.getEntity() instanceof LivingEntity attacker && attacker != this
            && !(attacker instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            setTarget(attacker);
            if (getMode() == CALM) setMode(SHELLED);
        }
        return hit;
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        modeTicks++;
        if (stompCooldown > 0) stompCooldown--;
        if (getCrystals() < MAX_CRYSTALS && ++regrowTicks >= REGROW_TICKS) { regrowTicks = 0; setCrystals(getCrystals() + 1); }
        LivingEntity target = getTarget();
        switch (getMode()) {
            case SHELLED -> { if (modeTicks >= SHELL_TICKS) startStomp(); }
            case STOMPING -> {
                if (target != null) getLookControl().setLookAt(target);
                if (modeTicks == STOMP_IMPACT) shockwave();
                if (modeTicks >= STOMP_TICKS) setMode(ANGRY);
            }
            case ANGRY -> {
                if (target == null || !target.isAlive() || distanceToSqr(target) > CALM_DISTANCE * CALM_DISTANCE
                    || target instanceof Player player && (player.isCreative() || player.isSpectator())) {
                    setTarget(null);
                    setMode(CALM);
                } else if (stompCooldown <= 0 && distanceToSqr(target) < 3.5 * 3.5 && onGround()) {
                    startStomp();
                }
            }
            default -> {}
        }
    }

    private void startStomp() {
        setMode(STOMPING);
        stompCooldown = STOMP_COOLDOWN;
        triggerAnim("action", "stomp");
    }

    // 余烬冲击波：半径 4 格内（上下 2 格）的其他生物受伤并被向外击飞；同类不受影响
    private void shockwave() {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 center = position();
        for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(STOMP_RADIUS, 2, STOMP_RADIUS),
            e -> e != this && e.isAlive() && !(e instanceof MeteorTortoiseEntity) && e.position().distanceToSqr(center) <= STOMP_RADIUS * STOMP_RADIUS + 4)) {
            if (victim instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
            if (victim.position().subtract(center).horizontalDistanceSqr() > STOMP_RADIUS * STOMP_RADIUS) continue;
            if (victim.hurt(damageSources().mobAttack(this), STOMP_DAMAGE)) {
                Vec3 away = victim.position().subtract(center).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
                victim.knockback(1.1, -away.x, -away.z);
                victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.35, 0));
                victim.hurtMarked = true;
            }
        }
        BlockState ground = getBlockStateOn();
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? ModStarwreck.STARWRECK_STONE.get().defaultBlockState() : ground);
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI * 2 / 32;
            double ring = 1.2 + random.nextDouble() * 0.5;
            server.sendParticles(ModParticles.STAR_EMBER.get(), getX() + Math.cos(angle) * ring, getY() + 0.1, getZ() + Math.sin(angle) * ring, 0,
                Math.cos(angle) * 0.22, 0.03, Math.sin(angle) * 0.22, 1);
            if (i % 2 == 0) server.sendParticles(dust, getX() + Math.cos(angle) * 2.2, getY() + 0.1, getZ() + Math.sin(angle) * 2.2, 2, 0.3, 0.05, 0.3, 0.1);
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.3F, 0.7F);
    }

    // 镐右击龟壳：敲下一簇星晶，掉 1～2 个星晶碎片，不激怒
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ItemTags.PICKAXES)) return super.mobInteract(player, hand);
        if (getCrystals() <= 0) return InteractionResult.PASS;
        if (!level().isClientSide) {
            setCrystals(getCrystals() - 1);
            regrowTicks = 0;
            spawnAtLocation(new ItemStack(ModStarwreck.STAR_CRYSTAL_SHARD.get(), 1 + random.nextInt(2)), 1.0F);
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.NEUTRAL, 1.0F, 0.9F);
            if (level() instanceof ServerLevel server)
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModStarwreck.STAR_CRYSTAL_BLOCK.get().defaultBlockState()),
                    getX(), getY() + 1.1, getZ(), 12, 0.3, 0.2, 0.3, 0.1);
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // 壳上剩余的晶簇越多，死亡时掉的星晶碎片越多：0～1 簇掉 1 个，2～3 簇掉 2 个，4 簇掉 3 个
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        spawnAtLocation(new ItemStack(ModStarwreck.STAR_CRYSTAL_SHARD.get(), 1 + getCrystals() / 2));
    }

    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.TURTLE_AMBIENT_LAND; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return isShelled() ? SoundEvents.SHIELD_BLOCK : SoundEvents.TURTLE_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.TURTLE_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.TURTLE_SHAMBLE, 0.3F, 0.6F); }
    @Override public float getVoicePitch() { return super.getVoicePitch() * 0.6F; }
    @Override public int getAmbientSoundInterval() { return 400; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Crystals", getCrystals());
        tag.putInt("CrystalRegrow", regrowTicks);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Crystals")) setCrystals(tag.getInt("Crystals"));
        regrowTicks = tag.getInt("CrystalRegrow");
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 4, state -> {
            if (isShelled()) return state.setAndContinue(RETRACT);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        // 踏地由服务端在进入踏地阶段的同一 tick 触发；冲击波在第 14 tick，对应动画 0.7 s 的砸地帧
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP).triggerableAnim("stomp", STOMP));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // 缩壳和踏地期间原地不动、不转身
    private final class HoldStillGoal extends Goal {
        HoldStillGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK)); }
        @Override public boolean canUse() { return getMode() == SHELLED || getMode() == STOMPING; }
        @Override public void start() { getNavigation().stop(); }
        @Override public void tick() {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
    }

    // 啃星苔芽：平静时偶尔走到 6 格内的一株星苔芽旁把它吃掉
    private final class GrazeGoal extends Goal {
        private BlockPos food;
        private int ticks;
        GrazeGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() {
            if (getMode() != CALM || random.nextInt(400) != 0) return false;
            BlockPos base = blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-6, -2, -6), base.offset(6, 2, 6)))
                if (level().getBlockState(pos).is(ModStarwreck.STAR_MOSS_SPROUTS.get())) { food = pos.immutable(); return true; }
            return false;
        }

        @Override public boolean canContinueToUse() {
            return getMode() == CALM && food != null && ticks < 400 && level().getBlockState(food).is(ModStarwreck.STAR_MOSS_SPROUTS.get());
        }

        @Override public void start() { ticks = 0; getNavigation().moveTo(food.getX() + 0.5, food.getY(), food.getZ() + 0.5, 0.7); }

        @Override public void tick() {
            ticks++;
            getLookControl().setLookAt(Vec3.atCenterOf(food));
            if (distanceToSqr(Vec3.atBottomCenterOf(food)) < 2.6 * 2.6) {
                level().destroyBlock(food, false, MeteorTortoiseEntity.this);
                playSound(SoundEvents.GENERIC_EAT, 0.6F, 0.7F);
                food = null;
            } else if (ticks % 40 == 0) {
                getNavigation().moveTo(food.getX() + 0.5, food.getY(), food.getZ() + 0.5, 0.7);
            }
        }

        @Override public void stop() { food = null; getNavigation().stop(); }
    }
}
