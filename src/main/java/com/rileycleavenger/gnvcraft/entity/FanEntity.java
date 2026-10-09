package com.rileycleavenger.gnvcraft.entity;

import com.rileycleavenger.gnvcraft.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** A Gator fan on game day. One voice line only: "Go Gators!". Recycled by the GameDayDirector; never saved. */
public class FanEntity extends PathfinderMob {
	public FanEntity(EntityType<? extends FanEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.22);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && this.random.nextInt(260) == 0) {
			this.swing(InteractionHand.MAIN_HAND);
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.GO_GATORS.get(), SoundSource.NEUTRAL, 1.0F, 0.95F + this.random.nextFloat() * 0.1F);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return true;
	}

	/** Which of the six orange/blue outfits this fan wears. */
	public int variant() {
		return Math.floorMod(this.getUUID().hashCode(), 6);
	}
}
