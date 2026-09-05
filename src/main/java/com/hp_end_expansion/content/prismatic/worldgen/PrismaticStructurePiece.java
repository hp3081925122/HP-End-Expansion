package com.hp_end_expansion.content.prismatic.worldgen;

import com.hp_end_expansion.content.prismatic.PrismaticContent;
import com.hp_end_expansion.content.prismatic.PrismaticEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class PrismaticStructurePiece extends StructurePiece {
    private static final int FLOOR = 8;
    private static final String[] IDS = {"waystation", "polishing_works", "broken_observatory", "prism_court"};
    private final int layout;
    private final int northRise;
    private final int southRise;
    private boolean chestPlaced;
    private boolean elitePlaced;
    private long completedChunks;

    // 包围盒包含八格地基及十一格上部净空，祭台属于真实结构片段范围。
    public PrismaticStructurePiece(int layout, int x, int floorY, int z, int northRise, int southRise) {
        super(PrismaticWorldgen.RUIN_PIECE.get(), 0,
                new BoundingBox(x, floorY - FLOOR, z, x + width(layout) - 1, floorY + 11, z + depth(layout) - 1));
        this.layout = layout;
        this.northRise = northRise;
        this.southRise = southRise;
        setOrientation(Direction.SOUTH);
    }

    // 原版区块反序列化恢复布局、单次奖励和已经放置过的区块位图。
    public PrismaticStructurePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(PrismaticWorldgen.RUIN_PIECE.get(), tag);
        layout = Math.clamp(tag.getInt("Layout"), 0, 3);
        northRise = Math.clamp(tag.getInt("NorthRise"), 0, 8);
        southRise = Math.clamp(tag.getInt("SouthRise"), 0, 8);
        chestPlaced = tag.getBoolean("ChestPlaced");
        elitePlaced = tag.getBoolean("ElitePlaced");
        completedChunks = tag.getLong("CompletedChunks");
    }

    // 公共尺寸用于生成点预检和片段布局，避免两处占地不一致。
    public static int width(int layout) { return switch (layout) { case 0 -> 13; case 1 -> 25; case 2 -> 23; default -> 33; }; }
    public static int depth(int layout) { return switch (layout) { case 0 -> 11; case 1 -> 23; case 2 -> 23; default -> 33; }; }

    // 与区块生成同步保存，重载不会补箱、复制精英或清掉玩家后续放置的方块。
    @Override protected synchronized void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("Layout", layout);
        tag.putInt("NorthRise", northRise);
        tag.putInt("SouthRise", southRise);
        tag.putBoolean("ChestPlaced", chestPlaced);
        tag.putBoolean("ElitePlaced", elitePlaced);
        tag.putLong("CompletedChunks", completedChunks);
    }

    // 单个片段可能由多个区块分别生成；同步锁和区块位图保证奖励只结算一次。
    @Override public synchronized void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
            RandomSource random, BoundingBox chunkBounds, ChunkPos chunkPos, BlockPos pivot) {
        int chunksWide = (boundingBox.maxX() >> 4) - (boundingBox.minX() >> 4) + 1;
        int index = (chunkPos.x - (boundingBox.minX() >> 4)) + (chunkPos.z - (boundingBox.minZ() >> 4)) * chunksWide;
        if (index < 0 || index >= 63 || (completedChunks & (1L << index)) != 0) return;
        int width = width(layout);
        int depth = depth(layout);
        BlockState bricks = state("chalkstone_bricks");
        // 完整实心地基最大八格；所有写入裁切到正在生成的区块包围盒。
        fill(level, chunkBounds, 0, 0, 0, width - 1, FLOOR - 1, depth - 1, state("chalkstone"));
        fill(level, chunkBounds, 0, FLOOR, 0, width - 1, FLOOR, depth - 1, bricks);
        fill(level, chunkBounds, 0, FLOOR + 1, 0, width - 1, FLOOR + 10, depth - 1, Blocks.AIR.defaultBlockState());
        // 地面少量裂砖和凿纹标线保持建筑的风化感，同时不留下绊脚坑。
        for (int x = 1; x < width - 1; x++) {
            for (int z = 1; z < depth - 1; z++) {
                if ((x * 13 + z * 7) % 23 == 0) placeBlock(level, state("cracked_bricks"), x, FLOOR, z, chunkBounds);
                else if (x == width / 2 || z == depth / 2) placeBlock(level, state("chiseled_chalkstone"), x, FLOOR, z, chunkBounds);
            }
        }
        // 每类建筑保留独立轮廓和内部教学空间。
        switch (layout) {
            case 0 -> waystation(level, chunkBounds);
            case 1 -> polishingWorks(level, chunkBounds);
            case 2 -> observatory(level, chunkBounds);
            default -> court(level, chunkBounds);
        }
        // 两侧入口按当前区块地面高度修出内嵌台阶，不让高地基阻断徒步进入。
        entrances(level, chunkBounds, width, depth);
        int chestX = layout == 0 ? 9 : 3;
        int chestZ = layout == 0 ? 7 : depth / 2;
        BlockPos chestPos = getWorldPos(chestX, FLOOR + 1, chestZ);
        if (!chestPlaced && chunkBounds.isInside(chestPos)) {
            chestPlaced = createChest(level, chunkBounds, random, chestX, FLOOR + 1, chestZ,
                    ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("hp_end_expansion", "chests/prismatic_" + IDS[layout])));
        }
        // 两种精英仅由各自设施生成一个，Boss 留给祭台仪式，不在这里自然重复生成。
        if (!elitePlaced && (layout == 1 || layout == 2)) {
            BlockPos spawn = getWorldPos(width / 2, FLOOR + 1, depth / 2);
            if (chunkBounds.isInside(spawn)) {
                Entity entity = PrismaticEntities.type(layout == 1 ? "fault_warden" : "mirror_huntress").create(level.getLevel());
                if (entity instanceof Mob mob) {
                    mob.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0F, 0F);
                    mob.setPersistenceRequired();
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.STRUCTURE, null);
                    level.addFreshEntityWithPassengers(mob);
                    elitePlaced = true;
                }
            }
        }
        completedChunks |= 1L << index;
    }

    // 开放雨棚、栽培槽和定线标展示可采收植株，不依赖额外文字牌实体。
    private void waystation(WorldGenLevel level, BoundingBox bounds) {
        for (int x : new int[]{3, 10}) for (int z : new int[]{2, 8}) {
            fill(level, bounds, x, FLOOR + 1, z, x, FLOOR + 4, z, state("prism_log"));
        }
        fill(level, bounds, 2, FLOOR + 5, 1, 11, FLOOR + 5, 9, state("prism_slab"));
        fill(level, bounds, 1, FLOOR, 3, 2, FLOOR, 7, state("prism_soil"));
        for (int z = 3; z <= 7; z++) {
            placeBlock(level, state(z % 2 == 0 ? "lantern_bloom" : "prism_grass").setValue(BlockStateProperties.AGE_3, 3), 1, FLOOR + 1, z, bounds);
        }
        placeBlock(level, state("survey_marker"), 6, FLOOR + 1, 2, bounds);
        placeBlock(level, Blocks.CRAFTING_TABLE.defaultBlockState(), 8, FLOOR + 1, 7, bounds);
        placeBlock(level, state("lumen_block"), 6, FLOOR + 4, 5, bounds);
    }

    // 工坊有宽车间、四根冲锋掩体、侧门和后方储料棚，中央保留精英动作空间。
    private void polishingWorks(WorldGenLevel level, BoundingBox bounds) {
        perimeter(level, bounds, 25, 23, 3);
        for (int x : new int[]{5, 19}) for (int z : new int[]{5, 17}) {
            fill(level, bounds, x, FLOOR + 1, z, x + 1, FLOOR + 5, z + 1, state("chiseled_chalkstone"));
        }
        fill(level, bounds, 4, FLOOR + 6, 15, 20, FLOOR + 6, 20, state("prism_planks"));
        fill(level, bounds, 0, FLOOR + 1, 9, 0, FLOOR + 3, 13, Blocks.AIR.defaultBlockState());
        for (int x = 7; x <= 17; x += 5) {
            fill(level, bounds, x, FLOOR + 1, 18, x + 1, FLOOR + 2, 19, state("chalkstone"));
            placeBlock(level, state("lumen_ore"), x, FLOOR + 3, 19, bounds);
        }
        placeBlock(level, Blocks.GRINDSTONE.defaultBlockState(), 3, FLOOR + 1, 5, bounds);
        placeBlock(level, state("lumen_block"), 12, FLOOR + 5, 19, bounds);
    }

    // 观测所的残缺镜框与两层侧台提供高低差，实心墙柱承担可靠遮挡。
    private void observatory(WorldGenLevel level, BoundingBox bounds) {
        perimeter(level, bounds, 23, 23, 2);
        fill(level, bounds, 15, FLOOR + 1, 14, 19, FLOOR + 2, 19, state("chalkstone_bricks"));
        for (int z = 11; z <= 13; z++) {
            int rise = z - 10;
            fill(level, bounds, 16, FLOOR + 1, z, 18, FLOOR + Math.min(2, rise), z,
                    state("chalkstone_stairs").setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int x : new int[]{5, 17}) {
            fill(level, bounds, x, FLOOR + 1, 5, x + 1, FLOOR + 7, 6, state("chiseled_chalkstone"));
        }
        fill(level, bounds, 5, FLOOR + 8, 5, 13, FLOOR + 8, 6, state("chalkstone_bricks"));
        fill(level, bounds, 7, FLOOR + 4, 5, 9, FLOOR + 6, 5, state("dusk_glass"));
        fill(level, bounds, 13, FLOOR + 2, 5, 16, FLOOR + 4, 5, state("dusk_glass"));
        fill(level, bounds, 5, FLOOR + 1, 15, 6, FLOOR + 4, 16, state("chalkstone_bricks"));
        placeBlock(level, state("survey_marker"), 18, FLOOR + 3, 18, bounds);
        placeBlock(level, state("lumen_block"), 6, FLOOR + 6, 6, bounds);
    }

    // 三十三格无顶庭院保留四根三格宽实心柱，南侧出生区没有高台或装饰阻挡。
    private void court(WorldGenLevel level, BoundingBox bounds) {
        perimeter(level, bounds, 33, 33, 2);
        for (int x : new int[]{5, 25}) for (int z : new int[]{5, 25}) {
            fill(level, bounds, x, FLOOR + 1, z, x + 2, FLOOR + 7, z + 2, state("chiseled_chalkstone"));
            fill(level, bounds, x, FLOOR + 8, z, x + 2, FLOOR + 8, z + 2, state("chalkstone_slab"));
            placeBlock(level, state("lumen_block"), x + 1, FLOOR + 7, z + 1, bounds);
        }
        for (int i = 9; i <= 23; i++) {
            placeBlock(level, state("lumen_block"), i, FLOOR, 9, bounds);
            placeBlock(level, state("lumen_block"), i, FLOOR, 23, bounds);
            placeBlock(level, state("lumen_block"), 9, FLOOR, i, bounds);
            placeBlock(level, state("lumen_block"), 23, FLOOR, i, bounds);
        }
        placeBlock(level, state("regent_altar"), 16, FLOOR + 1, 16, bounds);
        placeBlock(level, state("survey_marker"), 12, FLOOR + 1, 2, bounds);
        placeBlock(level, state("survey_marker"), 20, FLOOR + 1, 30, bounds);
    }

    // 外围矮墙同时界定战斗边缘，南北两处留出五格宽步行通道。
    private void perimeter(WorldGenLevel level, BoundingBox bounds, int width, int depth, int height) {
        BlockState bricks = state("chalkstone_bricks");
        fill(level, bounds, 0, FLOOR + 1, 0, 0, FLOOR + height, depth - 1, bricks);
        fill(level, bounds, width - 1, FLOOR + 1, 0, width - 1, FLOOR + height, depth - 1, bricks);
        fill(level, bounds, 0, FLOOR + 1, 0, width / 2 - 3, FLOOR + height, 0, bricks);
        fill(level, bounds, width / 2 + 3, FLOOR + 1, 0, width - 1, FLOOR + height, 0, bricks);
        fill(level, bounds, 0, FLOOR + 1, depth - 1, width / 2 - 3, FLOOR + height, depth - 1, bricks);
        fill(level, bounds, width / 2 + 3, FLOOR + 1, depth - 1, width - 1, FLOOR + height, depth - 1, bricks);
    }

    // 台阶最多向内延伸八格；路站使用紧凑的两格坡道，较大建筑保留完整外缘缓冲。
    private void entrances(WorldGenLevel level, BoundingBox bounds, int width, int depth) {
        int center = width / 2;
        for (boolean north : new boolean[]{true, false}) {
            int rise = north ? northRise : southRise;
            for (int step = 0; step < rise; step++) {
                int z = north ? step : depth - 1 - step;
                int y = FLOOR - rise + step + 1;
                fill(level, bounds, center - 2, y + 1, z, center + 2, FLOOR + 3, z, Blocks.AIR.defaultBlockState());
                fill(level, bounds, center - 2, y, z, center + 2, y, z,
                        state("chalkstone_stairs").setValue(StairBlock.FACING, north ? Direction.SOUTH : Direction.NORTH));
            }
        }
    }

    // 将体积操作在循环前裁切，避免每个区块都遍历整座大型庭院。
    private void fill(WorldGenLevel level, BoundingBox bounds, int x1, int y1, int z1, int x2, int y2, int z2, BlockState block) {
        int minX = Math.max(x1, bounds.minX() - boundingBox.minX());
        int maxX = Math.min(x2, bounds.maxX() - boundingBox.minX());
        int minZ = Math.max(z1, bounds.minZ() - boundingBox.minZ());
        int maxZ = Math.min(z2, bounds.maxZ() - boundingBox.minZ());
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) for (int y = y1; y <= y2; y++) {
            placeBlock(level, block, x, y, z, bounds);
        }
    }

    // 简写只在注册完成后的实际放置阶段解析，不让注册顺序影响结构类型加载。
    private static BlockState state(String id) { return PrismaticContent.block(id).defaultBlockState(); }
}
