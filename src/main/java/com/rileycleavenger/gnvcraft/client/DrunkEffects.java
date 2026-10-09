package com.rileycleavenger.gnvcraft.client;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.drunk.DrunkSystem;
import com.rileycleavenger.gnvcraft.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client side of being drunk: the camera sways and rolls, the view narrows and finally goes black, and every
 * sound gets lower, quieter and wobbly. All of it scales with the synced drunk level (0..9; 9 kills).
 */
public final class DrunkEffects {
	private DrunkEffects() {
	}

	public static void register(IEventBus modBus) {
		modBus.addListener(DrunkEffects::onRegisterLayers);
		NeoForge.EVENT_BUS.addListener(DrunkEffects::onCameraAngles);
		NeoForge.EVENT_BUS.addListener(DrunkEffects::onFov);
		NeoForge.EVENT_BUS.addListener(DrunkEffects::onPlaySound);
	}

	/** Drunk level of the local player, 0 when not in a world. */
	static float level() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? 0.0F : mc.player.getData(ModAttachments.DRUNK);
	}

	/** 0..1 strength: starts at the second drink, full at the last. */
	static float strength(float level) {
		return Mth.clamp((level - 0.5F) / (DrunkSystem.DEATH_LEVEL - 0.5F), 0.0F, 1.0F);
	}

	private static float time() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? 0.0F : mc.player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		float s = strength(level());
		if (s <= 0.0F) {
			return;
		}
		float t = time();
		float amp = s * s;
		event.setRoll(event.getRoll() + Mth.sin(t * 0.045F) * 14.0F * amp);
		event.setYaw(event.getYaw() + Mth.sin(t * 0.031F) * 9.0F * amp);
		event.setPitch(event.getPitch() + Mth.cos(t * 0.037F) * 5.0F * amp);
	}

	private static void onFov(ViewportEvent.ComputeFov event) {
		float s = strength(level());
		if (s > 0.0F) {
			event.setFOV(event.getFOV() + Mth.sin(time() * 0.025F) * 14.0F * s * s);
		}
	}

	private static void onRegisterLayers(RegisterGuiLayersEvent event) {
		event.registerAboveAll(GnvCraftMod.id("drunk_overlay"), DrunkEffects::renderOverlay);
	}

	private static void renderOverlay(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
		float level = level();
		float s = strength(level);
		if (s <= 0.0F) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		float t = time();
		// Blurry double-vision wash: a slow pulsing tint over the whole view.
		int wash = (int) (Mth.clamp(0.05F + 0.18F * s, 0, 1) * (0.6F + 0.4F * Mth.sin(t * 0.08F)) * 255);
		g.fill(0, 0, w, h, (wash << 24) | 0x3A6B2E);
		// Tunnel vision: dark bands close in from the edges as the level rises past 3.
		float tunnel = Mth.clamp((level - 3.0F) / 5.0F, 0.0F, 1.0F);
		if (tunnel > 0.0F) {
			int steps = 12;
			for (int i = 0; i < steps; i++) {
				float f = (float) i / steps;
				int alpha = (int) (Mth.clamp(tunnel * (1.0F - f) * 1.1F, 0, 1) * 200);
				int bx = (int) (w * 0.5F * f * tunnel * 0.9F) ;
				int by = (int) (h * 0.5F * f * tunnel * 0.9F);
				int x1 = (int) (w * 0.5F * tunnel * 0.9F / steps);
				int y1 = (int) (h * 0.5F * tunnel * 0.9F / steps);
				int col = alpha << 24;
				int ix = (int) (w * 0.5F * tunnel * 0.9F * (1.0F - f));
				int iy = (int) (h * 0.5F * tunnel * 0.9F * (1.0F - f));
				g.fill(0, 0, ix, h, col);
				g.fill(w - ix, 0, w, h, col);
				g.fill(0, 0, w, iy, col);
				g.fill(0, h - iy, w, h, col);
			}
		}
		// Blackout: fully dark from 8.2 until the end.
		float black = Mth.clamp((level - 7.2F) / 1.0F, 0.0F, 1.0F);
		if (black > 0.0F) {
			g.fill(0, 0, w, h, ((int) (black * 255) << 24));
		}
	}

	private static void onPlaySound(PlaySoundEvent event) {
		float s = strength(level());
		SoundInstance original = event.getSound();
		if (s <= 0.0F || original == null || original.isLooping() || original.getDelay() > 0 || original.getSource() == net.minecraft.sounds.SoundSource.MUSIC) {
			return;
		}
		RandomSource random = RandomSource.create();
		float pitch = original.getPitch() * (1.0F - 0.35F * s + (random.nextFloat() - 0.5F) * 0.35F * s);
		float volume = original.getVolume() * (1.0F - 0.45F * s);
		event.setSound(new SimpleSoundInstance(original.getIdentifier(), original.getSource(), volume, Math.max(0.5F, pitch), random,
			false, 0, original.getAttenuation(), original.getX(), original.getY(), original.getZ(), original.isRelative()));
	}
}
