package com.immortals.Immortal;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.immortals.Main;
import com.immortals.Utils;
import com.immortals.api.ImmortalsData;
import com.immortals.entity.FragmentEntity;
import com.immortals.entity.ImmortalEntity;
import com.immortals.network.NetworkChannels;

import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import com.immortals.ModItems;

/**
 * Defines all available spells, their cooldowns, activation logic,
 * and static methods to bind spells to hotbar slots and manage cooldowns.
 */
public enum SpellRegistry {

    DASH("dash", Main.CONFIG.getInt("dashCooldown"),
            String.format(
                    """
                                    Propels you 10 blocks horizontally
                                    and 2 blocks vertically in the direction you're facing.
                                    Cooldown %d seconds.
                            """,
                    Main.CONFIG.getInt("dashCooldown") / 20)) {
        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            Vec3d dir = user.getRotationVec(1.0F);
            Vec3d startPos = user.getPos();
            // Propel player 10 blocks horizontally and 2 blocks vertically
            user.addVelocity(dir.x * 2.5, dir.y * 1.2, dir.z * 2.5);
            user.velocityModified = true;

            ServerWorld world = (ServerWorld) user.getWorld();
            int rings = 3;
            int particlesPerRing = 20;

            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_WITHER_SHOOT, net.minecraft.sound.SoundCategory.PLAYERS,
                    0.5F, 1.2F);
            world.playSound(null, user.getX(), user.getY(), user.getZ(), net.minecraft.sound.SoundEvents.UI_TOAST_IN,
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
            recordUse(user, this);
        }
    },

    GLOW("glow", Main.CONFIG.getInt("glowCooldown"),
            String.format(
                    """
                                    All players within %.2f blocks of you are revealed with
                                    the glowing effect for %d seconds, indicated by a lattice
                                    of gold dust. Cooldown %d seconds.
                            """,
                    Main.CONFIG.getDouble("glowRadius"),
                    Main.CONFIG.getInt("glowDuration") / 20,
                    Main.CONFIG.getInt("glowCooldown") / 20)) {
        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            ServerWorld world = (ServerWorld) user.getWorld();

            // Apply glowing effect to nearby players
            for (ServerPlayerEntity other : world.getPlayers()) {
                if (!other.equals(user) && other.squaredDistanceTo(user) <= 30 * 30) {
                    other.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,
                            Main.CONFIG.getInt("glowDuration"), 0, false, false));
                }
            }

            // Spawn lattice of gold dust outlining a sphere centered at mid‐body height
            Vec3d center = user.getPos().add(0, user.getStandingEyeHeight() * 0.5, 0);
            DustParticleEffect goldDust = new DustParticleEffect(0xFFD700, 3f);

            double maxRadius = Main.CONFIG.getDouble("glowRadius");
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
                Main.scheduler.schedule(() -> {
                    world.spawnParticles(goldDust, particlePos.x, particlePos.y, particlePos.z, 1, 0, 0, 0, 0.01);
                }, 1);
            }

            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_BELL_RESONATE, net.minecraft.sound.SoundCategory.PLAYERS,
                    1.0F, 0.8F);
            user.sendMessage(Text.literal("§eYou glow, revealing nearby players!"), true);
            recordUse(user, this);
        }
    },

    FROSTBITE("frostbite", Main.CONFIG.getInt("frostbiteCooldown"),
            String.format(
                    """
                                    When activated, the next player you hit is
                                    given slowness 4 for %d seconds the freezing effect.
                                    %d s cooldown.
                            """,
                    Main.CONFIG.getInt("frostbiteDuration") / 20,
                    Main.CONFIG.getInt("frostbiteCooldown") / 20)) {
        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            ServerWorld world = (ServerWorld) user.getWorld();
            Vec3d center = target.getPos();
            int duration = Main.CONFIG.getInt("frostbiteDuration");

            // Apply slowness 4 to target
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 4, false, false));
            target.setFrozenTicks(duration);

            // Spawn ice particles in a sphere around the target
            int particles = 200;
            for (int i = 0; i < particles; i++) {
                double theta = Math.acos(2 * Math.random() - 1); // polar angle
                double phi = 2 * Math.PI * Math.random(); // azimuthal angle
                double r = 2 * Math.cbrt(Math.random()); // cubic root for uniform distribution

                double x = r * Math.sin(theta) * Math.cos(phi);
                double y = r * Math.sin(theta) * Math.sin(phi);
                double z = r * Math.cos(theta);

                Vec3d particlePos = center.add(x, y + target.getStandingEyeHeight() * 0.5, z);
                world.spawnParticles(ParticleTypes.ITEM_SNOWBALL, particlePos.x, particlePos.y, particlePos.z, 1, 0, 0,
                        0, 0.01);
            }

            world.playSound(null, target.getX(), target.getY(), target.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_GLASS_BREAK, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F,
                    0.9F);
            user.sendMessage(Text.literal("§bA freezing aura emanates from you!"), true);
        }
    },

    BLACKOUT("blackout", Main.CONFIG.getInt("blackoutCooldown"),
            String.format(
                    """
                                    When activated, all untrusted players within %.2f blocks
                                    are inflicted with darkness for %d seconds and
                                    have their abilities disabled for %d seconds.
                                    Mortals are unaffected. %d s cooldown.
                            """,
                    Main.CONFIG.getDouble("blackoutRadius"),
                    Main.CONFIG.getInt("blackoutBlind") / 20,
                    Main.CONFIG.getInt("blackoutDuration") / 20,
                    Main.CONFIG.getInt("blackoutCooldown") / 20)) {
        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            ServerWorld world = (ServerWorld) user.getWorld();
            double radius = Main.CONFIG.getDouble("blackoutRadius");

            // Apply blindness to untrusted players within radius
            for (ServerPlayerEntity other : world.getPlayers()) {
                if (other.squaredDistanceTo(user) <= radius * radius) {
                    if (other == user || other.isTeammate(user) || !((ImmortalsData) other).isImmortal()
                            || Utils.getPlayerData(user).getTrusted().contains(other.getUuid())) {
                        return;
                    } else {
                        // Add blindness effect and disable abilities
                        other.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,
                                Main.CONFIG.getInt("blackoutBlind"), 0, false, true));

                        ((ImmortalsData) other).setAbilitiesDisabled(true);
                        Main.scheduler.schedule(() -> {
                            ((ImmortalsData) other).setAbilitiesDisabled(false);
                        }, Main.CONFIG.getInt("blackoutDuration"));
                    }
                }
            }

            Main.scheduler.schedule(() -> {
                recordUse(user, this);
            }, Main.CONFIG.getInt("blackoutDuration"));

            // Play a sound and spawn particles for feedback
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_WARDEN_HEARTBEAT, net.minecraft.sound.SoundCategory.PLAYERS,
                    1.2F, 0.7f);

            for (double r = 0; r <= radius; r += 0.5) {
                int points = Math.max(8, (int) (r * 8));
                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    double x = user.getX() + Math.cos(angle) * r;
                    double z = user.getZ() + Math.sin(angle) * r;
                    double y = user.getY() + 1.0;
                    DustParticleEffect blackDust = new DustParticleEffect(0x000000, 1f);
                    world.spawnParticles(blackDust, x, y, z, 2, 0.0, 0.0, 0.0, 0.05);
                }
            }

            user.sendMessage(Text.literal("§8Blackout! Nearby abilities are disabled"), true);
        }
    },

    PERSIST("persist", Main.CONFIG.getInt("persistCooldown"),
            String.format(
                    """
                                    Automatic activation when below 3 hearts, but
                                    persist can also be manually activated. Grants Resistance II
                                    for %d s and 6 Absorption hearts. %d s cooldown.
                            """,
                    Main.CONFIG.getInt("persistResistance") / 20,
                    Main.CONFIG.getInt("persistCooldown") / 20)) {
        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,
                    Main.CONFIG.getInt("persistResistance"), 1, false, true));
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20 * 5 * 60, 2, false, true));

            // Draw particles
            ServerWorld world = (ServerWorld) user.getWorld();
            Vec3d center = user.getPos();
            double playerHeight = user.getHeight();
            double playerRadius = 0.7; // slightly
                                       // larger
                                       // than
                                       // player
            int steps = 10;
            int particlesPerCircle = 32;
            DustParticleEffect effect = new DustParticleEffect(0x00FF00, 1f); // lime
                                                                              // green

            for (int i = 0; i < steps; i++) {
                double y = center.y - 1 + playerHeight - (i * playerHeight / (steps - 1));
                int delay = i * 20 / steps; // spread
                                            // over
                                            // 1
                                            // second
                                            // (20
                                            // ticks
                                            // =
                                            // 1
                                            // second)
                Main.scheduler.schedule(() -> {
                    for (int j = 0; j < particlesPerCircle; j++) {
                        double angle = 2 * Math.PI * j / particlesPerCircle;
                        double x = center.x + Math.cos(angle) * playerRadius;
                        double z = center.z + Math.sin(angle) * playerRadius;
                        world.spawnParticles(effect, x, y, z, 1, 0, 0, 0, 0.01);
                    }
                }, delay);
            }

            Main.scheduler.schedule(() -> {
                recordUse(user, this);
            }, 20);

            user.sendMessage(Text.literal("§aYour will strengthens... (+Resistance II)"), true);
        }
    },

    SPLINTER_BLOW("splinter_blow", 0,
            String.format(
                    """
                                    After a %d hit combo on the same target,
                                    land an extra large attack on top of your normal one dealing
                                    %.2f%% their max health as true damage. The counter resets for
                                    a given target if you take damage from them or go 3 seconds
                                    without hitting them. Does not count blocked hits.
                            """,
                    Main.CONFIG.getInt("splinterBlowCombo"),
                    Main.CONFIG.getDouble("splinterBlowDmg") * 100)) {
        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            int required_combo = Main.CONFIG.getInt("splinterBlowCombo");
            if (((ImmortalsData) user).getComboCount(target.getUuid()) >= required_combo) {
                float damage = (float) (target.getMaxHealth() * (Main.CONFIG.getDouble("splinterBlowDmg")));
                ServerWorld world = (ServerWorld) user.getWorld();

                target.damage(world, Utils.of(world, Utils.SPELL_DAMAGE_TYPE, (Entity) user), damage);

                // Spawn an X of critical particles in front of the user
                double yaw = Math.toRadians(user.getYaw());
                double pitch = Math.toRadians(user.getPitch());
                double x = user.getX() - Math.sin(yaw) * Math.cos(pitch) * 1.5;
                double y = user.getY() + user.getStandingEyeHeight() - Math.sin(pitch) * 1.0;
                double z = user.getZ() + Math.cos(yaw) * Math.cos(pitch) * 1.5;

                world.playSound(null, user.getX(), user.getY(), user.getZ(),
                        net.minecraft.sound.SoundEvents.ITEM_WOLF_ARMOR_CRACK,
                        net.minecraft.sound.SoundCategory.PLAYERS, 0.7f, 1.0f);

                // X shape: two crossing lines
                for (int i = -5; i <= 5; i++) {
                    double t = i * 0.1;
                    // First line
                    world.spawnParticles(ParticleTypes.CRIT, x + t, y + t, z + t, 1, 0, 0, 0, 0);
                    // Second line
                    world.spawnParticles(ParticleTypes.CRIT, x + t, y - t, z + t, 1, 0, 0, 0, 0);
                    double offset = 0.5; // distance to push the X
                                         // shape forward
                    double forwardX = x - Math.sin(yaw) * Math.cos(pitch) * offset;
                    double forwardY = y - Math.sin(pitch) * offset;
                    double forwardZ = z + Math.cos(yaw) * Math.cos(pitch) * offset;

                    world.spawnParticles(ParticleTypes.ENCHANTED_HIT, forwardX + t, forwardY + t, forwardZ + t, 1, 0, 0,
                            0, 0);
                    // Second line
                    world.spawnParticles(ParticleTypes.ENCHANTED_HIT, forwardX + t, forwardY - t, forwardZ + t, 1, 0, 0,
                            0, 0);
                }
                ((ImmortalsData) user).setComboCount(target.getUuid(), 0);
            }
        }
    },

    GAMBLE("gamble", Main.CONFIG.getInt("gambleCooldown"),
            String.format(
                    """
                                    When activated, your max health
                                    is temporarily set to a random value
                                    between %.2f and %.2f hearts for
                                    %d seconds. Cooldown %d seconds.
                            """,
                    Main.CONFIG.getDouble("gambleMin"),
                    Main.CONFIG.getDouble("gambleMax"),
                    Main.CONFIG.getInt("gambleDuration") / 20,
                    Main.CONFIG.getInt("gambleCooldown") / 20)) {
        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            float oldHealth = user.getMaxHealth();
            float minHealth = (float) Main.CONFIG.getDouble("gambleMin");
            float maxHealth = (float) Main.CONFIG.getDouble("gambleMax");
            float newHealth = minHealth + (float) (Math.random() * (maxHealth - minHealth));

            // Apply the new max health
            user.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(newHealth);
            user.setHealth(newHealth);

            if (newHealth < oldHealth) {
                user.sendMessage(
                        Text.literal(String.format("§cYou lost the gamble! Max health set to %.1f hearts!", newHealth)),
                        true);
            } else {
                user.sendMessage(
                        Text.literal(String.format("§aYou won the gamble! Max health set to %.1f hearts!", newHealth)),
                        true);
            }
            user.playSound(net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.0F);

            gambleParticles(user, newHealth >= oldHealth);
            Main.scheduler.schedule(() -> {
                recordUse(user, this);
                gambleParticles(user, newHealth >= oldHealth);
                // After duration, reset max health to normal
                user.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(oldHealth);
            }, Main.CONFIG.getInt("gambleDuration"));
        }

        private void gambleParticles(ServerPlayerEntity user, boolean wonGamble) {
            // Spawn particles around the player
            ServerWorld world = (ServerWorld) user.getWorld();
            Vec3d center = user.getPos().add(0, user.getStandingEyeHeight() * 0.5, 0);
            int particles = 100;
            ParticleEffect particleType = wonGamble ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ANGRY_VILLAGER;

            for (int i = 0; i < particles; i++) {
                double theta = Math.acos(2 * Math.random() - 1); // polar angle
                double phi = 2 * Math.PI * Math.random(); // azimuthal angle
                double r = 1.5 * Math.cbrt(Math.random()); // cubic root for uniform distribution

                double x = r * Math.sin(theta) * Math.cos(phi);
                double y = r * Math.sin(theta) * Math.sin(phi);
                double z = r * Math.cos(theta);

                Vec3d particlePos = center.add(x, y, z);
                world.spawnParticles(particleType, particlePos.x, particlePos.y, particlePos.z, 1, 0, 0, 0, 0.01);
            }
        }
    },

    WAVE("wave", Main.CONFIG.getInt("waveCooldown"),
            String.format(
                    """
                                    When activated, a tidal wave covers
                                    %d blocks around you, removing all
                                    cobwebs continuously for %d seconds.
                                    Cooldown %d seconds.
                            """,
                    Main.CONFIG.getInt("waveRadius"),
                    Main.CONFIG.getInt("waveDuration") / 20,
                    Main.CONFIG.getInt("waveCooldown") / 20)) {
        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            ServerWorld world = (ServerWorld) user.getWorld();
            Vec3d center = user.getPos();
            int maxRadius = Main.CONFIG.getInt("waveRadius");
            int duration = Main.CONFIG.getInt("waveDuration");

            for (int i = 0; i <= maxRadius; i++) {
                System.out.println("Scheduling wave ring at radius " + i);
                final int radius = i; // Increment
                                      // radius
                                      // by 1
                                      // block
                                      // each
                                      // step
                final int delay = i;

                Main.scheduler.schedule(() -> {
                    // Calculate positions for the ring of radius i
                    for (int angle = 0; angle < 360; angle += 10) { // 10-degree increments
                        double radians = Math.toRadians(angle);
                        double x = center.x + radius * Math.cos(radians);
                        double z = center.z + radius * Math.sin(radians);
                        double y = center.y + 0.1; // Slightly above ground level

                        // Spawn blue dust particles to imitate water
                        DustParticleEffect waterEffect = new DustParticleEffect(0x0000FF, 2.0f); // Blue color, large
                                                                                                 // size
                        world.spawnParticles(waterEffect, x, y, z, 5, 0, 0, 0, 0.01);
                    }
                    // Remove cobwebs within the radius
                    BlockPos.stream(BlockPos.ofFloored(center.subtract(radius, radius, radius)),
                            BlockPos.ofFloored(center.add(radius, radius, radius)))
                            .filter(pos -> world.getBlockState(pos).isOf(net.minecraft.block.Blocks.COBWEB))
                            .forEach(pos -> world.breakBlock(pos, false));
                }, delay);
            }

            int remainingTicks = duration - (maxRadius);
            Main.scheduler.schedule(() -> {
                System.out.println("Starting continuous cobweb removal");
                for (int i = 0; i < remainingTicks / 5; i++) {
                    final int step = i;
                    Main.scheduler.schedule(() -> {
                        // Continuous water particles evenly distributed within the radius
                        for (int j = 0; j < 50; j++) { // Spawn 50 particles
                            double randomRadius = Math.random() * maxRadius;
                            double randomAngle = Math.random() * 2 * Math.PI;
                            double x = center.x + Math.cos(randomAngle) * randomRadius;
                            double z = center.z + Math.sin(randomAngle) * randomRadius;
                            double y = center.y + 0.1;
                            world.spawnParticles(ParticleTypes.SPLASH, x, y, z, 1, 0, 0, 0, 0.01);
                        }
                        BlockPos.stream(BlockPos.ofFloored(center.subtract(maxRadius, maxRadius, maxRadius)),
                                BlockPos.ofFloored(center.add(maxRadius, maxRadius, maxRadius)))
                                .filter(pos -> world.getBlockState(pos).isOf(net.minecraft.block.Blocks.COBWEB))
                                .forEach(pos -> world.breakBlock(pos, false));
                    }, step * 5); // every 5 ticks
                }
            }, maxRadius);

            Main.scheduler.schedule(() -> {
                recordUse(user, this);
            }, duration);

            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_DOLPHIN_SPLASH, net.minecraft.sound.SoundCategory.PLAYERS,
                    1.0F, 1.0F);
            user.sendMessage(Text.literal("§bA tidal wave washes away the cobwebs!"), true);
        }
    },

    // hardcoded for now because im out of time
    LINK("link", 10 * 20,
            String.format(
                    """
                                    When activated, you have 10 seconds to look at the players
                                    you want to link with for 3 seconds each. After linking,
                                    For the next 40 seconds, Mortals gain a
                                    +1 attack damage boost and Immortals gain
                                    regeneration I, as long as both players
                                    are within 10 blocks of each other. Cooldown 10 seconds.
                                    (Temporary for debugging)
                            """)) {
        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            ServerWorld world = (ServerWorld) user.getWorld();
            user.sendMessage(Text.literal("§dLook at players to link with them!"), true);

            // Initialize the user in the union-find structure
            find(user);

            // Track individual look times for each player
            Map<ServerPlayerEntity, Long> lookStartTimes = new HashMap<>();

            // Check for players being looked at every tick for 10 seconds
            for (int i = 0; i < 10 * 20; i++) {
                final int tick = i;
                Main.scheduler.schedule(() -> {
                    ServerPlayerEntity nearestPlayer = null;
                    double nearestDistance = Double.MAX_VALUE;

                    for (ServerPlayerEntity other : world.getPlayers()) {
                        if (other == user || find(other).equals(find(user))) {
                            continue;
                        }

                        // Check if the player is within 15 blocks and no blocks in between
                        if (Utils.canSee(user, other)) {
                            double distance = user.squaredDistanceTo(other);
                            if (distance < nearestDistance) {
                                nearestDistance = distance;
                                nearestPlayer = other;
                            }
                        }
                    }

                    if (nearestPlayer != null) {
                        // Start tracking look time for the nearest player
                        lookStartTimes.putIfAbsent(nearestPlayer, System.currentTimeMillis());
                        long lookTime = System.currentTimeMillis() - lookStartTimes.get(nearestPlayer);

                        user.sendMessage(Text.literal(
                                "§dLooking at " + nearestPlayer.getName().getString() + " (" + (lookTime / 1000.0)
                                        + "s)"),
                                true);
                        world.spawnParticles(new DustParticleEffect(0xFF0000, 1.0f), nearestPlayer.getX(),
                                nearestPlayer.getY() + nearestPlayer.getHeight() + 0.5, nearestPlayer.getZ(), 1, 0, 0,
                                0, 0.01);

                        if (lookTime >= 3000) { // 3 seconds of looking
                            if (union(user, nearestPlayer)) {
                                nearestPlayer.sendMessage(
                                        Text.literal("§dYou have been linked with " + user.getName().getString() + "!"),
                                        true);
                                user.sendMessage(Text.literal(
                                        "§dLinked with " + nearestPlayer.getName().getString() + "!"), true);
                            }
                            // Remove the player from the tracking map after linking
                            lookStartTimes.remove(nearestPlayer);
                        }
                    } else {
                        // Reset look time for players no longer being looked at
                        lookStartTimes.keySet().removeIf(player -> {
                            if (!Utils.canSee(user, player)) {
                                user.sendMessage(Text.literal(
                                        "§cStopped looking at " + player.getName().getString() + "."), true);
                                return true;
                            }
                            return false;
                        });
                    }
                }, tick);
            }

            // After 10 seconds, start the 40-second benefit period
            Main.scheduler.schedule(() -> {
                Set<ServerPlayerEntity> linkedPlayers = new HashSet<>();
                for (ServerPlayerEntity player : Immortals.parent.keySet()) {
                    if (find(player).equals(find(user))) {
                        linkedPlayers.add(player);
                    }
                }

                if (linkedPlayers.size() <= 1) {
                    user.sendMessage(Text.literal("§cNo players were linked."), true);
                    return;
                }

                user.sendMessage(Text.literal("§aLinked players will now receive benefits!"), true);
                for (ServerPlayerEntity linked : linkedPlayers) {
                    linked.sendMessage(Text.literal("§aYou are receiving benefits from the link!"), true);
                }

                // Apply benefits for 40 seconds
                for (int i = 0; i < 40 * 20; i++) {
                    if (i % 20 == 0) {
                        Main.scheduler.schedule(() -> {
                            for (ServerPlayerEntity linked : new HashSet<>(linkedPlayers)) {
                                if (linked.squaredDistanceTo(user) <= 15 * 15) { // Within 15 blocks
                                    // Draw a straight line of fire particles from the linked player to the user
                                    Vec3d start = linked.getPos().add(0, linked.getHeight() / 2, 0);
                                    Vec3d end = user.getPos().add(0, user.getHeight() / 2, 0);
                                    Vec3d direction = end.subtract(start).normalize();
                                    double distance = start.distanceTo(end);
                                    for (double d = 0; d <= distance; d += 0.5) {
                                        Vec3d particlePos = start.add(direction.multiply(d));
                                        world.spawnParticles(ParticleTypes.FLAME, particlePos.x, particlePos.y,
                                                particlePos.z, 1, 0, 0, 0, 0.01);
                                    }
                                    // Dish out effects
                                    if (((ImmortalsData) linked).isImmortal()) {
                                        linked.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40,
                                                0, false, false));
                                    } else {
                                        if (linked.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE)
                                                .getBaseValue() == linked
                                                        .getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE)) {
                                            System.out.println("Applying attack damage boost from link to "
                                                    + linked.getName().getString());
                                            linked.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE)
                                                    .setBaseValue(2.0);
                                        }
                                    }
                                } else {
                                    // Remove from the set and reset attributes
                                    linkedPlayers.remove(linked);
                                    Immortals.parent.remove(linked);
                                    Immortals.size.remove(linked);
                                    linked.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE)
                                            .setBaseValue(linked
                                                    .getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE));
                                    linked.sendMessage(
                                            Text.literal(
                                                    "§cYou are too far from the link and have been removed."),
                                            true);
                                    user.sendMessage(
                                            Text.literal("§c" + linked.getName().getString()
                                                    + " is too far and has been removed from the link."),
                                            true);
                                }
                            }
                        }, i);
                    }
                }

                // After 40 seconds, remove benefits
                Main.scheduler.schedule(() -> {
                    for (ServerPlayerEntity linked : linkedPlayers) {
                        if (!((ImmortalsData) linked).isImmortal()) {
                            linked.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE)
                                    .setBaseValue(linked.getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE) - 1);
                        }
                        linked.sendMessage(Text.literal("§cThe link benefits have ended."), true);
                    }
                    recordUse(user, this);
                    user.sendMessage(Text.literal("§cThe link benefits have ended."), true);
                }, 40 * 20);
            }, 10 * 20);
        }

        // Disjoint set (union-find) for trusted players
        public static ServerPlayerEntity find(ServerPlayerEntity player) {
            if (!Immortals.parent.containsKey(player)) {
                Immortals.parent.put(player, player);
                Immortals.size.put(player, 1);
            }
            if (!Immortals.parent.get(player).equals(player)) {
                Immortals.parent.put(player, find(Immortals.parent.get(player))); // Path compression
            }
            return Immortals.parent.get(player);
        }

        public static boolean union(ServerPlayerEntity player1, ServerPlayerEntity player2) {
            ServerPlayerEntity root1 = find(player1);
            ServerPlayerEntity root2 = find(player2);

            if (root1.equals(root2)) {
                return false; // Already in the same pool
            }

            int size1 = Immortals.size.get(root1);
            int size2 = Immortals.size.get(root2);

            if (size1 + size2 > 3) {
                return false; // Pool size limit exceeded
            }

            // Union by size
            if (size1 >= size2) {
                Immortals.parent.put(root2, root1);
                Immortals.size.put(root1, size1 + size2);
            } else {
                Immortals.parent.put(root1, root2);
                Immortals.size.put(root2, size1 + size2);
            }

            return true;
        }
    },

    LOCK("lock", 10 * 20, String.format("""
                    When activated, the opponent's inventory is searched
                    for the item in the slot corresponding with this spell
                    in your hotbar. If it is found, it is locked for
                    10 seconds for both players, preventing it from
                    being moved or used.
            """)) {

        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            int hotbarSlot = user.getInventory().getSelectedSlot();
            ItemStack userItem = user.getInventory().getStack(hotbarSlot);

            if (userItem.isEmpty()) {
                user.sendMessage(Text.literal("§cYour hotbar slot is empty!"), true);
                return;
            }
            ItemStack targetItem = null;

            for (int i = 0; i < 9; i++) { // Loop through the target's hotbar slots
                ItemStack currentItem = target.getInventory().getStack(i);
                if (!currentItem.isEmpty() && userItem.isOf(currentItem.getItem())) {
                    targetItem = currentItem;
                    break;
                }
            }

            if (targetItem == null) {
                user.sendMessage(Text.literal("§cNo matching item found in the target's hotbar!"), true);
                return;
            }

            // Make the lock
            target.getItemCooldownManager().set(targetItem, 10 * 20);
            user.getItemCooldownManager().set(userItem, 10 * 20);

            user.sendMessage(Text.literal("§dLocked item: " + targetItem.getName().getString()), true);
            target.sendMessage(Text.literal("§cYour item has been locked: " + targetItem.getName().getString()), true);

            // Lock the item for 10 seconds
            Main.scheduler.schedule(() -> {
                recordUse(user, this);
                user.sendMessage(Text.literal("§aYour item is no longer locked."), true);
                target.sendMessage(Text.literal("§aYour item is no longer locked."), true);
            }, 10 * 20);
        }

    },

    FRAGMENT("fragment", Main.CONFIG.getInt("fragmentCooldown"), String.format("""
                    When activated, 3 fragments are summoned
                    in the direction you face, each dealing
                    %.2f%% of the target's max health
                    on hit. Cooldown %d seconds.
            """, Main.CONFIG.getDouble("fragmentDmg") * 100, Main.CONFIG.getInt("fragmentCooldown") / 20)) {

        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            Immortals.fragmentCount.put(user.getUuid(), 0);
            user.sendMessage(Text.literal("§dPunch to launch fragments!"), true);
        }
    },

    BEAM("beam", Main.CONFIG.getInt("beamCooldown"), String.format("""
                    Fires a beam of dark energy in the direction you're facing,
                    dealing %.2f%% of max health as damage to anything in its path
                    and knocking them back. Cooldown %d seconds.
            """, Main.CONFIG.getDouble("beamDmg") * 100, Main.CONFIG.getInt("beamCooldown") / 20)) {

        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            user.sendMessage(Text.literal("§5Charging beam..."), true);
            Vec3d initialPos = user.getPos();
            long chargeStartTime = System.currentTimeMillis();
            AtomicBoolean fired = new AtomicBoolean(false); // Use AtomicBoolean to track firing state

            for (var i = 0; i < 20 * 5; i++) { // Check for up to 5 seconds
                final int step = i;
                Main.scheduler.schedule(() -> {
                    if (fired.get())
                        return; // Return early if the beam has already been fired

                    long chargeDuration = System.currentTimeMillis() - chargeStartTime;
                    double chargePercentage = Math.min(chargeDuration / 5000.0, 1.0); // Max charge at 5 seconds
                    user.sendMessage(
                            Text.literal(String.format("§5Beam charge: %d%%", (int) (chargePercentage * 100))), true);

                    Vec3d currentPos = user.getPos();
                    ServerWorld world = (ServerWorld) user.getWorld();

                    // Spawn lightning charging particles
                    int particleCount = (int) (chargePercentage * 50); // More particles as charge increases
                    for (int j = 0; j < particleCount; j++) {
                        double angle = 2 * Math.PI * j / particleCount;
                        double radius = 0.5 + chargePercentage * 1.5; // Radius increases with charge
                        double x = user.getX() + Math.cos(angle) * radius;
                        double z = user.getZ() + Math.sin(angle) * radius;
                        double y = user.getY() + 1.0 + Math.sin(angle * 2) * 0.2; // Add some vertical variation

                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0, 0, 0, 0.01);
                    }

                    if (!currentPos.equals(initialPos)) {
                        float adjustedBeamDmg = (float) (Main.CONFIG.getDouble("beamDmg") * chargePercentage);
                        user.sendMessage(
                                Text.literal("§5Beam unleashed at " + (int) (chargePercentage * 100) + "% power!"),
                                true);
                        fireBeam(user, adjustedBeamDmg);
                        recordUse(user, this);
                        fired.set(true); // Mark as fired
                        return;
                    }

                    if (chargePercentage >= 1.0) {
                        user.sendMessage(Text.literal("§5Beam unleashed at full power!"), true);
                        fireBeam(user, (float) Main.CONFIG.getDouble("beamDmg"));
                        recordUse(user, this);
                        fired.set(true); // Mark as fired
                    }
                }, 2 * step); // Check every 2 ticks
            }

        }

        private void fireBeam(ServerPlayerEntity user, float beamDmg) {
            ServerWorld world = (ServerWorld) user.getWorld();
            Vec3d eyePos = user.getEyePos();
            Vec3d look = user.getRotationVec(1.0F).normalize();
            double maxDistance = 32.0; // Beam length
            float beamPct = beamDmg; // already a fraction (e.g. 0.2 for 20%)
            int beamTicks = 20; // 1 second
            for (int tick = 0; tick < beamTicks; tick++) {
                final int t = tick;
                Main.scheduler.schedule(() -> {
                    double spiralRadius = 0.4;
                    double spiralTurns = 2.5; // how many full turns over the beam
                    for (double d = 0; d < maxDistance; d += 0.25) {
                        Vec3d pos = eyePos.add(look.multiply(d));

                        // Spiral offset
                        double spiralAngle = 2 * Math.PI * spiralTurns * (d / maxDistance) + t * 0.25;
                        double offsetX = Math.cos(spiralAngle) * spiralRadius;
                        double offsetY = Math.sin(spiralAngle) * spiralRadius;

                        // Find a vector perpendicular to the beam direction for spiral
                        Vec3d up = new Vec3d(0, 1, 0);
                        Vec3d perp1 = look.crossProduct(up).normalize();
                        if (perp1.lengthSquared() < 0.01) {
                            // If look is vertical, use X axis
                            perp1 = new Vec3d(1, 0, 0);
                        }
                        Vec3d perp2 = look.crossProduct(perp1).normalize();

                        Vec3d spiralOffset = perp1.multiply(offsetX).add(perp2.multiply(offsetY));
                        Vec3d spiralPos = pos.add(spiralOffset);

                        // Main beam: purple dust
                        DustParticleEffect beamParticle = new DustParticleEffect(0x8B0000, 1.2f); // dark red
                        world.spawnParticles(beamParticle, spiralPos.x, spiralPos.y, spiralPos.z, 2, 0, 0, 0, 0.01);

                        // Add a white core for extra "laser" effect
                        DustParticleEffect coreParticle = new DustParticleEffect(0xFFFFFF, 0.7f);
                        world.spawnParticles(coreParticle, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0.01);

                        // Occasional sparkles on the spiral
                        if (t % 4 == 0 && Math.random() < 0.15) {
                            world.spawnParticles(ParticleTypes.END_ROD, spiralPos.x, spiralPos.y, spiralPos.z, 1, 0, 0,
                                    0, 0.01);
                        }
                    }
                }, tick);
            }

            // Raycast and hit entities
            Set<Entity> hitEntities = new HashSet<>();
            for (double d = 0; d < maxDistance; d += 0.5) {
                Vec3d pos = eyePos.add(look.multiply(d));
                List<Entity> entities = world.getOtherEntities(user,
                        user.getBoundingBox().expand(0.5).offset(pos.subtract(user.getPos())),
                        e -> e instanceof LivingEntity && e != user && !hitEntities.contains(e));
                for (Entity e : entities) {
                    LivingEntity le = (LivingEntity) e;
                    float dmg = le.getMaxHealth() * beamPct;
                    le.damage(world, Utils.of(world, Utils.SPELL_DAMAGE_TYPE, user), dmg);

                    // Knockback: 5 blocks horizontally away from user, plus a little up
                    Vec3d away = le.getPos().subtract(user.getPos()).normalize();
                    Vec3d knock = new Vec3d(away.x, 0.2, away.z).normalize().multiply(1.5);
                    le.addVelocity(knock.x, knock.y, knock.z);

                    hitEntities.add(le);
                    // Only hit each entity once
                }
            }

            // Sound and feedback
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.5F);
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.ITEM_TOTEM_USE,
                    net.minecraft.sound.SoundCategory.PLAYERS, 0.75F, 0.5F);
            recordUse(user, this);
            user.sendMessage(Text.literal("§5You fire a beam of dark energy!"), true);
        }

    },

    DRAGON_ASCENT("dragon_ascent", Main.CONFIG.getInt("dragonAscentCooldown"), String.format("""
                    All hostile entities and untrusted players within a radius of %.2f blocks
                    are struck twice by lightning, dealing a total of %.2f%% of their max health.
                    The spell propels you into the air for %d seconds.
                    Requires a Dragon Egg in your inventory.
                    Cooldown %d seconds.
            """, Main.CONFIG.getDouble("dragonAscentRadius"), Main.CONFIG.getDouble("dragonAscentTotalDmg") * 100,
            Main.CONFIG.getInt("dragonAscentLevitation") / 20, Main.CONFIG.getInt("dragonAscentCooldown") / 20)) {

        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            // Must have dragon egg
            if (Utils.inventoryHas(user, Items.DRAGON_EGG) == null) {
                user.sendMessage(Text.literal("§cYou need the Dragon Egg to cast this spell."), true);
                return;
            }
            Utils.grant(user, "dragon_ascent");

            // Announce to nearby players and draw dragon ascent runes
            NetworkChannels.RuneS2CPayload payload = new NetworkChannels.RuneS2CPayload(
                    "dragon_ascent", user.getX(), user.getY(), user.getZ(), 14.0f, 80);

            Utils.sendPayloadToNearby(user, payload);

            for (int i = 0; i < 100; i++) { // 5 seconds
                Main.scheduler.schedule(() -> {
                    Utils.drawDragonAscent(user.getPos(), user.getWorld());
                }, i); // schedule every
                       // tick
            }

            // Propel player into the air
            user.setVelocity(user.getVelocity().x, 1.3, user.getVelocity().z);
            user.velocityModified = true;
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION,
                    Main.CONFIG.getInt("dragonAscentLevitation"), 0, false, false));

            ServerWorld world = (ServerWorld) user.getWorld();
            double radius = Main.CONFIG.getDouble("dragonAscentRadius"); // detection
                                                                         // range
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_AMBIENT,
                    net.minecraft.sound.SoundCategory.PLAYERS, 0.5F, 1.0F);

            // Target all players and hostile entities within the radius
            List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class,
                    user.getBoundingBox().expand(radius),
                    e -> (e instanceof ServerPlayerEntity) || (e instanceof HostileEntity));

            for (LivingEntity t : targets) {
                // Skip yourself, teammates, trusted
                if (t == user || Utils.getPlayerData(user).getTrusted().contains(t.getUuid()) || t.isTeammate(user))
                    continue;

                // Spawn 2 lightning bolts, ignores armor
                Vec3d tpos = t.getPos().add(0, t.getStandingEyeHeight() * 0.5, 0);

                // Schedule two lightning strikes with 20% max health damage at 1s and 2s
                float maxHealth = t.getMaxHealth();
                float damage = maxHealth * ((Number) Main.CONFIG.getDouble("dragonAscentTotalDmg")).floatValue() / 2.0f;
                int[] delays = { 20, 40 };
                for (int delay : delays) {
                    Main.scheduler.schedule(() -> {
                        t.damage(world, Utils.of(world, Utils.SPELL_DAMAGE_TYPE, (Entity) user), damage);
                        Utils.strikeLightning(world, tpos);
                    }, delay); // 1s,
                               // 2s
                }
            }

            Main.scheduler.schedule(() -> {
                recordUse(user, this);
            }, Main.CONFIG.getInt("dragonAscentLevitation") + 20);
            user.sendMessage(Text.literal("§dThe dragon rune smites your enemies!"), true);
        }
    },

    TIMESLOW("timeslow", Main.CONFIG.getInt("timeSlowCooldown"), String.format("""
                    Within a radius of %.2f blocks, all entities are slowed to 1/4 speed
                    and projectiles to 1/20 speed for a duration of %d seconds.
                    Cooldown %d seconds.
            """, Main.CONFIG.getDouble("timeSlowRadius"), Main.CONFIG.getInt("timeSlowDuration") / 20,
            Main.CONFIG.getInt("timeSlowCooldown") / 20)) {

        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {

            // Must have timekeeper
            if (!user.getInventory().contains(new ItemStack(ModItems.TIMEKEEPER))) {
                user.sendMessage(Text.literal("§cYou need the Timekeeper to cast this spell."), true);
                return;
            }
            Utils.grant(user, "timeslow");
            NetworkChannels.RuneS2CPayload payload = new NetworkChannels.RuneS2CPayload(
                    "timeslow", user.getX(), user.getY(), user.getZ(), 14.0f, Main.CONFIG.getInt("timeSlowDuration"));

            Utils.sendPayloadToNearby(user, payload);

            ServerWorld world = (ServerWorld) user.getWorld();
            Vec3d center = user.getPos();
            double radius = Main.CONFIG.getDouble("timeSlowRadius");
            int duration = Main.CONFIG.getInt("timeSlowDuration");
            int checkInterval = 2;

            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_BEACON_ACTIVATE, net.minecraft.sound.SoundCategory.PLAYERS,
                    1.0F, 0.5F);

            Main.scheduler.schedule(() -> {
                for (int i = 0; i < 200; i++) { // 10 seconds
                    final int step = i;
                    double handAngle = (2 * Math.PI * step) / 200;
                    Main.scheduler.schedule(() -> {
                        Utils.drawTimeslow(center, world, radius, 100, handAngle);
                    }, step);
                }
            }, 0);

            // Repeat ambient sound every 80 ticks (4 seconds) for the spell duration
            int ambientRepeat = 80; // 80 ticks * 50ms per tick = 4000ms
            for (int i = ambientRepeat; i < duration; i += ambientRepeat) {
                Main.scheduler.schedule(() -> {
                    world.playSound(null, user.getX(), user.getY(), user.getZ(),
                            net.minecraft.sound.SoundEvents.BLOCK_BEACON_AMBIENT,
                            net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
                }, i);
            }

            // Track affected entities and their tickrate state
            Set<Entity> slowedEntities = Collections.synchronizedSet(new HashSet<>());

            // Schedule periodic checks for the duration, and restore after the last check
            final Vec3d effectCenter = center; // capture the center at cast time
            int numChecks = duration / checkInterval;
            for (int i = 0; i <= numChecks; i++) {
                int t = i * checkInterval;
                Main.scheduler.schedule(() -> {
                    // Find all living entities (including players) within the radius, except
                    // yourself
                    List<Entity> inZone = world.getOtherEntities(null,
                            user.getBoundingBox().expand(radius).offset(effectCenter.subtract(user.getPos())),
                            e -> e != user && e.squaredDistanceTo(effectCenter) <= radius * radius);

                    // Apply ender pearl cooldown to any players in the zone
                    for (Entity entity : inZone) {
                        if (entity instanceof ServerPlayerEntity affectedPlayer) {
                            // Set a cooldown on ender pearls that lasts until the next check
                            affectedPlayer.getItemCooldownManager().set(new ItemStack(Items.ENDER_PEARL),
                                    checkInterval / 50); // Convert
                                                         // ms to
                                                         // ticks
                        }
                    }

                    // Slow new entities entering the zone
                    for (Entity entity : inZone) {
                        if (slowedEntities.add(entity)) {
                            // If entity is a projectile, set tickrate to 1
                            if (entity.getType().toString().contains("arrow")
                                    || entity.getType().toString().contains("potion")
                                    || entity.getType().toString().contains("snowball")
                                    || entity.getType().toString().contains("wind_charge")
                                    || entity.getType().toString().contains("projectile")) {
                                try {
                                    Main.api.rateEntity(entity, 1);
                                } catch (Exception e) {
                                    Main.LOGGER.error("Failed to set tickrate for projectile: " + e.getMessage());
                                }
                            } else {
                                try {
                                    Main.api.rateEntity(entity, 5);
                                } catch (Exception e) {
                                    // If entity doesn't have tickrate apply slowness and fatigue instead
                                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                                            checkInterval, 4, false, false));
                                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE,
                                            checkInterval, 2, false, false));
                                }
                            }
                        }
                    }

                    // Restore tickrate for entities that left the zone
                    slowedEntities.removeIf(entity -> {
                        if (entity == null || entity.squaredDistanceTo(effectCenter) > radius * radius) {
                            if (entity != null) {
                                try {
                                    Main.api.rateEntity(entity, 20);
                                } catch (Exception e) {
                                    Main.LOGGER.error("Failed to reset tickrate: " + e.getMessage());
                                }
                            }
                            return true;
                        }
                        return false;
                    });
                }, t);
            }

            // Restore tickrate for any remaining entities after the last check
            Main.scheduler.schedule(() -> {
                recordUse(user, this);
                world.playSound(null, user.getX(), user.getY(), user.getZ(),
                        net.minecraft.sound.SoundEvents.BLOCK_BEACON_DEACTIVATE,
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 0.5F);
                for (Entity entity : slowedEntities) {
                    try {
                        Main.api.rateEntity(entity, 20);
                    } catch (Exception e) {
                        Main.LOGGER.error("Failed to reset tickrate: " + e.getMessage());
                    }
                }
                slowedEntities.clear();
            }, (numChecks + 1) * checkInterval);

            user.sendMessage(Text.literal("§7Timeslow in effect!"), true);
        }
    };
    // Spell interface

    private final String id;
    private final long cooldownTicks;
    private final String description;
    private static final Map<UUID, Map<SpellRegistry, Long>> LAST_USED = new ConcurrentHashMap<>();

    SpellRegistry(String id, long cooldownTicks, String description) {
        this.id = id;
        this.cooldownTicks = cooldownTicks;
        this.description = description;
        // Regular, onHit, automatic
    }

    /** Concrete spells implement their effect here */
    public abstract void activate(ServerPlayerEntity player, ServerPlayerEntity target);

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

    public String getDescription() {
        // Replace newlines and collapse multiple spaces into a single space
        return description.replace("\n", " ").replaceAll("\\s+", " ").trim();
    }

    /** The unique identifier players will use in `/bind ...` */
    public String getId() {
        return id;
    }

    public long getCooldownTicks() {
        return cooldownTicks;
    }

    /** Bind a spell to the first available slot */
    public static int bindDefault(ServerPlayerEntity player, int slot, SpellRegistry spell) {
        Map<Integer, String> bindings = ((ImmortalsData) player).getSpellBindings();

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
        Map<Integer, String> bindings = ((ImmortalsData) player).getSpellBindings();
        bindings.entrySet().removeIf(entry -> spell.getId().equals(entry.getValue()));
        bindings.put(slot, spell.getId());
    }

    /** Unbind a spell */
    public static void unbind(ServerPlayerEntity player, SpellRegistry spell) {
        Map<Integer, String> bindings = ((ImmortalsData) player).getSpellBindings();
        bindings.entrySet().removeIf(entry -> spell.getId().equals(entry.getValue()));
    }

    /**
     * Find the first unbound slot from 1-9 and return it, or -1 if all are bound
     */
    public static int findFirstUnboundSlot(ServerPlayerEntity player) {
        Map<Integer, String> bindings = ((ImmortalsData) player).getSpellBindings();
        for (int slot = 0; slot < 9; slot++) {
            if (!bindings.containsKey(slot)) {
                return slot;
            }
        }
        return -1;
    }

    /** Get the Spell bound to this slot, or null */
    public static SpellRegistry getBound(ServerPlayerEntity player, int slot) {
        String id = ((ImmortalsData) player).getSpellBindings().get(slot);
        return SpellRegistry.fromId(id);
    }

    /**
     * Returns the number of spells the player has bound, taking into account
     * special spells
     */
    public static int getNumBound(ServerPlayerEntity player) {
        Map<Integer, String> bindings = ((ImmortalsData) player).getSpellBindings();
        int count = 0;
        for (String id : bindings.values()) {
            if (!"dragon_ascent".equals(id) && !"timeslow".equals(id)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the slot number (0-8) where the spell is bound, or -1 if not bound
     */
    public static int getSlot(ServerPlayerEntity player, SpellRegistry spell) {
        Map<Integer, String> bindings = ((ImmortalsData) player).getSpellBindings();
        for (Map.Entry<Integer, String> entry : bindings.entrySet()) {
            if (spell.getId().equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /** Check if a spell is bound to any slot */
    public static boolean isSpellBound(ServerPlayerEntity player, SpellRegistry spell) {
        Map<Integer, String> bindings = ((ImmortalsData) player).getSpellBindings();
        return bindings.containsValue(spell.getId());
    }

    /** Has the spell’s cooldown expired? */
    public static boolean canUse(ServerPlayerEntity player, SpellRegistry spell) {
        if (Spell.pendingCooldownNotifications.containsKey(player.getUuid())
                && Spell.pendingCooldownNotifications.get(player.getUuid()).getOrDefault(spell, 0) == -1) {
            // Cooldown pending after spell effects end
            return false;
        }
        var map = LAST_USED.get(player.getUuid());
        if (map == null)
            return true;
        Long last = map.get(spell);
        if (last == null)
            return true;
        return (System.currentTimeMillis() - last) >= spell.cooldownTicks * 50;
    }

    /** Record that the player just used this spell */
    public static void recordUse(ServerPlayerEntity player, SpellRegistry spell) {
        LAST_USED
                .computeIfAbsent(player.getUuid(), u -> new HashMap<>())
                .put(spell, System.currentTimeMillis());
        long cd = spell.getCooldownTicks();
        int secs = (int) Math.ceil(cd / 20.0);
        Spell.pendingCooldownNotifications
                .computeIfAbsent(player.getUuid(), u -> new ConcurrentHashMap<>())
                .put(spell, secs);
    }

    // Record that the spell's cooldown will start after effects end
    public static void recordInUse(ServerPlayerEntity player, SpellRegistry spell) {
        Spell.pendingCooldownNotifications
                .computeIfAbsent(player.getUuid(), u -> new ConcurrentHashMap<>())
                .put(spell, -1);
    }

    /**
     * Attempt to activate—returns true on success, false if unbound or on cooldown
     */
    public static boolean tryActivate(ServerPlayerEntity player, ServerPlayerEntity target, int slot) {
        ImmortalsData playerData = (ImmortalsData) player;
        // Must have ascended
        if (!playerData.isImmortal()) {
            return false;
        }

        if (playerData.areAbilitiesDisabled()) {
            player.sendMessage(Text.literal("§cYour abilities are disabled!"), true);
            return false;
        }

        int corr = (playerData.getCorruption());
        SpellRegistry spell = getBound(player, slot);

        if (spell == null) {
            player.sendMessage(Text.literal("No spell bound to slot " + (slot + 1)), true);
            return false;
        }

        // Level requirements
        if (corr < Utils.getRequiredCorr(spell))
            return false;

        // On hit spell types have delayed activation handled in Immortals.java
        if (spell == SpellRegistry.FROSTBITE || spell == SpellRegistry.LOCK) {
            ((ImmortalsData) player).setOnHitSpell(spell.getId());
            player.sendMessage(Text.literal("§a" + spell.getDisplayName() + " will activate on your next hit!"), true);
            return false;
        }

        // Must have a dragon egg to use dragon ascent
        if (spell.id.equals("dragon_ascent") && Utils.inventoryHas(player, Items.DRAGON_EGG) == null) {
            unbind(player, SpellRegistry.DRAGON_ASCENT);
            return false;
        }

        // Must have a timekeeper to use timeslow
        if (spell.id.equals("timeslow") && Utils.inventoryHas(player, ModItems.TIMEKEEPER) == null) {
            unbind(player, SpellRegistry.TIMESLOW);
            return false;
        }

        // Cooldown needs to be up
        if (!canUse(player, spell)) {
            player.sendMessage(Text.literal("§c" + spell.getDisplayName() + " is on cooldown for "
                    + Spell.pendingCooldownNotifications.get(player.getUuid()).get(spell) + "!"), true);
            return false;
        }

        spell.activate(player, target);
        recordInUse(player, spell);
        playerData.setLastSpell(spell.getId());
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