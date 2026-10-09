package com.rileycleavenger.heysir.entity;

import com.rileycleavenger.heysir.HeySirMod;
import com.rileycleavenger.heysir.director.HeySirData;
import com.rileycleavenger.heysir.director.HeySirDirector;
import com.rileycleavenger.heysir.director.SpawnFinder;
import com.rileycleavenger.heysir.entity.goal.FollowTargetPlayerGoal;
import com.rileycleavenger.heysir.entity.goal.LeaveGoal;
import com.rileycleavenger.heysir.registry.ModItems;
import com.rileycleavenger.heysir.registry.ModSounds;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class HeySirEntity extends PathfinderMob {
	public static final double BASE_HEALTH = 20.0;
	private static final double MAX_HEALTH_CAP = 1024.0;
	private static final double LOCK_ON_DISTANCE = 12.0;
	private static final double LOSE_INTEREST_DISTANCE = 128.0;
	private static final double CATCH_UP_DISTANCE = 48.0;
	private static final double LEAVE_DISTANCE = 40.0;
	private static final int MAX_LEAVE_TICKS = 30 * 20;

	private static final EntityDataAccessor<Boolean> DATA_RIDING = SynchedEntityData.defineId(HeySirEntity.class, EntityDataSerializers.BOOLEAN);
	private static final Identifier RIDING_SPEED_ID = HeySirMod.id("riding_speed");
	private static final AttributeModifier RIDING_SPEED = new AttributeModifier(RIDING_SPEED_ID, 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

	private @Nullable UUID targetId;
	private Mode mode = Mode.WANDER;
	private int bikeTimer = 600;
	private int leaveTicks;
	private float damageScale = 1.0F;
	private boolean deathHandled;

	public HeySirEntity(EntityType<? extends HeySirEntity> type, Level level) {
		super(type, level);
		this.setRiding(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, BASE_HEALTH)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_RIDING, true);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new LeaveGoal(this));
		this.goalSelector.addGoal(2, new FollowTargetPlayerGoal(this));
		this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8) {
			@Override
			public boolean canUse() {
				return HeySirEntity.this.mode == Mode.WANDER && super.canUse();
			}
		});
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
	}

	public boolean isRiding() {
		return this.entityData.get(DATA_RIDING);
	}

	public void setRiding(boolean riding) {
		this.entityData.set(DATA_RIDING, riding);
		if (this.level().isClientSide()) {
			return;
		}
		AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			if (riding) {
				speed.addOrUpdateTransientModifier(RIDING_SPEED);
			} else {
				speed.removeModifier(RIDING_SPEED_ID);
			}
		}
	}

	public Mode getMode() {
		return this.mode;
	}

	public @Nullable UUID getTargetId() {
		return this.targetId;
	}

	public void setTarget(ServerPlayer player, Mode mode) {
		this.targetId = player.getUUID();
		this.mode = mode;
	}

	public @Nullable ServerPlayer getTargetPlayer() {
		if (this.targetId == null || !(this.level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(this.targetId);
		if (player == null || player.level() != this.level() || !player.isAlive() || player.isSpectator()) {
			return null;
		}
		return player;
	}

	/**
	 * Max health doubles with each death. Past the vanilla 1024 cap, incoming damage is
	 * scaled down instead so the effective health keeps doubling.
	 */
	public void applyDeaths(int deaths) {
		double desired = Math.scalb(BASE_HEALTH, Math.min(deaths, 120));
		double maxHealth = Math.min(desired, MAX_HEALTH_CAP);
		AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(maxHealth);
		}
		this.setHealth((float) maxHealth);
		this.damageScale = (float) (maxHealth / desired);
	}

	public void startLeaving() {
		this.mode = Mode.LEAVE;
		this.leaveTicks = 0;
		this.getNavigation().stop();
		if (!this.isInWater()) {
			this.setRiding(true);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel serverLevel && this.isAlive()) {
			this.tickBike();
			this.tickMode(serverLevel);
		}
	}

	private void tickBike() {
		if (this.isInWater()) {
			if (this.isRiding()) {
				this.setRiding(false);
				this.bikeTimer = 100;
			}
			return;
		}
		if (--this.bikeTimer > 0) {
			return;
		}
		if (this.isRiding()) {
			this.setRiding(false);
			this.bikeTimer = 160 + this.random.nextInt(240);
		} else {
			this.setRiding(true);
			this.bikeTimer = 600 + this.random.nextInt(1200);
		}
	}

	private void tickMode(ServerLevel level) {
		ServerPlayer target = this.getTargetPlayer();
		switch (this.mode) {
			case WANDER -> {
				Player nearest = level.getNearestPlayer(this, 32.0);
				if (nearest instanceof ServerPlayer player && !player.isSpectator()) {
					this.setTarget(player, Mode.APPROACH);
					this.applyDeaths(HeySirDirector.getData(player).deaths());
				}
			}
			case APPROACH -> {
				if (target == null || this.distanceTo(target) > LOSE_INTEREST_DISTANCE) {
					this.discard();
				} else if (this.distanceTo(target) < LOCK_ON_DISTANCE && this.hasLineOfSight(target)) {
					this.mode = Mode.FOLLOW;
					HeySirDirector.onLockOn(target);
					this.playSound(ModSounds.HEY_SIR);
				}
			}
			case FOLLOW -> {
				if (target == null) {
					this.discard();
					return;
				}
				HeySirData.Phase phase = HeySirDirector.getData(target).phase();
				if (phase == HeySirData.Phase.AWAY) {
					this.startLeaving();
				} else if (phase != HeySirData.Phase.FOLLOWING) {
					this.discard();
				} else if (this.distanceTo(target) > CATCH_UP_DISTANCE) {
					SpawnFinder.find(level, target, 16, 24, false, this.random).ifPresent(this::catchUpTo);
				}
			}
			case LEAVE -> {
				if (++this.leaveTicks > MAX_LEAVE_TICKS || target == null || this.distanceTo(target) > LEAVE_DISTANCE) {
					this.discard();
				}
			}
		}
	}

	private void catchUpTo(Vec3 pos) {
		this.getNavigation().stop();
		this.teleportTo(pos.x, pos.y, pos.z);
		this.setRiding(true);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(ModItems.MCDONALDS_GIFTCARD)) {
			if (this.mode == Mode.LEAVE) {
				return InteractionResult.PASS;
			}
			if (this.level() instanceof ServerLevel serverLevel && player instanceof ServerPlayer payer) {
				stack.consume(1, player);
				ServerPlayer owner = this.getTargetPlayer();
				HeySirDirector.onPaid(owner != null ? owner : payer);
				if (this.targetId == null) {
					this.setTarget(payer, Mode.LEAVE);
				}
				this.startLeaving();
				this.playSound(ModSounds.HEY_SIR);
				serverLevel.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 2.2, this.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
			}
			return InteractionResult.SUCCESS;
		}
		if (hand == InteractionHand.MAIN_HAND) {
			if (!this.level().isClientSide()) {
				this.playSound(ModSounds.HEY_SIR_EXCUSE_ME);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return super.hurtServer(level, source, damage * this.damageScale);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!this.deathHandled && this.level() instanceof ServerLevel serverLevel) {
			this.deathHandled = true;
			ServerPlayer owner = this.targetId == null ? null : serverLevel.getServer().getPlayerList().getPlayer(this.targetId);
			if (owner != null) {
				HeySirDirector.onKilled(owner);
			}
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		int roll = this.getRandom().nextInt(100);
		if (roll < 3) {
			return ModSounds.WIFE_AND_KIDS;
		}
		if (roll < 20) {
			return ModSounds.HEY_SIR_EXCUSE_ME;
		}
		return ModSounds.HEY_SIR;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 30;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	@Override
	public boolean shouldShowName() {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	public enum Mode {
		/** No player yet (spawn egg or /summon); picks the nearest player. */
		WANDER,
		/** Riding toward a player he hasn't locked onto yet. */
		APPROACH,
		FOLLOW,
		LEAVE
	}
}
