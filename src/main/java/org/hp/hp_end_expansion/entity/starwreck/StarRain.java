package org.hp.hp_end_expansion.entity.starwreck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.hp.hp_end_expansion.registry.StarwreckEntities;
import org.hp.hp_end_expansion.worldgen.StarwreckWorldgen;

/** 玩家站在星骸荒原时，每隔一段时间在附近打开一道星雨裂隙。 */
public final class StarRain {
    public static final int INTERVAL = 20 * 60 * 6;
    public static final int INTERVAL_EXTRA = 20 * 60 * 2;
    private static final int RETRY = 400;
    private static final Map<ResourceKey<Level>, Long> NEXT = new HashMap<>();

    private StarRain() {}

    public static void tick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getGameTime() % 20 != 0) continue;
            long now = level.getGameTime();
            long next = NEXT.getOrDefault(level.dimension(), Long.MIN_VALUE);
            if (next == Long.MIN_VALUE) {
                NEXT.put(level.dimension(), now + INTERVAL + level.random.nextInt(INTERVAL_EXTRA));
                continue;
            }
            if (now < next) continue;
            if (!level.getEntities(StarwreckEntities.STAR_RIFT.get(), Entity::isAlive).isEmpty()) continue;
            List<ServerPlayer> watchers = level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator()
                    && level.getBiome(player.blockPosition()).is(StarwreckWorldgen.BIOME))
                .toList();
            if (watchers.isEmpty()) continue;
            ServerPlayer player = watchers.get(level.random.nextInt(watchers.size()));
            NEXT.put(level.dimension(), now + (open(level, player) ? INTERVAL + level.random.nextInt(INTERVAL_EXTRA) : RETRY));
        }
    }

    public enum Result { STARTED, BUSY, NO_SITE }

    /** 在玩家附近立刻开一场，并推迟下一次自然星雨。调用前应确认玩家站在星骸荒原。 */
    public static Result summonNear(ServerLevel level, ServerPlayer player) {
        if (!level.getEntities(StarwreckEntities.STAR_RIFT.get(), Entity::isAlive).isEmpty()) return Result.BUSY;
        if (!open(level, player)) return Result.NO_SITE;
        NEXT.put(level.dimension(), level.getGameTime() + INTERVAL + level.random.nextInt(INTERVAL_EXTRA));
        return Result.STARTED;
    }

    private static boolean open(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double distance = 32 + level.random.nextDouble() * 32;
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, player.getBlockY(), z);
            if (!level.hasChunkAt(column)) continue;
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (surface.getY() <= level.getMinBuildHeight() + 4) continue;
            if (!level.getBiome(surface).is(StarwreckWorldgen.BIOME)) continue;
            if (level.getBlockState(surface.below()).getCollisionShape(level, surface.below()).isEmpty()) continue;
            if (!level.getBlockState(surface).getCollisionShape(level, surface).isEmpty()) continue;
            if (!level.getBlockState(surface.above()).getCollisionShape(level, surface.above()).isEmpty()) continue;
            if (!level.getEntitiesOfClass(Player.class, new AABB(surface).inflate(8), Player::isAlive).isEmpty()) continue;
            int riftX = x + (int) Math.round(Math.cos(angle) * StarRiftEntity.OFFSET);
            int riftZ = z + (int) Math.round(Math.sin(angle) * StarRiftEntity.OFFSET);
            if (!skyClear(level, riftX, riftZ, surface.getY())) continue;
            Vec3 impact = new Vec3(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5);
            Vec3 riftPos = new Vec3(riftX + 0.5, surface.getY() + StarRiftEntity.HEIGHT, riftZ + 0.5);
            StarRiftEntity.spawn(level, riftPos, impact);
            return true;
        }
        return false;
    }

    private static boolean skyClear(ServerLevel level, int x, int z, int groundY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = groundY + 8; y <= groundY + StarRiftEntity.HEIGHT; y += 4) {
            if (!level.getBlockState(cursor.setY(y)).isAir()) return false;
        }
        return level.hasChunkAt(cursor);
    }
}
