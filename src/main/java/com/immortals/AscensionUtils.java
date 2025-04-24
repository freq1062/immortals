package com.immortals;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

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

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("bind")
                .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 9))
                        .then(CommandManager.argument("spell", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
                                    String spellId = StringArgumentType.getString(ctx, "spell");
                                    Spell spell = Spell.fromId(spellId);

                                    if (spell == null) {
                                        player.sendMessage(Text.literal("Unknown spell: " + spellId), false);
                                        return 0;
                                    }

                                    Spell.bind(player, slot, spell);
                                    player.sendMessage(
                                            Text.literal("Bound " + spell.getId() + " to slot " + (slot + 1)), false);
                                    return 1;
                                }))));
        dispatcher.register(CommandManager.literal("corruption")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    int corruptionLevel = getCorruption(player);
                    player.sendMessage(Text.literal("Your corruption level is: " + corruptionLevel), false);
                    return 1;
                }));
    }

    public static ActionResult tryCastSpell(ServerPlayerEntity player, ServerWorld world) {
        if (!player.isSneaking())
            return ActionResult.PASS;
        int slot = player.getInventory().selectedSlot;
        return Spell.tryActivate(player, slot)
                ? ActionResult.SUCCESS
                : ActionResult.PASS;
    }

}