package org.hp.hp_end_expansion.entity.starwreck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
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

/** 玩家站在星骸荒原时，每隔一段时间打开一场持续 {@link #DURATION} 的星雨。 */
public final class StarRain {
    public static final int DURATION = 20 * 60 * 5;
    public static final int WAVE_INTERVAL = 20;
    public static final int INTERVAL = 20 * 60 * 6;
    public static final int INTERVAL_EXTRA = 20 * 60 * 2;
    private static final int WAVE_COUNT = 2;
    private static final int MAX_RIFTS = 12;
    private static final int RETRY = 400;
    private static final Map<ResourceKey<Level>, Long> NEXT = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> UNTIL = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> LAST_WAVE = new HashMap<>();
    private static final Map<ResourceKey<Level>, Integer> CHASERS = new HashMap<>();
    private static final int MAX_CHASERS = 2;
    private static final float CHASER_CHANCE = 0.1F;

    private StarRain() {}

    /** 星雨陨星落地时调用：有一定概率从陨星里爬出一只逐星兽，每场星雨最多 {@link #MAX_CHASERS} 只。 */
    public static void onImpact(ServerLevel level, Vec3 at) {
        int count = CHASERS.getOrDefault(level.dimension(), 0);
        if (count >= MAX_CHASERS || level.random.nextFloat() >= CHASER_CHANCE) return;
        StarChaserEntity chaser = StarwreckEntities.STAR_CHASER.get().create(level);
        if (chaser == null) return;
        chaser.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
        chaser.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
        chaser.markFromStarRain();
        level.addFreshEntity(chaser);
        CHASERS.put(level.dimension(), count + 1);
    }

    public static void tick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long now = level.getGameTime();
            if (active(level)) {
                if (now % WAVE_INTERVAL == 0) spawnWave(level);
                continue;
            }
            UNTIL.remove(level.dimension());
            if (now % 20 != 0) continue;
            long next = NEXT.getOrDefault(level.dimension(), Long.MIN_VALUE);
            if (next == Long.MIN_VALUE) {
                NEXT.put(level.dimension(), now + INTERVAL + level.random.nextInt(INTERVAL_EXTRA));
                continue;
            }
            if (now < next) continue;
            if (watchers(level).isEmpty()) continue;
            // 裂天之主战斗期间不自然开星雨
            if (SkyrenderEntity.present(level)) { NEXT.put(level.dimension(), now + RETRY); continue; }
            if (!begin(level)) NEXT.put(level.dimension(), now + RETRY);
        }
    }

    public static boolean active(ServerLevel level) {
        Long until = UNTIL.get(level.dimension());
        return until != null && level.getGameTime() < until;
    }

    /** 在一名荒原玩家周围新开 {@link #WAVE_COUNT} 道裂隙。每道自己张开、放一颗星、再合上。 */
    private static int spawnWave(ServerLevel level) {
        long now = level.getGameTime();
        if (LAST_WAVE.getOrDefault(level.dimension(), Long.MIN_VALUE) == now) return 0;
        LAST_WAVE.put(level.dimension(), now);
        List<ServerPlayer> watchers = watchers(level);
        if (watchers.isEmpty()) return 0;
        int alive = level.getEntities(StarwreckEntities.STAR_RIFT.get(), Entity::isAlive).size();
        if (alive >= MAX_RIFTS) return 0;
        Vec3 around = watchers.get(level.random.nextInt(watchers.size())).position();
        int spawned = 0;
        for (int i = 0; i < WAVE_COUNT && alive < MAX_RIFTS; i++) {
            Site site = findSite(level, around, 20, 76);
            if (site == null) continue;
            StarRiftEntity.spawn(level, site.from, site.impact);
            alive++;
            spawned++;
        }
        return spawned;
    }

    private static boolean begin(ServerLevel level) {
        if (spawnWave(level) == 0) return false;
        long now = level.getGameTime();
        UNTIL.put(level.dimension(), now + DURATION);
        CHASERS.put(level.dimension(), 0);
        NEXT.put(level.dimension(), now + DURATION + INTERVAL + level.random.nextInt(INTERVAL_EXTRA));
        return true;
    }

    private static List<ServerPlayer> watchers(ServerLevel level) {
        return level.players().stream()
            .filter(player -> player.isAlive() && !player.isSpectator()
                && level.getBiome(player.blockPosition()).is(StarwreckWorldgen.BIOME))
            .toList();
    }

    public enum Result { STARTED, BUSY, NO_SITE }

    /** 在玩家附近立刻开一场，并推迟下一次自然星雨。调用前应确认玩家站在星骸荒原。 */
    public static Result summonNear(ServerLevel level, ServerPlayer player) {
        if (active(level)) return Result.BUSY;
        if (!begin(level)) return Result.NO_SITE;
        return Result.STARTED;
    }

    private record Site(Vec3 impact, Vec3 from) {}

    private static Site findSite(ServerLevel level, Vec3 around, double minDist, double maxDist) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double distance = minDist + level.random.nextDouble() * (maxDist - minDist);
            int x = Mth.floor(around.x) + (int) Math.round(Math.cos(angle) * distance);
            int z = Mth.floor(around.z) + (int) Math.round(Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, Mth.floor(around.y), z);
            if (!level.hasChunkAt(column)) continue;
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (surface.getY() <= level.getMinBuildHeight() + 4) continue;
            if (!level.getBiome(surface).is(StarwreckWorldgen.BIOME)) continue;
            if (level.getBlockState(surface.below()).getCollisionShape(level, surface.below()).isEmpty()) continue;
            if (!level.getBlockState(surface).getCollisionShape(level, surface).isEmpty()) continue;
            if (!level.getBlockState(surface.above()).getCollisionShape(level, surface).isEmpty()) continue;
            if (!level.getEntitiesOfClass(Player.class, new AABB(surface).inflate(8), Player::isAlive).isEmpty()) continue;
            int skyX = x + (int) Math.round(Math.cos(angle) * StarRiftEntity.OFFSET);
            int skyZ = z + (int) Math.round(Math.sin(angle) * StarRiftEntity.OFFSET);
            if (!skyClear(level, skyX, skyZ, surface.getY())) continue;
            Vec3 impact = new Vec3(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5);
            Vec3 from = new Vec3(skyX + 0.5, surface.getY() + StarRiftEntity.HEIGHT, skyZ + 0.5);
            return new Site(impact, from);
        }
        return null;
    }

    private static boolean skyClear(ServerLevel level, int x, int z, int groundY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = groundY + 8; y <= groundY + StarRiftEntity.HEIGHT; y += 4) {
            if (!level.getBlockState(cursor.setY(y)).isAir()) return false;
        }
        return level.hasChunkAt(cursor);
    }
}
