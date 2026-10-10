package com.rileycleavenger.gnvcraft.entity;

import com.rileycleavenger.gnvcraft.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Chandler: white Nike cap, always with his dog, and he'll offer you a napkin when you come close. */
public class ChandlerEntity extends PathfinderMob {
	private static final double OFFER_DISTANCE = 4.5;
	private static final int COOLDOWN_TICKS = 20 * 60;
	private static final String[] OFFERS = {"Hey, you want a napkin?", "Here, take a napkin.", "Napkin? I've got plenty.", "You look like you could use a napkin."};

	private final Map<UUID, Integer> nextOffer = new HashMap<>();

	public ChandlerEntity(EntityType<? extends ChandlerEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.23);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			if (this.tickCount % 40 == 0) {
				this.keepDog(level);
			}
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || this.distanceToSqr(player) > OFFER_DISTANCE * OFFER_DISTANCE) {
					continue;
				}
				if (this.tickCount >= this.nextOffer.getOrDefault(player.getUUID(), 0)) {
					this.nextOffer.put(player.getUUID(), this.tickCount + COOLDOWN_TICKS);
					this.getLookControl().setLookAt(player);
					this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
					player.sendSystemMessage(Component.literal("<Chandler> " + OFFERS[this.random.nextInt(OFFERS.length)]));
					ItemStack napkin = new ItemStack(ModItems.NAPKIN.get());
					if (!player.getInventory().add(napkin)) {
						player.drop(napkin, false);
					}
				}
			}
		}
	}

	/** Chandler always has his dog: a wolf tamed to him that follows him around. */
	private void keepDog(ServerLevel level) {
		boolean has = !level.getEntitiesOfClass(Wolf.class, new AABB(this.blockPosition()).inflate(48),
			w -> w.isTame() && w.getOwnerReference() != null && this.getUUID().equals(w.getOwnerReference().getUUID())).isEmpty();
		if (has) {
			return;
		}
		Wolf dog = net.minecraft.world.entity.EntityTypes.WOLF.create(level, EntitySpawnReason.EVENT);
		if (dog != null) {
			BlockPos p = this.blockPosition();
			dog.snapTo(p.getX() + 1.5, p.getY(), p.getZ() + 0.5, this.getYRot(), 0.0F);
			dog.setTame(true, false);
			dog.setOwner(this);
			dog.setOrderedToSit(false);
			dog.setPersistenceRequired();
			dog.setCustomName(Component.literal("Chandler's dog"));
			level.addFreshEntity(dog);
		}
	}
}
