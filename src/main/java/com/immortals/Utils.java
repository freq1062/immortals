package com.immortals;

import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.scoreboard.ReadableScoreboardScore;

public class Utils {
    // Return true if the player is ascended (immortal), false otherwise
    public static boolean getAscended(ServerPlayerEntity player) {
        Scoreboard sb = player.getWorld().getScoreboard();
        ScoreboardObjective obj = sb.getNullableObjective("hasAscended");
        if (obj == null) {
            return false;
        }

        ReadableScoreboardScore score = sb.getScore(player, obj);
        return score != null && score.getScore() == 1;
    }

    // Return the player's corruption level
    public static int getCorruption(ServerPlayerEntity player) {
        Scoreboard sb = player.getWorld().getScoreboard();
        ScoreboardObjective obj = sb.getNullableObjective("corruptionLevel");
        if (obj == null) {
            return 0;
        }

        ReadableScoreboardScore score = sb.getScore(player, obj);
        return score != null ? score.getScore() : 0;
    }

    // Add [level] to the player's corruption
    public static void addCorruption(ServerPlayerEntity player, int level) {
        Scoreboard sb = player.getWorld().getScoreboard();
        ScoreboardObjective obj = sb.getNullableObjective("corruptionLevel");
        int curr = getCorruption(player);
        sb.getOrCreateScore(player, obj).setScore(curr + level);
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
            world.spawnEntity(lightningBolt);
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