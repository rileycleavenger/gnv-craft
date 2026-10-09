package com.rileycleavenger.gnvcraft.entity.goal;

import com.rileycleavenger.gnvcraft.entity.HeySirEntity;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/** Rides (or walks the bike) toward HeySir's player and hangs around 2-4 blocks away. */
public class FollowTargetPlayerGoal extends Goal {
	private static final double START_DISTANCE = 4.0;
	private static final double STOP_DISTANCE = 2.5;

	private final HeySirEntity mob;
	private @Nullable ServerPlayer target;
	private int repathCooldown;

	public FollowTargetPlayerGoal(HeySirEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	private boolean isChasing() {
		HeySirEntity.Mode mode = this.mob.getMode();
		return mode == HeySirEntity.Mode.FOLLOW || mode == HeySirEntity.Mode.APPROACH;
	}

	@Override
	public boolean canUse() {
		if (!this.isChasing()) {
			return false;
		}
		ServerPlayer player = this.mob.getTargetPlayer();
		if (player == null || this.mob.distanceTo(player) < START_DISTANCE) {
			return false;
		}
		this.target = player;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return this.isChasing()
			&& this.target != null
			&& this.target.isAlive()
			&& this.target.level() == this.mob.level()
			&& this.mob.distanceTo(this.target) > STOP_DISTANCE;
	}

	@Override
	public void start() {
		this.repathCooldown = 0;
	}

	@Override
	public void stop() {
		this.target = null;
		this.mob.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (this.target == null) {
			return;
		}
		this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
		if (--this.repathCooldown <= 0) {
			this.repathCooldown = 10;
			this.mob.getNavigation().moveTo(this.target, 1.0);
		}
	}
}
