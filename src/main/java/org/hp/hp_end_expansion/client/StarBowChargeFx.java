package org.hp.hp_end_expansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.hp.hp_end_expansion.item.StarBowItem;
import org.hp.hp_end_expansion.registry.ModParticles;

public final class StarBowChargeFx {
    private static final int FULL_TICKS = 20;
    private static final double TIP_NDC_X = 0.39;
    private static final double TIP_NDC_Y = -0.19;

    private StarBowChargeFx() {
    }

    /** 戴裂天之角时最多蓄满几次（蓄力上限的逻辑在 StarBowItem，这里只管特效，按同样的上限封顶）。 */
    public static final int MAX_STAGES = 3;

    /** 已经蓄满了几次：没戴裂天之角最多 1 次，戴了最多 {@link #MAX_STAGES} 次。 */
    public static int stage(LivingEntity user) {
        int n = (int) (StarBowItem.getChargeTicks(user) / FULL_TICKS);
        boolean horn = user instanceof Player p && StarBowItem.hasSkyrenderHorn(p);
        return Math.min(n, horn ? MAX_STAGES : 1);
    }

    public static void tick(Level level, LivingEntity user) {
        float ticks = StarBowItem.getChargeTicks(user);
        float charge = StarBowItem.getChargeProgress(user);
        int rawTicks = user.getTicksUsingItem();
        Vec3 tip = toWorld(user, 1, 0, 0, 0);
        int id = user.getId();
        RandomSource random = user.getRandom();
        boolean horn = user instanceof Player p && StarBowItem.hasSkyrenderHorn(p);
        int maxStage = horn ? MAX_STAGES : 1;
        int stage = stage(user);
        // 上一拍的蓄力直接按上一拍的拉弓拍数重算。不能按比例倒推：戴裂天之角时蓄力封顶在 60，
        // 倒推出来永远略小于 60，会每拍都判成"刚蓄满第三次"，每拍叠一道新光环 + 一圈炸点 + 两声清鸣
        float prevTicks = rawTicks > 0 ? StarBowItem.getChargeTicks(user, rawTicks - 1) : 0;
        int prevStage = Math.min((int) (prevTicks / FULL_TICKS), maxStage);
        if (stage > prevStage) onStage(level, tip, id, stage, horn);
        if (stage < maxStage && charge < 1.0F) {
            // 还在往下一段蓄：光点继续汇进矢尖
            int streams = 1 + (int) (charge * 3.0F);
            for (int i = 0; i < streams; i++) {
                level.addParticle(ModParticles.STAR_CHARGE.get(), tip.x, tip.y, tip.z, id,
                    random.nextDouble(), charge);
            }
            if (rawTicks >= 6 && rawTicks % 2 == 0) {
                level.addParticle(ModParticles.STAR_CHARGE.get(), tip.x, tip.y, tip.z, id,
                    1.0 + random.nextDouble(), charge);
            }
            return;
        }
        // 蓄到顶了：偶尔还有一两粒星光绕进来
        if (rawTicks % 3 == 0) {
            for (int i = 0; i < 2; i++) {
                level.addParticle(ModParticles.STAR_CHARGE.get(), tip.x, tip.y, tip.z, id,
                    1.0 + random.nextDouble(), 1.0);
            }
        }
    }

    /** 蓄满第 n 次那一拍：清鸣一声（一次比一次高）、一圈星点炸开；戴裂天之角时在矢尖套上第 n 道光环。 */
    private static void onStage(Level level, Vec3 tip, int id, int n, boolean horn) {
        level.playLocalSound(tip.x, tip.y, tip.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.35F + 0.2F * (n - 1), false);
        if (n > 1) level.playLocalSound(tip.x, tip.y, tip.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F + 0.25F * n, false);
        int burst = 12 + 4 * (n - 1);
        for (int i = 0; i < burst; i++) {
            level.addParticle(ModParticles.STAR_CHARGE.get(), tip.x, tip.y, tip.z, id,
                2.0 + (i + 0.5) / burst, n);
        }
        if (n == 1) {
            for (int i = 0; i < 5; i++) {
                level.addParticle(ModParticles.STAR_CHARGE.get(), tip.x, tip.y, tip.z, id,
                    4.0 + i / 5.0, 1.0);
            }
        }
        if (horn) {
            level.addParticle(ModParticles.STAR_CHARGE.get(), tip.x, tip.y, tip.z, id, 5.0 + n / 10.0, 1.0);
        }
    }

    public static boolean full(LivingEntity user) {
        return StarBowItem.getChargeTicks(user) >= FULL_TICKS;
    }

    private static boolean firstPerson(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return entity != null && entity == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
    }

    public static float sizeScale(Entity entity) {
        return firstPerson(entity) ? 0.2F : 1.9F;
    }

    public static Vec3 toWorld(LivingEntity entity, float partialTick, double localRight, double localUp, double localForward) {
        Vec3 eye = entity.getEyePosition(partialTick);
        Vec3 look = entity.getViewVector(partialTick);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-4) {
            float yaw = entity.getViewYRot(partialTick) * ((float) Math.PI / 180.0F);
            right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        }
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();
        HumanoidArm arm = entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
        double side = arm == HumanoidArm.RIGHT ? 1 : -1;
        double forward;
        double rightOffset;
        double upOffset;
        if (firstPerson(entity)) {
            Minecraft minecraft = Minecraft.getInstance();
            double fov = minecraft.options.fov().get() * (entity instanceof AbstractClientPlayer player ? player.getFieldOfViewModifier() : 1);
            double tanY = Math.tan(Math.toRadians(fov / 2));
            double aspect = (double) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
            forward = 0.6;
            rightOffset = TIP_NDC_X * side * tanY * aspect * forward;
            upOffset = TIP_NDC_Y * tanY * forward;
            localRight *= 0.5;
            localUp *= 0.5;
            localForward *= 0.5;
        } else {
            forward = 0.95;
            rightOffset = 0.12 * side;
            upOffset = -0.22;
        }
        return eye.add(look.scale(forward + localForward)).add(right.scale(rightOffset + localRight)).add(up.scale(upOffset + localUp));
    }
}
