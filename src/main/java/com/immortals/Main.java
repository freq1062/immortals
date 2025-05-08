package com.immortals;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

import com.immortals.Mortal.Mortals;
import com.immortals.Mortal.Weapons;
import com.immortals.immortal.Immortals;
import com.immortals.immortal.Spell;
import com.immortals.item.ModItems;

public class Main implements ModInitializer {
    public static final String MOD_ID = "immortals";

    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ImmortalsConfig CONFIG;

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        try {
            CONFIG = ImmortalsConfig.load();
        } catch (IOException e) {
            LOGGER.error("Failed to load ImmortalsConfig", e);
            throw new RuntimeException("Configuration loading failed", e);
        }

        LOGGER.info("Immortals Mod Loaded");

        ModItems.registerModItems();
        Immortals.register();
        Mortals.register();
        Spell.register();
        Weapons.register();
        SupplyDropEvents.register();
    }
}