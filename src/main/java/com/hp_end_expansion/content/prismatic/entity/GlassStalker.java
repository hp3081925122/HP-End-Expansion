package com.hp_end_expansion.content.prismatic.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.level.Level;

// 玻行猎兽低伏蓄力后沿固定方向扑击，结束后露出收招窗口。
public final class GlassStalker extends PrismaticMonster {
    public GlassStalker(EntityType<? extends GlassStalker> type, Level level) { super(type, level); }
    @Override public String speciesId() { return "glass_stalker"; }
    @Override protected Attack chooseAttack(LivingEntity target) { return Attack.CHARGE; }
    @Override protected double preferredRange() { return 6.0; }
    @Override protected int idleCooldown() { return 24; }

    // 玩家与反击目标优先级更高，空闲时才捕食附近棱兔，形成局部食物链。
    @Override
    protected void registerGoals() {
        super.registerGoals();
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, PrismHare.class, true));
    }

    @Override
    protected boolean isCombatTarget(LivingEntity entity) {
        return entity instanceof PrismHare && entity.isAlive() || super.isCombatTarget(entity);
    }
}
