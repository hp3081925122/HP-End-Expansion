package org.hp.hp_end_expansion.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.entity.RiftVfxEntity;
import org.hp.hp_end_expansion.registry.ModParticles;

public final class MatriarchScytheItem extends SwordItem {
    // 闪现斩参数：距离、伤害、冷却
    private static final double DASH = 6.0D;
    private static final float DASH_DAMAGE = 12.0F;
    private static final int COOLDOWN = 160;

    public MatriarchScytheItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        // 水平向前，遇墙截断
        Vec3 look = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
        Vec3 start = player.position();
        Vec3 end = start.add(look.scale(DASH));
        BlockHitResult hit = level.clip(new ClipContext(start.add(0.0D, 0.5D, 0.0D), end.add(0.0D, 0.5D, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) {
            end = hit.getLocation().subtract(look.scale(0.6D)).subtract(0.0D, 0.5D, 0.0D);
        }
        // 斩击路径上的敌人
        AABB path = new AABB(start, end).inflate(1.2D, 1.0D, 1.2D).expandTowards(0.0D, 1.0D, 0.0D);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, path)) {
            if (victim != player && victim.isAlive() && !player.isAlliedTo(victim)) {
                victim.hurt(player.damageSources().playerAttack(player), DASH_DAMAGE);
            }
        }
        RiftVfxEntity.spawn(level, RiftVfxEntity.KIND_PORTAL, start.x, start.y, start.z, player.getYRot(), 0.0F, 0.8F, 10);
        player.teleportTo(end.x, end.y, end.z);
        player.fallDistance = 0.0F;
        Vec3 mid = start.add(end).scale(0.5D);
        RiftVfxEntity.spawn(level, RiftVfxEntity.KIND_SLASH, mid.x, mid.y + 1.0D, mid.z, player.getYRot(), 0.0F, 1.5F, 8);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.RIFT_SPARK.get(), mid.x, mid.y + 1.0D, mid.z, 12, 1.5D, 0.4D, 1.5D, 0.05D);
        }
        level.playSound(null, end.x, end.y, end.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.3F);
        player.getCooldowns().addCooldown(this, COOLDOWN);
        stack.hurtAndBreak(2, player, LivingEntity.getSlotForHand(hand));
        return InteractionResultHolder.consume(stack);
    }
}
