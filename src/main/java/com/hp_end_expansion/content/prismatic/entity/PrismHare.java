package com.hp_end_expansion.content.prismatic.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// 棱兔在地面移动时短跳，停下后不原地连跳。
public final class PrismHare extends PrismaticAnimal {
    private int hopDelay;

    public PrismHare(EntityType<? extends PrismHare> type, Level level) { super(type, level); }
    @Override public String speciesId() { return "prism_hare"; }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !isNoAi() && isAlive() && --hopDelay <= 0 && onGround() && !getNavigation().isDone()) {
            jumpFromGround();
            hopDelay = 12;
        }
    }
}
