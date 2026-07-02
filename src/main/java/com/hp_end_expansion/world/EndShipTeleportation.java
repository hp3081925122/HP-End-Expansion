package com.hp_end_expansion.world;

import com.hp_end_expansion.HpEndExpansion;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

public final class EndShipTeleportation {
    // 用自定义结构标签承载“末地船目的地”，目前数据中指向原版 end_city。
    private static final TagKey<Structure> END_SHIP_DESTINATIONS = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "end_ship_destinations"));
    // 查找结构时使用较大半径，避免末地外岛结构稀疏导致频繁找不到。
    private static final int STRUCTURE_SEARCH_RADIUS = 1024;
    // 落点搜索半径，围绕找到的结构中心寻找能站立的位置。
    private static final int SAFE_POS_SEARCH_RADIUS = 48;

    // 工具类只提供静态传送能力，不允许实例化。
    private EndShipTeleportation() {
    }

    // 把实体传送到最近的末地船目的地。
    public static boolean teleportToNearestEndShip(ServerLevel originLevel, Entity entity) {
        // 从服务端拿末地维度，确保非末地使用也能前往末地结构。
        ServerLevel endLevel = originLevel.getServer().getLevel(Level.END);
        if (endLevel == null) {
            return false;
        }

        // 用实体当前 XZ 作为结构查找起点，跨维度时也沿用坐标投影。
        BlockPos searchOrigin = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
        BlockPos structurePos = endLevel.findNearestMapStructure(END_SHIP_DESTINATIONS, searchOrigin, STRUCTURE_SEARCH_RADIUS, false);
        if (structurePos == null) {
            return false;
        }

        // 结构中心不一定能站人，所以先找附近安全落点。
        BlockPos arrivalPos = findSafeArrival(endLevel, structurePos, entity);
        if (arrivalPos == null) {
            return false;
        }

        // 清理摔落距离，防止落地后继承传送前坠落伤害。
        entity.fallDistance = 0.0F;

        // 玩家跨维度传送必须走 ServerPlayer 专用入口。
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(endLevel, arrivalPos.getX() + 0.5D, arrivalPos.getY(), arrivalPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
            player.setPortalCooldown(80);
            return true;
        }

        // 普通实体使用 Entity 跨维度传送入口。
        boolean teleported = entity.teleportTo(endLevel, arrivalPos.getX() + 0.5D, arrivalPos.getY(), arrivalPos.getZ() + 0.5D, Set.of(), entity.getYRot(), entity.getXRot());
        if (teleported) {
            entity.setPortalCooldown(80);
        }
        return teleported;
    }

    // 把实体传送到末影晶核绑定的位置。
    public static boolean teleportToBoundPosition(ServerLevel originLevel, Entity entity, ResourceKey<Level> targetDimension, Vec3 targetPosition) {
        // 绑定维度可能已被移除或未加载，找不到时直接失败。
        ServerLevel targetLevel = originLevel.getServer().getLevel(targetDimension);
        if (targetLevel == null) {
            return false;
        }

        // 传送前脱离骑乘关系，避免把其他实体一起拖过去。
        entity.stopRiding();
        // 清理摔落距离，避免传送目标吃到之前的坠落伤害。
        entity.fallDistance = 0.0F;

        // 玩家目标使用 ServerPlayer 专用传送。
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(targetLevel, targetPosition.x, targetPosition.y, targetPosition.z, player.getYRot(), player.getXRot());
            player.setPortalCooldown(40);
            return true;
        }

        // 普通实体跨维度传送到绑定坐标。
        boolean teleported = entity.teleportTo(targetLevel, targetPosition.x, targetPosition.y, targetPosition.z, Set.of(), entity.getYRot(), entity.getXRot());
        if (teleported) {
            entity.setPortalCooldown(40);
        }
        return teleported;
    }

    // 在结构附近寻找实体能安全站立的位置。
    @Nullable
    private static BlockPos findSafeArrival(ServerLevel level, BlockPos center, Entity entity) {
        // 先检查结构中心附近，再逐圈扩大搜索。
        for (int radius = 0; radius <= SAFE_POS_SEARCH_RADIUS; radius += 4) {
            for (int x = center.getX() - radius; x <= center.getX() + radius; x += 4) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += 4) {
                    // 只检查当前搜索圈边缘，减少无意义重复。
                    if (radius > 0 && Math.abs(x - center.getX()) < radius && Math.abs(z - center.getZ()) < radius) {
                        continue;
                    }
                    // 用高度图找到该 XZ 的最高可行动位置。
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (isSafeArrival(level, candidate, entity)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    // 判断一个位置是否适合作为传送落点。
    private static boolean isSafeArrival(ServerLevel level, BlockPos pos, Entity entity) {
        // 脚下必须有实体能站住的方块。
        BlockState below = level.getBlockState(pos.below());
        if (!below.blocksMotion()) {
            return false;
        }

        // 脚部和头部空间必须为空，避免传进方块里。
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }

        // 用实体碰撞盒再确认一次，覆盖大型实体或异常尺寸实体。
        Vec3 move = pos.getBottomCenter().subtract(entity.position());
        return level.noCollision(entity, entity.getBoundingBox().move(move));
    }
}
