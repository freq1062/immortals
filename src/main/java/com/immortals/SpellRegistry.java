package com.immortals;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;

/**
 * Defines all available spells, their cooldowns, activation logic,
 * and static methods to bind spells to hotbar slots and manage cooldowns.
 */
public enum SpellRegistry {

    DASH("dash", 5_000) {
        @Override
        public void activate(ServerPlayerEntity player) {
            System.out.println("DASH activated!");
            Vec3d dir = player.getRotationVec(1.0F);
            Vec3d startPos = player.getPos();
            player.addVelocity(dir.x * 2.5, dir.y * 1.2, dir.z * 2.5);
            player.velocityModified = true;

            ServerWorld world = (ServerWorld) player.getWorld();
            int rings = 3;
            int particlesPerRing = 20;

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

    GLOW("glow", 7_000) {
        @Override
        public void activate(ServerPlayerEntity player) {
            ServerWorld world = (ServerWorld) player.getWorld();

            // 1) Apply glowing effect to nearby players
            for (ServerPlayerEntity other : world.getPlayers()) {
                if (!other.equals(player) && other.squaredDistanceTo(player) <= 30 * 30) {
                    other.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, false, false));
                }
            }

            // 2) Spawn lattice of gold dust at the edges
            // center at mid‐body height
            Vec3d center = player.getPos().add(0, player.getStandingEyeHeight() * 0.5, 0);

            // yellow dust: RGB (1.0f, 0.84f, 0f), size 1
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

            player.sendMessage(Text.literal("§eYou glow, revealing nearby players!"), true);
        }
    },

    DRAGON_ASCENT("dragon_ascent", 10_000) {
        @Override
        public void activate(ServerPlayerEntity player) {
            // must have dragon egg
            if (!player.getInventory().contains(new ItemStack(Items.DRAGON_EGG))) {
                player.sendMessage(Text.literal("§cYou need a Dragon Egg to cast this spell."), true);
                return;
            }

            Vec3d pos = player.getPos().add(0, 0.1, 0); // slightly up so particles aren’t inside floor
            Spell.addTask(player.getUuid(), () -> {
                for (int i = 0; i < 100; i++) { // 5 seconds
                    Spell.addTask(player.getUuid(), () -> {
                        Utils.drawDragonAscent(pos, player.getWorld());
                    }, i * 50); // schedule every 50ms
                }
            }, 0);

            player.setVelocity(player.getVelocity().x, 1.3, player.getVelocity().z);
            player.velocityModified = true;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 60, 0, false, false));

            ServerWorld world = (ServerWorld) player.getWorld();
            double radius = 10.0; // detection range

            // Find all living entities (players & hostiles) in range…
            List<LivingEntity> targets = world.getEntitiesByClass(
                    LivingEntity.class,
                    player.getBoundingBox().expand(radius),
                    e -> (e instanceof ServerPlayerEntity) || (e instanceof HostileEntity));

            for (LivingEntity t : targets) {
                // skip yourself
                if (t == player)
                    continue;

                // SKIP any teammate (vanilla scoreboard teams)…
                if (t.isTeammate(player))
                    continue;

                Vec3d tpos = t.getPos().add(0, t.getStandingEyeHeight() * 0.5, 0);
                DamageSource source = world.getDamageSources().create(DamageTypes.MAGIC); // magic damage source
                // immediate lightning
                Spell.addTask(player.getUuid(), () -> {
                    t.damage(world, source, 15.0f);
                    Utils.strikeLightning(world, tpos);
                }, 1000); // 1 second delay

                Spell.addTask(player.getUuid(), () -> {
                    t.damage(world, source, 15.0f);
                    Utils.strikeLightning(world, tpos);
                }, 2000); // 2 seconds delay
            }

            player.sendMessage(Text.literal("§dThe dragon rune smites your enemies!"), true);
        }
    };

    private final String id;
    private final long cooldownMs;

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

    /** player UUID → (hotbar slot → Spell) */
    private static final Map<UUID, Map<Integer, SpellRegistry>> BINDINGS = new ConcurrentHashMap<>();

    /** player UUID → (Spell → last use timestamp ms) */
    private static final Map<UUID, Map<SpellRegistry, Long>> LAST_USED = new ConcurrentHashMap<>();

    /**
     * Bind a spell to a 0-8 hotbar slot, replacing any existing binding for the
     * spell
     */
    public static void bind(ServerPlayerEntity player, int slot, SpellRegistry spell) {
        var bindings = BINDINGS.computeIfAbsent(player.getUuid(), u -> new HashMap<>());

        // Remove the existing binding for the spell, if any
        bindings.entrySet().removeIf(entry -> entry.getValue() == spell);

        // Add the new binding
        bindings.put(slot, spell);
    }

    /** Get the Spell bound to this slot, or null */
    public static SpellRegistry getBound(ServerPlayerEntity player, int slot) {
        var map = BINDINGS.get(player.getUuid());
        return map == null ? null : map.get(slot);
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
        // must have ascended
        if (!Utils.getAscended(player)) {
            return false;
        }
        int corr = Utils.getCorruption(player);
        SpellRegistry spell = getBound(player, slot);

        // level requirements
        if (spell == DASH && corr < 2) {
            return false;
        }
        if (spell == GLOW && corr < 3) {
            return false;
        }
        // Dragon Ascent special requirement
        if (spell == DRAGON_ASCENT && !player.getInventory().contains(new ItemStack(Items.DRAGON_EGG))) {
            player.sendMessage(Text.literal("§cYou must carry a Dragon Egg to use this spell."), true);
            return false;
        }

        if (spell == null) {
            player.sendMessage(Text.literal("No spell bound to slot " + (slot + 1)), true);
            return false;
        }

        // check cooldown
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
    public static SpellRegistry fromId(String id) {
        for (SpellRegistry s : values()) {
            if (s.id.equalsIgnoreCase(id))
                return s;
        }
        return null;
    }

    /** For tab‐completion in your command */
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