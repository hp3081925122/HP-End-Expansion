package com.hp_end_expansion.content.prismatic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// 片角羊只对伤害来源反击，低头一秒后顶击，随后保留一秒收招。
public final class FacetRam extends PrismaticAnimal {
    private int retaliationTicks;
    private int headbuttTicks;

    public FacetRam(EntityType<? extends FacetRam> type, Level level) { super(type, level); }
    @Override public String speciesId() { return "facet_ram"; }
    @Override protected String productId() { return "horn_fragment"; }
    @Override protected int productionCooldown() { return 6000; }
    @Override protected boolean performingAction() { return headbuttTicks > 0; }

    // 正在反击时不播放喂食动作，以免遮盖低头顶击的前摇。
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return getTarget() != null ? InteractionResult.PASS : super.mobInteract(player, hand);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // 新伤害重置反击计时；超时后让原版目标也停止记忆，避免下一刻重新锁回旧仇敌。
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);
        if (damaged && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker && attacker != this) {
            retaliationTicks = 0;
            setTarget(attacker);
        }
        return damaged;
    }

    @Override
    public boolean canAttack(LivingEntity target) { return retaliationTicks <= 300 && super.canAttack(target); }

    // 反击只维持十五秒，创意和旁观玩家不会成为伤害目标。
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive() || isNoAi()) { return; }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            setTarget(null);
            headbuttTicks = 0;
            return;
        }
        if (++retaliationTicks > 300 || distanceToSqr(target) > 256) { setTarget(null); return; }
        getLookControl().setLookAt(target, 30, 30);
        if (headbuttTicks > 0) {
            getNavigation().stop();
            headbuttTicks--;
            if (headbuttTicks == 20 && distanceToSqr(target) < 5 && hasLineOfSight(target)) {
                target.hurt(damageSources().mobAttack(this), 4.0F);
                target.knockback(0.35, getX() - target.getX(), getZ() - target.getZ());
            }
        } else if (distanceToSqr(target) < 5 && hasLineOfSight(target)) {
            headbuttTicks = 40;
            triggerAnim("main", "attack");
        } else if (tickCount % 10 == 0) {
            getNavigation().moveTo(target, 1.2);
        }
    }

    // 重载会恢复完整收招，不把半段攻击改成无前摇命中。
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        headbuttTicks = 0;
        retaliationTicks = 0;
    }
}
