package com.rileycleavenger.gnvcraft.npc;

import com.rileycleavenger.gnvcraft.director.HeySirDirector;
import com.rileycleavenger.gnvcraft.entity.PlaneEntity;
import com.rileycleavenger.gnvcraft.registry.ModAttachments;
import com.rileycleavenger.gnvcraft.registry.ModEntities;
import com.rileycleavenger.gnvcraft.world.GnvChunkGenerator;
import com.rileycleavenger.gnvcraft.world.map.GnvMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Keeps one plane parked on the GNV apron: spawned the first time anyone is near, and again a day after it's lost. */
public final class PlaneDirector {
	private static final int NEAR = 96;

	private PlaneDirector() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
			MinecraftServer server = event.getServer();
			if (server.getTickCount() % 40 == 0) {
				tick(server.overworld());
			}
		});
	}

	/** Where the plane is parked: the GNV apron closest to the terminal. */
	public static BlockPos parking() {
		GnvMap map = GnvMap.get();
		GnvMap.Landmark terminal = map.landmarkKind("airport_terminal");
		int tx = terminal == null ? 0 : terminal.p[0];
		int tz = terminal == null ? 0 : terminal.p[1];
		GnvMap.Landmark apron = map.nearestKind("airport_apron", tx, tz);
		int x = apron == null ? tx : apron.p[0];
		int z = apron == null ? tz : apron.p[1];
		return new BlockPos(x, GnvChunkGenerator.SURFACE_Y + 1, z);
	}

	public static void onPlaneLost(ServerLevel level) {
		long today = HeySirDirector.clockTime(level.getServer()) / HeySirDirector.DAY_TICKS;
		level.getServer().overworld().setData(ModAttachments.PLANE_RESPAWN_DAY, today + 1);
	}

	static void tick(ServerLevel level) {
		if (!SpecialNpcDirector.isGainesville(level)) {
			return;
		}
		BlockPos spot = parking();
		if (level.players().stream().noneMatch(p -> p.blockPosition().distSqr(spot) < NEAR * NEAR) || !level.isLoaded(spot)) {
			return;
		}
		long today = HeySirDirector.clockTime(level.getServer()) / HeySirDirector.DAY_TICKS;
		if (today < level.getData(ModAttachments.PLANE_RESPAWN_DAY)) {
			return;
		}
		PlaneEntity plane = ModEntities.PLANE.get().create(level, EntitySpawnReason.EVENT);
		if (plane != null) {
			plane.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0F, 0.0F);
			level.addFreshEntity(plane);
			level.setData(ModAttachments.PLANE_RESPAWN_DAY, Long.MAX_VALUE);
		}
	}
}
