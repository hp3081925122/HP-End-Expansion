package com.hp_end_expansion.content.prismatic.entity;

import com.hp_end_expansion.content.prismatic.PrismaticContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

// 晶针为有方块碰撞的短寿投射物，以棱针物品模型渲染。
public final class CrystalNeedle extends ThrowableItemProjectile {
    private float damage = 4.0F;

    public CrystalNeedle(EntityType<? extends CrystalNeedle> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public void setDamage(float damage) { this.damage = damage; }
    @Override protected Item getDefaultItem() { return PrismaticContent.item("prism_needle"); }

    // 不攻击同阵营生物，也不允许投射物无限存在。
    @Override
    protected boolean canHitEntity(Entity entity) {
        return !(entity instanceof PrismaticMonster) && !(entity instanceof PrismaticAnimal) && super.canHitEntity(entity);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 80) { discard(); }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide && getOwner() instanceof LivingEntity owner) {
            result.getEntity().hurt(damageSources().mobProjectile(this, owner), damage);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) { discard(); }
    }

    // 保存伤害让服务端命令产生的持久化投射物也保持数值。
    @Override
    public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putFloat("PrismaticDamage", damage); }
    @Override
    public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); damage = tag.getFloat("PrismaticDamage"); setNoGravity(true); }
}
