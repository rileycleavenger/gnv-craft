package com.rileycleavenger.gnvcraft.entity;

import com.rileycleavenger.gnvcraft.npc.SpecialNpcDirector;
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
import net.minecraft.world.level.Level;

/** Dennis: dances on the corner of 13th St & University by the Chick-fil-A and rants about Linux at anyone who walks by. */
public class DennisEntity extends PathfinderMob {
	private static final double RANT_DISTANCE = 6.0;
	private static final int LINE_GAP_TICKS = 70;
	private static final int COOLDOWN_TICKS = 20 * 45;

	public static final String[] LINES = {
		"Hey! Hey you! Have you tried Linux?",
		"Linux is just better. Free, fast, and nobody tells you what to do.",
		"You know what never forces an update on you in the middle of a presentation? Linux.",
		"I run Arch, by the way. I installed it by hand. It took me three weekends and it was worth it.",
		"Windows is spyware with a start menu, man. Wake up!",
		"Package managers! One command and everything's installed. Just one command!",
		"I haven't rebooted since the spring. Uptime, baby.",
		"Why pay for an operating system when the whole thing is open source?",
		"The terminal is not scary. The terminal is freedom!",
		"Tiling window managers changed my life. Don't even ask me about my dotfiles.",
		"Everything on my laptop is Linux. My toaster is Linux. Okay, not the toaster. Yet.",
		"Dual boot? No no no. You commit. You go all in. You go Linux.",
		"Go install Ubuntu. Or Fedora. Or Arch. Anything but whatever you're on right now!",
	};

	private final Map<UUID, int[]> rants = new HashMap<>(); // player -> {next line index, ticks until next line}
	private final Map<UUID, Integer> cooldownUntil = new HashMap<>();

	public DennisEntity(EntityType<? extends DennisEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			this.tickRants(level);
		}
	}

	private void tickRants(ServerLevel level) {
		int now = this.tickCount;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			UUID id = player.getUUID();
			int[] rant = this.rants.get(id);
			if (rant == null) {
				if (this.distanceToSqr(player) <= RANT_DISTANCE * RANT_DISTANCE && now >= this.cooldownUntil.getOrDefault(id, 0)) {
					this.rants.put(id, new int[] {0, 0});
				}
				continue;
			}
			if (--rant[1] > 0) {
				continue;
			}
			if (rant[0] >= LINES.length || this.distanceToSqr(player) > 24.0 * 24.0) {
				this.rants.remove(id);
				this.cooldownUntil.put(id, now + COOLDOWN_TICKS);
				continue;
			}
			player.sendSystemMessage(Component.literal("<Dennis> " + LINES[rant[0]]));
			rant[0]++;
			rant[1] = LINE_GAP_TICKS;
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			SpecialNpcDirector.onDennisDied(level);
		}
	}
}
