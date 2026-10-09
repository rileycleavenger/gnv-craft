package com.rileycleavenger.gnvcraft.drunk;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.registry.ModAttachments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server side of drinking: drunk level 0..DEATH_LEVEL per player, decays over time, kills at the top. */
public final class DrunkSystem {
	public static final float DEATH_LEVEL = 9.0F;
	/** Drops 1 level per 2 in-game hours (2000 ticks). */
	private static final float DECAY_PER_TICK = 1.0F / 2000.0F;
	public static final ResourceKey<DamageType> ALCOHOL_POISONING = ResourceKey.create(Registries.DAMAGE_TYPE, GnvCraftMod.id("alcohol_poisoning"));

	private DrunkSystem() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				tick(player);
			}
		});
	}

	public static float get(ServerPlayer player) {
		return player.getData(ModAttachments.DRUNK);
	}

	public static void set(ServerPlayer player, float level) {
		player.setData(ModAttachments.DRUNK, Mth.clamp(level, 0.0F, DEATH_LEVEL));
	}

	public static void add(ServerPlayer player, float amount) {
		set(player, get(player) + amount);
	}

	private static void tick(ServerPlayer player) {
		float level = get(player);
		if (level <= 0.0F || !player.isAlive()) {
			return;
		}
		if (level >= DEATH_LEVEL) {
			ServerLevel serverLevel = player.level();
			DamageSource source = new DamageSource(serverLevel.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ALCOHOL_POISONING));
			set(player, 0.0F);
			player.hurtServer(serverLevel, source, Float.MAX_VALUE);
			return;
		}
		player.setData(ModAttachments.DRUNK, Math.max(0.0F, level - DECAY_PER_TICK));
	}
}
