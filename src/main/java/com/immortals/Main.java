package com.immortals;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import com.immortals.api.TaskScheduler;
import com.immortals.Immortal.Immortals;
import com.immortals.Immortal.Spell;
import com.immortals.Mortal.Mortals;
import com.immortals.Mortal.Weapons;
import com.immortals.network.NetworkChannels;
import io.github.dennisochulor.tickrate.api.TickRateAPI;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {

	public static final String MOD_ID = "immortals";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final TaskScheduler scheduler = new TaskScheduler();
	public static TickRateAPI api;
	public static Config CONFIG;

	@Override
	public void onInitialize() {

		try {
			CONFIG = Config.load();
		} catch (IOException e) {
			LOGGER.error("Failed to load ImmortalsConfig", e);
			throw new RuntimeException("Configuration loading failed", e);
		}
		// Initialize TickRateAPI after the server is fully initialized
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			// Safe to use TickRateAPI here
			api = TickRateAPI.getInstance();
		});

		// Server to client
		PayloadTypeRegistry.playS2C().register(NetworkChannels.RuneS2CPayload.ID, NetworkChannels.RuneS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(NetworkChannels.SphereS2CPayload.ID,
				NetworkChannels.SphereS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(NetworkChannels.ItemS2CPayload.ID,
				NetworkChannels.ItemS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(NetworkChannels.SpellHudS2CPayload.ID,
				NetworkChannels.SpellHudS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(NetworkChannels.GhostS2CPayload.ID,
				NetworkChannels.GhostS2CPayload.CODEC);
		// Client to server
		PayloadTypeRegistry.playC2S().register(NetworkChannels.SpellC2SPayload.ID,
				NetworkChannels.SpellC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(NetworkChannels.FragmentC2SPayload.ID,
				NetworkChannels.FragmentC2SPayload.CODEC);

		// Register a server tick event to process scheduled tasks
		ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
			scheduler.tick(server.getTicks());
		});

		Identifier glassSoundId = Identifier.of(MOD_ID, "glass");
		Registry.register(Registries.SOUND_EVENT, glassSoundId, SoundEvent.of(glassSoundId));
		ModItems.registerModItems();
		Immortals.register();
		Mortals.register();
		Spell.register();
		Weapons.register();

		LOGGER.info("Immortals S4 mod loaded!");
	}
}