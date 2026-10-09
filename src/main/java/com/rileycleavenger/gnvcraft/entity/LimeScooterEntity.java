package com.rileycleavenger.gnvcraft.entity;

import com.rileycleavenger.gnvcraft.director.HeySirDirector;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Lime scooter. Right-click with a gold ingot to rent it for one in-game day (24000 clock ticks); only the renter can ride it,
 * and it locks again where it stands when the rental runs out.
 */
public class LimeScooterEntity extends Mob {
	private @Nullable UUID renter;
	private long rentedUntil;

	public LimeScooterEntity(EntityType<? extends LimeScooterEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.2);
	}

	public boolean isRentedBy(Player player, long now) {
		return this.renter != null && this.renter.equals(player.getUUID()) && now < this.rentedUntil;
	}

	public long rentedUntil() {
		return this.rentedUntil;
	}

	/** Starts (or restarts) a one-day rental for this player. */
	public void rent(Player player, long now) {
		this.renter = player.getUUID();
		this.rentedUntil = now + HeySirDirector.DAY_TICKS;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		long now = HeySirDirector.clockTime(this.level().getServer());
		ItemStack held = player.getItemInHand(hand);
		if (this.isRentedBy(player, now)) {
			if (!this.isVehicle() && !player.isSecondaryUseActive()) {
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		if (this.renter != null && now < this.rentedUntil) {
			player.sendSystemMessage(Component.literal("Someone else has rented this scooter."));
			return InteractionResult.SUCCESS;
		}
		if (held.is(Items.GOLD_INGOT)) {
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
			}
			this.rent(player, now);
			player.sendSystemMessage(Component.literal("Lime scooter unlocked for 1 day."));
			return InteractionResult.SUCCESS;
		}
		player.sendSystemMessage(Component.literal("Insert 1 gold ingot to ride (unlocks for 1 day)."));
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof Player player && this.level().isClientSide() ? player : this.serverController();
	}

	private @Nullable LivingEntity serverController() {
		return this.getFirstPassenger() instanceof Player player ? player : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && this.renter != null) {
			long now = HeySirDirector.clockTime(level.getServer());
			if (now >= this.rentedUntil) {
				this.ejectPassengers();
				this.renter = null;
			} else if (this.tickCount % 20 == 0 && this.getFirstPassenger() instanceof ServerPlayer rider) {
				long left = (this.rentedUntil - now) / 20;
				rider.sendOverlayMessage(Component.literal(String.format("Lime rental: %d:%02d left", left / 60, left % 60)));
			}
		}
	}

	@Override
	protected void tickRidden(Player controller, Vec3 riddenInput) {
		super.tickRidden(controller, riddenInput);
		this.setRot(controller.getYRot(), 0.0F);
		this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
	}

	@Override
	protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
		float forward = controller.zza;
		if (forward < 0.0F) {
			forward *= 0.4F;
		}
		return new Vec3(0.0, 0.0, forward);
	}

	@Override
	protected float getRiddenSpeed(Player controller) {
		return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("renter", UUIDUtil.CODEC, this.renter);
		output.putLong("rented_until", this.rentedUntil);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.renter = input.read("renter", UUIDUtil.CODEC).orElse(null);
		this.rentedUntil = input.getLongOr("rented_until", 0L);
	}
}
