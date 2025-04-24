package com.immortals;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;

public class AscensionUtils {

    public static int getAscended(ServerPlayerEntity player) {
        Scoreboard sb = player.getWorld().getScoreboard();
        ScoreboardObjective obj = sb.getNullableObjective("hasAscended");
        if (obj == null) {
            return 0;
        }

        ReadableScoreboardScore score = sb.getScore(player, obj);
        return score == null ? 0 : score.getScore();
    }

    public static int getCorruption(ServerPlayerEntity player) {
        Scoreboard sb = player.getWorld().getScoreboard();
        ScoreboardObjective obj = sb.getNullableObjective("corruptionLevel");
        if (obj == null) {
            return 0;
        }

        ReadableScoreboardScore score = sb.getScore(player, obj);
        return score == null ? 0 : score.getScore();
    }

    public static void addCorruption(ServerPlayerEntity player, int level) {
        Scoreboard sb = player.getWorld().getScoreboard();
        ScoreboardObjective obj = sb.getNullableObjective("corruptionLevel");
        int curr = getCorruption(player);
        sb.getOrCreateScore(player, obj).setScore(curr + level);
    }

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

}