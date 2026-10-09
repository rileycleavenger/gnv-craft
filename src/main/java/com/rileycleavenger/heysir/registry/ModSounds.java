package com.rileycleavenger.heysir.registry;

import com.rileycleavenger.heysir.HeySirMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	public static final SoundEvent HEY_SIR = register("entity.heysir.hey_sir");
	public static final SoundEvent HEY_SIR_EXCUSE_ME = register("entity.heysir.hey_sir_excuse_me");
	public static final SoundEvent WIFE_AND_KIDS = register("entity.heysir.wife_and_kids");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = HeySirMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
	}
}
