package com.hp_end_expansion.content.prismatic;

import com.hp_end_expansion.HpEndExpansion;
import com.hp_end_expansion.content.prismatic.block.PrismaticPlantBlock;
import com.hp_end_expansion.content.prismatic.block.PrismaticSaplingBlock;
import com.hp_end_expansion.content.prismatic.block.RegentAltarBlock;
import com.hp_end_expansion.content.prismatic.item.PrismaticMaterialItem;
import com.hp_end_expansion.content.prismatic.item.PrismMealItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PrismaticContent {
    // 独立注册器与有序索引让群系内容不修改原有注册清单。
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HpEndExpansion.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HpEndExpansion.MODID);
    private static final Map<String, Supplier<? extends Block>> BLOCK_INDEX = new LinkedHashMap<>();
    private static final Map<String, Supplier<? extends Item>> ITEM_INDEX = new LinkedHashMap<>();
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, HpEndExpansion.MODID);

    // 注册建筑材质和原版形状，物品随方块登记，避免遗漏获取入口。
    static {
        addBlock("prism_soil", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).strength(0.8F).sound(SoundType.SAND)));
        addBlock("chalkstone", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE)));
        addBlock("chalkstone_bricks", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE_BRICKS)));
        addBlock("chalkstone_stairs", () -> new StairBlock(block("chalkstone_bricks").defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE_BRICK_STAIRS)));
        addBlock("chalkstone_slab", () -> new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE_BRICK_SLAB)));
        addBlock("chalkstone_wall", () -> new WallBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE_BRICK_WALL)));
        addBlock("cracked_bricks", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE_BRICKS)));
        addBlock("chiseled_chalkstone", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE_BRICKS)));
        addBlock("lumen_ore", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE).lightLevel(state -> 3)));
        addBlock("lumen_block", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).lightLevel(state -> 10)));
        addBlock("dusk_glass", () -> new TransparentBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)));
        addBlock("prism_log", () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG)));
        addBlock("stripped_prism_log", () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_OAK_LOG)));
        addBlock("prism_planks", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)));
        addBlock("prism_stairs", () -> new StairBlock(block("prism_planks").defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_STAIRS)));
        addBlock("prism_slab", () -> new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SLAB)));
        addBlock("prism_leaves", () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
        addBlock("prism_lamp", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.SEA_LANTERN).lightLevel(state -> 15)));
        addBlock("survey_marker", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_STONE_BRICKS).lightLevel(state -> 4)));
        addBlock("regent_altar", () -> new RegentAltarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.REINFORCED_DEEPSLATE).strength(12F, 1200F).lightLevel(state -> 7)));
        // 六种成熟植株共享年龄协议，产物、轮廓和危险性各自不同。
        String[][] plants = {{"prism_grass", "crystal_bud"}, {"mica_reed", "mica_sheet"}, {"lantern_bloom", "lumen_dust"},
                {"glass_fern", "crystal_shard"}, {"shard_cactus", "prism_needle"}, {"dusk_bloom", "dusk_bud"}};
        for (String[] plant : plants) {
            addBlock(plant[0], () -> new PrismaticPlantBlock(plant[1], plant[0].equals("shard_cactus"),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS).noCollission().randomTicks()
                            .lightLevel(state -> plant[0].equals("lantern_bloom") ? 2 + state.getValue(PrismaticPlantBlock.AGE) * 3 : 0)));
        }
        addBlock("prism_sapling", () -> new PrismaticSaplingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING)));
        // 材料只在其对应玩法需要时提供功能，避免额外的运行时扫描。
        String[] materials = {"crystal_bud", "crystal_shard", "prism_meal", "mica_sheet", "lumen_dust", "prism_needle", "dusk_bud", "dusk_silk",
                "horn_fragment", "carapace", "fault_sigil", "mirror_sigil", "lumen_crystal", "prism_key", "regent_core", "refraction_charm"};
        for (String id : materials) {
            ITEM_INDEX.put(id, ITEMS.register("prismatic_" + id, () -> {
                Item.Properties properties = new Item.Properties();
                if (id.equals("crystal_bud")) properties.food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.5F).build());
                if (id.equals("refraction_charm")) properties.stacksTo(1);
                return id.equals("prism_meal") ? new PrismMealItem(properties) : new PrismaticMaterialItem(id, properties);
            }));
        }
    }

    // 单独创造标签展示全部建筑和生态材料，实体蛋由实体注册器提供入口。
    private static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("prismatic_wastes", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hp_end_expansion.prismatic_wastes"))
            .icon(() -> new ItemStack(item("lumen_crystal")))
            .displayItems((parameters, output) -> ITEM_INDEX.values().forEach(item -> output.accept(item.get())))
            .build());

    // 所有跨域访问使用已冻结的短名称，并让未知内容立即暴露错误。
    public static Block block(String id) {
        Supplier<? extends Block> value = BLOCK_INDEX.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown prismatic block: " + id);
        return value.get();
    }

    // 材料和方块物品共用读取入口，调用方无需持有注册期对象。
    public static Item item(String id) {
        Supplier<? extends Item> value = ITEM_INDEX.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown prismatic item: " + id);
        return value.get();
    }

    // 只在构造群系注册清单时使用，保证一个方块恰好对应一个方块物品。
    private static void addBlock(String id, Supplier<? extends Block> factory) {
        var holder = BLOCKS.register("prismatic_" + id, factory);
        BLOCK_INDEX.put(id, holder);
        ITEM_INDEX.put(id, ITEMS.registerSimpleBlockItem("prismatic_" + id, holder));
    }

    // 入口统一挂接三个注册器，保持其他对话的旧入口改动可独立合并。
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
    }

    // 内容注册类不创建实例。
    private PrismaticContent() {}
}
