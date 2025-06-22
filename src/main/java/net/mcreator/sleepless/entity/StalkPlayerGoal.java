package net.mcreator.sleepless.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Custom goal for the Sleepless entity to stalk players from a distance.
 * The entity approaches a player that isn't currently looking at it and
 * stops once close enough for melee attacks.
 */
public class StalkPlayerGoal extends Goal {
    private final SleeplessEntity mob;
    private final double speed;
    private final double stopDistance;
    private Player target;

    public StalkPlayerGoal(SleeplessEntity mob, double speed, double stopDistance) {
        this.mob = mob;
        this.speed = speed;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player player = this.mob.level().getNearestPlayer(this.mob, 20);
        if (player != null && !isPlayerLooking(player)) {
            this.target = player;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.target.isAlive()
                && this.mob.distanceToSqr(this.target) > (stopDistance * stopDistance)
                && !isPlayerLooking(this.target);
    }

    @Override
    public void stop() {
        this.target = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.target == null)
            return;
        this.mob.getNavigation().moveTo(this.target, speed);
        this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
    }

    private boolean isPlayerLooking(Player player) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 diff = mob.position().subtract(player.getEyePosition()).normalize();
        return look.dot(diff) > 0.8;
    }
}

