package com.rileycleavenger.gnvcraft.npc;

import com.rileycleavenger.gnvcraft.entity.LimeScooterEntity;
import com.rileycleavenger.gnvcraft.registry.ModAttachments;
import com.rileycleavenger.gnvcraft.registry.ModEntities;
import com.rileycleavenger.gnvcraft.world.GnvChunkGenerator;
import com.rileycleavenger.gnvcraft.world.map.GnvMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Parks a Lime scooter on the sidewalk every ~150 blocks along the main roads, the first time a player gets near. */
public final class ScooterPlacer {
	private static final int SPACING = 150;
	private static final int NEAR = 48;
	private static List<int[]> points;

	private ScooterPlacer() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
			MinecraftServer server = event.getServer();
			if (server.getTickCount() % 60 == 0) {
				tick(server.overworld());
			}
		});
	}

	/** Candidate spots: {x, z, id}, along primary/secondary roads, offset onto the sidewalk. */
	static synchronized List<int[]> points() {
		if (points == null) {
			points = new ArrayList<>();
			for (GnvMap.Road r : GnvMap.get().roads) {
				if (!(r.cls.equals("primary") || r.cls.equals("secondary") || r.cls.equals("tertiary")) || r.pts.length < 2) {
					continue;
				}
				double carry = SPACING / 2.0;
				for (int i = 0; i + 1 < r.pts.length; i++) {
					double dx = r.pts[i + 1][0] - r.pts[i][0];
					double dz = r.pts[i + 1][1] - r.pts[i][1];
					double len = Math.hypot(dx, dz);
					if (len == 0) {
						continue;
					}
					for (double d = carry; d < len; d += SPACING) {
						double nx = -dz / len;
						double nz = dx / len;
						double off = r.w / 2.0 + 1.5;
						int x = (int) Math.round(r.pts[i][0] + dx * d / len + nx * off);
						int z = (int) Math.round(r.pts[i][1] + dz * d / len + nz * off);
						points.add(new int[] {x, z, (int) ((r.id * 31 + (long) (d * 7)) & 0x7fffffff)});
						carry = d + SPACING - len;
					}
					carry = Math.max(carry - len, 0) ;
				}
			}
		}
		return points;
	}

	static void tick(ServerLevel level) {
		if (!SpecialNpcDirector.isGainesville(level)) {
			return;
		}
		Set<Integer> placed = new HashSet<>(level.getData(ModAttachments.SCOOTERS_PLACED));
		boolean changed = false;
		for (ServerPlayer player : level.players()) {
			int px = player.getBlockX();
			int pz = player.getBlockZ();
			for (int[] p : points()) {
				if (Math.abs(p[0] - px) > NEAR || Math.abs(p[1] - pz) > NEAR || placed.contains(p[2])) {
					continue;
				}
				BlockPos pos = new BlockPos(p[0], GnvChunkGenerator.SURFACE_Y + 1, p[1]);
				if (!level.isLoaded(pos)) {
					continue;
				}
				LimeScooterEntity scooter = ModEntities.LIME_SCOOTER.get().create(level, EntitySpawnReason.EVENT);
				if (scooter != null) {
					scooter.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
					level.addFreshEntity(scooter);
					placed.add(p[2]);
					changed = true;
				}
			}
		}
		if (changed) {
			level.setData(ModAttachments.SCOOTERS_PLACED, new ArrayList<>(placed));
		}
	}
}
