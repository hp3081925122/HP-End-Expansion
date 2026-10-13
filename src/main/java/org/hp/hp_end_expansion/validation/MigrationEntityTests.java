package org.hp.hp_end_expansion.validation;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class MigrationEntityTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,Hp_end_expansion.MODID);
    public static void register(IEventBus bus) {
        FUNCTIONS.register("entity_lifecycle",()->helper->MigrationServerTests.prepare(helper,MigrationEntityTests::lifecycle));
        FUNCTIONS.register(bus);
        bus.addListener(MigrationEntityTests::tests);
    }
    private static void tests(RegisterGameTestsEvent event) {
        var environment=event.registerEnvironment(MigrationServerTests.id("entity_validation"),new TestEnvironmentDefinition.AllOf());
        event.registerTest(MigrationServerTests.id("entity_lifecycle"),data->new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,MigrationServerTests.id("entity_lifecycle")),data),new TestData<>(environment,Identifier.withDefaultNamespace("empty"),600,0,true));
    }
    private static void lifecycle(GameTestHelper helper) {
        var level=helper.getLevel();
        var player=MigrationServerTests.player(helper);
        Vec3 origin=player.position();
        int livingCount=0,tickCount=0;
        for(var entry:BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            if(!entry.getKey().identifier().getNamespace().equals(Hp_end_expansion.MODID)) continue;
            String name=entry.getKey().identifier().getPath();
            Entity entity=entry.getValue().create(level,EntitySpawnReason.COMMAND);
            entity.snapTo(origin.add(0,0,4)); entity.setNoGravity(true);
            level.addFreshEntity(entity);
            try {
                for(int i=0;i<80&&!entity.isRemoved();i++) { level.tickNonPassenger(entity); tickCount++; }
                if(entity instanceof Mob mob) {
                    livingCount++;
                    helper.assertTrue(Float.isFinite(mob.getHealth())&&mob.getMaxHealth()>0,"Invalid health: "+name);
                    helper.assertTrue(Double.isFinite(mob.getX())&&Double.isFinite(mob.getY())&&Double.isFinite(mob.getZ()),"Invalid position: "+name);
                    mob.invulnerableTime=0;
                    mob.hurtOrSimulate(level.damageSources().playerAttack(player),1);
                    for(int i=0;i<20&&!mob.isRemoved();i++) level.tickNonPassenger(mob);
                    var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());
                    mob.saveWithoutId(output);
                    Entity restored=entry.getValue().create(level,EntitySpawnReason.LOAD);
                    restored.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),output.buildResult()));
                    helper.assertValueEqual(((LivingEntity)restored).getHealth(),mob.getHealth(),"Damaged mob health save: "+name);
                    var second=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess()); restored.saveWithoutId(second);
                    if(name.equals("star_bearer")) {
                        int action=output.buildResult().getIntOr("Action",0);
                        helper.assertValueEqual(second.buildResult().getIntOr("Action",0),action==5||action==6?action:0,"Star bearer interrupted action reset");
                    }
                    if(name.equals("tidecrown_anemone")) helper.assertValueEqual(second.buildResult().getIntOr("TidecrownBloomCooldown",0),Math.max(40,output.buildResult().getIntOr("TidecrownBloomCooldown",0)),"Anemone reload cooldown clamp");
                    for(String field:List.of("Phase","Enraged","Minion","Crystals","CrystalRegrow","StarHealth","StarBroken","Action","Vulnerable","MartyrTicks","ThrowCooldown","CastCooldown","Howled","FromStarRain","AloneTicks","ChargeCooldown","SummonCooldown","StompCooldown","SummonedByEgg","SummonedByBeacon","PartyScaled","BrokenCores","CoreHealth0","CoreHealth1","CoreHealth2","DevourCount","GuardsSummoned","ArenaX","ArenaY","ArenaZ","UltUnlocked","SkullBroken","SkullRecoveryTicks","SkullRestoreArmor","SkullRestoreSpeed","ShellDamage","TidecrownBloomCooldown")) {
                        var saved=output.buildResult().get(field);
                        if(saved!=null&&!field.equals("Action")&&!field.equals("TidecrownBloomCooldown")) helper.assertValueEqual(second.buildResult().get(field),saved,"Custom state save: "+name+"/"+field);
                    }
                    LogUtils.getLogger().info("Entity lifecycle validated: type={}, health={}, target={}",name,mob.getHealth(),mob.getTarget()==null?"none":mob.getTarget().getType());
                }
            } finally {
                entity.discard();
                MigrationServerTests.nearby(level,origin).stream().filter(e->e.getType().builtInRegistryHolder().getRegisteredName().startsWith(Hp_end_expansion.MODID+":" )).forEach(Entity::discard);
            }
        }
        helper.assertValueEqual(livingCount,21,"Living entity lifecycle count");
        MigrationServerTests.passed(helper,"39 entity tick/expiry paths, 21 living hurt/save paths; ticks="+tickCount);
    }
}
