package com.hp_end_expansion.content.prismatic.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

// 裂层卫交替使用正面重击与裂层冲锋，结构实例持久保存。
public final class FaultWarden extends PrismaticMonster {
    public FaultWarden(EntityType<? extends FaultWarden> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 35;
    }

    @Override public String speciesId() { return "fault_warden"; }
    @Override protected Attack chooseAttack(LivingEntity target) { return attackSequence % 2 == 0 ? Attack.STRIKE : Attack.CHARGE; }
    @Override protected double preferredRange() { return attackSequence % 2 == 0 ? 3.0 : 7.0; }
    @Override public double attackRadius() { return 4.0; }
    @Override protected double chargeSpeed() { return 1.0; }
    @Override protected int idleCooldown() { return 30; }
}
