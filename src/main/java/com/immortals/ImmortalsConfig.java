package com.immortals;

import java.io.IOException;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.OutputStream;

public class ImmortalsConfig {
    // Default values
    public long supplyDropIntervalMs = 86_400_000; // 24 hours
    public int supplyDropRadius = 50; // 50 blocks
    public int supplyDropUnlockTime = 300_000; // 5 minutes

    public int dashCooldown = 15_000; // 15 seconds
    public int glowCooldown = 60_000; // 1 minute
    public int dragonAscentCooldown = 45_000; // 45 seconds
    public int dragonAscentRadius = 10; // 10 blocks
    public int timeSlowDuration = 10_000; // 10 seconds
    public int timeSlowCooldown = 60_000; // 60 seconds
    public int timeSlowRadius = 7; // 7 blocks
    public int overclockCooldown = 45_000; // 45 seconds
    public int overclockDuration = 20_000; // 20 seconds
    public int blinkDuration = 5_000; // 5 seconds
    public int phaseChangeCooldown = 20_000; // 20 seconds

    // Load or create defaults
    public static ImmortalsConfig load() throws IOException {
        ImmortalsConfig cfg = new ImmortalsConfig();
        Properties p = new Properties();

        // Make sure config folder exists
        Files.createDirectories(ConfigPaths.CONFIG_DIR);

        // If file exists, read it
        Path file = ConfigPaths.IMMORTALS_PROPS;
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
        }

        // Use reflection to parse and write properties dynamically
        for (var field : ImmortalsConfig.class.getDeclaredFields()) {
            if (!field.isSynthetic() && (field.getType() == int.class || field.getType() == long.class)) {
                String key = field.getName();
                try {
                    if (field.getType() == int.class) {
                        field.setInt(cfg, parseInt(p, key, field.getInt(cfg)));
                    } else if (field.getType() == long.class) {
                        field.setLong(cfg, parseLong(p, key, field.getLong(cfg)));
                    }
                    p.setProperty(key, field.get(cfg).toString());
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to access field: " + key, e);
                }
            }
        }

        // Write back *all* keys (fills in any missing defaults)
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "=== Immortals mod settings ===");
        }

        return cfg;
    }

    private static long parseLong(Properties p, String key, long def) {
        try {
            return Long.parseLong(p.getProperty(key, Long.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int parseInt(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, Integer.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}