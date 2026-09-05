package com.hp_end_expansion.content.prismatic.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

// 针冠射手保持射程，抬冠一秒后释放单枚晶针。
public final class NeedleSpitter extends PrismaticMonster {
    public NeedleSpitter(EntityType<? extends NeedleSpitter> type, Level level) { super(type, level); }
    @Override public String speciesId() { return "needle_spitter"; }
    @Override protected Attack chooseAttack(LivingEntity target) { return Attack.NEEDLES; }
    @Override protected double preferredRange() { return 10.0; }
    @Override protected int idleCooldown() { return 30; }
}
