package org.hp.hp_end_expansion.registry;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.effect.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.*;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.block.tidelight.*;
import org.hp.hp_end_expansion.item.TideCrossbowItem;

public final class ModTidelight {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hp_end_expansion.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Hp_end_expansion.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Hp_end_expansion.MODID);
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, Hp_end_expansion.MODID);
    public static final DeferredHolder<Attribute, Attribute> CROSSBOW_CAPACITY = ATTRIBUTES.register("crossbow_capacity",
        () -> new RangedAttribute("attribute.hp_end_expansion.crossbow_capacity", 0.0D, 0.0D, 1024.0D).setSyncable(true));
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Hp_end_expansion.MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ChargedProjectiles>>> CROSSBOW_RESERVE = COMPONENTS.registerComponentType(
        "crossbow_reserve", builder -> builder.persistent(ChargedProjectiles.CODEC.listOf())
            .networkSynchronized(ChargedProjectiles.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list())));
    public static final List<DeferredItem<? extends Item>> ITEMS = new ArrayList<>();
    public static final DeferredBlock<ReefstoneBlock> REEFSTONE = block("reefstone", id -> new ReefstoneBlock(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id))));
    public static final DeferredBlock<ReefstoneBlock> GLOWKELP_REEFSTONE = block("glowkelp_reefstone", id -> new ReefstoneBlock(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 3)));
    public static final DeferredBlock<Block> TIDEMARKED_REEFSTONE = block("tidemarked_reefstone", id -> new Block(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 2)));
    public static final DeferredBlock<ColoredFallingBlock> PEARL_SAND = block("pearl_sand", id -> new ColoredFallingBlock(new ColorRGBA(0xFFDCE8E0), BlockBehaviour.Properties.ofFullCopy(Blocks.SAND).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id))));
    public static final DeferredBlock<TidelightCoralBlock> LUMEN_CORAL = block("lumen_coral", id -> new TidelightCoralBlock(plant().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 6)));
    public static final DeferredBlock<Block> LUMEN_CORAL_BLOCK = block("lumen_coral_block", id -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.TUBE_CORAL_BLOCK).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 8)));
    public static final DeferredBlock<BaseCoralFanBlock> LUMEN_CORAL_FAN = BLOCKS.register("lumen_coral_fan", id -> new BaseCoralFanBlock(plant().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 5)));
    public static final DeferredBlock<BaseCoralWallFanBlock> LUMEN_CORAL_WALL_FAN = BLOCKS.register("lumen_coral_wall_fan", id -> new BaseCoralWallFanBlock(plant().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 5)));
    public static final DeferredItem<StandingAndWallBlockItem> CORAL_FAN_ITEM = item("lumen_coral_fan", id -> new StandingAndWallBlockItem(LUMEN_CORAL_FAN.get(), LUMEN_CORAL_WALL_FAN.get(), Direction.DOWN, new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, id))));
    public static final DeferredBlock<TidelightFlowerBlock> TIDE_WHISKERS = block("tide_whiskers", id -> new TidelightFlowerBlock(plant().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).replaceable()));
    public static final DeferredBlock<TidelightFlowerBlock> LANTERN_ANEMONE = block("lantern_anemone", id -> new TidelightFlowerBlock(plant().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 7)));
    public static final DeferredBlock<TidelightCoralBlock> PEARL_CLAM = block("pearl_clam", id -> new TidelightCoralBlock(plant().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).strength(0.5F).lightLevel(s -> 3)));
    public static final DeferredBlock<FlowerPotBlock> POTTED_LANTERN_ANEMONE = BLOCKS.register("potted_lantern_anemone", id -> new FlowerPotBlock(() -> (FlowerPotBlock)Blocks.FLOWER_POT, LANTERN_ANEMONE, BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_POPPY).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 7)));
    public static final DeferredBlock<HangingGlowkelpBlock> HANGING_GLOWKELP = BLOCKS.register("hanging_glowkelp", id -> new HangingGlowkelpBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CAVE_VINES).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> s.getValue(HangingGlowkelpBlock.POD) ? 10 : 4)));
    public static final DeferredBlock<HangingGlowkelpPlantBlock> HANGING_GLOWKELP_PLANT = BLOCKS.register("hanging_glowkelp_plant", id -> new HangingGlowkelpPlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CAVE_VINES_PLANT).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 4)));
    public static final DeferredBlock<Block> TIDE_LANTERN = block("tide_lantern", id -> new Block(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 15)));
    public static final DeferredBlock<Block> REEFSTONE_BRICKS = block("reefstone_bricks", id -> new Block(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> REEFSTONE_BRICK_STAIRS = block("reefstone_brick_stairs", id -> new StairBlock(REEFSTONE_BRICKS.get().defaultBlockState(), stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).sound(SoundType.STONE)));
    public static final DeferredBlock<SlabBlock> REEFSTONE_BRICK_SLAB = block("reefstone_brick_slab", id -> new SlabBlock(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).sound(SoundType.STONE)));
    public static final DeferredBlock<WallBlock> REEFSTONE_BRICK_WALL = block("reefstone_brick_wall", id -> new WallBlock(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).sound(SoundType.STONE)));
    public static final DeferredBlock<Block> CHISELED_REEFSTONE = block("chiseled_reefstone", id -> new Block(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).sound(SoundType.STONE)));
    public static final DeferredBlock<Block> PEARL_BLOCK = block("pearl_block", id -> new Block(stone().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id)).lightLevel(s -> 4)));
    public static final DeferredBlock<RotatedPillarBlock> REEF_BONE_BLOCK = block("reef_bone_block", id -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BONE_BLOCK).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id))));
    public static final DeferredItem<Item> TIDE_PEARL = simpleItem("tide_pearl");
    public static final DeferredItem<Item> LUMEN_CORAL_BRANCH = simpleItem("lumen_coral_branch");
    public static final DeferredItem<BlockItem> GLOWKELP_POD = item("glowkelp_pod", id -> new BlockItem(HANGING_GLOWKELP.get(), new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, id)).food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).build(), net.minecraft.world.item.component.Consumable.builder().onConsume(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,200),1F)).build())));
    public static final DeferredItem<Item> TIDELIGHT_GEL = simpleItem("tidelight_gel");
    public static final DeferredItem<Item> REEF_EEL_SCALE = simpleItem("reef_eel_scale");
    public static final DeferredItem<TideCrossbowItem> TIDE_CROSSBOW = item("tide_crossbow", id -> new TideCrossbowItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, id))
        .stacksTo(1).durability(465).fireResistant()
        .attributes(ItemAttributeModifiers.builder().add(CROSSBOW_CAPACITY,
            new AttributeModifier(TideCrossbowItem.CAPACITY_MODIFIER_ID, 3.0D, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND).build())
        .component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)));
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC = sound("music.tidelight_reef");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_LOOP = sound("ambient.tidelight_reef.loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_ADDITIONS = sound("ambient.tidelight_reef.additions");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TIDE_MOTE = PARTICLES.register("tide_mote", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TIDE_BUBBLE = PARTICLES.register("tide_bubble", () -> new SimpleParticleType(false));
    private static BlockBehaviour.Properties stone() { return BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE).strength(3, 9).sound(SoundType.TUFF); }
    private static BlockBehaviour.Properties plant() { return BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY).noCollision().noOcclusion(); }
    private static <B extends Block> DeferredBlock<B> block(String name, java.util.function.Function<Identifier, B> factory) {
        DeferredBlock<B> block = BLOCKS.register(name, factory);
        ITEMS.add(ModItems.ITEMS.registerSimpleBlockItem(name, block));
        return block;
    }
    private static <I extends Item> DeferredItem<I> item(String name, java.util.function.Function<Identifier, I> factory) {
        DeferredItem<I> item = ModItems.ITEMS.register(name, factory);
        ITEMS.add(item);
        return item;
    }
    private static DeferredItem<Item> simpleItem(String name) { return item(name, id -> new Item(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, id)))); }
    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) { return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID, name))); }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); SOUNDS.register(bus); PARTICLES.register(bus);
        ATTRIBUTES.register(bus); COMPONENTS.register(bus);
        bus.addListener(ModTidelight::attributes);
        bus.addListener(ModTidelight::setup);
        NeoForge.EVENT_BUS.addListener(ModTidelight::brewing);
    }
    private static void attributes(EntityAttributeModificationEvent event) { event.add(EntityType.PLAYER, CROSSBOW_CAPACITY); }
    private static void setup(FMLCommonSetupEvent event) { event.enqueueWork(() -> ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(LANTERN_ANEMONE.getId(), POTTED_LANTERN_ANEMONE)); }
    private static void brewing(RegisterBrewingRecipesEvent event) { event.getBuilder().addMix(Potions.AWKWARD, TIDELIGHT_GEL.get(), Potions.SLOW_FALLING); }
}
