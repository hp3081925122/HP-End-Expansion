package org.hp.hp_end_expansion.validation;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;

public final class MigrationCombatTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,Hp_end_expansion.MODID);
    private record Skills(String entity,String starter,String getter,int[] ids,String[] animations) {}
    private static final List<Skills> SKILLS=List.of(
        new Skills("rift_mantis","startSkill","getSkill",new int[]{1,2,3,4},new String[]{"slash","blink","blade","tear"}),
        new Skills("rift_matriarch","startSkill","getSkill",new int[]{1,2,3,4,5,6,7},new String[]{"triple_slash","hunt","storm","web","summon","field","finale"}),
        new Skills("void_ray","startSkill","getSkill",new int[]{1,2,3,4},new String[]{"dive","beam","vortex","starfall"}),
        new Skills("star_devourer","startSkill","getSkill",new int[]{1,2,3,4,5,6,8},new String[]{"dive","beam","black_hole","star_rain","wing_spin","devour","roar"}),
        new Skills("tidebreaker_shrimp","start","skill",new int[]{1,2,3,4},null),
        new Skills("tidebreaker_warlord","start","skill",new int[]{1,2,3,4,5,6,7,8},null),
        new Skills("tidecrown_anemone","start","skill",new int[]{1,2,3},null),
        new Skills("tidelamp_wyrm","startSkill","getSkill",new int[]{1,2,3,4,5,6,7},null),
        new Skills("tide_remnant_hermit_crab","startSkill","getSkill",new int[]{1,2,5,6,7},null),
        new Skills("star_bearer","begin","action",new int[]{1,2,3,4,5,7,8,9},new String[]{"slam","backfire","toss","slam_fast","shatter","rage","sweep","beam"}),
        new Skills("star_chaser","startState","getState",new int[]{1,2,3,4,5,6,7,8,10},null),
        new Skills("skyrender","startState","getState",new int[]{3,4,5,6,7,8,9,10,11,12,13},null)
    );
    public static void register(IEventBus bus) {
        FUNCTIONS.register("combat_skill_paths",()->helper->MigrationServerTests.prepare(helper,MigrationCombatTests::skills));
        FUNCTIONS.register("neutrality_rewards",()->helper->MigrationServerTests.prepare(helper,MigrationCombatTests::neutralityRewards));
        FUNCTIONS.register("star_rain_omen",()->helper->MigrationServerTests.prepare(helper,MigrationCombatTests::starRain));
        FUNCTIONS.register(bus);
        bus.addListener(MigrationCombatTests::tests);
    }
    private static void tests(RegisterGameTestsEvent event) {
        var environment=event.registerEnvironment(MigrationServerTests.id("combat_validation"),new TestEnvironmentDefinition.AllOf());
        event.registerTest(MigrationServerTests.id("combat_skill_paths"),data->new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,MigrationServerTests.id("combat_skill_paths")),data),new TestData<>(environment,Identifier.withDefaultNamespace("empty"),1200,0,true));
        for(String name:List.of("neutrality_rewards","star_rain_omen")) event.registerTest(MigrationServerTests.id(name),data->new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,MigrationServerTests.id(name)),data),new TestData<>(environment,Identifier.withDefaultNamespace("empty"),1200,0,true));
    }
    private static ServerPlayer bot(GameTestHelper helper,Vec3 origin) {
        var level=helper.getLevel(); var server=level.getServer();
        var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"migration-bot"),false);
        var player=new ServerPlayer(server,level,cookie.gameProfile(),cookie.clientInformation()) {
            @Override public GameType gameMode() { return GameType.SURVIVAL; }
        };
        var connection=new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        player.connection=new ServerGamePacketListenerImpl(server,connection,player,cookie) {
            @Override public void send(Packet<?> packet) {}
        };
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); player.setHealth(1000);
        player.snapTo(origin); player.setOnGround(true); player.setNoGravity(true);
        level.addNewPlayer(player);
        return player;
    }
    private static int state(Mob actor,String name) {
        try {
            var method=actor.getClass().getDeclaredMethod(name); method.setAccessible(true);
            return ((Number)method.invoke(actor)).intValue();
        } catch(ReflectiveOperationException e) { throw new IllegalStateException("Skill state getter failed: "+actor.getClass()+"."+name,e); }
    }
    private static void stage(Mob actor) {
        var level=actor.level(); var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());
        actor.saveWithoutId(output); var tag=output.buildResult();
        tag.putInt("Phase",3); tag.putBoolean("Enraged",true); tag.putBoolean("UltUnlocked",true);
        actor.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),tag));
    }
    private static void skills(GameTestHelper helper) {
        var level=helper.getLevel(); Vec3 root=MigrationServerTests.player(helper).position();
        for(int dx=-20;dx<=20;dx++) for(int dz=-20;dz<=20;dz++) level.setBlock(BlockPos.containing(root).offset(dx,-1,dz),Blocks.END_STONE.defaultBlockState(),2);
        ServerPlayer player=bot(helper,root.add(0,0,4));
        int cases=0; double damageTotal=0;
        try {
            for(Skills spec:SKILLS) {
                double entityDamage=0;
                for(int i=0;i<spec.ids.length;i++) {
                    Mob actor=(Mob)BuiltInRegistries.ENTITY_TYPE.get(MigrationServerTests.id(spec.entity)).orElseThrow().value().create(level,EntitySpawnReason.COMMAND);
                    actor.snapTo(root); actor.setNoGravity(true); actor.setOnGround(true);
                    level.addFreshEntity(actor); actor.finalizeSpawn(level,level.getCurrentDifficultyAt(actor.blockPosition()),EntitySpawnReason.COMMAND,null);
                    stage(actor); actor.setTarget(player);
                    int action=spec.ids[i];
                    if(spec.animations!=null) MigrationServerTests.invoke(actor,actor.getClass(),spec.starter,new Class<?>[]{int.class,String.class},action,spec.animations[i]);
                    else if(spec.entity.equals("tidebreaker_shrimp")||spec.entity.equals("tidebreaker_warlord")) MigrationServerTests.invoke(actor,actor.getClass(),spec.starter,new Class<?>[]{int.class,LivingEntity.class},action,player);
                    else if(spec.entity.equals("tidecrown_anemone")) MigrationServerTests.invoke(actor,actor.getClass(),spec.starter,new Class<?>[]{byte.class,LivingEntity.class,Vec3.class},(byte)action,player,player.position().subtract(0,0.1,0));
                    else MigrationServerTests.invoke(actor,actor.getClass(),spec.starter,new Class<?>[]{byte.class},(byte)action);
                    helper.assertValueEqual(state(actor,spec.getter),action,"Skill start state: "+spec.entity+"/"+action);
                    double actionDamage=0;
                    for(int tick=0;tick<180;tick++) {
                        ((ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
                        player.snapTo(root.add(0,0,4)); player.setHealth(1000); player.invulnerableTime=0; player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true);
                        if(!actor.isRemoved()) level.tickNonPassenger(actor);
                        for(Entity entity:MigrationServerTests.nearby(level,root)) if(entity!=actor&&entity!=player&&entity.getType().builtInRegistryHolder().getRegisteredName().startsWith(Hp_end_expansion.MODID+":" )&&!entity.isRemoved()) level.tickNonPassenger(entity);
                        actionDamage+=Math.max(0,1000-player.getHealth());
                        helper.assertTrue(Double.isFinite(actor.getX())&&Double.isFinite(actor.getY())&&Double.isFinite(actor.getZ()),"Non finite combat position: "+spec.entity);
                    }
                    entityDamage+=actionDamage; cases++;
                    LogUtils.getLogger().info("Combat skill path executed: entity={}, skill={}, damage={}",spec.entity,action,actionDamage);
                    actor.discard();
                    MigrationServerTests.nearby(level,root).stream().filter(e->e!=player&&e.getType().builtInRegistryHolder().getRegisteredName().startsWith(Hp_end_expansion.MODID+":" )).forEach(Entity::discard);
                }
                helper.assertTrue(entityDamage>0,"No combat damage observed for "+spec.entity);
                damageTotal+=entityDamage;
            }
            for(String name:List.of("star_caller","star_martyr","ember_beetle","meteor_tortoise")) {
                Mob actor=(Mob)BuiltInRegistries.ENTITY_TYPE.get(MigrationServerTests.id(name)).orElseThrow().value().create(level,EntitySpawnReason.COMMAND);
                for(int dx=-6;dx<=6;dx++) for(int dz=-6;dz<=6;dz++) level.setBlock(BlockPos.containing(root).offset(dx,-1,dz),Blocks.END_STONE.defaultBlockState(),2);
                actor.snapTo(root); actor.setNoGravity(false); actor.setOnGround(true); level.addFreshEntity(actor); actor.setTarget(player);
                if(name.equals("star_martyr")) { actor.setHealth(3); actor.hurtOrSimulate(level.damageSources().playerAttack(player),1); }
                if(name.equals("meteor_tortoise")) actor.hurtOrSimulate(level.damageSources().playerAttack(player),1);
                double damage=0;
                for(int tick=0;tick<240;tick++) {
                    ((ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
                    player.setHealth(1000); player.invulnerableTime=0; player.snapTo(root.add(0,0,name.equals("ember_beetle")||name.equals("star_martyr")?1:3)); player.setDeltaMovement(Vec3.ZERO);
                    if(!actor.isRemoved()) level.tickNonPassenger(actor);
                    for(Entity entity:MigrationServerTests.nearby(level,root)) if(entity!=actor&&entity!=player&&entity.getType().builtInRegistryHolder().getRegisteredName().startsWith(Hp_end_expansion.MODID+":" )&&!entity.isRemoved()) level.tickNonPassenger(entity);
                    damage+=Math.max(0,1000-player.getHealth());
                }
                helper.assertTrue(damage>0,"Natural combat did not deal damage: "+name);
                cases++; damageTotal+=damage; actor.discard();
                MigrationServerTests.nearby(level,root).stream().filter(e->e!=player&&e.getType().builtInRegistryHolder().getRegisteredName().startsWith(Hp_end_expansion.MODID+":" )).forEach(Entity::discard);
                LogUtils.getLogger().info("Natural combat validated: entity={}, damage={}",name,damage);
            }
        } finally {
            level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
            MigrationServerTests.nearby(level,root).stream().filter(e->e.getType().builtInRegistryHolder().getRegisteredName().startsWith(Hp_end_expansion.MODID+":" )).forEach(Entity::discard);
        }
        MigrationServerTests.passed(helper,"Combat state paths and natural attacks: cases="+cases+", damage="+damageTotal);
    }
    private static void neutralityRewards(GameTestHelper helper) {
        var level=helper.getLevel(); Vec3 root=MigrationServerTests.player(helper).position();
        ServerPlayer player=bot(helper,root.add(0,0,4));
        try {
            for(String name:List.of("rift_mantis","void_ray","ember_beetle","meteor_tortoise","tidebreaker_shrimp","tidecrown_anemone","tidelamp_wyrm","tide_remnant_hermit_crab")) {
                Mob actor=(Mob)BuiltInRegistries.ENTITY_TYPE.get(MigrationServerTests.id(name)).orElseThrow().value().create(level,EntitySpawnReason.COMMAND);
                actor.snapTo(root); actor.setNoGravity(true); actor.setOnGround(true); level.addFreshEntity(actor);
                try {
                    for(int i=0;i<60;i++) level.tickNonPassenger(actor);
                    helper.assertTrue(actor.getTarget()==null,"Neutral mob attacked without provocation: "+name);
                    helper.assertTrue(actor.hurtOrSimulate(level.damageSources().playerAttack(player),1),"Neutral mob hurt rejected: "+name);
                    for(int i=0;i<20;i++) level.tickNonPassenger(actor);
                    helper.assertTrue(actor.getTarget()==player,"Neutral mob did not retaliate: "+name);
                } finally { actor.discard(); }
            }
            for(String name:List.of("rift_matriarch","star_devourer")) {
                Mob boss=(Mob)BuiltInRegistries.ENTITY_TYPE.get(MigrationServerTests.id(name)).orElseThrow().value().create(level,EntitySpawnReason.COMMAND);
                boss.snapTo(root); level.addFreshEntity(boss);
                boss.setHealth(0); boss.die(level.damageSources().playerAttack(player));
                String rewardKey=name.equals("rift_matriarch")?"hp_end_expansion.matriarch_first_kill":"hp_end_expansion.devourer_first_kill";
                helper.assertTrue(player.getPersistentData().getBooleanOr(rewardKey,false),"Boss first kill reward flag: "+name);
                var item=name.equals("rift_matriarch")?org.hp.hp_end_expansion.registry.ModItems.MATRIARCH_SCYTHE.get():org.hp.hp_end_expansion.registry.ModItems.DEVOURER_WINGS.get();
                helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,player.getBoundingBox().inflate(8),e->e.getItem().is(item)).size()>0,"Boss first kill reward item: "+name);
                for(int i=0;i<100&&!boss.isRemoved();i++) level.tickNonPassenger(boss);
                helper.assertTrue(boss.isRemoved(),"Boss death lifetime: "+name);
            }
        } finally {
            level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
            MigrationServerTests.nearby(level,root).stream().filter(e->e instanceof net.minecraft.world.entity.item.ItemEntity||e.getType().builtInRegistryHolder().getRegisteredName().startsWith(Hp_end_expansion.MODID+":" )).forEach(Entity::discard);
        }
        MigrationServerTests.passed(helper,"Eight neutral retaliation behaviors and two boss first kill/death rewards");
    }
    private static void starRain(GameTestHelper helper) {
        var level=helper.getLevel(); BlockPos center=new BlockPos(256,64,256);
        var biome=level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(org.hp.hp_end_expansion.worldgen.StarwreckWorldgen.BIOME);
        for(int cx=10;cx<=21;cx++) for(int cz=10;cz<=21;cz++) {
            var chunk=level.getChunk(cx,cz);
            chunk.fillBiomesFromNoise((qx,qy,qz,sampler)->biome,level.getChunkSource().randomState().sampler());
            chunk.markUnsaved();
        }
        for(int dx=-80;dx<=80;dx++) for(int dz=-80;dz<=80;dz++) level.setBlock(center.offset(dx,0,dz),Blocks.END_STONE.defaultBlockState(),2);
        ServerPlayer player=bot(helper,Vec3.atBottomCenterOf(center.above()));
        try {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(org.hp.hp_end_expansion.registry.StarwreckEntities.STARFALL_OMEN.get(),2));
            var item=org.hp.hp_end_expansion.registry.StarwreckEntities.STARFALL_OMEN.get();
            helper.assertValueEqual(item.use(level,player,net.minecraft.world.InteractionHand.MAIN_HAND),net.minecraft.world.InteractionResult.CONSUME,"Star rain omen summon");
            helper.assertTrue(org.hp.hp_end_expansion.entity.starwreck.StarRain.active(level),"Star rain active state");
            helper.assertValueEqual(player.getMainHandItem().getCount(),1,"Star rain omen consumption");
            helper.assertValueEqual(item.use(level,player,net.minecraft.world.InteractionHand.MAIN_HAND),net.minecraft.world.InteractionResult.FAIL,"Active rain must reject repeated summon");
            helper.assertValueEqual(player.getMainHandItem().getCount(),1,"Busy rain must not consume omen");
            helper.assertTrue(level.getEntities(org.hp.hp_end_expansion.registry.StarwreckEntities.STAR_RIFT.get(),Entity::isAlive).size()>0,"Star rain rifts missing");
        } finally {
            level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
            level.getEntities(org.hp.hp_end_expansion.registry.StarwreckEntities.STAR_RIFT.get(),Entity::isAlive).forEach(Entity::discard);
        }
        MigrationServerTests.passed(helper,"Star rain omen, weather state, rift creation and busy rejection");
    }
}
