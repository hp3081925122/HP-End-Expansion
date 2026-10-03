package org.hp.hp_end_expansion.entity.starwreck;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/** 裂天之主的头部判定。头伸出本体碰撞箱两格多，单独一块判定让近战和箭都打得到；受到的伤害转给本体。 */
public final class SkyrenderPart extends PartEntity<SkyrenderEntity> {
    private final EntityDimensions size;

    public SkyrenderPart(SkyrenderEntity parent, float width, float height) {
        super(parent);
        // 尺寸固定，不随姿态变化
        size = EntityDimensions.scalable(width, height);
        refreshDimensions();
    }

    // 打头：交给本体按头部倍率结算
    @Override public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        return getParent().hurtFromHead(source, amount);
    }

    @Override public boolean is(Entity entity) { return this == entity || getParent() == entity; }
    @Override public boolean isPickable() { return true; }
    @Override public @Nullable ItemStack getPickResult() { return getParent().getPickResult(); }
    @Override public EntityDimensions getDimensions(Pose pose) { return size; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
