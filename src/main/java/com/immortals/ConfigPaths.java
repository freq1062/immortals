package com.immortals;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

public class ConfigPaths {
    public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    public static final Path IMMORTALS_PROPS = CONFIG_DIR.resolve("immortals.properties");
}
