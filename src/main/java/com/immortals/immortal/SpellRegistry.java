package com.immortals.Immortal;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.immortals.Main;
import com.immortals.Utils;
import com.immortals.api.PlayerImmortalsData;

import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import com.immortals.item.ModItems;

/**
 * Defines all available spells, their cooldowns, activation logic,
 * and static methods to bind spells to hotbar slots and manage cooldowns.
 */
public enum SpellRegistry {

    DASH("dash", Main.CONFIG.dashCooldown) {
        @Override
        public void activate(ServerPlayerEntity player) {
            Vec3d dir = player.getRotationVec(1.0F);
            Vec3d startPos = player.getPos();
            player.addVelocity(dir.x * 2.5, dir.y * 1.2, dir.z * 2.5);
            player.velocityModified = true;

            ServerWorld world = (ServerWorld) player.getWorld();
            int rings = 3;
            int particlesPerRing = 20;

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_WITHER_SHOOT,
                    net.minecraft.sound.SoundCategory.PLAYERS, 0.5F, 1.2F);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sound.SoundEvents.UI_TOAST_IN,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);

            for (int i = 0; i < rings; i++) {
                double ringRadius = 0.5 + i * 0.4; // each ring gets larger
                double backStep = 0.6 + i * 0.5; // spacing behind player

                // Center of ring behind the player
                Vec3d ringCenter = startPos.subtract(dir.multiply(backStep));

                for (int j = 0; j < particlesPerRing; j++) {
                    double angle = 2 * Math.PI * j / particlesPerRing;

                    // Circle in local (X, Z) space
                    double localX = Math.cos(angle) * ringRadius;
                    double localY = Math.sin(angle) * ringRadius;
                    Vec3d localOffset = new Vec3d(localX, localY, 0);

                    // Rotate local offset to align with player's look direction
                    Vec3d rotated = rotateVectorToMatchDirection(localOffset, dir);
                    Vec3d finalPos = ringCenter.add(rotated);

                    world.spawnParticles(ParticleTypes.CLOUD, finalPos.x, finalPos.y, finalPos.z, 1, 0, 0, 0, 0.01);
                }
            }
        }
    },

    GLOW("glow", Main.CONFIG.glowCooldown) {
        @Override
        public void activate(ServerPlayerEntity player) {
            ServerWorld world = (ServerWorld) player.getWorld();

            // Apply glowing effect to nearby players
            for (ServerPlayerEntity other : world.getPlayers()) {
                if (!other.equals(player) && other.squaredDistanceTo(player) <= 30 * 30) {
                    other.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, false, false));
                }
            }

            // Spawn lattice of gold dust outlining a sphere centered at mid‐body height
            Vec3d center = player.getPos().add(0, player.getStandingEyeHeight() * 0.5, 0);
            DustParticleEffect goldDust = new DustParticleEffect(0xFFD700, 3f);

            int maxRadius = 30;
            double spacing = 2.0; // distance between lattice points

            // Compute lattice points only at the edges
            List<Vec3d> latticePoints = new ArrayList<>();
            for (double x = -maxRadius; x <= maxRadius; x += spacing) {
                for (double y = -maxRadius; y <= maxRadius; y += spacing) {
                    for (double z = -maxRadius; z <= maxRadius; z += spacing) {
                        double distanceSquared = x * x + y * y + z * z;
                        if (distanceSquared <= maxRadius * maxRadius
                                && distanceSquared >= (maxRadius - spacing) * (maxRadius - spacing)) {
                            latticePoints.add(new Vec3d(x, y, z));
                        }
                    }
                }
            }

            // Schedule particles using the task queue
            for (Vec3d offset : latticePoints) {
                Vec3d particlePos = center.add(offset);
                Spell.addTask(player.getUuid(), () -> {
                    world.spawnParticles(goldDust, particlePos.x, particlePos.y, particlePos.z, 1, 0, 0, 0, 0.01);
                }, 0);
            }

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_BELL_RESONATE,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 0.8F);
            player.sendMessage(Text.literal("§eYou glow, revealing nearby players!"), true);
        }
    },

    DRAGON_ASCENT("dragon_ascent", Main.CONFIG.dragonAscentCooldown) {
        @Override
        public void activate(ServerPlayerEntity player) {
            // Must have dragon egg
            if (!player.getInventory().contains(new ItemStack(Items.DRAGON_EGG))) {
                player.sendMessage(Text.literal("§cYou need the Dragon Egg to cast this spell."), true);
                return;
            }

            Utils.updateRune(player, "dragon_ascent", 1);

            Spell.addTask(player.getUuid(), () -> {
                for (int i = 0; i < 100; i++) { // 5 seconds
                    Spell.addTask(player.getUuid(), () -> {
                        Utils.drawDragonAscent(player.getPos(), player.getWorld());
                    }, i * 50); // schedule every 50ms
                }
            }, 0);

            // Propel player into the air
            player.setVelocity(player.getVelocity().x, 1.3, player.getVelocity().z);
            player.velocityModified = true;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 60, 0, false, false));

            ServerWorld world = (ServerWorld) player.getWorld();
            double radius = Main.CONFIG.dragonAscentRadius; // detection range
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_AMBIENT,
                    net.minecraft.sound.SoundCategory.PLAYERS, 0.5F, 1.0F);

            // Target all players and hostile entities within the radius
            List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class,
                    player.getBoundingBox().expand(radius),
                    e -> (e instanceof ServerPlayerEntity) || (e instanceof HostileEntity));

            for (LivingEntity t : targets) {
                // Skip yourself
                if (t == player)
                    continue;

                // Skip teammates
                if (t.isTeammate(player))
                    continue;

                // Spawn 2 lightning bolts, ignores armor
                Vec3d tpos = t.getPos().add(0, t.getStandingEyeHeight() * 0.5, 0);
                DamageSource source = world.getDamageSources().create(DamageTypes.MAGIC);
                Spell.addTask(player.getUuid(), () -> {
                    t.damage(world, source, 8.0f);
                    Utils.strikeLightning(world, tpos);
                }, 1000); // 1
                          // second
                          // delay

                Spell.addTask(player.getUuid(), () -> {
                    t.damage(world, source, 8.0f);
                    Utils.strikeLightning(world, tpos);
                }, 2000); // 2
                          // seconds
                          // delay
            }
            Spell.addTask(player.getUuid(), () -> {
                Utils.updateRune(player, "dragon_ascent", 0);
            }, 3000);
            player.sendMessage(Text.literal("§dThe dragon rune smites your enemies!"), true);
        }
    },

    TIMESLOW("timeslow", Main.CONFIG.timeSlowCooldown) {
        @Override
        public void activate(ServerPlayerEntity player) {

            // Must have timekeeper
            if (!player.getInventory().contains(new ItemStack(ModItems.TIMEKEEPER))) {
                player.sendMessage(Text.literal("§cYou need the Timekeeper to cast this spell."), true);
                return;
            }
            Utils.updateRune(player, "timeslow", 1);

            ServerWorld world = (ServerWorld) player.getWorld();
            Vec3d center = player.getPos();
            int radius = Main.CONFIG.timeSlowRadius;
            int duration = Main.CONFIG.timeSlowDuration;
            int checkInterval = 100; // 2
                                     // ticks
                                     // between
                                     // checks

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_BEACON_ACTIVATE,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 0.5F);

            Spell.addTask(player.getUuid(), () -> {
                for (int i = 0; i < 200; i++) { // 10 seconds
                    final int step = i;
                    double handAngle = (2 * Math.PI * step) / 200;
                    Spell.addTask(player.getUuid(), () -> {
                        Utils.drawTimeslow(center, world, radius, 100, handAngle);
                    }, step * 50); // schedule every 50ms
                }
            }, 0);

            // Play beacon ambient sound for the duration of the spell
            Spell.addTask(player.getUuid(), () -> {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sound.SoundEvents.BLOCK_BEACON_AMBIENT,
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
            }, 0);

            // Repeat ambient sound every 80 ticks (4 seconds) for the spell duration
            int ambientRepeat = 80 * 50; // 80 ticks * 50ms per tick = 4000ms
            for (int i = ambientRepeat; i < duration; i += ambientRepeat) {
                Spell.addTask(player.getUuid(), () -> {
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            net.minecraft.sound.SoundEvents.BLOCK_BEACON_AMBIENT,
                            net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
                }, i);
            }

            // Track affected entities and their tickrate state
            Set<UUID> slowedEntities = Collections.synchronizedSet(new HashSet<>());

            // Schedule periodic checks for the duration, and restore after the last check
            final Vec3d effectCenter = center; // capture the center at cast time
            int numChecks = duration / checkInterval;
            for (int i = 0; i <= numChecks; i++) {
                int t = i * checkInterval;
                Spell.addTask(player.getUuid(), () -> {
                    // Find all living entities (including players) within the radius, except
                    // yourself
                    List<Entity> inZone = world.getOtherEntities(null,
                            player.getBoundingBox().expand(radius).offset(effectCenter.subtract(player.getPos())),
                            e -> e != player && e.squaredDistanceTo(effectCenter) <= radius * radius);

                    // Slow new entities entering the zone
                    for (Entity entity : inZone) {
                        if (slowedEntities.add(entity.getUuid())) {
                            // Set tickrate to 5 using Tickrate mod's command
                            world.getServer().getCommandManager().executeWithPrefix(
                                    world.getServer().getCommandSource(),
                                    String.format("tick entity %s rate 5", entity.getUuid()));
                        }
                    }

                    // Restore tickrate for entities that left the zone
                    slowedEntities.removeIf(uuid -> {
                        Entity entity = world.getEntity(uuid);
                        if (entity == null || entity.squaredDistanceTo(effectCenter) > radius * radius) {
                            // Set tickrate back to 20
                            world.getServer().getCommandManager().executeWithPrefix(
                                    world.getServer().getCommandSource(),
                                    String.format("tick entity %s rate 20", uuid));
                            return true;
                        }
                        return false;
                    });
                }, t);
            }

            // Restore tickrate for any remaining entities after the last check
            Spell.addTask(player.getUuid(), () -> {

                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sound.SoundEvents.BLOCK_BEACON_DEACTIVATE,
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 0.5F);
                Utils.updateRune(player, "timeslow", 0);
                for (UUID uuid : slowedEntities) {
                    world.getServer().getCommandManager().executeWithPrefix(world.getServer().getCommandSource(),
                            String.format("tick entity %s rate 20", uuid));
                }
                slowedEntities.clear();
            }, (numChecks + 1) * checkInterval);

            player.sendMessage(Text.literal("§7Timeslow in effect!"), true);
        }
    };

    // Spell interface

    private final String id;
    private final long cooldownMs;
    /** player UUID -> (Spell -> last use timestamp ms) */
    private static final Map<UUID, Map<SpellRegistry, Long>> LAST_USED = new ConcurrentHashMap<>();
    // private static final Map<UUID, EnumSet<SpellRegistry>> activeSpells = new
    // ConcurrentHashMap<>();

    /** Returns true if the given spell is currently active for that player. */
    public static boolean isActive(ServerPlayerEntity player, SpellRegistry spell) {
        return System.currentTimeMillis() - getLastUse(player, spell) < spell.getCooldownMs();
    }

    SpellRegistry(String id, long cooldownMs) {
        this.id = id;
        this.cooldownMs = cooldownMs;
    }

    /** Get capitalized display name for spell */
    public String getDisplayName() {
        String[] words = id.split("_");
        StringBuilder displayName = new StringBuilder();
        for (String word : words) {
            displayName.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }
        return displayName.toString().trim();
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

    /** Bind a spell to the first available slot */
    public static int bindDefault(ServerPlayerEntity player, int slot, SpellRegistry spell) {
        Map<Integer, String> bindings = ((PlayerImmortalsData) player).getSpellBindings();

        if (bindings.containsKey(slot)) {
            // Find the next unbound slot
            slot = findFirstUnboundSlot(player);
            if (slot == -1) {
                return -1;
            }
        }

        bindings.put(slot, spell.getId());
        return slot;
    }

    /** Bind a spell, replacing the spell that was there if necessary */
    public static void bind(ServerPlayerEntity player, int slot, SpellRegistry spell) {
        Map<Integer, String> bindings = ((PlayerImmortalsData) player).getSpellBindings();
        bindings.entrySet().removeIf(entry -> spell.getId().equals(entry.getValue()));
        bindings.put(slot, spell.getId());
    }

    /** Unbind a spell */
    public static void unbind(ServerPlayerEntity player, SpellRegistry spell) {
        Map<Integer, String> bindings = ((PlayerImmortalsData) player).getSpellBindings();
        bindings.entrySet().removeIf(entry -> spell.getId().equals(entry.getValue()));
    }

    /**
     * Find the first unbound slot from 1-9 and return it, or -1 if all are bound
     */
    public static int findFirstUnboundSlot(ServerPlayerEntity player) {
        Map<Integer, String> bindings = ((PlayerImmortalsData) player).getSpellBindings();
        for (int slot = 0; slot < 9; slot++) {
            if (!bindings.containsKey(slot)) {
                return slot;
            }
        }
        return -1;
    }

    /** Get the Spell bound to this slot, or null */
    public static SpellRegistry getBound(ServerPlayerEntity player, int slot) {
        String id = ((PlayerImmortalsData) player).getSpellBindings().get(slot);
        return SpellRegistry.fromId(id);
    }

    /** Check if a spell is bound to any slot */
    public static boolean isSpellBound(ServerPlayerEntity player, SpellRegistry spell) {
        Map<Integer, String> bindings = ((PlayerImmortalsData) player).getSpellBindings();
        return bindings.containsValue(spell.getId());
    }

    /** Has the spell’s cooldown expired? */
    public static boolean canUse(ServerPlayerEntity player, SpellRegistry spell) {
        var map = LAST_USED.get(player.getUuid());
        if (map == null)
            return true;
        Long last = map.get(spell);
        if (last == null)
            return true;
        return (System.currentTimeMillis() - last) >= spell.cooldownMs;
    }

    /** Record that the player just used this spell */
    public static void recordUse(ServerPlayerEntity player, SpellRegistry spell) {
        LAST_USED
                .computeIfAbsent(player.getUuid(), u -> new HashMap<>())
                .put(spell, System.currentTimeMillis());
        // right after Spell.recordUse(player, spell) inside your Activate callback:
        long cd = spell.getCooldownMs();
        int secs = (int) Math.ceil(cd / 1000.0);
        Spell.pendingCooldownNotifications
                .computeIfAbsent(player.getUuid(), u -> new ConcurrentHashMap<>())
                .put(spell, secs);
    }

    /**
     * Attempt to activate—returns true on success, false if unbound or on cooldown
     */
    public static boolean tryActivate(ServerPlayerEntity player, int slot) {
        // Must have ascended
        if (!Utils.getAscended(player)) {
            return false;
        }
        int corr = Utils.getCorruption(player);
        SpellRegistry spell = getBound(player, slot);

        if (spell == null) {
            player.sendMessage(Text.literal("No spell bound to slot " + (slot + 1)), true);
            return false;
        }

        // Level requirements
        if (spell == DASH && corr < 2) {
            return false;
        }
        if (spell == GLOW && corr < 3) {
            return false;
        }
        // Must have a dragon egg to use dragon ascent
        if (!player.getInventory().contains(new ItemStack(Items.DRAGON_EGG)) && spell.id.equals("dragon_ascent")) {
            unbind(player, SpellRegistry.DRAGON_ASCENT);
            return false;
        }

        // Must have a timekeeper to use timeslow
        if (!player.getInventory().contains(new ItemStack(ModItems.TIMEKEEPER)) && spell.id.equals("timeslow")) {
            unbind(player, SpellRegistry.TIMESLOW);
            return false;
        }

        // Cooldown needs to be up
        if (!canUse(player, spell)) {
            player.sendMessage(Text.literal("§c" + spell.getDisplayName() + " is on cooldown!"), true);
            return false;
        }

        spell.activate(player);
        recordUse(player, spell);
        return true;
    }

    /** Helper to look up spell by id */
    public static SpellRegistry fromId(String id) {
        for (SpellRegistry s : values()) {
            if (s.id.equalsIgnoreCase(id))
                return s;
        }
        return null;
    }

    /** For tab‐completion */
    public static Collection<String> allIds() {
        List<String> ids = new ArrayList<>();
        for (SpellRegistry s : values())
            ids.add(s.id);
        return ids;
    }

    public static long getLastUse(ServerPlayerEntity player, SpellRegistry spell) {
        var map = LAST_USED.get(player.getUuid());
        return map == null ? 0L : map.getOrDefault(spell, 0L);
    }

    private static Vec3d rotateVectorToMatchDirection(Vec3d vec, Vec3d direction) {
        // Get yaw and pitch from the direction vector
        float yaw = (float) Math.atan2(-direction.x, direction.z);
        float pitch = (float) Math.asin(-direction.y);

        // Rotate around X axis (pitch)
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double y1 = vec.y * cosPitch - vec.z * sinPitch;
        double z1 = vec.y * sinPitch + vec.z * cosPitch;

        // Rotate around Y axis (yaw)
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double x2 = vec.x * cosYaw - z1 * sinYaw;
        double z2 = vec.x * sinYaw + z1 * cosYaw;

        return new Vec3d(x2, y1, z2);
    }

}