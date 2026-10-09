package org.hp.hp_end_expansion.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class FlightAvoidance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG = Boolean.getBoolean("hp_end_expansion.debugFlightMovement");
    private final Mob mob;
    private Vec3 escapeTarget;
    private Vec3 progressPosition;
    private int escapeUntil;
    private int nextCheckTick;
    private int progressTick;
    private int lastSteerTick = -1;
    private int nextLogTick;

    public FlightAvoidance(Mob mob) {
        this.mob = mob;
    }

    public void reset() {
        escapeTarget = null;
        progressPosition = null;
        nextCheckTick = 0;
        lastSteerTick = -1;
    }

    public Vec3 steer(Vec3 target) {
        int tick = mob.tickCount;
        if (lastSteerTick < tick - 1) {
            reset();
        }
        lastSteerTick = tick;
        Vec3 delta = target.subtract(mob.position());
        if (delta.lengthSqr() < 0.25) {
            escapeTarget = null;
            progressPosition = null;
            return target;
        }
        if (tick < nextCheckTick) {
            return escapeTarget == null ? target : escapeTarget;
        }
        nextCheckTick = tick + 4;

        boolean stuck = false;
        if (progressPosition == null) {
            progressPosition = mob.position();
            progressTick = tick;
        } else if (tick - progressTick >= 20) {
            stuck = progressPosition.distanceToSqr(mob.position()) < 0.04;
            progressPosition = mob.position();
            progressTick = tick;
        }
        if (!stuck && escapeTarget != null && tick < escapeUntil
            && escapeTarget.distanceToSqr(mob.position()) > 0.36
            && clearPath(escapeTarget.subtract(mob.position()))) {
            return escapeTarget;
        }
        escapeTarget = null;
        double distance = Math.min(6.0, Math.max(2.0, mob.getBbWidth() * 0.5 + mob.getDeltaMovement().length() * 6.0));
        Vec3 direction = delta.normalize();
        if (!stuck && clearPath(direction.scale(Math.min(distance, delta.length())))) {
            return target;
        }

        Vec3 forward = new Vec3(direction.x, 0, direction.z);
        if (forward.lengthSqr() < 0.01) {
            forward = new Vec3(1, 0, 0);
        } else {
            forward = forward.normalize();
        }
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3[] candidates = {
            forward.add(0, 0.8, 0),
            forward.add(side), forward.subtract(side),
            new Vec3(0, 1, 0),
            side.add(0, 0.5, 0), side.scale(-1).add(0, 0.5, 0),
            side, side.scale(-1),
            new Vec3(0, -1, 0), forward.scale(-1)
        };
        Vec3 selected = Vec3.ZERO;
        double bestScore = -Double.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            double probeDistance = pass == 0 ? distance : distance * 0.5;
            for (Vec3 candidate : candidates) {
                Vec3 heading = candidate.normalize();
                Vec3 offset = heading.scale(probeDistance);
                if (!clearPath(offset)) {
                    continue;
                }
                double score = heading.dot(direction) + heading.y * 0.15;
                if (score > bestScore) {
                    bestScore = score;
                    selected = offset;
                }
            }
            if (selected.lengthSqr() > 0) {
                break;
            }
        }
        escapeTarget = mob.position().add(selected);
        escapeUntil = tick + 20;
        mob.setDeltaMovement(mob.getDeltaMovement().scale(0.5));
        if (DEBUG && tick >= nextLogTick) {
            nextLogTick = tick + 20;
            LOGGER.info("Flight avoidance: entity={} id={} stuck={} position={} target={} escape={} pathFound={}",
                mob.getType(), mob.getId(), stuck, mob.position(), target, escapeTarget, selected.lengthSqr() > 0);
        }
        return escapeTarget;
    }

    private boolean clearPath(Vec3 offset) {
        AABB swept = mob.getBoundingBox().deflate(0.05).expandTowards(offset);
        return mob.level().hasChunksAt(BlockPos.containing(swept.minX, swept.minY, swept.minZ),
            BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))
            && mob.level().noBlockCollision(mob, swept);
    }
}
