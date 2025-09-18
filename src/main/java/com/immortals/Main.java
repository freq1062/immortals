package com.immortals;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.MinecraftServer;

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

		// Register the custom payloads
		PayloadTypeRegistry.playS2C().register(NetworkChannels.RuneS2CPayload.ID, NetworkChannels.RuneS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(NetworkChannels.SphereS2CPayload.ID,
				NetworkChannels.SphereS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(NetworkChannels.ItemS2CPayload.ID,
				NetworkChannels.ItemS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(NetworkChannels.SpellC2SPayload.ID,
				NetworkChannels.SpellC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(NetworkChannels.FragmentC2SPayload.ID,
				NetworkChannels.FragmentC2SPayload.CODEC);

		// Register a server tick event to process scheduled tasks
		ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
			scheduler.tick(server.getTicks());
		});

		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		ModItems.registerModItems();
		Immortals.register();
		Mortals.register();
		Spell.register();
		Weapons.register();

		LOGGER.info("Immortals S4 mod loaded!");
	}
}