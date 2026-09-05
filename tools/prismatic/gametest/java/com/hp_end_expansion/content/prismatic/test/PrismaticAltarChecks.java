package com.hp_end_expansion.content.prismatic.test;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.content.prismatic.PrismaticContent;
import com.hp_end_expansion.content.prismatic.entity.ParallaxRegent;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 由真实末地自然庭院测试调用，不单独生成手工结构冒充合法祭台。
public final class PrismaticAltarChecks {
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("fb486ea6-63b2-4c61-86ac-116c27273b30"), "PrismaticTest");
    private static final ResourceKey<Structure> COURT = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "prismatic_prism_court"));

    private PrismaticAltarChecks() { }

    // 所有操作在同一个服务端 tick 内完成，异常时也恢复难度、临时遮挡和测试玩家。
    public static void verify(GameTestHelper helper, ServerLevel end, BlockPos altar) {
        helper.assertTrue(end.dimension() == Level.END, "Altar validation must run in the real End");
        helper.assertTrue(end.getBlockState(altar).is(PrismaticContent.block("regent_altar")), "Natural court must contain its actual altar");
        helper.assertTrue(end.structureManager().getStructureWithPieceAt(altar, holder -> holder.is(COURT)).isValid(), "Altar must belong to a registered natural court");
        helper.assertTrue(!end.getServer().getWorldData().isHardcore(), "Peaceful rejection requires a non-hardcore test world");
        AABB area = new AABB(altar).inflate(96);
        helper.assertTrue(bosses(end, area).isEmpty(), "Natural altar must be unoccupied before ritual checks");

        FakePlayer player = FakePlayerFactory.get(end, PROFILE);
        Difficulty originalDifficulty = end.getDifficulty();
        Difficulty activeDifficulty = originalDifficulty == Difficulty.PEACEFUL ? Difficulty.NORMAL : originalDifficulty;
        GameType originalMode = player.gameMode.getGameModeForPlayer();
        Vec3 originalPosition = player.position();
        float originalYaw = player.getYRot();
        float originalPitch = player.getXRot();
        boolean originalCrouch = player.isShiftKeyDown();
        ItemStack originalMainHand = player.getMainHandItem().copy();
        ItemStack originalOffHand = player.getOffhandItem().copy();
        BlockPos spawn = altar.offset(0, 1, 4);
        BlockPos obstruction = spawn.above();
        BlockState originalBlock = end.getBlockState(obstruction);
        helper.assertTrue(end.getBlockEntity(obstruction) == null, "Ritual spawn clearance must not contain a block entity");
        ParallaxRegent summoned = null;
        try {
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            player.setShiftKeyDown(false);
            player.moveTo(altar.getX() + 0.5, altar.getY() + 1, altar.getZ() - 1.5, 0, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PrismaticContent.item("prism_key"), 3));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertTrue(player.level() == end && !player.isCreative() && !player.getAbilities().instabuild,
                    "FakePlayer must use the End and survival item consumption rules");

            // 和平拒绝在没有首领时验证，避免误走重复占用分支。
            end.getServer().setDifficulty(Difficulty.PEACEFUL, true);
            helper.assertTrue(end.getDifficulty() == Difficulty.PEACEFUL, "Test must actually activate peaceful difficulty");
            useAltar(player, end, altar);
            helper.assertTrue(player.getMainHandItem().getCount() == 3 && bosses(end, area).isEmpty(), "Peaceful ritual must create no boss and retain all keys");
            end.getServer().setDifficulty(activeDifficulty, true);

            // 用出生碰撞体中间的一块黑曜石验证空间不足不会吞钥匙。
            end.setBlock(obstruction, Blocks.OBSIDIAN.defaultBlockState(), 3);
            useAltar(player, end, altar);
            helper.assertTrue(player.getMainHandItem().getCount() == 3 && bosses(end, area).isEmpty(), "Obstructed ritual must create no boss and retain all keys");
            end.setBlock(obstruction, originalBlock, 3);

            // 走真实服务端右键事件路径，检查成功只消费一把钥匙并创建一个首领。
            InteractionResult success = useAltar(player, end, altar);
            List<ParallaxRegent> created = bosses(end, area);
            helper.assertTrue(success.consumesAction() && created.size() == 1, "Valid natural court ritual must create exactly one boss");
            summoned = created.getFirst();
            helper.assertTrue(player.getMainHandItem().getCount() == 2, "Successful ritual must consume exactly one key");
            helper.assertTrue(summoned.level() == end && summoned.isPersistenceRequired(), "Ritual boss must persist in the End");
            helper.assertTrue(summoned.nest().equals(spawn), "Ritual boss nest must match its final court spawn position");
            helper.assertTrue(end.structureManager().getStructureWithPieceAt(summoned.nest(), holder -> holder.is(COURT)).isValid(), "Ritual boss nest must remain inside the actual court");
            CompoundTag saved = summoned.saveWithoutId(new CompoundTag());
            helper.assertTrue(saved.getLong("PrismaticNest") == spawn.asLong(), "Ritual nest must be persisted in boss NBT");
            UUID firstBoss = summoned.getUUID();

            // 再次右键必须保留原首领和剩余钥匙，不能复制或替换现有实体。
            useAltar(player, end, altar);
            List<ParallaxRegent> repeated = bosses(end, area);
            helper.assertTrue(repeated.size() == 1 && repeated.getFirst().getUUID().equals(firstBoss), "Repeated ritual must retain the same single boss");
            helper.assertTrue(player.getMainHandItem().getCount() == 2, "Repeated occupied ritual must not consume another key");
            HpEndExpansion.LOGGER.info("Prismatic natural altar verified: altar={}, nest={}, boss={}, keys=3->2, peaceful=retained, obstruction=retained, repeated=retained",
                    altar, summoned.nest(), firstBoss);
        } finally {
            // 入口已证明范围内没有首领，同刻新增者全部来自本次交互；失败时也清理复制错误。
            for (ParallaxRegent created : bosses(end, area)) { created.discard(); }
            end.setBlock(obstruction, originalBlock, 3);
            end.getServer().setDifficulty(originalDifficulty, true);
            player.setItemInHand(InteractionHand.MAIN_HAND, originalMainHand);
            player.setItemInHand(InteractionHand.OFF_HAND, originalOffHand);
            player.setShiftKeyDown(originalCrouch);
            player.gameMode.changeGameModeForPlayer(originalMode);
            player.moveTo(originalPosition.x, originalPosition.y, originalPosition.z, originalYaw, originalPitch);
        }
    }

    // FakePlayer 与祭台处于同一维度，交互仍经过 NeoForge 右键事件和物品消耗流程。
    private static InteractionResult useAltar(FakePlayer player, ServerLevel end, BlockPos altar) {
        BlockHitResult hit = new BlockHitResult(altar.getCenter().add(0, 0.5, 0), Direction.UP, altar, false);
        return player.gameMode.useItemOn(player, end, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static List<ParallaxRegent> bosses(ServerLevel end, AABB area) {
        return end.getEntitiesOfClass(ParallaxRegent.class, area, ParallaxRegent::isAlive);
    }
}
