package org.hp.hp_end_expansion.block.tidelight;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.hp.hp_end_expansion.registry.ModTidelight;

public final class HangingGlowkelpBlock extends GrowingPlantHeadBlock {
    public static final BooleanProperty POD = BooleanProperty.create("pod");
    public static final MapCodec<HangingGlowkelpBlock> CODEC = simpleCodec(HangingGlowkelpBlock::new);
    public HangingGlowkelpBlock(Properties properties) {
        super(properties, Direction.DOWN, Block.box(2, 0, 2, 14, 16, 14), false, 0.08);
        registerDefaultState(defaultBlockState().setValue(AGE, 0).setValue(POD, false));
    }
    @Override protected MapCodec<? extends HangingGlowkelpBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(POD); }
    public static boolean isVine(BlockState state) { return state.is(ModTidelight.HANGING_GLOWKELP.get()) || state.is(ModTidelight.HANGING_GLOWKELP_PLANT.get()); }
    public static boolean support(BlockState state) { return isVine(state) || ReefstoneBlock.isReef(state) || state.is(Blocks.END_STONE) || state.is(ModTidelight.REEF_BONE_BLOCK.get()) || state.is(ModTidelight.LUMEN_CORAL_BLOCK.get()); }
    public static int lengthAbove(LevelReader level, BlockPos pos) {
        int length = 1;
        while (length < 13 && isVine(level.getBlockState(pos.above(length)))) length++;
        return length;
    }
    @Override protected boolean canAttachTo(BlockState state) { return support(state); }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return lengthAbove(level, pos) <= 12 && super.canSurvive(state, level, pos); }
    @Override protected Block getBodyBlock() { return ModTidelight.HANGING_GLOWKELP_PLANT.get(); }
    @Override protected boolean canGrowInto(BlockState state) { return state.isAir(); }
    @Override protected int getBlocksToGrowWhenBonemealed(RandomSource random) { return 1; }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(POD) && lengthAbove(level, pos) < 12) super.randomTick(state, level, pos, random);
    }
    @Override protected BlockState getGrowIntoState(BlockState state, RandomSource random) { return super.getGrowIntoState(state, random).setValue(POD, false); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(POD)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            popResource(level, pos, new ItemStack(ModTidelight.GLOWKELP_POD.get()));
            level.setBlockAndUpdate(pos, state.setValue(POD, false));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return !state.getValue(POD) || lengthAbove(level, pos) < 12 && level.getBlockState(pos.below()).isAir();
    }
    @Override public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (!state.getValue(POD)) level.setBlockAndUpdate(pos, state.setValue(POD, true));
        else if (lengthAbove(level, pos) < 12 && level.isEmptyBlock(pos.below())) {
            level.setBlockAndUpdate(pos.below(), defaultBlockState().setValue(POD, true));
        }
    }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) { return new ItemStack(ModTidelight.GLOWKELP_POD.get()); }
}
