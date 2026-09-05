package com.hp_end_expansion.content.prismatic.test;

import com.google.gson.JsonElement;
import com.hp_end_expansion.content.prismatic.PrismaticContent;
import com.hp_end_expansion.content.prismatic.PrismaticEntities;
import com.hp_end_expansion.content.prismatic.worldgen.PrismaticBiomeRouting;
import com.hp_end_expansion.content.prismatic.worldgen.PrismaticStructurePiece;
import com.hp_end_expansion.content.prismatic.worldgen.PrismaticTerrainSampler;
import com.hp_end_expansion.content.prismatic.worldgen.PrismaticWorldgen;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("hp_end_expansion")
@PrefixGameTestTemplate(false)
public final class PrismaticWorldgenTests {
    // 三个种子、四片跨单元区域逐柱对照原版，包含负坐标、中央岛、边缘和外岛。
    @GameTest(template = "prismatic_empty", timeoutTicks = 400)
    public static void batchedTerrainMatchesVanillaColumnsAcrossSeeds(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null && end.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,
                "Terrain equivalence requires the real noise-based End generator");
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) end.getChunkSource().getGenerator();
        int[][] areas = {{-9, -9}, {1023, -1025}, {-4003, 3559}, {3533, -4027}};
        int compared = 0;
        long totalBatchNanos = 0L;
        long totalVanillaNanos = 0L;
        for (long seed : new long[]{0L, 918273645L, -482901L}) {
            RandomState state = RandomState.create(generator.generatorSettings().value(),
                    end.registryAccess().registryOrThrow(Registries.NOISE).asLookup(), seed);
            for (int[] area : areas) {
                long started = System.nanoTime();
                var terrain = PrismaticTerrainSampler.sample(generator, state, end, area[0], area[1], 5, 5);
                totalBatchNanos += System.nanoTime() - started;
                started = System.nanoTime();
                for (int x = area[0]; x < area[0] + 5; x++) for (int z = area[1]; z < area[1] + 5; z++) {
                    int vanilla = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, end, state);
                    helper.assertTrue(terrain.height(x, z) == vanilla, "Terrain height mismatch at " + x + "," + z + " seed=" + seed);
                    compared++;
                }
                totalVanillaNanos += System.nanoTime() - started;
                // 角柱还对照原版完整地层列，确保共享采样未把薄壳当作稳固地基。
                for (int x : new int[]{area[0], area[0] + 4}) for (int z : new int[]{area[1], area[1] + 4}) {
                    var column = generator.getBaseColumn(x, z, end, state);
                    int top = terrain.height(x, z);
                    boolean supported = column.getBlock(top).is(Blocks.END_STONE) && column.getBlock(top - 1).is(Blocks.END_STONE)
                            && column.getBlock(top - 2).is(Blocks.END_STONE);
                    helper.assertTrue(terrain.supports(x, z) == supported, "Terrain footing mismatch at " + x + "," + z + " seed=" + seed);
                }
            }
        }
        LogUtils.getLogger().info("Prismatic terrain parity verified columns={} seeds=3 regions=4 batchMs={} vanillaHeightMs={}",
                compared, totalBatchNanos / 1_000_000.0, totalVanillaNanos / 1_000_000.0);
        helper.succeed();
    }

    // 使用服务器真实末地来源及注册表编解码，不以手造固定群系来源替代自然采样。
    @GameTest(template = "prismatic_empty", timeoutTicks = 200)
    public static void endBiomeSourceSurvivesCodecAndPreservesVanillaRegions(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "The End must be available in the test server");
        var source = end.getChunkSource().getGenerator().getBiomeSource();
        helper.assertTrue(source instanceof TheEndBiomeSource, "Test must use a real TheEndBiomeSource");
        helper.assertTrue(source.possibleBiomes().stream().anyMatch(b -> b.is(PrismaticBiomeRouting.WASTES)), "Possible biomes omitted prismatic wastes");
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, end.registryAccess());
        JsonElement encoded = TheEndBiomeSource.CODEC.codec().encodeStart(ops, (TheEndBiomeSource) source).result().orElseThrow();
        TheEndBiomeSource restored = TheEndBiomeSource.CODEC.codec().parse(ops, encoded).result().orElseThrow();
        var sampler = end.getChunkSource().randomState().sampler();
        helper.assertTrue(restored.getNoiseBiome(0, 16, 0, sampler).is(Biomes.THE_END), "Central island biome was replaced");
        boolean foundWastes = false;
        boolean foundVanillaLand = false;
        boolean foundIslands = false;
        // 四象限均覆盖，负坐标跨格点边界取样与编解码前完全一致。
        for (int x = -2048; x <= 2048; x += 64) for (int z = -2048; z <= 2048; z += 64) {
            var before = source.getNoiseBiome(x, 16, z, sampler);
            var after = restored.getNoiseBiome(x, 16, z, sampler);
            helper.assertTrue(before.equals(after), "End biome changed after codec round trip at " + x + ", " + z);
            foundWastes |= after.is(PrismaticBiomeRouting.WASTES);
            foundVanillaLand |= after.is(Biomes.END_HIGHLANDS) || after.is(Biomes.END_MIDLANDS);
            foundIslands |= after.is(Biomes.SMALL_END_ISLANDS);
        }
        helper.assertTrue(foundWastes && foundVanillaLand && foundIslands, "Custom land, vanilla land and void islands must coexist");
        helper.succeed();
    }

    // 四个结构必须通过正式编解码，并在各自自然布置网格上找到合格的真实末地地形。
    @GameTest(template = "prismatic_empty", timeoutTicks = 600)
    public static void registeredStructuresHaveNaturalGenerationCandidates(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "The End must be available for natural structure validation");
        var generator = end.getChunkSource().getGenerator();
        var state = end.getChunkSource().randomState();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, end.registryAccess());
        String[] names = {"waystation", "polishing_works", "broken_observatory", "prism_court"};
        for (String name : names) {
            var id = ResourceLocation.fromNamespaceAndPath("hp_end_expansion", "prismatic_" + name);
            Structure structure = end.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, id)).value();
            helper.assertTrue(structure.type() == PrismaticWorldgen.STRUCTURE.get(), "Wrong registered structure type: " + name);
            var encoded = Structure.DIRECT_CODEC.encodeStart(ops, structure).result().orElseThrow();
            Structure restored = Structure.DIRECT_CODEC.parse(ops, encoded).result().orElseThrow();
            var setId = ResourceLocation.fromNamespaceAndPath("hp_end_expansion", "prismatic_ruins");
            var structureSet = end.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, setId)).value();
            helper.assertTrue(structureSet.structures().stream().anyMatch(entry -> entry.structure().is(ResourceKey.create(Registries.STRUCTURE, id))),
                    "Structure is absent from its natural placement set: " + name);
            helper.assertTrue(structureSet.placement() instanceof RandomSpreadStructurePlacement, "Structure requires real natural placement: " + name);
            RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) structureSet.placement();
            StructureStart found = null;
            long searchStarted = System.nanoTime();
            long worstGenerationNanos = 0L;
            int terrainChecks = 0;
            // 限制最多二百五十六个候选，只检查真实候选的噪声；不预生成数千个远区块。
            for (int candidate = 0; candidate < 256 && found == null; candidate++) {
                int regionX = (candidate % 16 - 8) * 3;
                int regionZ = (candidate / 16 - 8) * 3;
                ChunkPos chunk = placement.getPotentialStructureChunk(end.getSeed(), regionX * placement.spacing(), regionZ * placement.spacing());
                var biome = generator.getBiomeSource().getNoiseBiome(chunk.getMiddleBlockX() >> 2, 16, chunk.getMiddleBlockZ() >> 2, state.sampler());
                if (!biome.is(PrismaticBiomeRouting.WASTES)) continue;
                long generationStarted = System.nanoTime();
                StructureStart start = restored.generate(end.registryAccess(), generator, generator.getBiomeSource(), state,
                        end.getStructureManager(), end.getSeed(), chunk, 0, end, b -> b.is(PrismaticBiomeRouting.WASTES));
                worstGenerationNanos = Math.max(worstGenerationNanos, System.nanoTime() - generationStarted);
                terrainChecks++;
                if (start.isValid()) found = start;
            }
            helper.assertTrue(found != null, "No valid natural structure candidate in the bounded search: " + name);
            LogUtils.getLogger().info("Prismatic natural structure candidate {} at {}; terrainChecks={}, searchMs={}, worstGenerationMs={}",
                    name, found.getBoundingBox().getCenter(), terrainChecks, (System.nanoTime() - searchStarted) / 1_000_000.0, worstGenerationNanos / 1_000_000.0);
        }
        helper.succeed();
    }

    // 走原版定位与完整区块生成流程，确认统一结构集实际选中了全部四种布局。
    @GameTest(template = "prismatic_empty", timeoutTicks = 1200)
    public static void naturalLocateGeneratesAllFourLayoutsAndCourtAltar(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "The End must be available for actual natural placement");
        var generator = end.getChunkSource().getGenerator();
        helper.assertTrue(end.getServer().getWorldData().worldGenOptions().generateStructures(), "GameTest environment must enable real structure generation");
        helper.assertTrue(generator instanceof NoiseBasedChunkGenerator noise && noise.generatorSettings().is(NoiseGeneratorSettings.END),
                "Natural structure validation must use the actual vanilla End noise settings");
        var setKey = ResourceKey.create(Registries.STRUCTURE_SET, ResourceLocation.fromNamespaceAndPath("hp_end_expansion", "prismatic_ruins"));
        helper.assertTrue(end.getChunkSource().getGeneratorState().possibleStructureSets().stream().anyMatch(set -> set.is(setKey)),
                "Real End generator omitted the prismatic structure set");
        String[] names = {"waystation", "polishing_works", "broken_observatory", "prism_court"};
        // 搜索半径限定十二个结构网格环；原点远离中央岛，避免空查核心区域。
        for (int layout = 0; layout < names.length; layout++) {
            String name = names[layout];
            var key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("hp_end_expansion", "prismatic_" + name));
            var holder = end.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(key);
            long searchStarted = System.nanoTime();
            var located = generator.findNearestMapStructure(end, HolderSet.direct(holder), new BlockPos(4096, 64, 4096), 12, false);
            long locateNanos = System.nanoTime() - searchStarted;
            helper.assertTrue(located != null, "Bounded native locate found no naturally selected structure: " + name);
            // 定位结果来自实际结构起点；加载完整占地后再检查方块和结构管理器，而不是手工放置。
            ChunkPos startChunk = new ChunkPos(located.getFirst());
            var start = end.getChunk(startChunk.x, startChunk.z).getStartForStructure(holder.value());
            helper.assertTrue(start != null && start.isValid(), "Located structure has no actual chunk start: " + name);
            BoundingBox box = start.getBoundingBox();
            int loadedChunks = 0;
            for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                end.getChunk(x, z);
                loadedChunks++;
            }
            int floorY = box.minY() + 8;
            int centerX = box.minX() + PrismaticStructurePiece.width(layout) / 2;
            int centerZ = box.minZ() + PrismaticStructurePiece.depth(layout) / 2;
            BlockPos center = new BlockPos(centerX, floorY + 1, centerZ);
            helper.assertTrue(end.structureManager().getStructureWithPieceAt(center, h -> h.is(key)).isValid(), "Structure manager cannot identify generated layout: " + name);
            BlockPos chest = new BlockPos(box.minX() + (layout == 0 ? 9 : 3), floorY + 1,
                    box.minZ() + (layout == 0 ? 7 : PrismaticStructurePiece.depth(layout) / 2));
            helper.assertTrue(end.getBlockState(chest).is(Blocks.CHEST), "Natural structure loot chest is absent: " + name);
            if (layout == 3) {
                helper.assertTrue(end.getBlockState(center).is(PrismaticContent.block("regent_altar")), "Natural court altar is absent");
                LogUtils.getLogger().info("Prismatic natural court altar context dimension=minecraft:the_end altar={} bossSpawn={}", center, center.offset(0, 1, 4));
                PrismaticAltarChecks.verify(helper, end, center);
            }
            LogUtils.getLogger().info("Prismatic native locate verified layout={} start={} box={} loadedChunks={} locateMs={} totalMs={}",
                    name, located.getFirst(), box, loadedChunks, locateNanos / 1_000_000.0, (System.nanoTime() - searchStarted) / 1_000_000.0);
        }
        helper.succeed();
    }

    // 路站分块生成后检查栽培槽与奖励保存。
    @GameTest(template = "prismatic_empty")
    public static void waystationPlacementPersists(GameTestHelper helper) { verifyPiece(helper, 0); }

    // 工坊检查真实驻守精英、箱子与重复放置防护。
    @GameTest(template = "prismatic_empty")
    public static void polishingWorksPlacementPersists(GameTestHelper helper) { verifyPiece(helper, 1); }

    // 观测所检查第二种精英及其独立结构片段保存。
    @GameTest(template = "prismatic_empty")
    public static void observatoryPlacementPersists(GameTestHelper helper) { verifyPiece(helper, 2); }

    // 庭院额外检查祭台与出生位置的完整地板和四柱。
    @GameTest(template = "prismatic_empty")
    public static void prismCourtPlacementPersists(GameTestHelper helper) { verifyPiece(helper, 3); }

    // 共用真实逐区块放置与反序列化流程，禁止以只看注册数量替代实际结构测试。
    private static void verifyPiece(GameTestHelper helper, int layout) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 5));
        PrismaticStructurePiece piece = new PrismaticStructurePiece(layout, origin.getX(), origin.getY(), origin.getZ(), 0, 0);
        BoundingBox box = piece.getBoundingBox();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
            piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.random,
                    new BoundingBox(x * 16, level.getMinBuildHeight(), z * 16, x * 16 + 15, level.getMaxBuildHeight() - 1, z * 16 + 15),
                    new ChunkPos(x, z), origin);
        }
        var context = StructurePieceSerializationContext.fromLevel(level);
        var tag = piece.createTag(context);
        helper.assertTrue(tag.getBoolean("ChestPlaced"), "Structure did not place its loot chest: " + layout);
        if (layout == 1 || layout == 2) helper.assertTrue(tag.getBoolean("ElitePlaced"), "Structure did not persist its elite: " + layout);
        // 通过正式片段注册器重载，再重复所有区块，检查玩家改动不会被覆盖。
        var restored = PrismaticWorldgen.RUIN_PIECE.get().load(context, tag);
        BlockPos edit = origin.offset(2, 0, 2);
        level.setBlock(edit, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
        for (int x = box.maxX() >> 4; x >= box.minX() >> 4; x--) for (int z = box.maxZ() >> 4; z >= box.minZ() >> 4; z--) {
            restored.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.random,
                    new BoundingBox(x * 16, level.getMinBuildHeight(), z * 16, x * 16 + 15, level.getMaxBuildHeight() - 1, z * 16 + 15),
                    new ChunkPos(x, z), origin);
        }
        helper.assertTrue(level.getBlockState(edit).is(Blocks.DIAMOND_BLOCK), "Reloaded structure overwrote a completed chunk");
        if (layout == 1 || layout == 2) {
            var elite = PrismaticEntities.type(layout == 1 ? "fault_warden" : "mirror_huntress");
            AABB area = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
            helper.assertTrue(level.getEntitiesOfClass(Mob.class, area, mob -> mob.getType() == elite).size() == 1, "Structure duplicated or lost its elite");
        }
        if (layout == 3) {
            helper.assertTrue(level.getBlockState(origin.offset(16, 1, 16)).is(PrismaticContent.block("regent_altar")), "Court altar is missing");
            for (int x = 13; x <= 19; x++) for (int z = 17; z <= 23; z++) {
                helper.assertTrue(!level.getBlockState(origin.offset(x, 0, z)).isAir(), "Boss spawn floor is missing");
                for (int y = 1; y <= 5; y++) helper.assertTrue(level.getBlockState(origin.offset(x, y, z)).isAir(), "Boss spawn area is obstructed");
            }
            for (int x : new int[]{6, 26}) for (int z : new int[]{6, 26}) {
                helper.assertTrue(level.getBlockState(origin.offset(x, 4, z)).is(PrismaticContent.block("chiseled_chalkstone")), "Court cover pillar is missing");
            }
        }
        helper.succeed();
    }
}
