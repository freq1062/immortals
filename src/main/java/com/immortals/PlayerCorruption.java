package com.immortals;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerCorruption {
    private static final Map<UUID, Integer> LEVELS = new ConcurrentHashMap<>();

    public static int get(ServerPlayerEntity player) {
        return LEVELS.getOrDefault(player.getUuid(), 0);
    }

    public static void set(ServerPlayerEntity player, int level) {
        int v = Math.max(-3, Math.min(3, level));
        LEVELS.put(player.getUuid(), v);
    }

    public static void increment(ServerPlayerEntity player) {
        set(player, get(player) + 1);
    }

    public static void decrement(ServerPlayerEntity player) {
        set(player, get(player) - 1);
    }
}