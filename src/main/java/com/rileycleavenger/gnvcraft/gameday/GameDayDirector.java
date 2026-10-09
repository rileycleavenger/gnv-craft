package com.rileycleavenger.gnvcraft.gameday;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.director.HeySirDirector;
import com.rileycleavenger.gnvcraft.director.SpawnFinder;
import com.rileycleavenger.gnvcraft.entity.FanEntity;
import com.rileycleavenger.gnvcraft.npc.SpecialNpcDirector;
import com.rileycleavenger.gnvcraft.registry.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Every 7th in-game day the Gainesville world "fills" with 90,000 orange-and-blue fans. Actually spawning that many
 * entities would stall the game, so each player gets a recycled crowd of up to CROWD_CAP fans close by and the rest is the HUD number.
 */
public final class GameDayDirector {
	public static final int DAYS_PER_GAME = 7;
	public static final int CROWD_CAP = 300;
	private static final int MIN_DIST = 12;
	private static final int MAX_DIST = 56;
	private static final int DESPAWN_DIST = 80;
	private static final int SPAWNS_PER_SECOND = 24;
	private static final String[] OPPONENTS = {"Georgia", "Florida State", "Tennessee", "LSU", "Alabama", "Auburn", "Texas A&M", "Kentucky", "Miami"};

	private static boolean forced;
	private static boolean suppressed;
	private static final ServerBossEvent BAR = new ServerBossEvent(java.util.UUID.nameUUIDFromBytes("gnvcraft:gameday".getBytes()), Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);

	private GameDayDirector() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
			MinecraftServer server = event.getServer();
			if (server.getTickCount() % 20 == 0) {
				tick(server.overworld());
			}
		});
	}

	public static boolean isGameDay(MinecraftServer server) {
		if (forced) {
			return true;
		}
		if (suppressed) {
			return false;
		}
		long day = HeySirDirector.clockTime(server) / HeySirDirector.DAY_TICKS;
		return day % DAYS_PER_GAME == DAYS_PER_GAME - 1;
	}

	/** /gnv gameday start|stop|auto */
	public static void force(boolean on) {
		forced = on;
		suppressed = !on;
	}

	public static void auto() {
		forced = false;
		suppressed = false;
	}

	static void tick(ServerLevel level) {
		if (!SpecialNpcDirector.isGainesville(level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		boolean gameDay = isGameDay(server);
		if (!gameDay) {
			BAR.removeAllPlayers();
			for (Entity fan : level.getEntities(ModEntities.FAN.get(), new AABB(-3.0E7, -64, -3.0E7, 3.0E7, 320, 3.0E7), e -> true)) {
				fan.discard();
			}
			return;
		}
		long day = HeySirDirector.clockTime(server) / HeySirDirector.DAY_TICKS;
		BAR.setName(Component.translatable("gnvcraft.gameday.bar", OPPONENTS[(int) Math.floorMod(day / DAYS_PER_GAME, (long) OPPONENTS.length)]));
		for (ServerPlayer player : level.players()) {
			BAR.addPlayer(player);
			tickPlayer(level, player);
		}
		for (Entity fan : level.getEntities(ModEntities.FAN.get(), new AABB(-3.0E7, -64, -3.0E7, 3.0E7, 320, 3.0E7), e -> true)) {
			double nearest = Double.MAX_VALUE;
			for (ServerPlayer player : level.players()) {
				nearest = Math.min(nearest, fan.distanceToSqr(player));
			}
			if (nearest > DESPAWN_DIST * DESPAWN_DIST) {
				fan.discard();
			}
		}
	}

	public static int fansNear(ServerLevel level, ServerPlayer player) {
		return level.getEntities(ModEntities.FAN.get(), player.getBoundingBox().inflate(DESPAWN_DIST), e -> true).size();
	}

	private static void tickPlayer(ServerLevel level, ServerPlayer player) {
		int have = fansNear(level, player);
		for (int i = 0; i < SPAWNS_PER_SECOND && have < CROWD_CAP; i++) {
			SpawnFinder.find(level, player, MIN_DIST, MAX_DIST, true, level.getRandom()).ifPresent(pos -> {
				FanEntity fan = ModEntities.FAN.get().create(level, EntitySpawnReason.EVENT);
				if (fan != null) {
					fan.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
					level.addFreshEntity(fan);
				}
			});
			have++;
		}
	}
}
