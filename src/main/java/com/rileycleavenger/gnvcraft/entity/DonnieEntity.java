package com.rileycleavenger.gnvcraft.entity;

import com.rileycleavenger.gnvcraft.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Donnie walks up and down 10th Street. Come close and he follows you; hit him and he stops, hands you his ID and tells you
 * "I'm real man, I'm real". He can't be hurt, so he's always back on 10th Street.
 */
public class DonnieEntity extends PathfinderMob {
	private static final double NOTICE = 6.0;
	private static final int ID_COOLDOWN = 20 * 30;

	/** x of 10th Street and the stretch of it he walks (set by the director from the map). */
	private int streetX;
	private int streetZ0 = -300;
	private int streetZ1 = 300;
	private @Nullable UUID following;
	private final Map<UUID, Integer> leftAlone = new HashMap<>();
	private int nextId;
	private int walkTargetZ;

	public DonnieEntity(EntityType<? extends DonnieEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.24);
	}

	public void setStreet(int x, int z0, int z1) {
		this.streetX = x;
		this.streetZ0 = Math.min(z0, z1);
		this.streetZ1 = Math.max(z0, z1);
		this.walkTargetZ = this.streetZ1;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level) || this.tickCount % 10 != 0) {
			return;
		}
		ServerPlayer target = this.following == null ? null : level.getServer().getPlayerList().getPlayer(this.following);
		if (target != null && (!target.isAlive() || target.level() != level || this.distanceToSqr(target) > 40 * 40)) {
			target = null;
			this.following = null;
		}
		if (target == null) {
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator() && this.distanceToSqr(p) < NOTICE * NOTICE && this.tickCount >= this.leftAlone.getOrDefault(p.getUUID(), 0)) {
					this.following = p.getUUID();
					target = p;
					break;
				}
			}
		}
		if (target != null) {
			if (this.distanceToSqr(target) > 3 * 3) {
				this.getNavigation().moveTo(target, 1.0);
			} else {
				this.getNavigation().stop();
			}
			return;
		}
		// walk the length of 10th Street and back
		if (Math.abs(this.getZ() - this.walkTargetZ) < 3) {
			this.walkTargetZ = this.walkTargetZ == this.streetZ1 ? this.streetZ0 : this.streetZ1;
		}
		if (this.getNavigation().isDone()) {
			double z = this.getZ() + Math.signum(this.walkTargetZ - this.getZ()) * Math.min(24, Math.abs(this.walkTargetZ - this.getZ()));
			this.getNavigation().moveTo(this.streetX + 0.5, this.getY(), z, 0.8);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof ServerPlayer player) {
			this.following = null;
			this.leftAlone.put(player.getUUID(), this.tickCount + 20 * 60);
			this.getNavigation().stop();
			this.getLookControl().setLookAt(player);
			if (this.tickCount >= this.nextId) {
				this.nextId = this.tickCount + ID_COOLDOWN;
				this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				player.sendSystemMessage(Component.literal("<Donnie> I'm real man, I'm real"));
				ItemStack id = new ItemStack(ModItems.DONNIE_ID.get());
				if (!player.getInventory().add(id)) {
					player.drop(id, false);
				}
			}
		}
		return false;
	}
}
