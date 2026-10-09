package com.rileycleavenger.gnvcraft.entity;

import com.rileycleavenger.gnvcraft.director.HeySirDirector;
import com.rileycleavenger.gnvcraft.npc.PlaneDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A single-prop plane parked at GNV airport. W/S = throttle, A/D = yaw, look up/down = pitch, jump = brake on the ground.
 * It needs {@link #TAKEOFF_SPEED} blocks/tick of airspeed to lift off, stalls and sinks below that, and blows up on a hard impact.
 */
public class PlaneEntity extends Mob {
	public static final double MAX_SPEED = 1.5;
	public static final double TAKEOFF_SPEED = 0.8;
	private static final double GRAVITY = 0.06;
	private static final double CRASH_SPEED = 0.65;

	/** 0..1, only simulated on the pilot's client. */
	private float throttle;
	private double speed;
	private boolean crashed;

	public PlaneEntity(EntityType<? extends PlaneEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.2);
	}

	public double speed() {
		return this.speed;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!this.level().isClientSide() && !this.isVehicle()) {
			player.startRiding(this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof Player player ? player : null;
	}

	@Override
	protected void tickRidden(Player pilot, Vec3 riddenInput) {
		super.tickRidden(pilot, riddenInput);
		boolean airborne = !this.onGround();
		if (pilot.zza > 0) {
			this.throttle = Math.min(1.0F, this.throttle + 0.02F);
		} else if (pilot.zza < 0) {
			this.throttle = Math.max(0.0F, this.throttle - 0.03F);
		}
		if (pilot.isJumping() && !airborne) {
			this.throttle = Math.max(0.0F, this.throttle - 0.08F);
			this.speed *= 0.9;
		}
		double target = this.throttle * MAX_SPEED;
		this.speed += (target - this.speed) * (airborne ? 0.02 : 0.015);
		float yawRate = (float) (airborne ? 2.5 : (this.speed > 0.05 ? 3.0 : 0.0));
		this.setYRot(this.getYRot() - pilot.xxa * yawRate);
		float authority = (float) Mth.clamp(this.speed / TAKEOFF_SPEED, 0.0, 1.0);
		float wantPitch = airborne || authority >= 1.0F ? Mth.clamp(pilot.getXRot(), -40.0F, 40.0F) * authority : 0.0F;
		this.setXRot(Mth.rotLerp(0.15F, this.getXRot(), wantPitch));
		this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
	}

	@Override
	public void travel(Vec3 input) {
		if (!(this.getControllingPassenger() instanceof Player) || !this.canSimulateMovement()) {
			this.speed *= 0.97;
			this.throttle = 0.0F;
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, -GRAVITY, 0.0).multiply(0.9, 0.98, 0.9));
			this.move(MoverType.SELF, this.getDeltaMovement());
			return;
		}
		double authority = Mth.clamp(this.speed / TAKEOFF_SPEED, 0.0, 1.0);
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		float pitch = this.getXRot() * Mth.DEG_TO_RAD;
		Vec3 dir = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
		Vec3 motion = dir.scale(this.speed).add(0.0, -GRAVITY * (1.0 - authority), 0.0);
		if (this.onGround() && authority < 1.0 && motion.y > 0) {
			motion = new Vec3(motion.x, 0.0, motion.z);
		}
		this.setDeltaMovement(motion);
		this.move(MoverType.SELF, motion);
		if (this.horizontalCollision) {
			this.speed *= 0.5;
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && !this.crashed && this.isVehicle()) {
			Vec3 moved = this.position().subtract(this.xo, this.yo, this.zo);
			double v = moved.length();
			if (v > CRASH_SPEED && !level.noCollision(this, this.getBoundingBox().move(moved.scale(1.5)))) {
				this.crash(level);
			}
		}
	}

	private void crash(ServerLevel level) {
		this.crashed = true;
		level.explode(this, this.getX(), this.getY() + 0.5, this.getZ(), 3.0F, Level.ExplosionInteraction.NONE);
		for (var rider : this.getPassengers()) {
			if (rider instanceof LivingEntity living) {
				living.hurtServer(level, level.damageSources().explosion(this, this), 18.0F);
			}
		}
		this.discard();
		PlaneDirector.onPlaneLost(level);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			PlaneDirector.onPlaneLost(level);
		}
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}
}
