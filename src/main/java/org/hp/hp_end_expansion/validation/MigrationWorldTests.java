package org.hp.hp_end_expansion.validation;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.registry.*;
import org.hp.hp_end_expansion.worldgen.*;
import org.hp.hp_end_expansion.worldgen.tidelight.*;
import org.hp.hp_end_expansion.block.tidelight.HangingGlowkelpBlock;

public final class MigrationWorldTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,Hp_end_expansion.MODID);
    public static void register(IEventBus bus) {
        FUNCTIONS.register("structure_templates",()->MigrationWorldTests::templates);
        FUNCTIONS.register("world_features",()->helper->MigrationServerTests.prepare(helper,MigrationWorldTests::features));
        FUNCTIONS.register("block_behaviors",()->helper->MigrationServerTests.prepare(helper,MigrationWorldTests::blocks));
        FUNCTIONS.register("recipes_loot",()->helper->MigrationServerTests.prepare(helper,MigrationWorldTests::recipesLoot));
        FUNCTIONS.register(bus);
        bus.addListener(MigrationWorldTests::tests);
    }
    private static void tests(RegisterGameTestsEvent event) {
        var environment=event.registerEnvironment(MigrationServerTests.id("world_validation"),new TestEnvironmentDefinition.AllOf());
        event.registerTest(MigrationServerTests.id("structure_templates"),data->new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,MigrationServerTests.id("structure_templates")),data),new TestData<>(environment,Identifier.withDefaultNamespace("empty"),600,0,true));
        for(String name:java.util.List.of("world_features","block_behaviors","recipes_loot")) event.registerTest(MigrationServerTests.id(name),data->new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,MigrationServerTests.id(name)),data),new TestData<>(environment,Identifier.withDefaultNamespace("empty"),1200,0,true));
    }
    private static void templates(GameTestHelper helper) {
        var level=helper.getLevel(); var manager=level.getStructureManager();
        var resources=level.getServer().getResourceManager().listResources("structure",id->id.getNamespace().equals(Hp_end_expansion.MODID)&&id.getPath().endsWith(".nbt"));
        helper.assertTrue(!resources.isEmpty(),"Mod structure resources missing");
        BlockPos pos=helper.absolutePos(new BlockPos(64,12,64));
        int placed=0;
        for(var file:resources.keySet()) {
            var id=file.withPath(file.getPath().substring("structure/".length(),file.getPath().length()-4));
            var template=manager.get(id).orElseThrow();
            helper.assertTrue(template.getSize().getX()>0&&template.getSize().getY()>0&&template.getSize().getZ()>0,"Empty structure: "+id);
            var random=RandomSource.create(312);
            var settings=new StructurePlaceSettings().setBoundingBox(BoundingBox.fromCorners(pos,pos.offset(template.getSize())));
            helper.assertTrue(template.placeInWorld(level,pos,pos,settings,random,2),"Structure placement failed: "+id);
            placed++;
        }
        MigrationServerTests.passed(helper,"Structure template load and actual placement: count="+placed);
    }
    private static void features(GameTestHelper helper) {
        var level=helper.getLevel();
        var biomes=level.registryAccess().lookupOrThrow(Registries.BIOME);
        var source=level.getServer().getLevel(net.minecraft.world.level.Level.END).getChunkSource();
        var biomeSource=source.getGenerator().getBiomeSource();
        int star=0,tide=0;
        for(int x=4096;x<32768;x+=512) for(int z=4096;z<32768;z+=512) {
            var biome=biomeSource.getNoiseBiome(x>>2,16,z>>2,source.randomState().sampler());
            if(biome.is(StarwreckWorldgen.BIOME)) star++;
            if(biome.is(TidelightWorldgen.BIOME)) tide++;
        }
        helper.assertTrue(star>0&&tide>0,"End biome provider did not select both mod biomes");
        helper.assertTrue(!biomeSource.getNoiseBiome(0,16,0,source.randomState().sampler()).is(StarwreckWorldgen.BIOME),"Central End island must remain vanilla");
        int count=0;
        for(var entry:level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).listElements().toList()) {
            if(!entry.key().identifier().getNamespace().equals(Hp_end_expansion.MODID)) continue;
            ConfiguredFeature<?,?> feature=entry.value();
            if(!(feature.feature() instanceof StarwreckFeature)&&!(feature.feature() instanceof TidelightFeature)) continue;
            boolean reef=feature.feature() instanceof TidelightFeature;
            var biome=biomes.getOrThrow(reef?TidelightWorldgen.BIOME:StarwreckWorldgen.BIOME);
            int baseX=1024+(count*64); int baseZ=1024;
            for(int cx=(baseX>>4)-1;cx<=(baseX>>4)+1;cx++) for(int cz=(baseZ>>4)-1;cz<=(baseZ>>4)+1;cz++) {
                var chunk=level.getChunk(cx,cz);
                chunk.fillBiomesFromNoise((qx,qy,qz,sampler)->biome,level.getChunkSource().randomState().sampler());
                chunk.markUnsaved();
            }
            for(int x=baseX-16;x<baseX+32;x++) for(int z=baseZ-16;z<baseZ+32;z++) for(int y=36;y<=64;y++) level.setBlock(new BlockPos(x,y,z),Blocks.END_STONE.defaultBlockState(),2);
            BlockPos origin=new BlockPos(baseX,64,baseZ);
            boolean placed=feature.place(level,level.getChunkSource().getGenerator(),RandomSource.create(231),origin);
            if(feature.config() instanceof StarwreckFeature.Settings settings&&settings.kind().equals("surface")) {
                helper.assertTrue(placed,"Starwreck surface placement failed");
                helper.assertTrue(!level.getBlockState(origin).is(Blocks.END_STONE),"Starwreck surface did not replace stone");
            }
            if(feature.config() instanceof TidelightFeature.Settings settings&&settings.kind().equals("surface")) {
                helper.assertTrue(placed,"Tidelight surface placement failed");
                helper.assertTrue(!level.getBlockState(origin).is(Blocks.END_STONE),"Tidelight surface did not replace stone");
            }
            com.mojang.logging.LogUtils.getLogger().info("Configured feature executed: id={}, placed={}",entry.key().identifier(),placed);
            count++;
        }
        helper.assertTrue(count>=10,"Configured feature coverage incomplete");
        MigrationServerTests.passed(helper,"End biome sampling and configured terrain features: count="+count+", star="+star+", tide="+tide);
    }
    private static void blocks(GameTestHelper helper) {
        var player=MigrationServerTests.player(helper); var level=helper.getLevel();
        BlockPos pos=BlockPos.containing(player.position()).offset(4,0,0);
        level.setBlock(pos,ModStarwreck.STARWRECK_STONE.get().defaultBlockState(),3);
        level.setBlock(pos.east(),ModStarwreck.STAR_MOSS.get().defaultBlockState(),3);
        var stone=(BonemealableBlock)ModStarwreck.STARWRECK_STONE.get();
        helper.assertTrue(stone.isValidBonemealTarget(level,pos,level.getBlockState(pos)),"Starwreck bone meal target");
        stone.performBonemeal(level,RandomSource.create(12),pos,level.getBlockState(pos));
        helper.assertTrue(level.getBlockState(pos).is(ModStarwreck.STAR_MOSS.get()),"Starwreck bone meal conversion");
        helper.assertTrue(ModStarwreck.EMBERBLOOM.get().defaultBlockState().canSurvive(level,pos.above()),"Emberbloom moss support");
        level.setBlock(pos,ModTidelight.REEFSTONE.get().defaultBlockState(),3);
        level.setBlock(pos.east(),ModTidelight.GLOWKELP_REEFSTONE.get().defaultBlockState(),3);
        ((BonemealableBlock)ModTidelight.REEFSTONE.get()).performBonemeal(level,RandomSource.create(12),pos,level.getBlockState(pos));
        helper.assertTrue(level.getBlockState(pos).is(ModTidelight.GLOWKELP_REEFSTONE.get()),"Reef bone meal conversion");
        helper.assertTrue(ModTidelight.TIDE_WHISKERS.get().defaultBlockState().canSurvive(level,pos.above()),"Tide grass reef support");
        level.setBlock(pos,ModTidelight.PEARL_SAND.get().defaultBlockState(),3);
        helper.assertTrue(!ModTidelight.TIDE_WHISKERS.get().defaultBlockState().canSurvive(level,pos.above()),"Tide grass rejects sand");
        helper.assertTrue(ModTidelight.LANTERN_ANEMONE.get().defaultBlockState().canSurvive(level,pos.above()),"Anemone sand support");
        BlockPos support=pos.above(16); level.setBlock(support,ModTidelight.REEFSTONE.get().defaultBlockState(),3);
        BlockPos head=support.below(); var kelp=ModTidelight.HANGING_GLOWKELP.get();
        level.setBlock(head,kelp.defaultBlockState(),3);
        kelp.performBonemeal(level,RandomSource.create(12),head,level.getBlockState(head));
        helper.assertTrue(level.getBlockState(head).getValue(HangingGlowkelpBlock.POD),"Kelp bone meal pod");
        var hit=new BlockHitResult(Vec3.atCenterOf(head),Direction.DOWN,head,false);
        level.getBlockState(head).useWithoutItem(level,player,hit);
        helper.assertTrue(!level.getBlockState(head).getValue(HangingGlowkelpBlock.POD),"Kelp pod harvesting");
        helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(head).inflate(2),e->e.getItem().is(ModTidelight.GLOWKELP_POD.get())).size()>0,"Kelp pod drop");
        for(int i=1;i<=12;i++) level.setBlock(support.below(i),ModTidelight.HANGING_GLOWKELP_PLANT.get().defaultBlockState(),2);
        BlockPos last=support.below(12); level.setBlock(last,kelp.defaultBlockState().setValue(HangingGlowkelpBlock.POD,true),2);
        helper.assertTrue(!kelp.isValidBonemealTarget(level,last,level.getBlockState(last)),"Kelp twelve block growth limit");
        MigrationServerTests.passed(helper,"Bone meal conversions, plant support, glowkelp pod harvest and length limit");
    }
    private static net.minecraft.world.item.ItemStack ingredient(net.minecraft.world.item.crafting.Ingredient ingredient) {
        return new net.minecraft.world.item.ItemStack(ingredient.items().findFirst().orElseThrow().value());
    }
    private static void recipesLoot(GameTestHelper helper) {
        var level=helper.getLevel(); var player=MigrationServerTests.player(helper);
        int assembled=0;
        for(var holder:level.getServer().getRecipeManager().getRecipes()) {
            if(!holder.id().identifier().getNamespace().equals(Hp_end_expansion.MODID)) continue;
            var recipe=holder.value(); net.minecraft.world.item.ItemStack result;
            if(recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
                var items=shaped.getIngredients().stream().map(value->value.map(MigrationWorldTests::ingredient).orElse(net.minecraft.world.item.ItemStack.EMPTY)).toList();
                var input=net.minecraft.world.item.crafting.CraftingInput.of(shaped.getWidth(),shaped.getHeight(),items);
                helper.assertTrue(shaped.matches(input,level),"Shaped recipe match: "+holder.id()); result=shaped.assemble(input);
            } else if(recipe instanceof net.minecraft.world.item.crafting.ShapelessRecipe shapeless) {
                var items=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
                shapeless.placementInfo().ingredients().forEach(i->items.add(ingredient(i)));
                while(items.size()<9) items.add(net.minecraft.world.item.ItemStack.EMPTY);
                var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,items);
                helper.assertTrue(shapeless.matches(input,level),"Shapeless recipe match: "+holder.id()); result=shapeless.assemble(input);
            } else if(recipe instanceof net.minecraft.world.item.crafting.SingleItemRecipe single) {
                var input=new net.minecraft.world.item.crafting.SingleRecipeInput(ingredient(single.input()));
                helper.assertTrue(single.matches(input,level),"Stonecutting recipe match: "+holder.id()); result=single.assemble(input);
            } else { throw new IllegalStateException("Uncovered recipe type: "+holder.id()); }
            helper.assertTrue(!result.isEmpty(),"Empty recipe result: "+holder.id()); assembled++;
        }
        helper.assertValueEqual(assembled,38,"Assembled recipes");
        int loot=0;
        var tables=level.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE);
        for(var entry:tables.listElements().toList()) {
            if(!entry.key().identifier().getNamespace().equals(Hp_end_expansion.MODID)) continue;
            var table=entry.value(); var keys=table.getParamSet().allowed();
            var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(level);
            String name=entry.key().identifier().getPath(); String simple=name.substring(name.indexOf('/')+1);
            var mob=BuiltInRegistries.ENTITY_TYPE.get(MigrationServerTests.id(simple)).map(net.minecraft.core.Holder::value).orElse(net.minecraft.world.entity.EntityType.COW).create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            var block=BuiltInRegistries.BLOCK.get(MigrationServerTests.id(simple)).map(net.minecraft.core.Holder::value).orElse(Blocks.STONE);
            if(keys.contains(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN)) params.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,player.position());
            if(keys.contains(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY)) params.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY,mob);
            if(keys.contains(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE)) params.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE,block.defaultBlockState());
            if(keys.contains(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL)) params.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
            if(keys.contains(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE)) params.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE,level.damageSources().playerAttack(player));
            if(keys.contains(net.minecraft.world.level.storage.loot.parameters.LootContextParams.LAST_DAMAGE_PLAYER)) params.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.LAST_DAMAGE_PLAYER,player);
            if(keys.contains(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ATTACKING_ENTITY)) params.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ATTACKING_ENTITY,player);
            if(keys.contains(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DIRECT_ATTACKING_ENTITY)) params.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DIRECT_ATTACKING_ENTITY,player);
            var drops=table.getRandomItems(params.create(table.getParamSet()),1234L);
            for(var item:drops) helper.assertTrue(!item.isEmpty()&&item.getCount()>0,"Invalid loot result: "+entry.key());
            loot++;
        }
        helper.assertValueEqual(loot,59,"Executed loot tables");
        MigrationServerTests.passed(helper,"38 actual recipe matches/assemblies and 59 loot table executions");
    }
}
