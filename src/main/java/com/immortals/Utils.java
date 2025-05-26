package com.immortals;

import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.particle.ParticleTypes;

import com.immortals.api.PlayerImmortalsData;

import net.minecraft.entity.EntityType;

public class Utils {
    // Return true if the player is ascended (immortal), false otherwise
    public static boolean getAscended(ServerPlayerEntity player) {
        PlayerImmortalsData data = (PlayerImmortalsData) player;
        return data.isImmortal();
    }

    public static void setAscended(ServerPlayerEntity player, boolean ascended) {
        PlayerImmortalsData data = (PlayerImmortalsData) player;
        data.setImmortal(ascended);
    }

    // Return the player's corruption level
    public static int getCorruption(ServerPlayerEntity player) {
        PlayerImmortalsData data = (PlayerImmortalsData) player;
        return data.getCorruption();
    }

    // Add [level] to the player's corruption
    public static void addCorruption(ServerPlayerEntity player, int level) {
        PlayerImmortalsData data = (PlayerImmortalsData) player;
        data.setCorruption(data.getCorruption() + level);
    }

    // Helper function for getting the next shard cost based on the corruption level
    public static int nextShardCost(int level) {
        return switch (level) {
            case 0, -3, -2, -1 -> 1;
            case 1 -> 2;
            case 2 -> 3;
            default -> 0; // at max (+3)
        };
    }

    public static void applyCorruptionEffects(ServerPlayerEntity player) {
        int level = getCorruption(player);

        // Reset health and effects
        player.clearStatusEffects();
        player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(20.0); // Reset to 10 hearts

        if (level <= -1) {
            // -1: -1 heart, -10% XP gain (XP handled elsewhere)
            player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(18.0);
        }

        if (level <= -2) {
            // -2: Slowness I
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.SLOWNESS, Integer.MAX_VALUE, 0, false, false));
        }

        if (level <= -3) {
            // -3: Weakness I
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.WEAKNESS, Integer.MAX_VALUE, 0, false, false));
        }

        if (level >= 2) {
            // +2: Speed II, Strength II
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, Integer.MAX_VALUE, 1, false, false));
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.STRENGTH, Integer.MAX_VALUE, 1, false, false));
        }
    }

    // Draws the dragon ascent rune circle at pos
    public static void drawDragonAscent(Vec3d pos, World world) {
        double y = pos.y;
        // Circle
        int circlePts = 256; // Increase points for smoother circle
        double circleR = 5.0;
        for (int i = 0; i < circlePts; i++) {
            double ang = 2 * Math.PI * i / circlePts;
            double x = pos.x + Math.cos(ang) * circleR;
            double z = pos.z + Math.sin(ang) * circleR;
            if (world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.PORTAL, x, y, z, 1, 0, 0, 0, 0); // Set motion to 0 to prevent
                                                                                          // falling
            }
        }

        // Rotated squares
        double[] sqR = { 5.0, 4.0, 3.0 };
        double baseRotation = 30;
        for (int i = 0; i < sqR.length; i++) {
            double r = sqR[i];
            double rot = baseRotation + i * 45; // 0°, 45°, 90° etc.
            Vec3d[] corners = new Vec3d[4];
            for (int c = 0; c < 4; c++) {
                double ang = Math.toRadians(rot + 45 + 90 * c);
                corners[c] = pos.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            }
            int steps = 40; // Number of steps for line interpolation
            for (int c = 0; c < 4; c++) {
                Vec3d a = corners[c], b = corners[(c + 1) % 4];
                for (int s = 0; s <= steps; s++) {
                    double t = s / (double) steps;
                    Vec3d pt = a.lerp(b, t);
                    if (world instanceof ServerWorld serverWorld) {
                        serverWorld.spawnParticles(ParticleTypes.PORTAL, pt.x, y, pt.z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    // Strikes lightning at a given position
    public static void strikeLightning(ServerWorld world, Vec3d position) {
        LightningEntity lightningBolt = EntityType.LIGHTNING_BOLT.create(
                world,
                entity -> {
                }, // No-op consumer
                new BlockPos((int) position.x, (int) position.y, (int) position.z),
                SpawnReason.TRIGGERED,
                true,
                true);
        if (lightningBolt != null) {
            lightningBolt.setCosmetic(true); // Mark the lightning as cosmetic to prevent damage
            world.spawnEntity(lightningBolt);
        }
    }

    public static void drawTimeslow(Vec3d pos, ServerWorld world, int radius, int particles, double handAngle) {
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
        // Draw the hand as a line (10 particles, use CRIT for visibility)
        for (int j = 0; j <= 10; j++) {
            double frac = j / 10.0;
            double px = hx1 + (hx2 - hx1) * frac;
            double pz = hz1 + (hz2 - hz1) * frac;
            world.spawnParticles(ParticleTypes.GLOW, px, hy, pz, 1, 0, 0, 0, 0f);
        }
    }

    public static ItemStack findInInventory(PlayerEntity p, Item item) {
        for (ItemStack s : p.getInventory().main) {
            if (s.isOf(item))
                return s;
        }
        return null;
    }
}