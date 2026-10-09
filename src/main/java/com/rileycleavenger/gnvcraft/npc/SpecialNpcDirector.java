package com.rileycleavenger.gnvcraft.npc;

import com.rileycleavenger.gnvcraft.director.HeySirDirector;
import com.rileycleavenger.gnvcraft.entity.DennisEntity;
import com.rileycleavenger.gnvcraft.registry.ModAttachments;
import com.rileycleavenger.gnvcraft.registry.ModEntities;
import com.rileycleavenger.gnvcraft.world.GnvChunkGenerator;
import com.rileycleavenger.gnvcraft.world.map.GnvMap;
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

	/** Spot for Dennis: the Chick-fil-A at 13th & University, else the map origin. */
	public static BlockPos dennisHome() {
		GnvMap.Landmark l = GnvMap.get().landmark("chick-fil-a");
		int x = l == null ? 0 : l.p[0];
		int z = l == null ? 0 : l.p[1];
		return new BlockPos(x - 3, GnvChunkGenerator.SURFACE_Y + 1, z + 3);
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
