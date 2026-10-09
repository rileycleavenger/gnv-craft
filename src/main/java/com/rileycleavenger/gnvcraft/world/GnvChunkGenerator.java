package com.rileycleavenger.gnvcraft.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rileycleavenger.gnvcraft.world.map.GnvMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Builds Gainesville from the baked OpenStreetMap data: flat ground, roads, areas, and enterable buildings. */
public class GnvChunkGenerator extends ChunkGenerator {
	public static final MapCodec<GnvChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome)).apply(i, i.stable(GnvChunkGenerator::new))
	);

	/** Y of the top ground block; players stand on SURFACE_Y + 1. */
	public static final int SURFACE_Y = 63;
	private static final int MIN_Y = -64;

	private final Holder<Biome> biome;

	public GnvChunkGenerator(Holder<Biome> biome) {
		super(new FixedBiomeSource(biome));
		this.biome = biome;
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
		Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		ChunkPlan plan = new ChunkPlan(GnvMap.get(), x0, z0);
		for (int lx0 = 0; lx0 < 16; lx0++) {
			for (int lz0 = 0; lz0 < 16; lz0++) {
				final int lx = lx0;
				final int lz = lz0;
				plan.build(lx, lz, (y, state) -> {
					chunk.setBlockState(pos.set(lx, y, lz), state);
					oceanFloor.update(lx, y, lz, state);
					worldSurface.update(lx, y, lz, state);
				});
			}
		}
		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
		return SURFACE_Y + 1;
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
		BlockState[] states = new BlockState[heightAccessor.getHeight()];
		for (int i = 0; i < states.length; i++) {
			states[i] = heightAccessor.getMinY() + i <= SURFACE_Y ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
		}
		return new NoiseColumn(heightAccessor.getMinY(), states);
	}

	@Override
	public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
	}

	@Override
	public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
	}

	@Override
	public int getGenDepth() {
		return 384;
	}

	@Override
	public int getSeaLevel() {
		return SURFACE_Y;
	}

	@Override
	public int getMinY() {
		return MIN_Y;
	}

	@Override
	public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {
		result.add("GNV-Craft map @ " + feetPos.getX() + ", " + feetPos.getZ());
	}
}
