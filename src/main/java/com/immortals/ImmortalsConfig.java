package com.immortals;

import java.io.IOException;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.OutputStream;

public class ImmortalsConfig {
    // Config keys and their default values
    private static final java.util.Map<String, Object> DEFAULTS = java.util.Map.ofEntries(
            java.util.Map.entry("supplyDropIntervalMs", 86_400_000L),
            java.util.Map.entry("supplyDropRadius", 50),
            java.util.Map.entry("supplyDropUnlockTime", 300_000),
            java.util.Map.entry("splinterBlowDmg", 0.15),
            java.util.Map.entry("dashCooldown", 15_000),
            java.util.Map.entry("glowCooldown", 45_000),
            java.util.Map.entry("glowDuration", 10),
            java.util.Map.entry("glowRadius", 30),
            java.util.Map.entry("backdraftCooldown", 25_000),
            java.util.Map.entry("backdraftDmg", 0.2),
            java.util.Map.entry("persistCooldown", 40_000),
            java.util.Map.entry("persistResistance", 7),
            java.util.Map.entry("blackoutCooldown", 45_000),
            java.util.Map.entry("blackoutWither", 8),
            java.util.Map.entry("blackoutBlind", 15),
            java.util.Map.entry("dragonAscentCooldown", 45_000),
            java.util.Map.entry("dragonAscentRadius", 10),
            java.util.Map.entry("dragonAscentLevitation", 3),
            java.util.Map.entry("dragonAscentTotalDmg", 0.4),
            java.util.Map.entry("fractalTotalDmg", 0.45),
            java.util.Map.entry("timeSlowDuration", 10_000),
            java.util.Map.entry("timeSlowCooldown", 60_000),
            java.util.Map.entry("timeSlowRadius", 7),
            java.util.Map.entry("overclockCooldown", 45_000),
            java.util.Map.entry("overclockDuration", 20_000),
            java.util.Map.entry("blinkDuration", 5_000),
            java.util.Map.entry("blinkCooldown", 40_000),
            java.util.Map.entry("phaseChangeCooldown", 20_000),
            java.util.Map.entry("mortalMaxXpGain", 0.25));

    private final java.util.Map<String, Object> values = new java.util.HashMap<>(DEFAULTS);

    public Object get(String key) {
        if (!values.containsKey(key)) {
            throw new IllegalArgumentException("Unknown config key: " + key);
        }
        return values.get(key);
    }

    public static ImmortalsConfig load() throws IOException {
        ImmortalsConfig cfg = new ImmortalsConfig();
        Properties p = new Properties();

        Files.createDirectories(ConfigPaths.CONFIG_DIR);
        Path file = ConfigPaths.IMMORTALS_PROPS;
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
        }

        for (var entry : DEFAULTS.entrySet()) {
            String key = entry.getKey();
            Object def = entry.getValue();
            String prop = p.getProperty(key);
            Object value = def;
            try {
                if (def instanceof Integer) {
                    value = prop != null ? Integer.parseInt(prop) : def;
                } else if (def instanceof Long) {
                    value = prop != null ? Long.parseLong(prop) : def;
                } else if (def instanceof Double) {
                    value = prop != null ? Double.parseDouble(prop) : def;
                }
            } catch (NumberFormatException ignored) {
            }
            cfg.values.put(key, value);
            p.setProperty(key, value.toString());
        }

        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "=== Immortals mod settings ===");
        }

        return cfg;
    }
}