package com.hp_end_expansion.content.prismatic.test;

import com.hp_end_expansion.content.prismatic.PrismaticContent;
import com.hp_end_expansion.content.prismatic.block.PrismaticPlantBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("hp_end_expansion")
@PrefixGameTestTemplate(false)
public class PrismaticPlantTests {
    // 检查全部六种成熟植株经真实右键流程只产一次，并保留可继续生长的植株。
    @GameTest(template = "prismatic_empty")
    public static void harvestPreservesPlantAndPreventsDuplicateDrops(GameTestHelper helper) {
        String[] plants = {"prism_grass", "mica_reed", "lantern_bloom", "glass_fern", "shard_cactus", "dusk_bloom"};
        String[] produce = {"crystal_bud", "mica_sheet", "lumen_dust", "crystal_shard", "prism_needle", "dusk_bud"};
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < plants.length; i++) {
            BlockPos pos = new BlockPos(4 + i * 5, 2, 5);
            helper.setBlock(pos.below(), PrismaticContent.block("prism_soil"));
            helper.setBlock(pos, PrismaticContent.block(plants[i]).defaultBlockState().setValue(PrismaticPlantBlock.AGE, 3));
            helper.useBlock(pos, player);
            helper.assertBlockProperty(pos, PrismaticPlantBlock.AGE, 0);
            final var item = PrismaticContent.item(produce[i]);
            var area = new AABB(helper.absolutePos(pos)).inflate(1);
            int first = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(first >= 1 && first <= 2, "Mature harvest must produce one or two items: " + plants[i]);
            helper.useBlock(pos, player);
            int second = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(first == second, "Immature repeated interaction duplicated produce: " + plants[i]);
        }
        helper.succeed();
    }

    // 验证真实物品使用只消耗一份肥料，普通石头不会被错误施肥或吞物品。
    @GameTest(template = "prismatic_empty")
    public static void prismMealConsumesOnlyOnValidPlant(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos.below(), PrismaticContent.block("prism_soil"));
        helper.setBlock(pos, PrismaticContent.block("prism_grass"));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack meal = new ItemStack(PrismaticContent.item("prism_meal"), 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, meal);
        helper.useBlock(pos, player);
        helper.assertBlockProperty(pos, PrismaticPlantBlock.AGE, 1);
        helper.assertTrue(meal.getCount() == 3, "Valid fertilization must consume exactly one meal");
        helper.setBlock(pos, Blocks.STONE);
        helper.useBlock(pos, player);
        helper.assertTrue(meal.getCount() == 3, "Invalid target must retain meal");
        helper.succeed();
    }

    // 树苗通过注册表中正式树特征成长，检查主干和扁平冠层真实出现。
    @GameTest(template = "prismatic_empty", timeoutTicks = 100)
    public static void saplingGrowsRegisteredPrismTree(GameTestHelper helper) {
        BlockPos pos = new BlockPos(12, 2, 12);
        helper.setBlock(pos.below(), PrismaticContent.block("prism_soil"));
        helper.setBlock(pos, PrismaticContent.block("prism_sapling"));
        var level = helper.getLevel();
        var absolute = helper.absolutePos(pos);
        var sapling = (BonemealableBlock) PrismaticContent.block("prism_sapling");
        for (int attempt = 0; attempt < 3 && level.getBlockState(absolute).is(PrismaticContent.block("prism_sapling")); attempt++) {
            sapling.performBonemeal(level, level.random, absolute, level.getBlockState(absolute));
        }
        helper.assertBlockPresent(PrismaticContent.block("prism_log"), pos);
        long leaves = BlockPos.betweenClosedStream(absolute.offset(-4, 1, -4), absolute.offset(4, 10, 4))
                .filter(p -> level.getBlockState(p).is(PrismaticContent.block("prism_leaves"))).count();
        helper.assertTrue(leaves >= 10, "Registered prism tree must contain a real leaf canopy");
        helper.succeed();
    }

    // 不在末地正式庭院中的祭台必须拒绝召唤，并保持钥匙数量不变。
    @GameTest(template = "prismatic_empty")
    public static void misplacedAltarDoesNotConsumeKey(GameTestHelper helper) {
        BlockPos pos = new BlockPos(6, 2, 6);
        helper.setBlock(pos, PrismaticContent.block("regent_altar"));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack key = new ItemStack(PrismaticContent.item("prism_key"), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, key);
        helper.useBlock(pos, player);
        helper.assertTrue(key.getCount() == 2, "Invalid altar consumed a summoning key");
        helper.succeed();
    }
}
