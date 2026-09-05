package com.hp_end_expansion.content.prismatic.block;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.content.prismatic.PrismaticContent;
import com.hp_end_expansion.content.prismatic.PrismaticEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

public class RegentAltarBlock extends Block {
    // 祭台只在正式庭院结构中允许仪式，搬运方块不能绕过探索阶段。
    public static final MapCodec<RegentAltarBlock> CODEC = simpleCodec(RegentAltarBlock::new);
    private static final ResourceKey<Structure> COURT = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(HpEndExpansion.MODID, "prismatic_prism_court"));

    // 祭台不使用逐刻方块实体，存活首领本身记录仪式结果与巢点。
    public RegentAltarBlock(Properties properties) { super(properties); }

    // 注册对应类型的属性编解码器。
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    // 空手检查提供双印钥匙的游戏内提示。
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.hp_end_expansion.prismatic_altar_hint"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // 仪式仅由服务端执行，所有失败分支保持钥匙不变。
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(PrismaticContent.item("prism_key"))) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level instanceof ServerLevel server)) return ItemInteractionResult.SUCCESS;
        // 和平难度会立即清除怪物，因此仪式提前拒绝且不消耗钥匙。
        if (server.getDifficulty() == Difficulty.PEACEFUL) {
            player.displayClientMessage(Component.translatable("message.hp_end_expansion.prismatic_altar_peaceful"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (server.dimension() != Level.END || !server.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(COURT)).isValid()) {
            player.displayClientMessage(Component.translatable("message.hp_end_expansion.prismatic_altar_outside_court"), true);
            return ItemInteractionResult.CONSUME;
        }
        // 限制同庭院附近只有一个存活首领，重复点击和多人点击都不会复制。
        var bossType = PrismaticEntities.type("parallax_regent");
        if (!server.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(96), entity -> entity.isAlive() && entity.getType() == bossType).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.hp_end_expansion.prismatic_altar_occupied"), true);
            return ItemInteractionResult.CONSUME;
        }
        var entity = bossType.create(server);
        if (!(entity instanceof Mob boss)) return ItemInteractionResult.FAIL;
        boss.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 4.5, 180F, 0F);
        // 完整碰撞检查避免在被玩家封闭的祭台内召唤窒息首领。
        if (!server.noCollision(boss) || !server.getWorldBorder().isWithinBounds(boss.getBoundingBox())) {
            player.displayClientMessage(Component.translatable("message.hp_end_expansion.prismatic_altar_obstructed"), true);
            return ItemInteractionResult.CONSUME;
        }
        boss.finalizeSpawn(server, server.getCurrentDifficultyAt(boss.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
        boss.setPersistenceRequired();
        if (server.addFreshEntity(boss)) {
            stack.consume(1, player);
            boss.setTarget(player);
            HpEndExpansion.LOGGER.debug("Prismatic regent summoned at {} by {}", pos, player.getUUID());
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.FAIL;
    }
}
