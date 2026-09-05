package com.hp_end_expansion.content.prismatic.entity;

import com.hp_end_expansion.content.prismatic.PrismaticContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

// 暮翅蛾使用原版飞行寻路，局部寻找花朵，始终保持低空生态活动。
public final class DuskMoth extends PrismaticAnimal {
    public DuskMoth(EntityType<? extends DuskMoth> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    @Override public String speciesId() { return "dusk_moth"; }
    @Override protected String foodId() { return "dusk_bud"; }
    @Override protected String productId() { return "dusk_silk"; }
    @Override protected int productionCooldown() { return 3600; }
    @Override protected boolean usesGroundWander() { return false; }

    @Override
    protected PathNavigation createNavigation(Level level) { return new FlyingPathNavigation(this, level); }

    // 每两秒最多查十六个加载方块，目标在花上方或当前位置周边。
    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        if (level().isClientSide || !isAlive() || isNoAi() || tickCount % 40 != 0 || !getNavigation().isDone()) { return; }
        BlockPos destination = blockPosition().offset(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
        for (int i = 0; i < 16; i++) {
            BlockPos candidate = blockPosition().offset(random.nextInt(13) - 6, random.nextInt(5) - 3, random.nextInt(13) - 6);
            if (level().hasChunkAt(candidate) && level().getBlockState(candidate).is(PrismaticContent.block("dusk_bloom"))) {
                destination = candidate.above();
                break;
            }
        }
        if (!level().hasChunkAt(destination)) { return; }
        BlockPos ground = destination;
        for (int i = 0; i < 6 && level().getBlockState(ground).isAir(); i++) { ground = ground.below(); }
        if (!level().getBlockState(ground).isAir() && level().getBlockState(ground.above(2)).isAir()) {
            getNavigation().moveTo(ground.getX() + 0.5, ground.getY() + 1.5, ground.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
}
