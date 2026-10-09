package com.rileycleavenger.heysir.entity.goal;

import com.rileycleavenger.heysir.entity.HeySirEntity;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/** After being paid, HeySir rides away from his player until he's out of range. */
public class LeaveGoal extends Goal {
	private final HeySirEntity mob;
	private int repathCooldown;

	public LeaveGoal(HeySirEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return this.mob.getMode() == HeySirEntity.Mode.LEAVE;
	}

	@Override
	public void start() {
		this.repathCooldown = 0;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (--this.repathCooldown > 0 && !this.mob.getNavigation().isDone()) {
			return;
		}
		this.repathCooldown = 40;
		ServerPlayer player = this.mob.getTargetPlayer();
		Vec3 awayFrom = player != null ? player.position() : this.mob.position().add(this.mob.getLookAngle().scale(-4.0));
		Vec3 destination = DefaultRandomPos.getPosAway(this.mob, 24, 7, awayFrom);
		if (destination != null) {
			this.mob.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.2);
		}
	}
}
