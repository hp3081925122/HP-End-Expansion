package com.hp_end_expansion.content.prismatic.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

// 砾背兽接近后抬甲震地，攻击范围明确限制在三格内。
public final class Shardback extends PrismaticMonster {
    public Shardback(EntityType<? extends Shardback> type, Level level) { super(type, level); }
    @Override public String speciesId() { return "shardback"; }
    @Override protected Attack chooseAttack(LivingEntity target) { return Attack.PULSE; }
    @Override protected double preferredRange() { return 2.0; }
    @Override public double attackRadius() { return 3.0; }
    @Override protected int idleCooldown() { return 34; }
}
