package com.rileycleavenger.gnvcraft.registry;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
	private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, GnvCraftMod.MOD_ID);

	public static final DeferredHolder<SoundEvent, SoundEvent> HEY_SIR = register("entity.heysir.hey_sir");
	public static final DeferredHolder<SoundEvent, SoundEvent> HEY_SIR_EXCUSE_ME = register("entity.heysir.hey_sir_excuse_me");
	public static final DeferredHolder<SoundEvent, SoundEvent> WIFE_AND_KIDS = register("entity.heysir.wife_and_kids");

	private ModSounds() {
	}

	private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
		return SOUND_EVENTS.register(name, SoundEvent::createVariableRangeEvent);
	}

	public static void register(IEventBus modBus) {
		SOUND_EVENTS.register(modBus);
	}
}
