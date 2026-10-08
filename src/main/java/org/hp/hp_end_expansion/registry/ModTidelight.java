package org.hp.hp_end_expansion.registry;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.effect.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.*;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.block.tidelight.*;
import org.hp.hp_end_expansion.item.TideCrossbowItem;

public final class ModTidelight {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hp_end_expansion.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Hp_end_expansion.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Hp_end_expansion.MODID);
    public static final List<DeferredItem<? extends Item>> ITEMS = new ArrayList<>();
    public static final DeferredBlock<ReefstoneBlock> REEFSTONE = block("reefstone", () -> new ReefstoneBlock(stone()));
    public static final DeferredBlock<ReefstoneBlock> GLOWKELP_REEFSTONE = block("glowkelp_reefstone", () -> new ReefstoneBlock(stone().lightLevel(s -> 3)));
    public static final DeferredBlock<Block> TIDEMARKED_REEFSTONE = block("tidemarked_reefstone", () -> new Block(stone().lightLevel(s -> 2)));
    public static final DeferredBlock<ColoredFallingBlock> PEARL_SAND = block("pearl_sand", () -> new ColoredFallingBlock(new ColorRGBA(0xFFDCE8E0), BlockBehaviour.Properties.ofFullCopy(Blocks.SAND)));
    public static final DeferredBlock<TidelightCoralBlock> LUMEN_CORAL = block("lumen_coral", () -> new TidelightCoralBlock(plant().lightLevel(s -> 6)));
    public static final DeferredBlock<Block> LUMEN_CORAL_BLOCK = block("lumen_coral_block", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.TUBE_CORAL_BLOCK).lightLevel(s -> 8)));
    public static final DeferredBlock<BaseCoralFanBlock> LUMEN_CORAL_FAN = BLOCKS.register("lumen_coral_fan", () -> new BaseCoralFanBlock(plant().lightLevel(s -> 5)));
    public static final DeferredBlock<BaseCoralWallFanBlock> LUMEN_CORAL_WALL_FAN = BLOCKS.register("lumen_coral_wall_fan", () -> new BaseCoralWallFanBlock(plant().lightLevel(s -> 5)));
    public static final DeferredItem<StandingAndWallBlockItem> CORAL_FAN_ITEM = item("lumen_coral_fan", () -> new StandingAndWallBlockItem(LUMEN_CORAL_FAN.get(), LUMEN_CORAL_WALL_FAN.get(), new Item.Properties(), Direction.DOWN));
    public static final DeferredBlock<TidelightFlowerBlock> TIDE_WHISKERS = block("tide_whiskers", () -> new TidelightFlowerBlock(plant().replaceable()));
    public static final DeferredBlock<TidelightFlowerBlock> LANTERN_ANEMONE = block("lantern_anemone", () -> new TidelightFlowerBlock(plant().lightLevel(s -> 7)));
    public static final DeferredBlock<TidelightCoralBlock> PEARL_CLAM = block("pearl_clam", () -> new TidelightCoralBlock(plant().strength(0.5F).lightLevel(s -> 3)));
    public static final DeferredBlock<FlowerPotBlock> POTTED_LANTERN_ANEMONE = BLOCKS.register("potted_lantern_anemone", () -> new FlowerPotBlock(() -> (FlowerPotBlock)Blocks.FLOWER_POT, LANTERN_ANEMONE, BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_POPPY).lightLevel(s -> 7)));
    public static final DeferredBlock<HangingGlowkelpBlock> HANGING_GLOWKELP = BLOCKS.register("hanging_glowkelp", () -> new HangingGlowkelpBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CAVE_VINES).lightLevel(s -> s.getValue(HangingGlowkelpBlock.POD) ? 10 : 4)));
    public static final DeferredBlock<HangingGlowkelpPlantBlock> HANGING_GLOWKELP_PLANT = BLOCKS.register("hanging_glowkelp_plant", () -> new HangingGlowkelpPlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CAVE_VINES_PLANT).lightLevel(s -> 4)));
    public static final DeferredBlock<Block> TIDE_LANTERN = block("tide_lantern", () -> new Block(stone().lightLevel(s -> 15)));
    public static final DeferredBlock<Block> REEFSTONE_BRICKS = block("reefstone_bricks", () -> new Block(stone().sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> REEFSTONE_BRICK_STAIRS = block("reefstone_brick_stairs", () -> new StairBlock(REEFSTONE_BRICKS.get().defaultBlockState(), stone().sound(SoundType.STONE)));
    public static final DeferredBlock<SlabBlock> REEFSTONE_BRICK_SLAB = block("reefstone_brick_slab", () -> new SlabBlock(stone().sound(SoundType.STONE)));
    public static final DeferredBlock<WallBlock> REEFSTONE_BRICK_WALL = block("reefstone_brick_wall", () -> new WallBlock(stone().sound(SoundType.STONE)));
    public static final DeferredBlock<Block> CHISELED_REEFSTONE = block("chiseled_reefstone", () -> new Block(stone().sound(SoundType.STONE)));
    public static final DeferredBlock<Block> PEARL_BLOCK = block("pearl_block", () -> new Block(stone().lightLevel(s -> 4)));
    public static final DeferredBlock<RotatedPillarBlock> REEF_BONE_BLOCK = block("reef_bone_block", () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BONE_BLOCK)));
    public static final DeferredItem<Item> TIDE_PEARL = simpleItem("tide_pearl");
    public static final DeferredItem<Item> LUMEN_CORAL_BRANCH = simpleItem("lumen_coral_branch");
    public static final DeferredItem<BlockItem> GLOWKELP_POD = item("glowkelp_pod", () -> new BlockItem(HANGING_GLOWKELP.get(), new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 200), 1).build())));
    public static final DeferredItem<Item> TIDELIGHT_GEL = simpleItem("tidelight_gel");
    public static final DeferredItem<Item> REEF_EEL_SCALE = simpleItem("reef_eel_scale");
    public static final DeferredItem<TideCrossbowItem> TIDE_CROSSBOW = item("tide_crossbow", () -> new TideCrossbowItem(new Item.Properties()
        .stacksTo(1).durability(465).fireResistant()
        .component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)));
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC = sound("music.tidelight_reef");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_LOOP = sound("ambient.tidelight_reef.loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_ADDITIONS = sound("ambient.tidelight_reef.additions");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TIDE_MOTE = PARTICLES.register("tide_mote", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TIDE_BUBBLE = PARTICLES.register("tide_bubble", () -> new SimpleParticleType(false));
    private static BlockBehaviour.Properties stone() { return BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE).strength(3, 9).sound(SoundType.TUFF); }
    private static BlockBehaviour.Properties plant() { return BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY).noCollission().noOcclusion(); }
    private static <B extends Block> DeferredBlock<B> block(String name, Supplier<B> factory) {
        DeferredBlock<B> block = BLOCKS.register(name, factory);
        ITEMS.add(ModItems.ITEMS.registerSimpleBlockItem(name, block));
        return block;
    }
    private static <I extends Item> DeferredItem<I> item(String name, Supplier<I> factory) {
        DeferredItem<I> item = ModItems.ITEMS.register(name, factory);
        ITEMS.add(item);
        return item;
    }
    private static DeferredItem<Item> simpleItem(String name) { return item(name, () -> new Item(new Item.Properties())); }
    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) { return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, name))); }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); SOUNDS.register(bus); PARTICLES.register(bus);
        bus.addListener(ModTidelight::setup);
        NeoForge.EVENT_BUS.addListener(ModTidelight::brewing);
    }
    private static void setup(FMLCommonSetupEvent event) { event.enqueueWork(() -> ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(LANTERN_ANEMONE.getId(), POTTED_LANTERN_ANEMONE)); }
    private static void brewing(RegisterBrewingRecipesEvent event) { event.getBuilder().addMix(Potions.AWKWARD, TIDELIGHT_GEL.get(), Potions.SLOW_FALLING); }
}
