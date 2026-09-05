package com.hp_end_expansion.content.prismatic.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

// 狩镜者轮换三道扇形晶针和锁线突进，长前摇换取较快位移。
public final class MirrorHuntress extends PrismaticMonster {
    public MirrorHuntress(EntityType<? extends MirrorHuntress> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 35;
    }

    @Override public String speciesId() { return "mirror_huntress"; }
    @Override protected Attack chooseAttack(LivingEntity target) { return attackSequence % 2 == 0 ? Attack.NEEDLES : Attack.CHARGE; }
    @Override protected boolean usesSpecial(Attack attack) { return true; }
    @Override protected double preferredRange() { return attackSequence % 2 == 0 ? 11.0 : 7.0; }
    @Override protected int needleCount() { return 3; }
    @Override protected double chargeSpeed() { return 1.05; }
    @Override protected int idleCooldown() { return 24; }
}
