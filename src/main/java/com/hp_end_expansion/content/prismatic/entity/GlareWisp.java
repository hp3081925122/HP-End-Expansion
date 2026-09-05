package com.hp_end_expansion.content.prismatic.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

// 耀斑灵低空悬浮，蓄亮后释放可由掩体阻断的短距脉冲。
public final class GlareWisp extends PrismaticMonster {
    public GlareWisp(EntityType<? extends GlareWisp> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    @Override public String speciesId() { return "glare_wisp"; }
    @Override protected Attack chooseAttack(LivingEntity target) { return Attack.PULSE; }
    @Override protected boolean usesSpecial(Attack attack) { return true; }
    @Override protected double preferredRange() { return 3.0; }
    @Override public double attackRadius() { return 4.0; }

    @Override
    protected PathNavigation createNavigation(Level level) { return new FlyingPathNavigation(this, level); }

    // 有目标时飞向目标上方一格，不越过整个岛屿做高空追击。
    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        if (!level().isClientSide && !isNoAi() && isAlive() && currentAttack() == Attack.NONE && getTarget() != null && tickCount % 10 == 0) {
            LivingEntity target = getTarget();
            getNavigation().moveTo(target.getX(), target.getY() + 1.0, target.getZ(), 1.0);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
}
