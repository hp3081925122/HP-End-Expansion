package org.hp.hp_end_expansion.registry;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.hp.hp_end_expansion.block.ColoredFallingBlock;
import net.minecraft.world.effect.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.registries.*;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.block.tidelight.*;
import org.hp.hp_end_expansion.item.TideCrossbowItem;

public final class ModTidelight {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS, Hp_end_expansion.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Hp_end_expansion.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Hp_end_expansion.MODID);
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, Hp_end_expansion.MODID);
    public static final RegistryObject<Attribute> CROSSBOW_CAPACITY = ATTRIBUTES.register("crossbow_capacity",
        () -> new RangedAttribute("attribute.hp_end_expansion.crossbow_capacity", 0.0D, 0.0D, 1024.0D).setSyncable(true));
    public static final List<RegistryObject<? extends Item>> ITEMS = new ArrayList<>();
    public static final RegistryObject<ReefstoneBlock> REEFSTONE = block("reefstone", () -> new ReefstoneBlock(stone()));
    public static final RegistryObject<ReefstoneBlock> GLOWKELP_REEFSTONE = block("glowkelp_reefstone", () -> new ReefstoneBlock(stone().lightLevel(s -> 3)));
    public static final RegistryObject<Block> TIDEMARKED_REEFSTONE = block("tidemarked_reefstone", () -> new Block(stone().lightLevel(s -> 2)));
    public static final RegistryObject<ColoredFallingBlock> PEARL_SAND = block("pearl_sand", () -> new ColoredFallingBlock(0xFFDCE8E0, BlockBehaviour.Properties.copy(Blocks.SAND)));
    public static final RegistryObject<TidelightCoralBlock> LUMEN_CORAL = block("lumen_coral", () -> new TidelightCoralBlock(plant().lightLevel(s -> 6)));
    public static final RegistryObject<Block> LUMEN_CORAL_BLOCK = block("lumen_coral_block", () -> new Block(BlockBehaviour.Properties.copy(Blocks.TUBE_CORAL_BLOCK).lightLevel(s -> 8)));
    public static final RegistryObject<BaseCoralFanBlock> LUMEN_CORAL_FAN = BLOCKS.register("lumen_coral_fan", () -> new BaseCoralFanBlock(plant().offsetType(BlockBehaviour.OffsetType.NONE).lightLevel(s -> 5)));
    public static final RegistryObject<BaseCoralWallFanBlock> LUMEN_CORAL_WALL_FAN = BLOCKS.register("lumen_coral_wall_fan", () -> new BaseCoralWallFanBlock(plant().offsetType(BlockBehaviour.OffsetType.NONE).lightLevel(s -> 5)));
    public static final RegistryObject<StandingAndWallBlockItem> CORAL_FAN_ITEM = item("lumen_coral_fan", () -> new StandingAndWallBlockItem(LUMEN_CORAL_FAN.get(), LUMEN_CORAL_WALL_FAN.get(), new Item.Properties(), Direction.DOWN));
    public static final RegistryObject<TidelightFlowerBlock> TIDE_WHISKERS = block("tide_whiskers", () -> new TidelightFlowerBlock(plant().replaceable()));
    public static final RegistryObject<TidelightFlowerBlock> LANTERN_ANEMONE = block("lantern_anemone", () -> new TidelightFlowerBlock(plant().lightLevel(s -> 7)));
    public static final RegistryObject<TidelightCoralBlock> PEARL_CLAM = block("pearl_clam", () -> new TidelightCoralBlock(plant().strength(0.5F).lightLevel(s -> 3)));
    public static final RegistryObject<FlowerPotBlock> POTTED_LANTERN_ANEMONE = BLOCKS.register("potted_lantern_anemone", () -> new FlowerPotBlock(() -> (FlowerPotBlock)Blocks.FLOWER_POT, LANTERN_ANEMONE, BlockBehaviour.Properties.copy(Blocks.POTTED_POPPY).lightLevel(s -> 7)));
    public static final RegistryObject<HangingGlowkelpBlock> HANGING_GLOWKELP = BLOCKS.register("hanging_glowkelp", () -> new HangingGlowkelpBlock(BlockBehaviour.Properties.copy(Blocks.CAVE_VINES).lightLevel(s -> s.getValue(HangingGlowkelpBlock.POD) ? 10 : 4)));
    public static final RegistryObject<HangingGlowkelpPlantBlock> HANGING_GLOWKELP_PLANT = BLOCKS.register("hanging_glowkelp_plant", () -> new HangingGlowkelpPlantBlock(BlockBehaviour.Properties.copy(Blocks.CAVE_VINES_PLANT).lightLevel(s -> 4)));
    public static final RegistryObject<Block> TIDE_LANTERN = block("tide_lantern", () -> new Block(stone().lightLevel(s -> 15)));
    public static final RegistryObject<Block> REEFSTONE_BRICKS = block("reefstone_bricks", () -> new Block(stone().sound(SoundType.STONE)));
    public static final RegistryObject<StairBlock> REEFSTONE_BRICK_STAIRS = block("reefstone_brick_stairs", () -> new StairBlock(REEFSTONE_BRICKS.get().defaultBlockState(), stone().sound(SoundType.STONE)));
    public static final RegistryObject<SlabBlock> REEFSTONE_BRICK_SLAB = block("reefstone_brick_slab", () -> new SlabBlock(stone().sound(SoundType.STONE)));
    public static final RegistryObject<WallBlock> REEFSTONE_BRICK_WALL = block("reefstone_brick_wall", () -> new WallBlock(stone().sound(SoundType.STONE)));
    public static final RegistryObject<Block> CHISELED_REEFSTONE = block("chiseled_reefstone", () -> new Block(stone().sound(SoundType.STONE)));
    public static final RegistryObject<Block> PEARL_BLOCK = block("pearl_block", () -> new Block(stone().lightLevel(s -> 4)));
    public static final RegistryObject<RotatedPillarBlock> REEF_BONE_BLOCK = block("reef_bone_block", () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.BONE_BLOCK)));
    public static final RegistryObject<Item> TIDE_PEARL = simpleItem("tide_pearl");
    public static final RegistryObject<Item> LUMEN_CORAL_BRANCH = simpleItem("lumen_coral_branch");
    public static final RegistryObject<BlockItem> GLOWKELP_POD = item("glowkelp_pod", () -> new BlockItem(HANGING_GLOWKELP.get(), new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationMod(0.1F).effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 200), 1).build())));
    public static final RegistryObject<Item> TIDELIGHT_GEL = simpleItem("tidelight_gel");
    public static final RegistryObject<Item> REEF_EEL_SCALE = simpleItem("reef_eel_scale");
    public static final RegistryObject<TideCrossbowItem> TIDE_CROSSBOW = item("tide_crossbow", () -> new TideCrossbowItem(new Item.Properties()
        .stacksTo(1).durability(465).fireResistant()));
    public static final RegistryObject<SoundEvent> MACE_SMASH_GROUND = sound("item.mace.smash_ground");
    public static final RegistryObject<SoundEvent> MACE_SMASH_GROUND_HEAVY = sound("item.mace.smash_ground_heavy");
    public static final RegistryObject<SoundEvent> MUSIC = sound("music.tidelight_reef");
    public static final RegistryObject<SoundEvent> AMBIENT_LOOP = sound("ambient.tidelight_reef.loop");
    public static final RegistryObject<SoundEvent> AMBIENT_ADDITIONS = sound("ambient.tidelight_reef.additions");
    public static final RegistryObject<SimpleParticleType> TIDE_MOTE = PARTICLES.register("tide_mote", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> TIDE_BUBBLE = PARTICLES.register("tide_bubble", () -> new SimpleParticleType(false));
    private static BlockBehaviour.Properties stone() { return BlockBehaviour.Properties.copy(Blocks.END_STONE).strength(3, 9).sound(SoundType.TUFF); }
    private static BlockBehaviour.Properties plant() { return BlockBehaviour.Properties.copy(Blocks.POPPY).noCollission().noOcclusion(); }
    private static <B extends Block> RegistryObject<B> block(String name, Supplier<B> factory) {
        RegistryObject<B> block = BLOCKS.register(name, factory);
        ITEMS.add(ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
        return block;
    }
    private static <I extends Item> RegistryObject<I> item(String name, Supplier<I> factory) {
        RegistryObject<I> item = ModItems.ITEMS.register(name, factory);
        ITEMS.add(item);
        return item;
    }
    private static RegistryObject<Item> simpleItem(String name) { return item(name, () -> new Item(new Item.Properties())); }
    private static RegistryObject<SoundEvent> sound(String name) { return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Hp_end_expansion.MODID, name))); }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); SOUNDS.register(bus); PARTICLES.register(bus);
        ATTRIBUTES.register(bus);
        bus.addListener(ModTidelight::attributes);
        bus.addListener(ModTidelight::setup);

    }
    private static void attributes(EntityAttributeModificationEvent event) { event.add(EntityType.PLAYER, CROSSBOW_CAPACITY.get()); }
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(LANTERN_ANEMONE.getId(), POTTED_LANTERN_ANEMONE);
            net.minecraftforge.common.brewing.BrewingRecipeRegistry.addRecipe(new net.minecraftforge.common.brewing.IBrewingRecipe() {
                @Override public boolean isInput(ItemStack input) {
                    return (input.is(Items.POTION) || input.is(Items.SPLASH_POTION) || input.is(Items.LINGERING_POTION))
                        && net.minecraft.world.item.alchemy.PotionUtils.getPotion(input) == Potions.AWKWARD;
                }
                @Override public boolean isIngredient(ItemStack ingredient) { return ingredient.is(TIDELIGHT_GEL.get()); }
                @Override public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
                    return isInput(input) && isIngredient(ingredient)
                        ? net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(input.getItem()), Potions.SLOW_FALLING)
                        : ItemStack.EMPTY;
                }
            });
        });
    }
}
