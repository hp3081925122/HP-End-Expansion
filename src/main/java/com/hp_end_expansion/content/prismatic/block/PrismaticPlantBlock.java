package com.hp_end_expansion.content.prismatic.block;

import com.hp_end_expansion.content.prismatic.PrismaticContent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;

public class PrismaticPlantBlock extends BushBlock implements BonemealableBlock {
    // 产物和刺伤标记随方块编解码保留，年龄直接使用原版状态属性。
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final MapCodec<PrismaticPlantBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("produce").forGetter(block -> block.produce),
            Codec.BOOL.fieldOf("thorny").forGetter(block -> block.thorny), propertiesCodec()).apply(instance, PrismaticPlantBlock::new));
    private final String produce;
    private final boolean thorny;

    // 新生植株从零龄开始，不要求天空光以适应末地环境。
    public PrismaticPlantBlock(String produce, boolean thorny, Properties properties) {
        super(properties);
        this.produce = produce;
        this.thorny = thorny;
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    // 返回真实包含植株种类数据的编解码器。
    @Override public MapCodec<? extends BushBlock> codec() { return CODEC; }

    // 仅允许本群系土壤和原版末地石，移植时仍须铺设适宜基底。
    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(PrismaticContent.block("prism_soil")) || state.is(Blocks.END_STONE);
    }

    // 命中轮廓随成熟度生长，方便玩家辨识可收获状态。
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(2, 0, 2, 14, 4 + state.getValue(AGE) * 4, 14);
    }

    // 成熟植株停用随机更新，降低自然密集植被的后台开销。
    @Override protected boolean isRandomlyTicking(BlockState state) { return state.getValue(AGE) < 3; }

    // 使用原版作物事件钩子允许整合包调整生长，且只更新当前方块。
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(AGE) < 3 && CommonHooks.canCropGrow(level, pos, state, random.nextInt(5) == 0)) {
            level.setBlock(pos, state.cycle(AGE), 2);
            CommonHooks.fireCropGrowPost(level, pos, state);
        }
    }

    // 未成熟时让肥料物品继续处理，其他右键进入统一的采收逻辑。
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(AGE) < 3 && (stack.is(Items.BONE_MEAL) || stack.is(PrismaticContent.item("prism_meal")))) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // 服务端先重置成熟度再产出，客户端只返回交互结果，避免双倍掉落。
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(AGE) < 3) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(AGE, 0), 3);
            popResource(level, pos, new ItemStack(PrismaticContent.item(produce), 1 + level.random.nextInt(2)));
            level.levelEvent(2005, pos, 0);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // 只有成熟碎棱柱带刺；原版受伤无敌帧限制接触伤害频率。
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (thorny && state.getValue(AGE) > 1 && !level.isClientSide) entity.hurt(level.damageSources().cactus(), 1F);
    }

    // 年龄在方块状态中持久化，不需要方块实体逐刻运行。
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AGE); }

    // 肥料只对未成熟植株有效，满龄交互不会消耗资源。
    @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) { return state.getValue(AGE) < 3; }

    // 肥料固定成功，使碎晶虫回收链的产量可预测。
    @Override public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) { return true; }

    // 每份肥料推进一个生长阶段，避免单份资源无限放大。
    @Override public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(AGE, Math.min(3, state.getValue(AGE) + 1)), 3);
    }
}
