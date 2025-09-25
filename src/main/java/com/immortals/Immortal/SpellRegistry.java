package com.immortals.Immortal;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.immortals.Main;
import com.immortals.Utils;
import com.immortals.api.ImmortalsData;
import com.immortals.network.NetworkChannels;

import net.minecraft.item.Items;
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
                                    Propels you 10 blocks in the direction you're looking.
                                    Does not ignore friction. Cooldown %d seconds.
                            """,
                    Main.CONFIG.getInt("dashCooldown") / 20)) {
        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            Vec3d dir = user.getRotationVec(1.0F).normalize();
            user.setVelocity(dir.x * 1.75, dir.y * 1.5, dir.z * 1.75);
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
                Vec3d ringCenter = user.getPos().subtract(dir.multiply(backStep));

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
                if (other.squaredDistanceTo(user) <= 30 * 30) {
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
                }, 2);
            }

            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_BELL_RESONATE, net.minecraft.sound.SoundCategory.PLAYERS,
                    1.0F, 0.8F);
            user.sendMessage(Text.literal("You glow, revealing nearby players!"), true);
            recordUse(user, this);
        }
    },

    FROSTBITE("frostbite", Main.CONFIG.getInt("frostbiteCooldown"),
            String.format(
                    """
                                    When activated, the next player you hit is
                                    given slowness 4 and the freezing effect
                                    for %d seconds. %d s cooldown.
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
            target.setFrozenTicks(duration * 2);

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
            recordUse(user, this);
            user.sendMessage(Text.literal("Your target was frozen!"), true);
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

            // Apply darkness to untrusted players within radius
            for (ServerPlayerEntity other : world.getPlayers()) {
                if (other.squaredDistanceTo(user) <= radius * radius) {
                    if (other == user || other.isTeammate(user) || !((ImmortalsData) other).isImmortal()
                            || Utils.getPlayerData(user).getTrusted().contains(other.getUuid())) {
                        continue;
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

            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_WARDEN_HEARTBEAT, net.minecraft.sound.SoundCategory.PLAYERS,
                    1.2F, 0.7f);

            NetworkChannels.RuneS2CPayload payload = new NetworkChannels.RuneS2CPayload(
                    this.getId(), user.getX(), user.getY(), user.getZ(),
                    (float) Main.CONFIG.getDouble("blackoutRadius") * 2.0f,
                    60);
            Utils.sendPayloadToNearby(user, payload);

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

            user.sendMessage(Text.literal("Nearby abilities have been disabled!"), true);
        }
    },

    PERSIST("persist", Main.CONFIG.getInt("persistCooldown"),
            String.format(
                    """
                                    Automatic activation when below 3 hearts, but
                                    can also be manually activated. Grants Resistance II
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
            double playerRadius = 0.7;
            int steps = 10;
            int particlesPerCircle = 32;
            DustParticleEffect effect = new DustParticleEffect(0x00FF00, 1f); // lime
                                                                              // green

            for (int i = 0; i < steps; i++) {
                double y = center.y - 1 + playerHeight - (i * playerHeight / (steps - 1));
                int delay = i * 20 / steps;
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

            user.sendMessage(Text.literal("Your will strengthens, enduring the pain..."), true);
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

                for (int i = -5; i <= 5; i++) {
                    double t = i * 0.1;

                    // First line of the X
                    world.spawnParticles(ParticleTypes.CRIT,
                            x + t * Math.cos(yaw),
                            y + t,
                            z + t * Math.sin(yaw),
                            1, 0, 0, 0, 0);

                    // Second line of the X
                    world.spawnParticles(ParticleTypes.CRIT,
                            x - t * Math.cos(yaw),
                            y - t,
                            z - t * Math.sin(yaw),
                            1, 0, 0, 0, 0);
                }

                // Offset the second X slightly forward to make both visible
                double offset = 0.5; // distance to push the second X shape forward
                double forwardX = x - Math.sin(yaw) * Math.cos(pitch) * offset;
                double forwardY = y - Math.sin(pitch) * offset;
                double forwardZ = z + Math.cos(yaw) * Math.cos(pitch) * offset;

                for (int i = -5; i <= 5; i++) {
                    double t = i * 0.1;

                    // First line of the forward X
                    world.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                            forwardX + t * Math.cos(yaw),
                            forwardY + t,
                            forwardZ + t * Math.sin(yaw),
                            1, 0, 0, 0, 0);

                    // Second line of the forward X
                    world.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                            forwardX - t * Math.cos(yaw),
                            forwardY - t,
                            forwardZ - t * Math.sin(yaw),
                            1, 0, 0, 0, 0);
                }

                ((ImmortalsData) user).setComboCount(target.getUuid(), 0);
            }
        }
    },

    WAVE("wave", Main.CONFIG.getInt("waveCooldown"),
            String.format(
                    """
                                    When activated, a tidal wave floods the area up to
                                    %.2f blocks around you, removing all
                                    cobwebs continuously for %d seconds.
                                    Cooldown %d seconds.
                            """,
                    Main.CONFIG.getDouble("waveRadius"),
                    Main.CONFIG.getInt("waveDuration") / 20,
                    Main.CONFIG.getInt("waveCooldown") / 20)) {
        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            ServerWorld world = (ServerWorld) user.getWorld();
            Vec3d center = user.getPos();
            int maxRadius = (int) Math.ceil(Main.CONFIG.getDouble("waveRadius"));
            int duration = Main.CONFIG.getInt("waveDuration");

            for (int i = 0; i <= maxRadius; i++) {
                final int radius = i;
                final int delay = i;

                Main.scheduler.schedule(() -> {
                    // Calculate positions for the ring of radius i
                    int particlesPerRing = (int) (radius * 10); // More particles for larger radius
                    for (int j = 0; j < particlesPerRing; j++) {
                        double angle = 2 * Math.PI * j / particlesPerRing;
                        double x = center.x + radius * Math.cos(angle);
                        double z = center.z + radius * Math.sin(angle);
                        double y = center.y + 0.1; // Slightly
                                                   // above
                                                   // ground
                                                   // level

                        DustParticleEffect effect = new DustParticleEffect(0x0000FF, 2f); // blue
                        world.spawnParticles(effect, x, y, z, 1, 0, 0, 0, 0.01);
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
                for (int i = 0; i < remainingTicks / 5; i++) {
                    final int step = i;
                    Main.scheduler.schedule(() -> {
                        // Continuous water particles evenly distributed within the radius
                        for (int j = 0; j < 30; j++) {
                            double randomRadius = Math.random() * maxRadius;
                            double randomAngle = Math.random() * 2 * Math.PI;
                            double x = center.x + Math.cos(randomAngle) * randomRadius;
                            double z = center.z + Math.sin(randomAngle) * randomRadius;
                            double y = center.y + 0.1;
                            world.spawnParticles(ParticleTypes.SPLASH, x, y, z, 5, 0, 0, 0, 0.01);
                        }
                        BlockPos.stream(BlockPos.ofFloored(center.subtract(maxRadius, maxRadius, maxRadius)),
                                BlockPos.ofFloored(center.add(maxRadius, maxRadius, maxRadius)))
                                .filter(pos -> world.getBlockState(pos).isOf(net.minecraft.block.Blocks.COBWEB))
                                .forEach(pos -> world.breakBlock(pos, false));
                    }, step * 10);
                }
            }, maxRadius);

            Main.scheduler.schedule(() -> {
                recordUse(user, this);
            }, duration);

            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_DOLPHIN_SPLASH, net.minecraft.sound.SoundCategory.PLAYERS,
                    1.0F, 1.0F);
            user.sendMessage(Text.literal("A tidal wave washes away the cobwebs!"), true);
        }
    },

    LINK("link", Main.CONFIG.getInt("linkCooldown"),
            String.format(
                    """
                                    When activated, the next player you hit will be linked
                                    to you. For the next %d seconds, Mortals gain a
                                    +1 attack damage boost and Immortals gain Regeneration I,
                                    as long as both players are within %.2f blocks of each other.
                                    Cooldown %d seconds.
                            """,
                    Main.CONFIG.getInt("linkDuration") / 20,
                    Main.CONFIG.getDouble("linkRadius"),
                    Main.CONFIG.getInt("linkCooldown") / 20)) {
        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_ANVIL_LAND, net.minecraft.sound.SoundCategory.PLAYERS, 1.5F,
                    2.0F);
            ((ImmortalsData) user).setLinked(target.getUuid());
            ((ImmortalsData) target).setLinked(user.getUuid());
            user.sendMessage(Text.literal("A link has been formed with " + target.getName().getString()), true);
            target.sendMessage(Text.literal("A link has been formed with " + user.getName().getString()), true);
            AtomicBoolean ended = new AtomicBoolean(false);
            for (int i = 0; i < Main.CONFIG.getInt("linkDuration"); i++) {
                if (i % 20 == 0) {
                    Main.scheduler.schedule(() -> {
                        if (ended.get())
                            return;
                        if (user.squaredDistanceTo(target) <= Math.pow(Main.CONFIG.getDouble("linkRadius"), 2)) {
                            // Dish out benefits
                            applyBenefits(user);
                            applyBenefits(target);

                            // Draw a line of metal particles from user to target at center level
                            Vec3d userPos = user.getPos().add(0, user.getStandingEyeHeight() * 0.5, 0);
                            Vec3d targetPos = target.getPos().add(0, target.getStandingEyeHeight() * 0.5, 0);
                            Vec3d direction = targetPos.subtract(userPos).normalize();
                            double distance = userPos.distanceTo(targetPos);
                            ServerWorld world = (ServerWorld) user.getWorld();

                            for (double d = 0; d < distance; d += 0.2) {
                                Vec3d particlePos = userPos.add(direction.multiply(d));
                                world.spawnParticles(ParticleTypes.FALLING_LAVA, particlePos.x, particlePos.y,
                                        particlePos.z, 1, 0, 0, 0, 0.01);
                            }
                        } else {
                            // Revoke benefits
                            revokeBenefits(user);
                            revokeBenefits(target);
                            user.sendMessage(Text.literal("The link has been broken!"), true);
                            target.sendMessage(Text.literal("The link has been broken!"), true);
                            ((ImmortalsData) user).setLinked(null);
                            ((ImmortalsData) target).setLinked(null);
                            ended.set(true);
                            recordUse(user, this); // Record
                                                   // use
                                                   // if
                                                   // all
                                                   // linked
                                                   // players
                                                   // are
                                                   // removed
                        }
                    }, i);
                }
            }

            Main.scheduler.schedule(() -> {
                if (ended.get()) {
                    // Already recorded
                    return;
                }

                revokeBenefits(user);
                revokeBenefits(target);
                recordUse(user, this);
                user.sendMessage(Text.literal("The link benefits have ended."), true);
                target.sendMessage(Text.literal("The link benefits have ended."), true);
                ((ImmortalsData) user).setLinked(null);
                ((ImmortalsData) target).setLinked(null);
            }, Main.CONFIG.getInt("linkDuration"));
        }

        private void applyBenefits(ServerPlayerEntity player) {
            if (((ImmortalsData) player).isImmortal()) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 0, false, false));
            } else {
                if (player.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).getBaseValue() == player
                        .getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE)) {
                    player.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(2.0);
                }
            }
        }

        private void revokeBenefits(ServerPlayerEntity player) {
            if (((ImmortalsData) player).isImmortal()) {
                player.removeStatusEffect(StatusEffects.REGENERATION);
            } else {
                player.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE)
                        .setBaseValue(player.getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE));
            }

        }
    },

    LOCK("lock", Main.CONFIG.getInt("lockCooldown"), String.format("""
                    When activated, the opponent's inventory is searched
                    for the item in the slot corresponding with this spell
                    in your hotbar. If it is found, it is locked for
                    %d seconds for both players, preventing it from
                    being moved or used. %d s cooldown.
            """,
            Main.CONFIG.getInt("lockDuration") / 20,
            Main.CONFIG.getInt("lockCooldown") / 20)) {

        @Override
        public void activate(ServerPlayerEntity user, ServerPlayerEntity target) {
            int hotbarSlot = user.getInventory().getSelectedSlot();
            ItemStack userItem = user.getInventory().getStack(hotbarSlot);

            if (userItem.isEmpty()) {
                user.sendMessage(Text.literal("Your hotbar slot is empty!"), true);
                return;
            }
            final ItemStack[] targetItem = { null };

            // Check hotbar slots first
            for (int i = 0; i < 9; i++) { // Loop through the target's hotbar slots
                ItemStack currentItem = target.getInventory().getStack(i);
                if (!currentItem.isEmpty() && userItem.isOf(currentItem.getItem())) {
                    targetItem[0] = currentItem;
                    break;
                }
            }

            // If not found in hotbar, check offhand slot
            if (targetItem[0] == null) {
                ItemStack offhandItem = target.getOffHandStack();
                if (!offhandItem.isEmpty() && userItem.isOf(offhandItem.getItem())) {
                    targetItem[0] = offhandItem;
                }
            }

            if (targetItem[0] == null) {
                user.sendMessage(Text.literal("Matching item could not be found!"), true);
                recordUse(user, this);
                return;
            } else {
                target.getItemCooldownManager().set(targetItem[0], 10 * 20);
                user.getItemCooldownManager().set(userItem, 10 * 20);

                user.sendMessage(Text.literal("Locked item: " + targetItem[0].getName().getString()), true);
                target.sendMessage(Text.literal("Your item has been locked: " + targetItem[0].getName().getString()),
                        true);

                // Lock the item for 10 seconds
                Main.scheduler.schedule(() -> {
                    recordUse(user, this);
                    user.sendMessage(
                            Text.literal(targetItem[0].getItem().getName().getString() + " has been unlocked."), true);
                    target.sendMessage(
                            Text.literal(targetItem[0].getItem().getName().getString() + " has been unlocked."), true);
                }, Main.CONFIG.getInt("lockDuration"));
            }
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
            if (Utils.inventoryHas(user, Items.DRAGON_EGG) == -1) {
                user.sendMessage(Text.literal("You require the Dragon Egg to cast this spell."), true);
                return;
            }
            Utils.grant(user, "dragon_ascent");

            // Announce to nearby players and draw dragon ascent runes
            NetworkChannels.RuneS2CPayload payload = new NetworkChannels.RuneS2CPayload("dragon_ascent", user.getX(),
                    user.getY(), user.getZ(), 14.0f, 80);

            Utils.sendPayloadToNearby(user, payload);

            for (int i = 0; i < 100; i++) { // 5 seconds
                Main.scheduler.schedule(() -> {
                    dragonAscentParticles(user.getPos(), user.getWorld());
                }, i);
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

            Box box = user.getBoundingBox().expand(radius);

            List<ServerPlayerEntity> players = world.getEntitiesByClass(ServerPlayerEntity.class, box, e -> true);
            List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class, box, e -> true);

            // merge if needed
            List<LivingEntity> targets = new ArrayList<>(players);
            targets.addAll(hostiles);

            for (LivingEntity t : targets) {
                // Skip yourself, teammates, trusted
                if (t == user || Utils.getPlayerData(user).getTrusted().contains(t.getUuid()) || t.isTeammate(user))
                    continue;

                Vec3d tpos = t.getPos().add(0, t.getStandingEyeHeight() * 0.5, 0);
                float maxHealth = t.getMaxHealth();
                float damage = maxHealth * ((Number) Main.CONFIG.getDouble("dragonAscentTotalDmg")).floatValue() / 2.0f;
                int[] delays = { 20, 40 };
                for (int delay : delays) {
                    Main.scheduler.schedule(() -> {
                        t.damage(world, Utils.of(world, Utils.SPELL_DAMAGE_TYPE, (Entity) user), damage);
                        strikeLightning(world, tpos);
                    }, delay);
                }
            }

            Main.scheduler.schedule(() -> {
                recordUse(user, this);
            }, Main.CONFIG.getInt("dragonAscentLevitation") + 20);
            user.sendMessage(Text.literal("§dThe dragon rune smites your enemies!"), true);
        }

        private static void dragonAscentParticles(Vec3d pos, World world) {
            if (!(world instanceof ServerWorld serverWorld))
                return;
            double y = pos.y;
            int particleCount = 100; // Number of
                                     // particles to
                                     // spawn
            double radius = 30.0;
            for (int i = 0; i < particleCount; i++) {
                double angle = Math.random() * 2 * Math.PI;
                double dist = Math.sqrt(Math.random()) * radius; // Uniform
                                                                 // distribution
                                                                 // in
                                                                 // circle
                double x = pos.x + Math.cos(angle) * dist;
                double z = pos.z + Math.sin(angle) * dist;
                double py = y + (Math.random() - 0.5) * 2; // Small
                                                           // vertical
                                                           // variation
                serverWorld.spawnParticles(ParticleTypes.PORTAL, x, py, z, 1, 0, 0, 0, 0);
            }
        }

        public static void strikeLightning(ServerWorld world, Vec3d position) {
            LightningEntity lightningBolt = EntityType.LIGHTNING_BOLT.create(world, entity -> {
            }, // No-op consumer
                    new BlockPos((int) position.x, (int) position.y, (int) position.z), SpawnReason.TRIGGERED, true,
                    true);
            if (lightningBolt != null) {
                lightningBolt.setCosmetic(true); // Mark
                                                 // the
                                                 // lightning
                                                 // as
                                                 // cosmetic
                                                 // to
                                                 // prevent
                                                 // damage
                world.spawnEntity(lightningBolt);
            }
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
            if (!user.getInventory().contains(new ItemStack(ModItems.TIMEKEEPER))) {
                user.sendMessage(Text.literal("You require the Timekeeper to cast this spell."), true);
                return;
            }
            Utils.grant(user, "timeslow");
            NetworkChannels.RuneS2CPayload payload = new NetworkChannels.RuneS2CPayload("timeslow", user.getX(),
                    user.getY(), user.getZ(), 14.0f, Main.CONFIG.getInt("timeSlowDuration"));

            Utils.sendPayloadToNearby(user, payload);

            ServerWorld world = (ServerWorld) user.getWorld();
            Vec3d center = user.getPos();
            double radius = Main.CONFIG.getDouble("timeSlowRadius");
            int duration = Main.CONFIG.getInt("timeSlowDuration");
            int checkInterval = 10;

            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_BEACON_ACTIVATE, net.minecraft.sound.SoundCategory.PLAYERS,
                    1.0F, 0.5F);

            Main.scheduler.schedule(() -> {
                for (int i = 0; i < duration; i++) {
                    final int step = i;
                    double handAngle = (2 * Math.PI * step) / duration;
                    Main.scheduler.schedule(() -> {
                        timeslowParticles(center, world, radius, 100, handAngle);
                    }, step);
                }
            }, 0);

            // Repeat ambient sound every 80 ticks (4 seconds) for the spell duration
            int ambientRepeat = 80;
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
                    Box box = user.getBoundingBox().expand(radius);

                    List<LivingEntity> inZone = world.getEntitiesByClass(LivingEntity.class, box,
                            e -> e != user && e.isAlive());

                    // Apply ender pearl cooldown to any players in the zone
                    for (Entity entity : inZone) {
                        if (entity instanceof ServerPlayerEntity affectedPlayer) {
                            // Set a cooldown on ender pearls that lasts until the next check
                            affectedPlayer.getItemCooldownManager().set(new ItemStack(Items.ENDER_PEARL),
                                    checkInterval);
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

            user.sendMessage(Text.literal("§3Timeslow is in effect!"), true);
        }

        // Timeslow dynamic clock particles
        public static void timeslowParticles(Vec3d pos, ServerWorld world, double radius, int particles,
                double handAngle) {
            // Draw the circle
            for (int i = 0; i < particles; i++) {
                double angle = 2 * Math.PI * i / particles;
                double x = pos.x + radius * Math.cos(angle);
                double z = pos.z + radius * Math.sin(angle);
                double y = pos.y + 0.1;
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0, 0, 0, 0f);
            }

            // Draw 8 clock lines (static)
            int clockLines = 8;
            double lineLength = radius * 0.9;
            for (int i = 0; i < clockLines; i++) {
                double angle = 2 * Math.PI * i / clockLines;
                double x1 = pos.x + (radius - 0.2) * Math.cos(angle);
                double z1 = pos.z + (radius - 0.2) * Math.sin(angle);
                double x2 = pos.x + lineLength * Math.cos(angle);
                double z2 = pos.z + lineLength * Math.sin(angle);
                double y = pos.y + 0.1;
                // Draw a line from x1,z1 to x2,z2 (5 particles)
                for (int j = 0; j <= 5; j++) {
                    double frac = j / 5.0;
                    double px = x1 + (x2 - x1) * frac;
                    double pz = z1 + (z2 - z1) * frac;
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, y, pz, 1, 0, 0, 0, 0f);
                }
            }

            // Draw the moving minute hand
            double hx1 = pos.x;
            double hz1 = pos.z;
            double hx2 = pos.x + (radius - 0.3) * Math.cos(handAngle);
            double hz2 = pos.z + (radius - 0.3) * Math.sin(handAngle);
            double hy = pos.y + 0.12;
            // Draw the hand as a line
            for (int j = 0; j <= 10; j++) {
                double frac = j / 10.0;
                double px = hx1 + (hx2 - hx1) * frac;
                double pz = hz1 + (hz2 - hz1) * frac;
                world.spawnParticles(ParticleTypes.GLOW, px, hy, pz, 1, 0, 0, 0, 0f);
            }
        }
    };

    // Spell interface
    private final String id;
    private final int cooldownTicks;
    private final String description;
    private static final Map<UUID, Map<SpellRegistry, Long>> LAST_USED = new ConcurrentHashMap<>();

    SpellRegistry(String id, int cooldownTicks, String description) {
        this.id = id;
        this.cooldownTicks = cooldownTicks;
        this.description = description;
        // General types: Regular, onHit, automatic
    }

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

    /** The unique identifier players will use in `/bind ...` */
    public String getId() {
        return id;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public String getDescription() {
        // Replace newlines and collapse multiple spaces into a single space
        return description.replace("\n", " ").replaceAll("\\s+", " ").trim();
    }

    /** Bind a spell, replacing the spell that was there if necessary */
    public static void bind(ServerPlayerEntity player, int slot, SpellRegistry spell) {
        Map<Integer, String> bindings = ((ImmortalsData) player).getSpellBindings();
        bindings.entrySet().removeIf(entry -> spell.getId().equals(entry.getValue()));
        bindings.put(slot, spell.getId());
        Utils.sendSpellInfoToPlayer(player, spell.getId(), Utils.currentSpellState(player, spell),
                Utils.currentCooldown(player, spell), spell.getCooldownTicks());
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

    /** Helper to look up spell by id */
    public static SpellRegistry fromId(String id) {
        for (SpellRegistry s : values()) {
            if (s.id.equalsIgnoreCase(id))
                return s;
        }
        return null;
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
        Utils.sendSpellInfoToPlayer(player, spell.getId(), "cooldown",
                Utils.currentCooldown(player, spell), spell.getCooldownTicks());
    }

    // Record that the spell's cooldown will start after effects end
    public static void recordInUse(ServerPlayerEntity player, SpellRegistry spell) {
        Spell.pendingCooldownNotifications
                .computeIfAbsent(player.getUuid(), u -> new ConcurrentHashMap<>())
                .put(spell, -1);
        Utils.sendSpellInfoToPlayer(player, spell.getId(), "in_use",
                0, spell.getCooldownTicks());
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
            player.sendMessage(Text.literal("Your abilities are disabled!"), true);
            return false;
        }

        int corr = (playerData.getCorruption());
        SpellRegistry spell = getBound(player, slot);

        if (spell == null) {
            player.sendMessage(Text.literal("No spell bound to slot " + (slot + 1)), true);
            return false;
        }

        // Level requirements
        if (corr < Utils.getRequiredCorr(spell)) {
            return false;
        }

        // Must have a dragon egg to use dragon ascent
        if (spell.id.equals("dragon_ascent") && Utils.inventoryHas(player, Items.DRAGON_EGG) == -1) {
            unbind(player, SpellRegistry.DRAGON_ASCENT);
            return false;
        }

        // Must have a timekeeper to use timeslow
        if (spell.id.equals("timeslow") && Utils.inventoryHas(player, ModItems.TIMEKEEPER) == -1) {
            unbind(player, SpellRegistry.TIMESLOW);
            return false;
        }

        // Cooldown needs to be up
        if (!canUse(player, spell)) {
            int cooldown = Spell.pendingCooldownNotifications.get(player.getUuid()).get(spell);
            if (cooldown == -1) {
                player.sendMessage(Text.literal(spell.getDisplayName() + " is still in use!"), true);
                return false;
            }
            player.sendMessage(Text.literal(spell.getDisplayName() + " is on cooldown for " + cooldown + "s!"), true);
            return false;
        }

        // On hit spell types have delayed activation handled in Immortals.java
        if (spell == SpellRegistry.FROSTBITE || spell == SpellRegistry.LOCK || spell == SpellRegistry.LINK) {
            ((ImmortalsData) player).setOnHitSpell(spell.getId());
            player.sendMessage(Text.literal("§a" + spell.getDisplayName() + " will activate on your next hit!"), true);
            return false;
        }

        if (spell != SpellRegistry.SPLINTER_BLOW)
            recordInUse(player, spell);
        spell.activate(player, target);
        return true;
    }
}