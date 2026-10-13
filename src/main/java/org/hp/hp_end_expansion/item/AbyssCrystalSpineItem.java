package org.hp.hp_end_expansion.item;

import net.minecraft.world.level.Level;
import java.util.List;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.network.PacketDistributor;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.network.AbyssSpineBeamPayload;
import org.hp.hp_end_expansion.registry.ModItems;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

@EventBusSubscriber(modid = Hp_end_expansion.MODID)
public final class AbyssCrystalSpineItem extends Item implements ICurioItem {
    public static final String TIDE_ARROW_KEY = Hp_end_expansion.MODID + ":tide_crossbow_arrow";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double RANGE = 12.0D;
    private static final float DAMAGE = 1.0F;

    public AbyssCrystalSpineItem(Properties properties) {
        super(properties);
    }

    public static boolean isEquipped(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
            .map(handler -> handler.isEquipped(ModItems.ABYSS_CRYSTAL_SPINE.get()))
            .orElse(false);
    }

    @SubscribeEvent
    public static void onArrowImpact(ProjectileImpactEvent event) {
        // 只处理服务端、潮光之弩射出、命中方块的箭矢
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)) return;
        if (!arrow.getPersistentData().getBoolean(TIDE_ARROW_KEY)) return;
        if (event.getRayTraceResult().getType() != HitResult.Type.BLOCK || !(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
        if (!(arrow.getOwner() instanceof LivingEntity owner) || !isEquipped(owner)) return;

        // 激光起点：命中点沿受击面稍微外移，避免视线检测被自己打中的方块挡住
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 origin = hit.getLocation().add(normal.scale(0.15D));

        LivingEntity target = null;
        double best = RANGE * RANGE;
        AABB area = new AABB(origin, origin).inflate(RANGE);
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area, e -> isValidTarget(e, owner))) {
            Vec3 aim = candidate.getBoundingBox().getCenter();
            double dist = aim.distanceToSqr(origin);
            if (dist > best) continue;
            // 中间被方块挡住的不算
            BlockHitResult block = level.clip(new ClipContext(origin, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow));
            if (block.getType() != HitResult.Type.MISS) continue;
            best = dist;
            target = candidate;
        }
        if (target == null) {
            LOGGER.debug("Abyss crystal spine: no target near arrow={} at {}", arrow.getId(), origin);
            return;
        }

        // 无视无敌帧造成伤害，结束后还原，不影响后续正常攻击
        Vec3 aim = target.getBoundingBox().getCenter();
        int oldInvulnerable = target.invulnerableTime;
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(level.damageSources().indirectMagic(arrow, owner), DAMAGE);
        target.invulnerableTime = oldInvulnerable;
        LOGGER.debug("Abyss crystal spine laser: owner={}, arrow={}, target={}, hurt={}, oldInvulnerable={}",
            owner.getId(), arrow.getId(), target.getId(), hurt, oldInvulnerable);

        // 音效与客户端特效
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.7F);
        level.playSound(null, aim.x, aim.y, aim.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.5F);
        org.hp.hp_end_expansion.network.ModNetwork.sendBeam(level,
            new AbyssSpineBeamPayload(origin, aim, hit.getDirection().get3DDataValue()));

        // 发射激光后移除箭矢，并取消这次命中
        arrow.discard();
        event.setCanceled(true);
    }

    private static boolean isValidTarget(LivingEntity entity, LivingEntity owner) {
        return entity != owner && entity.isAlive() && !(entity instanceof Player) && !entity.isAlliedTo(owner)
            && (entity instanceof Mob || entity instanceof Enemy);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.abyss_crystal_spine.laser").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.hp_end_expansion.abyss_crystal_spine.damage").withStyle(ChatFormatting.GRAY));
    }
}
