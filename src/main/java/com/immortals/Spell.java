package com.immortals;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines all available spells, their cooldowns, activation logic,
 * and static methods to bind spells to hotbar slots and manage cooldowns.
 */
public enum Spell {

    DASH("dash", 5_000) {
        @Override
        public void activate(ServerPlayerEntity player) {
            Vec3d dir = player.getRotationVec(1.0F);
            player.addVelocity(dir.x * 2.5, dir.y * 1.2, dir.z * 2.5);
            player.velocityModified = true;
        }
    },

    GLOW("glow", 60_000) {
        @Override
        public void activate(ServerPlayerEntity player) {
            for (var other : player.getWorld().getPlayers()) {
                if (other instanceof ServerPlayerEntity serverPlayer &&
                        !serverPlayer.equals(player) &&
                        serverPlayer.squaredDistanceTo(player) <= 900) {
                    serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, false, false));
                }
            }
            player.sendMessage(Text.literal("§eYou glow, revealing nearby players!"), true);
        }
    };

    private final String id;
    private final long cooldownMs;

    Spell(String id, long cooldownMs) {
        this.id = id;
        this.cooldownMs = cooldownMs;
    }

    /** Get capitalized display name for spell */
    public String getDisplayName() {
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    /** The unique identifier players will use in `/bind ...` */
    public String getId() {
        return id;
    }

    /** Milliseconds between uses */
    public long getCooldownMs() {
        return cooldownMs;
    }

    /** Concrete spells implement their effect here */
    public abstract void activate(ServerPlayerEntity player);

    /** player UUID → (hotbar slot → Spell) */
    private static final Map<UUID, Map<Integer, Spell>> BINDINGS = new ConcurrentHashMap<>();

    /** player UUID → (Spell → last use timestamp ms) */
    private static final Map<UUID, Map<Spell, Long>> LAST_USED = new ConcurrentHashMap<>();

    /**
     * Bind a spell to a 0-8 hotbar slot, replacing any existing binding for the
     * spell
     */
    public static void bind(ServerPlayerEntity player, int slot, Spell spell) {
        var bindings = BINDINGS.computeIfAbsent(player.getUuid(), u -> new HashMap<>());

        // Remove the existing binding for the spell, if any
        bindings.entrySet().removeIf(entry -> entry.getValue() == spell);

        // Add the new binding
        bindings.put(slot, spell);
    }

    /** Get the Spell bound to this slot, or null */
    public static Spell getBound(ServerPlayerEntity player, int slot) {
        var map = BINDINGS.get(player.getUuid());
        return map == null ? null : map.get(slot);
    }

    /** Has the spell’s cooldown expired? */
    public static boolean canUse(ServerPlayerEntity player, Spell spell) {
        var map = LAST_USED.get(player.getUuid());
        if (map == null)
            return true;
        Long last = map.get(spell);
        if (last == null)
            return true;
        return (System.currentTimeMillis() - last) >= spell.cooldownMs;
    }

    /** Record that the player just used this spell */
    public static void recordUse(ServerPlayerEntity player, Spell spell) {
        LAST_USED
                .computeIfAbsent(player.getUuid(), u -> new HashMap<>())
                .put(spell, System.currentTimeMillis());
        // right after Spell.recordUse(player, spell) inside your Activate callback:
        long cd = spell.getCooldownMs();
        int secs = (int) Math.ceil(cd / 1000.0);
        ModEvents.pendingCooldownNotifications
                .computeIfAbsent(player.getUuid(), u -> new ConcurrentHashMap<>())
                .put(spell, secs);
    }

    /**
     * Attempt to activate—returns true on success, false if unbound or on cooldown
     */
    public static boolean tryActivate(ServerPlayerEntity player, int slot) {
        // must have ascended
        if (AscensionUtils.getAscended(player) != 1) {
            return false;
        }
        int corr = AscensionUtils.getCorruption(player);
        Spell spell = getBound(player, slot);

        // level requirements
        if (spell == DASH && corr < 2) {
            return false;
        }
        if (spell == GLOW && corr < 3) {
            return false;
        }

        if (spell == null) {
            player.sendMessage(Text.literal("No spell bound to slot " + (slot + 1)), true);
            return false;
        }

        // cooldown check (unchanged)
        if (!canUse(player, spell)) {
            player.sendMessage(Text.literal("§c" + spell.getDisplayName() + " is on cooldown!"), true);
            return false;
        }

        // activate
        spell.activate(player);
        recordUse(player, spell);
        return true;
    }

    /** Helper to look up by name in your /bind command */
    public static Spell fromId(String id) {
        for (Spell s : values()) {
            if (s.id.equalsIgnoreCase(id))
                return s;
        }
        return null;
    }

    /** For tab‐completion in your command */
    public static Collection<String> allIds() {
        List<String> ids = new ArrayList<>();
        for (Spell s : values())
            ids.add(s.id);
        return ids;
    }

    public static long getLastUse(ServerPlayerEntity player, Spell spell) {
        var map = LAST_USED.get(player.getUuid());
        return map == null ? 0L : map.getOrDefault(spell, 0L);
    }

}
