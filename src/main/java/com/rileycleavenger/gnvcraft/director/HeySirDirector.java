package com.rileycleavenger.gnvcraft.director;

import com.rileycleavenger.gnvcraft.entity.HeySirEntity;
import com.rileycleavenger.gnvcraft.registry.ModAttachments;
import com.rileycleavenger.gnvcraft.registry.ModEntities;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Decides when each player's HeySir shows up: the random first meeting, keeping him around while
 * he's following, and bringing him back after a giftcard (3 days) or a death (next day).
 */
public final class HeySirDirector {
	public static final long DAY_TICKS = 24000L;
	public static final long AWAY_TICKS = 3 * DAY_TICKS;
	/** Rolled once per second, so this averages one first meeting per 20-minute in-game day. */
	private static final int FIRST_MEETING_ODDS = 1200;
	private static final int RETRY_TICKS = 100;
	private static final double SEARCH_RADIUS = 96.0;

	private static final Map<UUID, Integer> RETRY_AT = new HashMap<>();

	private HeySirDirector() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> onServerTick(event.getServer()));
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> RETRY_AT.remove(event.getEntity().getUUID()));
	}

	/** Overworld clock ticks; advances with sleeping and /time add, unlike game time. */
	public static long clockTime(MinecraftServer server) {
		return server.overworld().getOverworldClockTime();
	}

	public static HeySirData getData(ServerPlayer player) {
		return player.getData(ModAttachments.HEYSIR_DATA);
	}

	public static void setData(ServerPlayer player, HeySirData data) {
		player.setData(ModAttachments.HEYSIR_DATA, data);
	}

	private static void onServerTick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		long now = clockTime(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player, now);
		}
	}

	private static void tickPlayer(ServerPlayer player, long now) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		HeySirData data = getData(player);
		switch (data.phase()) {
			case UNMET -> {
				ServerLevel level = player.level();
				if (level.dimension() == Level.OVERWORLD
					&& findHeySir(player) == null
					&& level.getRandom().nextInt(FIRST_MEETING_ODDS) == 0) {
					trySpawn(player, HeySirEntity.Mode.APPROACH, 24, 40, true);
				}
			}
			case FOLLOWING -> {
				if (findHeySir(player) == null) {
					trySpawn(player, HeySirEntity.Mode.FOLLOW, 16, 28, false);
				}
			}
			case AWAY, DEAD -> {
				if (now >= data.returnAt()) {
					setData(player, data.withPhase(HeySirData.Phase.FOLLOWING));
					trySpawn(player, HeySirEntity.Mode.FOLLOW, 16, 28, false);
				}
			}
		}
	}

	public static @Nullable HeySirEntity findHeySir(ServerPlayer player) {
		List<HeySirEntity> found = player.level().getEntitiesOfClass(
			HeySirEntity.class,
			player.getBoundingBox().inflate(SEARCH_RADIUS),
			heySir -> heySir.isAlive() && player.getUUID().equals(heySir.getTargetId())
		);
		return found.isEmpty() ? null : found.getFirst();
	}

	private static void trySpawn(ServerPlayer player, HeySirEntity.Mode mode, int minDistance, int maxDistance, boolean surface) {
		int tick = player.level().getServer().getTickCount();
		if (tick < RETRY_AT.getOrDefault(player.getUUID(), 0)) {
			return;
		}
		if (spawn(player, mode, minDistance, maxDistance, surface) == null) {
			RETRY_AT.put(player.getUUID(), tick + RETRY_TICKS);
		}
	}

	public static @Nullable HeySirEntity spawn(ServerPlayer player, HeySirEntity.Mode mode, int minDistance, int maxDistance, boolean surface) {
		ServerLevel level = player.level();
		Vec3 pos = SpawnFinder.find(level, player, minDistance, maxDistance, surface, level.getRandom()).orElse(null);
		if (pos == null) {
			return null;
		}
		HeySirEntity heySir = ModEntities.HEYSIR.get().create(level, EntitySpawnReason.EVENT);
		if (heySir == null) {
			return null;
		}
		float yaw = (float) (Mth.atan2(player.getZ() - pos.z, player.getX() - pos.x) * Mth.RAD_TO_DEG) - 90.0F;
		heySir.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		heySir.setYHeadRot(yaw);
		heySir.setYBodyRot(yaw);
		heySir.setTarget(player, mode);
		heySir.applyDeaths(getData(player).deaths());
		level.addFreshEntity(heySir);
		return heySir;
	}

	public static void onLockOn(ServerPlayer player) {
		HeySirData data = getData(player);
		if (data.phase() == HeySirData.Phase.UNMET) {
			setData(player, data.withPhase(HeySirData.Phase.FOLLOWING));
		}
	}

	public static void onPaid(ServerPlayer player) {
		long now = clockTime(player.level().getServer());
		setData(player, new HeySirData(HeySirData.Phase.AWAY, getData(player).deaths(), now + AWAY_TICKS));
	}

	public static void onKilled(ServerPlayer player) {
		HeySirData data = getData(player);
		long now = clockTime(player.level().getServer());
		long nextDay = (Math.floorDiv(now, DAY_TICKS) + 1) * DAY_TICKS;
		long returnAt = data.phase() == HeySirData.Phase.AWAY ? Math.max(nextDay, data.returnAt()) : nextDay;
		setData(player, new HeySirData(HeySirData.Phase.DEAD, data.deaths() + 1, returnAt));
	}

	public static void discardAll(ServerPlayer player) {
		HeySirEntity heySir;
		while ((heySir = findHeySir(player)) != null) {
			heySir.discard();
		}
	}
}
