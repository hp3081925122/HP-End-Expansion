package org.hp.hp_end_expansion.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.hp.hp_end_expansion.block.ColoredFallingBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.*;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.block.*;
import org.hp.hp_end_expansion.item.StarBowItem;

public final class ModStarwreck {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS, Hp_end_expansion.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Hp_end_expansion.MODID);
    public static final List<RegistryObject<BlockItem>> BLOCK_ITEMS = new ArrayList<>();
    public static final RegistryObject<StarwreckStoneBlock> STARWRECK_STONE = block("starwreck_stone", () -> new StarwreckStoneBlock(stone()));
    public static final RegistryObject<EmberStarwreckBlock> EMBER_STARWRECK_STONE = block("ember_starwreck_stone", () -> new EmberStarwreckBlock(stone().lightLevel(s -> 6)));
    public static final RegistryObject<StarwreckStoneBlock> STAR_MOSS = block("star_moss", () -> new StarwreckStoneBlock(stone()));
    public static final RegistryObject<ColoredFallingBlock> METEOR_ASH = block("meteor_ash", () -> new ColoredFallingBlock(0xFF806E61, BlockBehaviour.Properties.copy(Blocks.SAND)));
    public static final RegistryObject<StarwreckPlantBlock> STAR_MOSS_SPROUTS = block("star_moss_sprouts", () -> new StarwreckPlantBlock(BlockBehaviour.Properties.copy(Blocks.NETHER_SPROUTS).replaceable()));
    public static final RegistryObject<EmberbloomBlock> EMBERBLOOM = block("emberbloom", () -> new EmberbloomBlock(BlockBehaviour.Properties.copy(Blocks.POPPY).lightLevel(s -> 4)));
    public static final RegistryObject<FlowerPotBlock> POTTED_EMBERBLOOM = BLOCKS.register("potted_emberbloom", () -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, EMBERBLOOM, BlockBehaviour.Properties.copy(Blocks.POTTED_POPPY).lightLevel(s -> 4)));
    public static final RegistryObject<AmethystClusterBlock> SMALL_STAR_CRYSTAL_BUD = crystal("small_star_crystal_bud", 3, 4, 1);
    public static final RegistryObject<AmethystClusterBlock> MEDIUM_STAR_CRYSTAL_BUD = crystal("medium_star_crystal_bud", 4, 3, 2);
    public static final RegistryObject<AmethystClusterBlock> LARGE_STAR_CRYSTAL_BUD = crystal("large_star_crystal_bud", 5, 3, 4);
    public static final RegistryObject<AmethystClusterBlock> STAR_CRYSTAL_CLUSTER = crystal("star_crystal_cluster", 7, 3, 5);
    public static final RegistryObject<AmethystBlock> STAR_CRYSTAL_BLOCK = block("star_crystal_block", () -> new AmethystBlock(BlockBehaviour.Properties.copy(Blocks.AMETHYST_BLOCK).lightLevel(s -> 3)));
    public static final RegistryObject<Block> STARWRECK_BRICKS = block("starwreck_bricks", () -> new Block(stone().sound(SoundType.NETHER_BRICKS)));
    public static final RegistryObject<StairBlock> STARWRECK_BRICK_STAIRS = block("starwreck_brick_stairs", () -> new StairBlock(STARWRECK_BRICKS.get().defaultBlockState(), stone().sound(SoundType.NETHER_BRICKS)));
    public static final RegistryObject<SlabBlock> STARWRECK_BRICK_SLAB = block("starwreck_brick_slab", () -> new SlabBlock(stone().sound(SoundType.NETHER_BRICKS)));
    public static final RegistryObject<WallBlock> STARWRECK_BRICK_WALL = block("starwreck_brick_wall", () -> new WallBlock(stone().sound(SoundType.NETHER_BRICKS)));
    public static final RegistryObject<Item> STAR_CRYSTAL_SHARD = ModItems.ITEMS.register("star_crystal_shard", () -> new Item(new Item.Properties()));
    public static final RegistryObject<StarBowItem> STAR_BOW = ModItems.ITEMS.register("star_bow",
        () -> new StarBowItem(new Item.Properties().durability(384).fireResistant()));
    public static final RegistryObject<SoundEvent> MUSIC = SOUNDS.register("music.starwreck_wastes", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, "music.starwreck_wastes")));

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.copy(Blocks.END_STONE).strength(3, 9).sound(SoundType.BASALT);
    }
    private static <B extends Block> RegistryObject<B> block(String name, Supplier<B> factory) {
        RegistryObject<B> block = BLOCKS.register(name, factory);
        BLOCK_ITEMS.add(ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
        return block;
    }
    private static RegistryObject<AmethystClusterBlock> crystal(String name, int height, int offset, int light) {
        return block(name, () -> new AmethystClusterBlock(height, offset, BlockBehaviour.Properties.copy(Blocks.AMETHYST_CLUSTER).lightLevel(s -> light)));
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
