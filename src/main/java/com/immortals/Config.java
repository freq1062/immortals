package com.immortals;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Simple, ordered, commentable properties-backed config.
 * No external deps; requires Java 16+ (uses record).
 */
public class Config {
    public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    public static final Path CONFIG_FILE = CONFIG_DIR.resolve("immortals.properties");

    private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();
    private final List<ConfigOption> options;

    private Config(List<ConfigOption> options) {
        this.options = options;
        // populate defaults
        for (ConfigOption opt : options)
            values.put(opt.key(), opt.defaultValue());
    }

    // A small container for a key, default value, and comment.
    private record ConfigOption(String key, Object defaultValue, String comment) {
    }

    /* ========== Define your options concisely here ========== */
    private static List<ConfigOption> buildOptions() {
        return List.of(
                new ConfigOption("dashCooldown", 300, "Cooldown for dash ability (ticks)"),
                new ConfigOption("glowCooldown", 900, "Cooldown for glow ability (ticks)"),
                new ConfigOption("glowRadius", 30.0, "Radius for glow ability (blocks)"),
                new ConfigOption("glowDuration", 100, "Duration for glow ability (ticks)"),
                new ConfigOption("frostbiteCooldown", 600, "Cooldown for frostbite ability (ticks)"),
                new ConfigOption("frostbiteDuration", 200, "Duration of frostbite effect (ticks)"),
                new ConfigOption("blackoutCooldown", 1200, "Cooldown for blackout ability (ticks)"),
                new ConfigOption("blackoutRadius", 7.0, "Radius for blackout ability (blocks)"),
                new ConfigOption("blackoutBlind", 40, "Duration of blindness effect in blackout (ticks)"),
                new ConfigOption("blackoutDuration", 100, "Duration of blackout effect (ticks)"),
                new ConfigOption("persistCooldown", 800, "Cooldown for persist ability (ticks)"),
                new ConfigOption("persistResistance", 140, "Duration of resistance effect in persist (ticks)"),
                new ConfigOption("splinterBlowCombo", 5, "Number of hits required to trigger splinter blow"),
                new ConfigOption("splinterBlowDmg", 0.15, "Damage dealt by splinter blow as a fraction of max HP"),
                new ConfigOption("shrinkCooldown", 1000, "Cooldown for shrink ability (ticks)"),
                new ConfigOption("shrinkDuration", 200, "Duration of shrink ability (ticks)"),
                new ConfigOption("shrinkScale", 0.5, "Scale factor for shrink ability (raw attribute value)"),
                new ConfigOption("shrinkDamageMultiplier", 1.5, "Damage multiplier while shrunk"),
                new ConfigOption("echoCooldown", 1200, "Cooldown for echo ability (ticks)"),
                new ConfigOption("surgeCooldown", 900, "Cooldown for surge ability (ticks)"),
                new ConfigOption("surgeDuration", 60, "Duration of surge ability (ticks)"),
                new ConfigOption("surgeReduction", 0.5, "Percentage of original damage while surge active (0.0-1.0)"),
                new ConfigOption("surgeDamageCap", 60.0, "Maximum damage cap for surge ability (absolute value)"),
                new ConfigOption("surgeRadius", 10.0, "Radius for surge ability (blocks)"),
                new ConfigOption("lockCooldown", 1200, "Cooldown for lock ability (ticks)"),
                new ConfigOption("lockDuration", 120, "Duration of lock ability (ticks)"),
                new ConfigOption("dragonAscentRadius", 10.0, "Radius for dragon ascent ability (blocks)"),
                new ConfigOption("dragonAscentLevitation", 60, "Levitation duration for dragon ascent (ticks)"),
                new ConfigOption("dragonAscentTotalDmg", 0.4, "Damage dealt by dragon ascent as a fraction of max HP"),
                new ConfigOption("dragonAscentCooldown", 900, "Cooldown for dragon ascent ability (ticks)"),
                new ConfigOption("timeSlowCooldown", 800, "Cooldown for time slow ability (ticks)"),
                new ConfigOption("timeSlowRadius", 7.0, "Radius for time slow ability (blocks)"),
                new ConfigOption("timeSlowDuration", 200, "Duration of time slow ability (ticks)"),
                new ConfigOption("mortalMaxXpGain", 0.25, "Maximum XP multiplier for mortals"),
                new ConfigOption("phaseChangeCooldown", 400, "Cooldown for phase change ability (ticks)"),
                new ConfigOption("fractalTotalDmg", 0.45, "Damage dealt by fractal as a fraction of max HP"),
                new ConfigOption("overclockCooldown", 900, "Cooldown for overclock ability (ticks)"),
                new ConfigOption("overclockDuration", 400, "Duration of overclock ability (ticks)"),
                new ConfigOption("blinkCooldown", 800, "Cooldown for blink ability (ticks)"),
                new ConfigOption("blinkDuration", 100, "Duration of blink ability (ticks)"),
                new ConfigOption("supplyDropWebhookURL", "", "Webhook URL for supply drops (leave empty to disable)"),
                new ConfigOption("maxSpellSlots", 3,
                        "Maximum number of spell slots. Still based on corruption"));
    }

