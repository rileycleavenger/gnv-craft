package com.rileycleavenger.heysir.director;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

/**
 * Per-player HeySir state. {@code returnAt} is an overworld clock tick.
 */
public record HeySirData(Phase phase, int deaths, long returnAt) {
	public static final HeySirData DEFAULT = new HeySirData(Phase.UNMET, 0, 0L);

	public static final MapCodec<HeySirData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Phase.CODEC.fieldOf("phase").forGetter(HeySirData::phase),
		Codec.INT.fieldOf("deaths").forGetter(HeySirData::deaths),
		Codec.LONG.fieldOf("return_at").forGetter(HeySirData::returnAt)
	).apply(instance, HeySirData::new));

	public HeySirData withPhase(Phase newPhase) {
		return new HeySirData(newPhase, this.deaths, this.returnAt);
	}

	public enum Phase implements StringRepresentable {
		UNMET("unmet"),
		FOLLOWING("following"),
		AWAY("away"),
		DEAD("dead");

		public static final Codec<Phase> CODEC = StringRepresentable.fromEnum(Phase::values);

		private final String name;

		Phase(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
}
