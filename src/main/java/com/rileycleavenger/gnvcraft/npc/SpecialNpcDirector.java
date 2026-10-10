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

	/** Where Chandler hangs out: the sidewalk in front of the UF Plaza strip in Midtown. */
	public static BlockPos chandlerHome() {
		for (GnvRaster.Building b : GnvRaster.buildings()) {
			if (b.name != null && b.name.startsWith("UF Plaza")) {
				int x = (b.minX + b.maxX) / 2;
				int z = b.maxZ + 3;
				return new BlockPos(x, GnvRaster.standY(x, z), z);
			}
		}
		return new BlockPos(-320, GnvRaster.standY(-320, 8), 8);
	}

	private static int[] tenthStreet;

	/** {x, z0, z1} of 10th Street (NW and SW) inside the V1 area, from the map. */
	public static synchronized int[] tenthStreet() {
		if (tenthStreet == null) {
			java.util.List<Integer> xs = new java.util.ArrayList<>();
			int z0 = 0, z1 = 0;
			for (GnvMap.Road r : GnvMap.get().roads) {
				if (r.name != null && (r.name.equals("Northwest 10th Street") || r.name.equals("Southwest 10th Street"))) {
					for (int[] p : r.pts) {
						if (Math.abs(p[1]) <= 450) {
							xs.add(p[0]);
							z0 = Math.min(z0, p[1]);
							z1 = Math.max(z1, p[1]);
						}
					}
				}
			}
			java.util.Collections.sort(xs);
			int x = xs.isEmpty() ? 415 : xs.get(xs.size() / 2);
			tenthStreet = new int[] {x, Math.max(-450, z0), Math.min(450, z1)};
		}
		return tenthStreet;
	}

	private static final java.util.Map<String, Long> RESPAWN_AT = new java.util.HashMap<>();

	/** Keeps one of an NPC near its home; if it disappears (killed), it comes back after an in-game day. */
	private static <T extends net.minecraft.world.entity.Mob> void keepOne(ServerLevel level, String key, net.minecraft.world.entity.EntityType<T> type,
		BlockPos home, int searchRadius, java.util.function.Consumer<T> setup) {
		if (level.players().stream().noneMatch(p -> p.blockPosition().distSqr(home) < (long) NEAR * NEAR) || !level.isLoaded(home)) {
			return;
		}
		if (!level.getEntities(type, new AABB(home).inflate(searchRadius), e -> true).isEmpty()) {
			RESPAWN_AT.remove(key);
			return;
		}
		long now = HeySirDirector.clockTime(level.getServer());
		Long at = RESPAWN_AT.get(key);
		if (at == null) {
			RESPAWN_AT.put(key, Long.MIN_VALUE);   // first time: spawn right away
		} else if (at == Long.MIN_VALUE) {
			RESPAWN_AT.put(key, now + HeySirDirector.DAY_TICKS);
			return;
		} else if (now < at) {
			return;
		}
		T mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob != null) {
			mob.snapTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.0F, 0.0F);
			setup.accept(mob);
			level.addFreshEntity(mob);
			RESPAWN_AT.put(key, Long.MIN_VALUE);
		}
	}

	static void tick(ServerLevel level) {
		if (!isGainesville(level) || level.players().isEmpty()) {
			return;
		}
		keepOne(level, "chandler", ModEntities.CHANDLER.get(), chandlerHome(), 96, m -> m.setHomeTo(chandlerHome(), 40));
		int[] street = tenthStreet();
		BlockPos donnie = new BlockPos(street[0], GnvRaster.standY(street[0], 0), 0);
		keepOne(level, "donnie", ModEntities.DONNIE.get(), donnie, 520, m -> ((com.rileycleavenger.gnvcraft.entity.DonnieEntity) m).setStreet(street[0], street[1], street[2]));
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