    /* ========== Loading / saving ========== */
    public static Config load() throws IOException {
        List<ConfigOption> opts = buildOptions();
        Config cfg = new Config(opts);

        // Read existing properties if present
        Properties p = new Properties();
        Files.createDirectories(CONFIG_DIR);
        if (Files.exists(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                p.load(in);
            }
        }

        // For each option, parse the string into the proper type (fallback to default
        // on parse errors)
        for (ConfigOption opt : opts) {
            String key = opt.key();
            Object def = opt.defaultValue();
            String prop = p.getProperty(key);
            Object value = def;
            try {
                if (def instanceof Integer) {
                    value = prop != null ? Integer.parseInt(prop.trim()) : def;
                } else if (def instanceof Long) {
                    value = prop != null ? Long.parseLong(prop.trim()) : def;
                } else if (def instanceof Double) {
                    value = prop != null ? Double.parseDouble(prop.trim()) : def;
                } else if (def instanceof Boolean) {
                    value = prop != null ? Boolean.parseBoolean(prop.trim()) : def;
                } else { // treat as string
                    value = prop != null ? prop : def;
                }
            } catch (NumberFormatException ignore) {
                /* keep default */ }

            cfg.values.put(key, value);
            p.setProperty(key, value.toString()); // ensure property exists for saving
        }

        // Save back with pretty formatting (comments, blank lines, order)
        cfg.savePretty();

        return cfg;
    }

    /**
     * Writes a nicer, human-friendly properties file with comments and blank lines.
     * This is done instead of Properties.store() so we can control
     * ordering/comments.
     */
    private void savePretty() throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(CONFIG_FILE)) {
            w.write("# Immortals mod settings");
            w.newLine();
            w.write("# Edit the values below. Blank lines and comments are preserved here.");
            w.newLine();
            w.newLine();

            // Example of grouping: we infer groups by comments embedded in
            // ConfigOption.comment (you design)
            for (ConfigOption opt : options) {
                String comment = opt.comment();
                if (comment != null && !comment.isBlank()) {
                    // allow multi-line comments separated by '\n'
                    String[] lines = comment.split("\n");
                    for (String c : lines) {
                        w.write("# " + c.trim());
                        w.newLine();
                    }
                }
                // key = value
                Object val = values.get(opt.key());
                w.write(opt.key() + " = " + String.valueOf(val));
                w.newLine();
                w.newLine(); // blank line between entries for readability
            }
            w.flush();
        }
    }

    /* ========== Typed getters ========== */
    public int getInt(String key) {
        return asType(key, Integer.class);
    }

    public long getLong(String key) {
        return asType(key, Long.class);
    }

    public double getDouble(String key) {
        return asType(key, Double.class);
    }

    public boolean getBoolean(String key) {
        return asType(key, Boolean.class);
    }

    public String getString(String key) {
        return asType(key, String.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T asType(String key, Class<T> cls) {
        if (!values.containsKey(key))
            throw new IllegalArgumentException("Unknown config key: " + key);
        Object v = values.get(key);
        if (cls.isInstance(v))
            return (T) v;
        // attempt conversions for common mismatches (e.g., integer stored as Long)
        if (cls == Integer.class && v instanceof Number)
            return (T) Integer.valueOf(((Number) v).intValue());
        if (cls == Long.class && v instanceof Number)
            return (T) Long.valueOf(((Number) v).longValue());
        if (cls == Double.class && v instanceof Number)
            return (T) Double.valueOf(((Number) v).doubleValue());
        if (cls == String.class)
            return (T) v.toString();
        if (cls == Boolean.class && v instanceof String)
            return (T) Boolean.valueOf((String) v);
        throw new ClassCastException("Config key '" + key + "' is not of type " + cls.getSimpleName());
    }

    /* ========== Optional: set and persist at runtime ========== */
    public void set(String key, Object value) throws IOException {
        if (!values.containsKey(key))
            throw new IllegalArgumentException("Unknown config key: " + key);
        values.put(key, value);
        savePretty();
    }
}
