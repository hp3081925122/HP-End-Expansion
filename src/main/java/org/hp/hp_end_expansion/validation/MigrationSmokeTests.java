package org.hp.hp_end_expansion.validation;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.item.TideCrossbowItem;
import org.hp.hp_end_expansion.registry.ModTidelight;

public final class MigrationSmokeTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,Hp_end_expansion.MODID);
    private static final ResourceKey<Consumer<GameTestHelper>> FUNCTION=ResourceKey.create(Registries.TEST_FUNCTION,id("migration_smoke"));
    public static void register(IEventBus bus) {
        if(!Boolean.getBoolean("hp_end_expansion.migrationTests")) return;
        FUNCTIONS.register("migration_smoke",()->MigrationSmokeTests::run);
        FUNCTIONS.register(bus);
        bus.addListener(MigrationSmokeTests::tests);
    }
    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID,name); }
    private static void tests(RegisterGameTestsEvent event) {
        var environment=event.registerEnvironment(id("migration_smoke"),new TestEnvironmentDefinition.AllOf());
        event.registerTest(id("migration_smoke"),data->new FunctionGameTestInstance(FUNCTION,data),new TestData<>(environment,Identifier.withDefaultNamespace("empty"),200,0,true));
    }
    private static void run(GameTestHelper helper) {
        var level=helper.getLevel();
        int entities=0;
        for(var entry:BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            if(!entry.getKey().identifier().getNamespace().equals(Hp_end_expansion.MODID)) continue;
            var entity=entry.getValue().create(level,EntitySpawnReason.LOAD);
            helper.assertTrue(entity!=null,"Entity factory returned null: "+entry.getKey().identifier());
            entity.snapTo(5,64,5);
            if(entity instanceof com.geckolib.animatable.GeoEntity geo) geo.getAnimatableInstanceCache().getManagerForId(entity.getId());
            var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());
            entity.saveWithoutId(output);
            var restored=entry.getValue().create(level,EntitySpawnReason.LOAD);
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),output.buildResult()));
            helper.assertValueEqual(restored.position(),entity.position(),"Entity position round trip: "+entry.getKey().identifier());
            entities++;
        }
        helper.assertValueEqual(entities,39,"Entity registry count");
        long items=BuiltInRegistries.ITEM.keySet().stream().filter(key->key.getNamespace().equals(Hp_end_expansion.MODID)).count();
        helper.assertValueEqual(items,89L,"Item registry count");
        var crossbow=new ItemStack(ModTidelight.TIDE_CROSSBOW.get());
        var round=ChargedProjectiles.of(new net.minecraft.world.item.ItemStackTemplate(Items.ARROW));
        crossbow.set(DataComponents.CHARGED_PROJECTILES,round);
        crossbow.set(ModTidelight.CROSSBOW_RESERVE.get(),List.of(round,round));
        var ops=level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded=ItemStack.CODEC.encodeStart(ops,crossbow).getOrThrow();
        var decoded=ItemStack.CODEC.parse(ops,encoded).getOrThrow();
        helper.assertValueEqual(TideCrossbowItem.loadedRounds(decoded),3,"Crossbow loaded round component round trip");
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        var weapon=new ItemStack(ModTidelight.TIDE_CROSSBOW.get());
        var bow=ModTidelight.TIDE_CROSSBOW.get();
        var arrows=new ItemStack(Items.ARROW,16);
        player.setItemInHand(InteractionHand.MAIN_HAND,weapon);
        player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(bow));
        player.getInventory().setItem(9,arrows);
        player.tick();
        helper.assertValueEqual(TideCrossbowItem.capacity(player),3,"Dual wield capacity must not stack");
        for(int i=1;i<=3;i++) {
            helper.assertTrue(bow.loadRound(weapon,player),"Reload failed at round "+i);
            helper.assertValueEqual(TideCrossbowItem.loadedRounds(weapon),i,"Reloaded round count");
        }
        helper.assertTrue(!bow.loadRound(weapon,player),"Reload exceeded capacity");
        helper.assertValueEqual(arrows.getCount(),13,"Reload ammunition consumption");
        for(int i=2;i>=0;i--) {
            bow.shootLoaded(player,InteractionHand.MAIN_HAND);
            helper.assertValueEqual(TideCrossbowItem.loadedRounds(weapon),i,"Shot must consume one round");
            player.getCooldowns().removeCooldown(BuiltInRegistries.ITEM.getKey(bow));
        }
        bow.use(level,player,InteractionHand.MAIN_HAND);
        for(int i=1;i<=3;i++) {
            helper.assertTrue(player.isUsingItem(),"Continuous reload stopped early");
            bow.onUseTick(level,player,weapon,bow.getUseDuration(weapon,player)-TideCrossbowItem.getChargeDuration(weapon,player));
            helper.assertValueEqual(TideCrossbowItem.loadedRounds(weapon),i,"Continuous reload round count");
        }
        helper.assertTrue(!player.isUsingItem(),"Continuous reload must stop at capacity");
        helper.assertValueEqual(arrows.getCount(),10,"Continuous reload ammunition consumption");
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        player.tick();
        helper.assertValueEqual(TideCrossbowItem.capacity(player),3,"Offhand capacity must remain after mainhand removal");
        player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        player.tick();
        helper.assertValueEqual(TideCrossbowItem.capacity(player),0,"Unheld crossbow must not retain capacity");
        helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.BIOME).get(id("starwreck_wastes")).isPresent(),"Starwreck biome missing");
        helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.BIOME).get(id("tidelight_reef")).isPresent(),"Tidelight biome missing");
        LogUtils.getLogger().info("Migration smoke test passed: entities={}, items={}, crossbowRounds=3, reload=true, firing=true, continuousReload=true, dualWieldCapacity=true",entities,items);
        helper.succeed();
    }
}
