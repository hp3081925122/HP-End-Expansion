package org.hp.hp_end_expansion.validation;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.starwreck.StarBoltEntity;
import org.hp.hp_end_expansion.item.*;
import org.hp.hp_end_expansion.network.*;
import org.hp.hp_end_expansion.registry.*;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

public final class MigrationServerTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS=DeferredRegister.create(Registries.TEST_FUNCTION,Hp_end_expansion.MODID);
    private record Test(String name,Consumer<GameTestHelper> run) {}
    private static final List<Test> TESTS=List.of(
        new Test("curios_auto_reload",MigrationServerTests::curiosAutoReload),
        new Test("crossbow_combat",MigrationServerTests::crossbowCombat),
        new Test("abyss_spine",MigrationServerTests::abyssSpine),
        new Test("star_bow_relics",MigrationServerTests::starBowRelics),
        new Test("star_bolt_effects",MigrationServerTests::starBoltEffects),
        new Test("wings_scythe_duels",MigrationServerTests::wingsScytheDuels),
        new Test("summoning",MigrationServerTests::summoning),
        new Test("data_resources",MigrationServerTests::dataResources)
    );
    public static void register(IEventBus bus) {
        for(Test test:TESTS) FUNCTIONS.register(test.name,()->helper->prepare(helper,test.run));
        FUNCTIONS.register(bus);
        bus.addListener(MigrationServerTests::tests);
        MigrationEntityTests.register(bus);
        MigrationWorldTests.register(bus);
        MigrationCombatTests.register(bus);
    }
    private static void tests(RegisterGameTestsEvent event) {
        var environment=event.registerEnvironment(id("server_validation"),new TestEnvironmentDefinition.AllOf());
        for(Test test:TESTS) event.registerTest(id(test.name),data->new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,id(test.name)),data),new TestData<>(environment,Identifier.withDefaultNamespace("empty"),600,0,true));
    }
    static Identifier id(String name) { return Identifier.fromNamespaceAndPath(Hp_end_expansion.MODID,name); }
    static void passed(GameTestHelper helper,String name) {
        LogUtils.getLogger().info("Server validation passed: {}",name);
        helper.succeed();
    }
    static void prepare(GameTestHelper helper,Consumer<GameTestHelper> run) {
        var level=helper.getLevel(); BlockPos center=helper.absolutePos(new BlockPos(8,8,8));
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) level.setChunkForced((center.getX()>>4)+dx,(center.getZ()>>4)+dz,true);
        var end=level.getServer().getLevel(Level.END);
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) end.setChunkForced(dx,dz,true);
        helper.runAfterDelay(20,()->run.accept(helper));
    }
    static void invoke(Object target,Class<?> owner,String name,Class<?>[] types,Object... args) {
        try {
            Method method=owner.getDeclaredMethod(name,types);
            method.setAccessible(true);
            method.invoke(target,args);
        } catch(ReflectiveOperationException e) { throw new IllegalStateException("Server validation invocation failed: "+owner.getSimpleName()+"."+name,e); }
    }
    static Player player(GameTestHelper helper) {
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 base=helper.absoluteVec(new Vec3(8,8,8));
        player.snapTo(base.x,100,base.z);
        player.setNoGravity(true);
        CuriosApi.getCuriosInventory(player).orElseThrow().loadDatapacks();
        return player;
    }
    static void tick(Player player) {
        ServerLevel level=(ServerLevel)player.level();
        ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
        level.tickNonPassenger(player);
    }
    static ICuriosItemHandler curios(Player player) { return CuriosApi.getCuriosInventory(player).orElseThrow(); }
    static void equip(Player player,Item... items) {
        var handler=curios(player);
        int slots=handler.getStacksHandler("relic").orElseThrow().getSlots();
        for(int i=0;i<slots;i++) handler.setEquippedCurio("relic",i,i<items.length?new ItemStack(items[i]):ItemStack.EMPTY);
        tick(player);
        for(Item item:items) if(!curios(player).isEquipped(item)) throw new IllegalStateException("Curio equipment rejected: "+BuiltInRegistries.ITEM.getKey(item));
    }
    static List<Entity> nearby(ServerLevel level,Vec3 origin) { return level.getEntities((Entity)null,new AABB(origin,origin).inflate(96)); }
    static Mob target(ServerLevel level,Vec3 position) {
        var mob=EntityType.COW.create(level,EntitySpawnReason.COMMAND);
        mob.snapTo(position);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        mob.setHealth(100);
        level.addFreshEntity(mob);
        return mob;
    }
    private static ChargedProjectiles round() { return ChargedProjectiles.of(new ItemStackTemplate(Items.ARROW)); }
    private static void charge(ItemStack weapon,int count) {
        weapon.set(DataComponents.CHARGED_PROJECTILES,count>0?round():ChargedProjectiles.EMPTY);
        weapon.set(ModTidelight.CROSSBOW_RESERVE.get(),java.util.Collections.nCopies(Math.max(0,count-1),round()));
    }
    private static void curiosAutoReload(GameTestHelper helper) {
        Player player=player(helper);
        ItemStack held=new ItemStack(ModTidelight.TIDE_CROSSBOW.get());
        player.setItemInHand(InteractionHand.MAIN_HAND,held);
        equip(player,ModItems.TIDECROWN_TENTACLE.get());
        helper.assertValueEqual(TideCrossbowItem.capacity(player),4,"Tentacle capacity");
        ItemStack fullest=new ItemStack(ModTidelight.TIDE_CROSSBOW.get());
        ItemStack empty=new ItemStack(ModTidelight.TIDE_CROSSBOW.get());
        player.getInventory().setItem(1,fullest);
        player.getInventory().setItem(2,empty);
        player.getInventory().setItem(9,new ItemStack(Items.ARROW,16));
        charge(held,1); charge(fullest,3);
        player.getCooldowns().removeCooldown(BuiltInRegistries.ITEM.getKey(ModItems.TIDECROWN_TENTACLE.get()));
        tick(player);
        helper.assertValueEqual(TideCrossbowItem.loadedRounds(fullest),4,"Auto reload selects fullest");
        helper.assertValueEqual(TideCrossbowItem.loadedRounds(held),1,"Auto reload ignores less loaded");
        for(int i=0;i<59;i++) tick(player);
        helper.assertValueEqual(TideCrossbowItem.loadedRounds(held),1,"Auto reload cooldown and full selection");
        tick(player); tick(player);
        helper.assertValueEqual(TideCrossbowItem.loadedRounds(empty),0,"Full crossbow must not fall through to empty one");
        charge(held,0); charge(fullest,0);
        player.getCooldowns().removeCooldown(BuiltInRegistries.ITEM.getKey(ModItems.TIDECROWN_TENTACLE.get()));
        tick(player);
        helper.assertValueEqual(TideCrossbowItem.loadedRounds(held),1,"Zero loaded tie follows inventory order");
        equip(player,ModItems.TIDE_REMNANT_CLAW.get());
        helper.assertValueEqual(TideCrossbowItem.capacity(player),4,"Claw capacity");
        equip(player,ModItems.TIDEBREAKER_HAMMER_CORE.get());
        helper.assertValueEqual(TideCrossbowItem.capacity(player),4,"Hammer core capacity");
        equip(player,ModItems.VOID_CORE.get(),ModItems.VOID_CORE.get());
        helper.assertValueEqual(TideCrossbowItem.capacity(player),6,"Duplicate void core must not stack");
        equip(player);
        helper.assertValueEqual(TideCrossbowItem.capacity(player),3,"Unequipped relic capacity removed");
        passed(helper,"Curios attributes, auto reload priority, tie order and cooldown");
    }
    private static void crossbowCombat(GameTestHelper helper) {
        Player player=player(helper);
        ServerLevel level=helper.getLevel();
        ItemStack weapon=new ItemStack(ModTidelight.TIDE_CROSSBOW.get());
        player.setItemInHand(InteractionHand.MAIN_HAND,weapon);
        equip(player,ModItems.TIDE_REMNANT_CLAW.get());
        player.getInventory().setItem(9,new ItemStack(Items.ARROW,16));
        helper.assertValueEqual(EnchantmentHelper.processProjectileCount(level,weapon,player,1),3,"Claw projectile count");
        helper.assertValueEqual(EnchantmentHelper.processProjectileCount(level,new ItemStack(Items.CROSSBOW),player,1),1,"Claw must not modify vanilla crossbow");
        helper.assertTrue(EnchantmentHelper.processProjectileSpread(level,weapon,player,0)>=10,"Crossbow spread");
        ModTidelight.TIDE_CROSSBOW.get().loadRound(weapon,player);
        helper.assertValueEqual(weapon.get(DataComponents.CHARGED_PROJECTILES).itemCopies().size(),3,"Actual multishot load");
        equip(player,ModItems.TIDEBREAKER_HAMMER_CORE.get());
        charge(weapon,8);
        ModTidelight.TIDE_CROSSBOW.get().shootLoaded(player,InteractionHand.MAIN_HAND);
        var arrows=level.getEntitiesOfClass(Arrow.class,player.getBoundingBox().inflate(20),e->e.getOwner()==player);
        helper.assertValueEqual(arrows.size(),1,"Crossbow actual projectile count");
        Arrow arrow=arrows.getFirst();
        helper.assertValueEqual(arrow.getPersistentData().getIntOr(TidebreakerHammerCoreItem.ARROW_DAMAGE_KEY,0),4,"Shot damage capped to current capacity");
        helper.assertTrue(arrow.getPersistentData().getBooleanOr(AbyssCrystalSpineItem.TIDE_ARROW_KEY,false),"Tide arrow marker");
        equip(player);
        Mob victim=target(level,player.position().add(4,0,0));
        try {
            arrow.setDeltaMovement(1,0,0); arrow.setBaseDamage(2); arrow.setCritArrow(false);
            invoke(arrow,AbstractArrow.class,"onHitEntity",new Class<?>[]{EntityHitResult.class},new EntityHitResult(victim));
            helper.assertValueEqual(victim.getHealth(),95F,"Impact damage recapped after relic removal");
            Arrow incoming=new Arrow(level,player,new ItemStack(Items.ARROW),null);
            float before=player.getHealth();
            helper.assertTrue(!player.hurtOrSimulate(level.damageSources().arrow(incoming,victim),5),"Held crossbow must block arrow damage");
            helper.assertValueEqual(player.getHealth(),before,"Arrow immunity health");
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND,weapon);
            helper.assertTrue(!player.hurtOrSimulate(level.damageSources().arrow(incoming,victim),5),"Offhand crossbow must block arrow damage");
            ThrownTrident trident=new ThrownTrident(level,victim,new ItemStack(Items.TRIDENT));
            player.invulnerableTime=0;
            helper.assertTrue(player.hurtOrSimulate(level.damageSources().trident(trident,victim),1),"Arrow immunity must not block tridents");
        } finally { victim.discard(); arrows.forEach(Entity::discard); }
        passed(helper,"Crossbow multishot, damage clamp, arrow immunity and trident exception");
    }
    private static void abyssSpine(GameTestHelper helper) {
        Player player=player(helper); ServerLevel level=helper.getLevel();
        equip(player,ModItems.ABYSS_CRYSTAL_SPINE.get());
        Vec3 hitPosition=player.position().add(0,1,0);
        BlockHitResult hit=new BlockHitResult(hitPosition,Direction.UP,BlockPos.containing(hitPosition).below(),false);
        Mob victim=target(level,hitPosition.add(10,0,0));
        try {
            victim.invulnerableTime=17;
            Arrow arrow=new Arrow(level,player,new ItemStack(Items.ARROW),null);
            arrow.getPersistentData().putBoolean(AbyssCrystalSpineItem.TIDE_ARROW_KEY,true);
            var impact=new ProjectileImpactEvent(arrow,hit);
            NeoForge.EVENT_BUS.post(impact);
            helper.assertValueEqual(victim.getHealth(),99F,"Passive target within twelve blocks must be hit");
            helper.assertValueEqual(victim.invulnerableTime,17,"Spine preserves invulnerable frames");
            helper.assertTrue(arrow.isRemoved()&&impact.isCanceled(),"Laser consumes arrow and cancels impact");
            victim.snapTo(hitPosition.add(13,0,0));
            Arrow outside=new Arrow(level,player,new ItemStack(Items.ARROW),null);
            outside.getPersistentData().putBoolean(AbyssCrystalSpineItem.TIDE_ARROW_KEY,true);
            var miss=new ProjectileImpactEvent(outside,hit); NeoForge.EVENT_BUS.post(miss);
            helper.assertTrue(!miss.isCanceled(),"Outside range must not trigger");
            helper.assertValueEqual(victim.getHealth(),99F,"Outside range damage");
            victim.snapTo(hitPosition.add(6,0,0));
            Arrow unmarked=new Arrow(level,player,new ItemStack(Items.ARROW),null);
            NeoForge.EVENT_BUS.post(new ProjectileImpactEvent(unmarked,hit));
            helper.assertValueEqual(victim.getHealth(),99F,"Unmarked arrow must not trigger");
            BlockPos wall=BlockPos.containing(hitPosition.add(3,0,0));
            level.setBlock(wall,Blocks.STONE.defaultBlockState(),3); level.setBlock(wall.above(),Blocks.STONE.defaultBlockState(),3);
            try { NeoForge.EVENT_BUS.post(new ProjectileImpactEvent(outside,hit)); helper.assertValueEqual(victim.getHealth(),99F,"Obstructed laser must not trigger"); }
            finally { level.setBlock(wall,Blocks.AIR.defaultBlockState(),3); level.setBlock(wall.above(),Blocks.AIR.defaultBlockState(),3); }
        } finally { victim.discard(); }
        passed(helper,"Abyss spine twelve block range, passive targets, visibility and invulnerable frames");
    }
    private static void starBowRelics(GameTestHelper helper) {
        Player player=player(helper); ServerLevel level=helper.getLevel();
        ItemStack weapon=new ItemStack(ModStarwreck.STAR_BOW.get());
        var bow=ModStarwreck.STAR_BOW.get();
        player.setItemInHand(InteractionHand.MAIN_HAND,weapon);
        Item[][] scenarios={new Item[]{},new Item[]{StarwreckEntities.HOLY_STAR.get()},new Item[]{StarwreckEntities.STARCALL_PENDANT.get()},new Item[]{StarwreckEntities.SKY_EYE.get()},new Item[]{StarwreckEntities.SKYRENDER_HORN.get()},new Item[]{StarwreckEntities.HOLY_STAR.get(),StarwreckEntities.STARCALL_PENDANT.get()}};
        for(int i=0;i<scenarios.length;i++) {
            equip(player,scenarios[i]);
            ItemStack ammo=new ItemStack(ModStarwreck.STAR_CRYSTAL_SHARD.get(),8); player.getInventory().setItem(9,ammo);
            bow.use(level,player,InteractionHand.MAIN_HAND);
            for(int t=0;t<(i==4?121:21)&&player.isUsingItem();t++) tick(player);
            if(player.isUsingItem()) player.releaseUsingItem();
            var bolts=level.getEntitiesOfClass(StarBoltEntity.class,player.getBoundingBox().inflate(16),e->e.getOwner()==player);
            helper.assertValueEqual(bolts.size(),i==2||i==5?3:1,"Star bow projectile count scenario "+i);
            helper.assertValueEqual(ammo.getCount(),i==1||i==5?8:7,"Star bow ammunition scenario "+i);
            if(i==4) helper.assertValueEqual(bolts.getFirst().getBaseDamage(),18D,"Horn full charge damage multiplier");
            bolts.forEach(Entity::discard); player.stopUsingItem();
        }
        equip(player);
        weapon.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.INFINITY),1);
        var ammo=new ItemStack(ModStarwreck.STAR_CRYSTAL_SHARD.get(),8); player.getInventory().setItem(9,ammo);
        bow.use(level,player,InteractionHand.MAIN_HAND);
        for(int t=0;t<21;t++) tick(player);
        player.releaseUsingItem();
        helper.assertValueEqual(ammo.getCount(),8,"Infinity must preserve star shard ammunition");
        level.getEntitiesOfClass(StarBoltEntity.class,player.getBoundingBox().inflate(16),e->e.getOwner()==player).forEach(Entity::discard);
        player.stopUsingItem();
        equip(player,StarwreckEntities.EMBER_CROWN.get());
        weapon.setDamageValue(12); ammo.setCount(2); player.tickCount=19; tick(player);
        helper.assertValueEqual(weapon.getDamageValue(),9,"Ember crown repair amount");
        helper.assertValueEqual(ammo.getCount(),1,"Ember crown repair cost");
        player.tickCount=39; tick(player);
        helper.assertValueEqual(ammo.getCount(),1,"Ember crown preserves final shard");
        passed(helper,"Star bow base, holy star, pendant, sky eye, horn, infinity and ember crown");
    }
    private static void wingsScytheDuels(GameTestHelper helper) {
        Player player=player(helper); ServerLevel level=helper.getLevel();
        var wings=new ItemStack(ModItems.DEVOURER_WINGS.get());
        var handler=curios(player);
        helper.assertTrue(handler.getStacksHandler("back").isPresent(),"Curios back slot missing");
        handler.setEquippedCurio("back",0,wings); tick(player);
        helper.assertTrue(DevourerWingsItem.getFlightStack(player,EquipmentSlot.CHEST)==wings,"Curios wings selected for flight");
        player.setOnGround(false); player.setDeltaMovement(0,-0.1,0);
        helper.assertTrue(player.tryToStartFallFlying(),"Curios wings must enable fall flying");
        player.setShiftKeyDown(true); Vec3 before=player.getDeltaMovement(); tick(player);
        helper.assertTrue(player.getDeltaMovement().distanceTo(before)>1,"Wings dash velocity");
        helper.assertTrue(player.getCooldowns().isOnCooldown(wings),"Wings dash cooldown");
        player.stopFallFlying(); player.setShiftKeyDown(false); player.setDeltaMovement(Vec3.ZERO);
        curios(player).setEquippedCurio("back",0,ItemStack.EMPTY); tick(player);
        helper.assertTrue(DevourerWingsItem.getFlightStack(player,EquipmentSlot.CHEST).isEmpty(),"Wings removed from flight slot");
        var scythe=new ItemStack(ModItems.MATRIARCH_SCYTHE.get()); player.setItemInHand(InteractionHand.MAIN_HAND,scythe);
        Vec3 start=player.position(); player.setYRot(0); player.setXRot(0);
        for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=8;dz++) for(int dy=0;dy<=3;dy++) level.setBlock(BlockPos.containing(start).offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);
        Mob victim=target(level,start.add(0,0,3));
        try {
            ModItems.MATRIARCH_SCYTHE.get().use(level,player,InteractionHand.MAIN_HAND);
            helper.assertTrue(player.position().distanceTo(start)>5.9,"Scythe dash distance");
            helper.assertValueEqual(victim.getHealth(),88F,"Scythe dash damage");
            helper.assertValueEqual(scythe.getDamageValue(),2,"Scythe durability cost");
            helper.assertTrue(player.getCooldowns().isOnCooldown(scythe),"Scythe cooldown");
        } finally { victim.discard(); }
        player.snapTo(start);
        BlockPos obstruction=BlockPos.containing(start).offset(0,0,3);
        for(int dy=0;dy<3;dy++) level.setBlock(obstruction.above(dy),Blocks.STONE.defaultBlockState(),2);
        try {
            ModItems.MATRIARCH_SCYTHE.get().use(level,player,InteractionHand.MAIN_HAND);
            helper.assertTrue(player.position().distanceTo(start)<3,"Scythe must stop before obstruction");
        } finally { for(int dy=0;dy<3;dy++) level.setBlock(obstruction.above(dy),Blocks.AIR.defaultBlockState(),2); }
        Mob first=target(level,player.position().add(0,0,2)); Mob second=target(level,player.position().add(0,0,4));
        first.setNoAi(false); second.setNoAi(false);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.MOB_DUEL_STICK.get()));
        try {
            NeoForge.EVENT_BUS.post(new AttackEntityEvent(player,first)); NeoForge.EVENT_BUS.post(new AttackEntityEvent(player,second));
            helper.assertTrue(first.getTarget()==second&&second.getTarget()==first,"Duel target pairing");
            first.setTarget(null); helper.assertTrue(first.getTarget()==second,"Duel target lock");
            player.setShiftKeyDown(true); NeoForge.EVENT_BUS.post(new AttackEntityEvent(player,first));
            helper.assertTrue(first.getTarget()==null&&second.getTarget()==null,"Duel cancellation");
        } finally { first.discard(); second.discard(); nearby(level,player.position()).stream().filter(e->e.getType().builtInRegistryHolder().getRegisteredName().equals("hp_end_expansion:rift_vfx")).forEach(Entity::discard); }
        passed(helper,"Curios flight, dash, scythe teleport damage and duel lock/cancel");
    }
    private static void starBoltEffects(GameTestHelper helper) {
        Player player=player(helper); ServerLevel level=helper.getLevel();
        Mob first=target(level,player.position().add(4,0,0)); Mob second=target(level,player.position().add(8,0,0));
        ItemStack weapon=new ItemStack(ModStarwreck.STAR_BOW.get());
        try {
            equip(player,StarwreckEntities.SKY_EYE.get(),StarwreckEntities.UNSTABLE_REMNANT_STAR.get());
            StarBoltEntity bolt=new StarBoltEntity(level,player,new ItemStack(ModStarwreck.STAR_CRYSTAL_SHARD.get()),weapon);
            bolt.snapTo(player.position()); bolt.setBaseDamage(8); bolt.setDeltaMovement(1,0,0);
            first.invulnerableTime=17;
            invoke(bolt,StarBoltEntity.class,"onHitEntity",new Class<?>[]{EntityHitResult.class},new EntityHitResult(first));
            helper.assertValueEqual(first.getHealth(),98F,"Sky eye quarter damage ignores existing frames");
            helper.assertTrue(nearby(level,first.position()).stream().anyMatch(e->e.getType()==StarwreckEntities.FALLING_STAR.get()),"Unstable star must summon falling star");
            equip(player);
            player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(StarwreckEntities.STAR_CORE_CHESTPLATE.get())); tick(player);
            StarBoltEntity bounce=new StarBoltEntity(level,player,new ItemStack(ModStarwreck.STAR_CRYSTAL_SHARD.get()),weapon);
            bounce.snapTo(player.position()); bounce.setBaseDamage(2); bounce.setDeltaMovement(1,0,0); first.invulnerableTime=0;
            invoke(bounce,StarBoltEntity.class,"onHitEntity",new Class<?>[]{EntityHitResult.class},new EntityHitResult(first));
            var bounced=level.getEntitiesOfClass(StarBoltEntity.class,player.getBoundingBox().inflate(24),e->e.getOwner()==player);
            helper.assertValueEqual(bounced.size(),1,"Chestplate bounce projectile");
            helper.assertTrue(bounced.getFirst().getDeltaMovement().length()>0,"Chestplate bounce velocity");
            var saved=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess());
            bounced.getFirst().saveWithoutId(saved);
            helper.assertValueEqual(saved.buildResult().getIntOr("ChestplateBounceCount",0),1,"Chestplate bounce persistent count");
        } finally {
            first.discard(); second.discard();
            nearby(level,player.position()).stream().filter(e->e instanceof StarBoltEntity||e.getType()==StarwreckEntities.FALLING_STAR.get()).forEach(Entity::discard);
        }
        passed(helper,"Star bolt sky eye damage, invulnerable frames, unstable star and chestplate bounce");
    }
    private static void summoning(GameTestHelper helper) {
        Player player=player(helper); ServerLevel level=helper.getLevel();
        var egg=new ItemStack(ModItems.RIFT_EGG.get()); var beacon=new ItemStack(ModItems.STAR_BEACON.get());
        BlockPos pos=BlockPos.containing(player.position()); var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        helper.assertValueEqual(ModItems.RIFT_EGG.get().useOn(new UseOnContext(level,player,InteractionHand.MAIN_HAND,egg,hit)),InteractionResult.FAIL,"Rift egg wrong dimension");
        helper.assertValueEqual(ModItems.STAR_BEACON.get().useOn(new UseOnContext(level,player,InteractionHand.MAIN_HAND,beacon,hit)),InteractionResult.FAIL,"Beacon wrong dimension");
        helper.assertValueEqual(egg.getCount(),1,"Failed egg must not consume");
        var end=level.getServer().getLevel(Level.END);
        Vec3 around=new Vec3(0,120,0);
        var endHit=new BlockHitResult(around,Direction.UP,BlockPos.containing(around),false);
        try {
            helper.assertValueEqual(ModItems.RIFT_EGG.get().useOn(new UseOnContext(end,null,InteractionHand.MAIN_HAND,egg,endHit)),InteractionResult.CONSUME,"Rift egg End summon");
            helper.assertValueEqual(ModItems.STAR_BEACON.get().useOn(new UseOnContext(end,null,InteractionHand.MAIN_HAND,beacon,endHit)),InteractionResult.CONSUME,"Beacon End summon");
            helper.assertTrue(nearby(end,around).stream().anyMatch(e->e.getType()==ModEntities.RIFT_MATRIARCH.get()),"Summoned matriarch missing");
            helper.assertTrue(nearby(end,around).stream().anyMatch(e->e.getType()==ModEntities.STAR_DEVOURER.get()),"Summoned devourer missing");
            helper.assertTrue(egg.isEmpty()&&beacon.isEmpty(),"Summoning item consumption");
        } finally { nearby(end,around).stream().filter(e->e.getType()==ModEntities.RIFT_MATRIARCH.get()||e.getType()==ModEntities.STAR_DEVOURER.get()).forEach(Entity::discard); }
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(StarwreckEntities.SKY_EYE_OMEN.get()));
        helper.assertValueEqual(StarwreckEntities.SKY_EYE_OMEN.get().use(level,player,InteractionHand.MAIN_HAND),InteractionResult.FAIL,"Sky omen wrong biome");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(StarwreckEntities.STARFALL_OMEN.get()));
        helper.assertValueEqual(StarwreckEntities.STARFALL_OMEN.get().use(level,player,InteractionHand.MAIN_HAND),InteractionResult.FAIL,"Starfall omen wrong biome");
        passed(helper,"End boss summoning, item consumption and dimension/biome rejection");
    }
    private static void dataResources(GameTestHelper helper) {
        var level=helper.getLevel();
        long recipes=level.getServer().getRecipeManager().getRecipes().stream().filter(r->r.id().identifier().getNamespace().equals(Hp_end_expansion.MODID)).count();
        helper.assertValueEqual(recipes,38L,"Loaded mod recipe count");
        var potion=new ItemStack(Items.POTION); potion.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.AWKWARD));
        var brewed=level.getServer().potionBrewing().mix(new ItemStack(ModTidelight.TIDELIGHT_GEL.get()),potion);
        helper.assertTrue(brewed.get(DataComponents.POTION_CONTENTS).potion().orElseThrow().is(Potions.SLOW_FALLING),"Tidelight gel brewing");
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
        try {
            var payload=new AbyssSpineBeamPayload(new Vec3(1.25,2.5,3.75),new Vec3(-4,5,6),5);
            AbyssSpineBeamPayload.STREAM_CODEC.encode(buf,payload);
            helper.assertValueEqual(AbyssSpineBeamPayload.STREAM_CODEC.decode(buf),payload,"Spine payload round trip");
            TideCrossbowShootPayload.STREAM_CODEC.encode(buf,TideCrossbowShootPayload.INSTANCE);
            helper.assertValueEqual(TideCrossbowShootPayload.STREAM_CODEC.decode(buf),TideCrossbowShootPayload.INSTANCE,"Crossbow payload round trip");
        } finally { buf.release(); }
        passed(helper,"Loaded recipes, brewing and network payload codecs");
    }
}
