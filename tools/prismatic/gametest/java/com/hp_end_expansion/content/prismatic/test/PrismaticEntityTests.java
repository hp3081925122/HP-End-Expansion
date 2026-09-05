package com.hp_end_expansion.content.prismatic.test;

import com.hp_end_expansion.content.prismatic.PrismaticContent;
import com.hp_end_expansion.content.prismatic.PrismaticEntities;
import com.hp_end_expansion.content.prismatic.entity.ParallaxRegent;
import com.hp_end_expansion.content.prismatic.entity.PrismHare;
import com.hp_end_expansion.content.prismatic.entity.PrismaticAnimal;
import com.hp_end_expansion.content.prismatic.entity.PrismaticMonster;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 验证真实服务端交互、存档与战斗时序，不以编译成功替代行为证据。
@GameTestHolder("hp_end_expansion")
@PrefixGameTestTemplate(false)
public final class PrismaticEntityTests {
    private PrismaticEntityTests() { }

    // 三种可再生产物都必须经过喂食、等待和空手收取，重载不能清掉冷却。
    @GameTest(template = "prismatic_empty", timeoutTicks = 250)
    public static void renewableProductsRespectSavedCooldown(GameTestHelper helper) {
        floor(helper, 1, 16, 1, 12);
        PrismaticAnimal[] animals = {
                helper.spawn(PrismaticEntities.SHARDLING.get(), 4, 2, 5),
                helper.spawn(PrismaticEntities.FACET_RAM.get(), 8, 2, 5),
                helper.spawn(PrismaticEntities.DUSK_MOTH.get(), 12, 2, 5)
        };
        String[] foods = {"crystal_shard", "crystal_bud", "dusk_bud"};
        String[] products = {"prism_meal", "horn_fragment", "dusk_silk"};
        Player[] players = new Player[3];
        for (int i = 0; i < animals.length; i++) {
            PrismaticAnimal animal = animals[i];
            animal.setNoAi(true);
            animal.setNoGravity(true);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            players[i] = player;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PrismaticContent.item(foods[i]), 4));
            animal.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.getMainHandItem().getCount() == 3, "Feeding must consume exactly one item");
            helper.assertTrue(animal.hasPendingProduct() && animal.productReadyTicks() > 0, "Feeding must begin delayed production");
            CompoundTag saved = animal.saveWithoutId(new CompoundTag());
            PrismaticAnimal copy = (PrismaticAnimal) animal.getType().create(helper.getLevel());
            helper.assertTrue(copy != null, "Animal must recreate from its registered type");
            copy.readAdditionalSaveData(saved);
            helper.assertTrue(copy.hasPendingProduct() && copy.productReadyTicks() == animal.productReadyTicks(), "Pending production must survive save/load");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            animal.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(itemCount(player, PrismaticContent.item(products[i])) == 0, "Unripe production must not yield an item");
        }
        helper.runAtTickTime(220, () -> {
            for (int i = 0; i < animals.length; i++) {
                PrismaticAnimal animal = animals[i];
                Player player = players[i];
                animal.mobInteract(player, InteractionHand.MAIN_HAND);
                helper.assertTrue(itemCount(player, PrismaticContent.item(products[i])) == 1, "Mature production must yield exactly one item");
                helper.assertTrue(animal.productCooldownTicks() > 0 && !animal.hasPendingProduct(), "Harvest must begin cooldown and clear production");
                animal.mobInteract(player, InteractionHand.MAIN_HAND);
                helper.assertTrue(itemCount(player, PrismaticContent.item(products[i])) == 1, "Repeated empty-hand use must not duplicate production");
                PrismaticAnimal copy = (PrismaticAnimal) animal.getType().create(helper.getLevel());
                copy.readAdditionalSaveData(animal.saveWithoutId(new CompoundTag()));
                helper.assertTrue(copy.productCooldownTicks() == animal.productCooldownTicks(), "Harvest cooldown must survive save/load");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PrismaticContent.item(foods[i]), 2));
                copy.mobInteract(player, InteractionHand.MAIN_HAND);
                helper.assertTrue(!copy.hasPendingProduct(), "Reloaded cooldown must prevent a new production cycle");
            }
            helper.succeed();
        });
    }

    // 棱兔通过原版繁殖目标实际产生幼体，幼体类型和持久化正确。
    @GameTest(template = "prismatic_empty", timeoutTicks = 200)
    public static void crystalBudsBreedPersistentHares(GameTestHelper helper) {
        floor(helper, 1, 18, 1, 18);
        PrismHare first = helper.spawn(PrismaticEntities.PRISM_HARE.get(), 7, 2, 8);
        PrismHare second = helper.spawn(PrismaticEntities.PRISM_HARE.get(), 9, 2, 8);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PrismaticContent.item("crystal_bud"), 2));
        first.mobInteract(player, InteractionHand.MAIN_HAND);
        second.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "Breeding must consume two crystal buds");
        helper.succeedWhen(() -> {
            var children = helper.getLevel().getEntitiesOfClass(PrismHare.class, AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(18, 8, 18))), PrismHare::isBaby);
            helper.assertTrue(children.size() == 1, "A pair of fed hares must create exactly one baby");
            helper.assertTrue(children.getFirst().isPersistenceRequired(), "Bred hare must remain persistent");
            helper.assertTrue(first.getAge() > 0 && second.getAge() > 0, "Parents must enter breeding cooldown");
        });
    }

    // 同一次锁线分别检查无掩体命中和中途放墙阻挡，同时证明前摇不会提前伤害。
    @GameTest(template = "prismatic_empty", timeoutTicks = 120)
    public static void bossBeamHasWindupAndRespectsNewCover(GameTestHelper helper) {
        floor(helper, 1, 42, 1, 24);
        ParallaxRegent exposedBoss = helper.spawn(PrismaticEntities.PARALLAX_REGENT.get(), 9, 2, 5);
        ParallaxRegent coveredBoss = helper.spawn(PrismaticEntities.PARALLAX_REGENT.get(), 32, 2, 5);
        Cow exposed = helper.spawn(EntityType.COW, 9, 2, 15);
        Cow covered = helper.spawn(EntityType.COW, 32, 2, 15);
        exposed.setNoAi(true);
        covered.setNoAi(true);
        exposedBoss.setTarget(exposed);
        coveredBoss.setTarget(covered);
        float exposedHealth = exposed.getHealth();
        float coveredHealth = covered.getHealth();
        boolean[] wallPlaced = {false};
        boolean[] windupObserved = {false};
        helper.onEachTick(() -> {
            if (exposedBoss.currentAttack() == PrismaticMonster.Attack.BEAM && exposedBoss.attackAge() >= 5 && exposedBoss.attackAge() < 20) {
                helper.assertTrue(exposed.getHealth() == exposedHealth, "Locked beam must not damage during windup");
                windupObserved[0] = true;
            }
            if (!wallPlaced[0] && coveredBoss.currentAttack() == PrismaticMonster.Attack.BEAM && coveredBoss.attackAge() >= 5) {
                for (int x = 29; x <= 35; x++) {
                    for (int y = 2; y <= 8; y++) { helper.setBlock(x, y, 10, Blocks.OBSIDIAN); }
                }
                wallPlaced[0] = true;
            }
            if (wallPlaced[0] && windupObserved[0] && exposedBoss.currentAttack() == PrismaticMonster.Attack.BEAM && exposedBoss.attackAge() > 28) {
                helper.assertTrue(exposed.getHealth() < exposedHealth, "Uncovered target must take damage at the beam hit frame");
                helper.assertTrue(covered.getHealth() == coveredHealth, "A solid wall added after lock-on must block beam damage");
                helper.succeed();
            }
        });
    }

    // 单枚晶针的真实碰撞必须在墙前结束，而不是穿透墙体命中目标。
    @GameTest(template = "prismatic_empty", timeoutTicks = 130)
    public static void crystalNeedleStopsAtCover(GameTestHelper helper) {
        floor(helper, 1, 18, 1, 22);
        var shooter = helper.spawn(PrismaticEntities.NEEDLE_SPITTER.get(), 8, 2, 5);
        Cow target = helper.spawn(EntityType.COW, 8, 2, 14);
        target.setNoAi(true);
        shooter.setTarget(target);
        float health = target.getHealth();
        boolean[] covered = {false};
        boolean[] projectileObserved = {false};
        helper.onEachTick(() -> {
            if (!covered[0] && shooter.currentAttack() == PrismaticMonster.Attack.NEEDLES && shooter.attackAge() >= 5) {
                for (int x = 5; x <= 11; x++) {
                    for (int y = 2; y <= 6; y++) { helper.setBlock(x, y, 11, Blocks.OBSIDIAN); }
                }
                covered[0] = true;
            }
            var needles = helper.getLevel().getEntitiesOfClass(com.hp_end_expansion.content.prismatic.entity.CrystalNeedle.class, shooter.getBoundingBox().inflate(15));
            if (!needles.isEmpty()) { projectileObserved[0] = true; }
            if (covered[0] && projectileObserved[0] && shooter.currentAttack() == PrismaticMonster.Attack.NEEDLES && shooter.attackAge() >= 34) {
                helper.assertTrue(target.getHealth() == health, "Crystal needle must not damage through a solid wall");
                helper.assertTrue(needles.isEmpty(), "Needle must be removed when colliding with a wall");
                helper.succeed();
            }
        });
    }

    // 阶段只上升一次，巢点和返巢状态保存，返巢不能偷偷回满血。
    @GameTest(template = "prismatic_empty", timeoutTicks = 30)
    public static void bossPhaseNestAndReturnStateSurviveSave(GameTestHelper helper) {
        floor(helper, 1, 47, 1, 15);
        ParallaxRegent boss = helper.spawn(PrismaticEntities.PARALLAX_REGENT.get(), 6, 2, 6);
        BlockPos expectedNest = boss.blockPosition();
        boss.setHealth(270);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(boss.phase() == 2, "Boss must enter phase two below sixty percent health");
            helper.assertTrue(boss.nest().equals(expectedNest), "First loaded position must become the nest");
            boss.setHealth(100);
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(boss.phase() == 3, "Boss must enter phase three below twenty-five percent health");
            ParallaxRegent copy = PrismaticEntities.PARALLAX_REGENT.get().create(helper.getLevel());
            copy.readAdditionalSaveData(boss.saveWithoutId(new CompoundTag()));
            helper.assertTrue(copy.phase() == 3 && copy.nest().equals(expectedNest), "Boss phase and nest must survive save/load");
            boss.setHealth(180);
            BlockPos away = helper.absolutePos(new BlockPos(46, 2, 6));
            boss.moveTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5, 0, 0);
        });
        helper.runAtTickTime(12, () -> {
            CompoundTag state = boss.saveWithoutId(new CompoundTag());
            helper.assertTrue(boss.phase() == 3, "Healing or reload must not regress the phase");
            helper.assertTrue(state.getBoolean("PrismaticReturning"), "Boss leaving nest radius must enter return state");
            helper.assertTrue(boss.getHealth() == 180, "Returning must not restore health");
            helper.assertTrue(boss.getTarget() == null, "Returning boss must not retain a combat target");
            helper.succeed();
        });
    }

    // 中立羊只对真正攻击者还手，首次顶击有前摇，十五秒后停止追仇。
    @GameTest(template = "prismatic_empty", timeoutTicks = 340)
    public static void ramRetaliatesWithWindupThenCalmsDown(GameTestHelper helper) {
        floor(helper, 1, 20, 1, 18);
        var ram = helper.spawn(PrismaticEntities.FACET_RAM.get(), 8, 2, 8);
        var attacker = helper.spawn(EntityType.IRON_GOLEM, 8, 2, 10);
        attacker.setNoAi(true);
        float health = attacker.getHealth();
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(ram.getTarget() == null && attacker.getHealth() == health, "Unprovoked ram must remain neutral");
            ram.hurt(ram.damageSources().mobAttack(attacker), 1.0F);
        });
        helper.runAtTickTime(20, () -> helper.assertTrue(attacker.getHealth() == health, "Ram headbutt must not hit during its windup"));
        helper.runAtTickTime(80, () -> helper.assertTrue(attacker.getHealth() < health, "Provoked ram must land a real headbutt"));
        helper.runAtTickTime(330, () -> {
            helper.assertTrue(ram.getTarget() == null, "Ram must stop retaliation after fifteen seconds");
            helper.succeed();
        });
    }

    // 暮翅蛾在实际花丛中使用飞行寻路移动，不退化成贴地步行生物。
    @GameTest(template = "prismatic_empty", timeoutTicks = 110)
    public static void duskMothMovesAboveFlowers(GameTestHelper helper) {
        for (int x = 1; x <= 22; x++) {
            for (int z = 1; z <= 22; z++) {
                helper.setBlock(x, 1, z, PrismaticContent.block("prism_soil"));
                helper.setBlock(x, 2, z, PrismaticContent.block("dusk_bloom"));
            }
        }
        var moth = helper.spawn(PrismaticEntities.DUSK_MOTH.get(), 11, 3, 11);
        var start = moth.position();
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(moth.isNoGravity(), "Dusk moth must remain in flight mode");
            helper.assertTrue(moth.position().distanceToSqr(start) > 0.05, "Dusk moth must navigate among nearby flowers");
            helper.assertTrue(moth.getY() > start.y - 0.5 && moth.getY() < start.y + 3.0, "Dusk moth must stay near flower height");
            helper.succeed();
        });
    }

    // 测试地基按当前模板相对坐标写入，避免把坠落伤害误判为技能伤害。
    private static void floor(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) { helper.setBlock(x, 1, z, Blocks.END_STONE); }
        }
    }

    private static int itemCount(Player player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) { total += stack.getCount(); }
        }
        return total;
    }
}
