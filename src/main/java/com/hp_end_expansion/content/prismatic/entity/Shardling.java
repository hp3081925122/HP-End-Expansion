package com.hp_end_expansion.content.prismatic.entity;

import com.hp_end_expansion.content.prismatic.PrismaticContent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

// 碎晶虫接受晶屑并产棱壤粉，也会捡取身旁少量晶屑作为生态动作。
public final class Shardling extends PrismaticAnimal {
    private int forageDelay;

    public Shardling(EntityType<? extends Shardling> type, Level level) { super(type, level); }
    @Override public String speciesId() { return "shardling"; }
    @Override protected String foodId() { return "crystal_shard"; }
    @Override protected String productId() { return "prism_meal"; }
    @Override protected int productionDelay() { return 100; }
    @Override protected int productionCooldown() { return 1200; }

    // 只查看六格内首个晶屑，自主觅食也占用喂食产物槽，不额外生成掉落实体。
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isNoAi() || isBaby() || !isAlive() || hasPendingProduct() || productCooldownTicks() > 0 || --forageDelay > 0) { return; }
        forageDelay = 200;
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(6.0),
                entity -> entity.isAlive() && entity.getItem().is(PrismaticContent.item("crystal_shard")))) {
            if (distanceToSqr(item) < 1.5 && hasLineOfSight(item) && beginProduction()) {
                getNavigation().stop();
                item.getItem().shrink(1);
                if (item.getItem().isEmpty()) { item.discard(); }
            } else {
                getNavigation().moveTo(item, 1.0);
            }
            break;
        }
    }
}
