package com.rileycleavenger.gnvcraft.npc;

import com.rileycleavenger.gnvcraft.director.HeySirDirector;
import com.rileycleavenger.gnvcraft.entity.DennisEntity;
import com.rileycleavenger.gnvcraft.registry.ModAttachments;
import com.rileycleavenger.gnvcraft.registry.ModEntities;
import com.rileycleavenger.gnvcraft.world.GnvChunkGenerator;
import com.rileycleavenger.gnvcraft.world.map.GnvMap;
import com.rileycleavenger.gnvcraft.world.map.GnvRaster;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Keeps exactly one of each world-wide special NPC (Dennis for now) standing at their home landmark in the Gainesville world. */
public final class SpecialNpcDirector {
	private static final int NEAR = 96;

	private SpecialNpcDirector() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
			MinecraftServer server = event.getServer();
			if (server.getTickCount() % 40 == 0) {
				tick(server.overworld());
			}
		});
	}

	public static boolean isGainesville(ServerLevel level) {
		return level.getChunkSource().getGenerator() instanceof GnvChunkGenerator;
	}

	/**
	 * Spot for Dennis: the sidewalk corner of 13th St & University Ave on the Chick-fil-A side. Searches outward from the point
	 * between the restaurant and the intersection for the nearest open sidewalk cell.
	 */
	public static BlockPos dennisHome() {
		GnvMap.Landmark l = GnvMap.get().landmark("chick-fil-a");
		// the corner itself: just off the intersection on the Chick-fil-A side
		int cx = l == null ? 0 : Integer.signum(l.p[0]) * 12;
		int cz = l == null ? 0 : Integer.signum(l.p[1]) * 12;
		if (GnvRaster.covers(cx, cz)) {
			for (int r = 0; r <= 24; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						int x = cx + dx;
						int z = cz + dz;
						if (GnvRaster.surface(x, z) == GnvRaster.S_CONCRETE && GnvRaster.building(x, z) == 0 && GnvRaster.leafTop(x, z) == 0) {
							return new BlockPos(x, GnvRaster.standY(x, z), z);
						}
					}
				}
			}
		}
		return new BlockPos(cx, GnvRaster.standY(cx, cz), cz);
	}

	public static void onDennisDied(ServerLevel level) {
		long today = HeySirDirector.clockTime(level.getServer()) / HeySirDirector.DAY_TICKS;
		level.getServer().overworld().setData(ModAttachments.DENNIS_RESPAWN_DAY, today + 1);
	}

	static void tick(ServerLevel level) {
		if (!isGainesville(level) || level.players().isEmpty()) {
			return;
		}
		BlockPos home = dennisHome();
		if (level.players().stream().noneMatch(p -> p.blockPosition().distSqr(home) < NEAR * NEAR)) {
			return;
		}
		if (!level.isLoaded(home)) {
			return;
		}
		if (!level.getEntities(ModEntities.DENNIS.get(), new AABB(home).inflate(NEAR), e -> true).isEmpty()) {
			return;
		}
		long today = HeySirDirector.clockTime(level.getServer()) / HeySirDirector.DAY_TICKS;
		if (today < level.getData(ModAttachments.DENNIS_RESPAWN_DAY)) {
			return;
		}
		DennisEntity dennis = ModEntities.DENNIS.get().create(level, EntitySpawnReason.EVENT);
		if (dennis != null) {
			dennis.snapTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 90.0F, 0.0F);
			level.addFreshEntity(dennis);
		}
	}
}
