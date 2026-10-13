package org.hp.hp_end_expansion.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.*;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.block.*;
import org.hp.hp_end_expansion.item.StarBowItem;

public final class ModStarwreck {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hp_end_expansion.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Hp_end_expansion.MODID);
    public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = new ArrayList<>();
    public static final DeferredBlock<StarwreckStoneBlock> STARWRECK_STONE = block("starwreck_stone", id -> new StarwreckStoneBlock(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id))));
    public static final DeferredBlock<EmberStarwreckBlock> EMBER_STARWRECK_STONE = block("ember_starwreck_stone", id -> new EmberStarwreckBlock(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 6)));
    public static final DeferredBlock<StarwreckStoneBlock> STAR_MOSS = block("star_moss", id -> new StarwreckStoneBlock(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id))));
    public static final DeferredBlock<ColoredFallingBlock> METEOR_ASH = block("meteor_ash", id -> new ColoredFallingBlock(new ColorRGBA(0xFF806E61), BlockBehaviour.Properties.ofFullCopy(Blocks.SAND).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id))));
    public static final DeferredBlock<StarwreckPlantBlock> STAR_MOSS_SPROUTS = block("star_moss_sprouts", id -> new StarwreckPlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_SPROUTS).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).replaceable()));
    public static final DeferredBlock<EmberbloomBlock> EMBERBLOOM = block("emberbloom", id -> new EmberbloomBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 4)));
    public static final DeferredBlock<FlowerPotBlock> POTTED_EMBERBLOOM = BLOCKS.register("potted_emberbloom", id -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, EMBERBLOOM, BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_POPPY).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 4)));
    public static final DeferredBlock<AmethystClusterBlock> SMALL_STAR_CRYSTAL_BUD = crystal("small_star_crystal_bud", 3, 4, 1);
    public static final DeferredBlock<AmethystClusterBlock> MEDIUM_STAR_CRYSTAL_BUD = crystal("medium_star_crystal_bud", 4, 3, 2);
    public static final DeferredBlock<AmethystClusterBlock> LARGE_STAR_CRYSTAL_BUD = crystal("large_star_crystal_bud", 5, 3, 4);
    public static final DeferredBlock<AmethystClusterBlock> STAR_CRYSTAL_CLUSTER = crystal("star_crystal_cluster", 7, 3, 5);
    public static final DeferredBlock<AmethystBlock> STAR_CRYSTAL_BLOCK = block("star_crystal_block", id -> new AmethystBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 3)));
    public static final DeferredBlock<Block> STARWRECK_BRICKS = block("starwreck_bricks", id -> new Block(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).sound(SoundType.NETHER_BRICKS)));
    public static final DeferredBlock<StairBlock> STARWRECK_BRICK_STAIRS = block("starwreck_brick_stairs", id -> new StairBlock(STARWRECK_BRICKS.get().defaultBlockState(), stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).sound(SoundType.NETHER_BRICKS)));
    public static final DeferredBlock<SlabBlock> STARWRECK_BRICK_SLAB = block("starwreck_brick_slab", id -> new SlabBlock(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).sound(SoundType.NETHER_BRICKS)));
    public static final DeferredBlock<WallBlock> STARWRECK_BRICK_WALL = block("starwreck_brick_wall", id -> new WallBlock(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).sound(SoundType.NETHER_BRICKS)));
    public static final DeferredItem<Item> STAR_CRYSTAL_SHARD = ModItems.ITEMS.register("star_crystal_shard", id -> new Item(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, id))));
    public static final DeferredItem<StarBowItem> STAR_BOW = ModItems.ITEMS.register("star_bow",
        id -> new StarBowItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, id)).durability(384).fireResistant()));
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC = SOUNDS.register("music.starwreck_wastes", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, "music.starwreck_wastes")));

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE).strength(3, 9).sound(SoundType.BASALT);
    }
    private static <B extends Block> DeferredBlock<B> block(String name, java.util.function.Function<Identifier, B> factory) {
        DeferredBlock<B> block = BLOCKS.register(name, factory);
        BLOCK_ITEMS.add(ModItems.ITEMS.registerSimpleBlockItem(name, block));
        return block;
    }
    private static DeferredBlock<AmethystClusterBlock> crystal(String name, float height, float offset, int light) {
        return block(name, id -> new AmethystClusterBlock(height, offset, BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> light)));
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        SOUNDS.register(bus);
        bus.addListener(ModStarwreck::setup);
    }
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(EMBERBLOOM.getId(), POTTED_EMBERBLOOM));
    }
}
