package com.immortals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import com.immortals.item.ModItems;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;

public class ModEvents {
    public static void register() {
        // Initialize the scoreboard objective for ascension
        ServerTickEvents.START_SERVER_TICK.register((MinecraftServer server) -> {
            Scoreboard sb = server.getScoreboard();
            // only create once
            if (sb.getNullableObjective("hasAscended") == null) {
                sb.addObjective(
                        "hasAscended",
                        ScoreboardCriterion.DUMMY,
                        (Text) Text.literal("Ascended"),
                        ScoreboardCriterion.RenderType.INTEGER,
                        false,
                        null);
            }
        });

        // On player death: decrement corruption, drop one soul shard if the killer has
        // ascended
        ServerLivingEntityEvents.AFTER_DEATH.register((ent, src) -> {
            if (!ent.getWorld().isClient() && ent instanceof ServerPlayerEntity victim) {
                Entity k = src.getAttacker();
                if (k instanceof ServerPlayerEntity killer
                        && killer.getInventory().contains(new ItemStack(ModItems.ASCENSION_TOTEM))) {
                    victim.dropItem(new ItemStack(ModItems.SOUL_SHARD, 1), false);
                }
            }
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            ItemStack stack = player.getStackInHand(hand);

            // Ascension Totem: Update objective hasAscended
            if (stack.getItem() == ModItems.ASCENSION_TOTEM) {
                Scoreboard sb = player.getWorld().getScoreboard();
                ScoreboardObjective obj = sb.getNullableObjective("hasAscended");
                ReadableScoreboardScore score = sb.getScore(player, obj);

                if (score == null || score.getScore() == 0) {
                    player.sendMessage(Text.literal("You feel a surge of divine power!"), true);
                    sb.getOrCreateScore(player, obj).setScore(1); // Set the score to 1

                    // Play totem animation and particles
                    player.setHealth(1.0F);
                    world.sendEntityStatus(player, (byte) 35); // Totem pop
                    if (world instanceof ServerWorld serverWorld) {
                        serverWorld.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1, player.getZ(),
                                50, 0.5, 0.5, 0.5, 0.01);
                        serverWorld.spawnParticles(ParticleTypes.DRAGON_BREATH, player.getX(), player.getY() + 1,
                                player.getZ(), 20, 0.5, 0.5, 0.5, 0.01);
                    }
                    player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 1.0F, 1.0F);

                    stack.decrement(1); // Consume the ascension totem
                    return ActionResult.SUCCESS;
                } else {
                    player.sendMessage(Text.literal("You have already ascended."), true);
                    return ActionResult.FAIL;
                }
            }

            // Soul Shard: Update corruption level -3 up to +3
            if (stack.getItem() == ModItems.SOUL_SHARD) {
                if (PlayerCorruption.get((ServerPlayerEntity) player) < 3) {
                    PlayerCorruption.increment((ServerPlayerEntity) player);
                    player.sendMessage(Text.literal("You feel your soul strengthening..."), true);
                    stack.decrement(1);
                    return ActionResult.SUCCESS;
                }
                player.sendMessage(Text.literal("Your soul is already at its peak."), true);
                return ActionResult.FAIL;
            }

            // Soul Purifier: Update corruption level +1 up to 0
            if (stack.getItem() == ModItems.SOUL_PURIFIER) {
                if (PlayerCorruption.get((ServerPlayerEntity) player) < 0) {
                    PlayerCorruption.increment((ServerPlayerEntity) player);
                    player.sendMessage(Text.literal("The purifier calms your inner darkness."), true);
                    stack.decrement(1);
                    return ActionResult.SUCCESS;
                }
                player.sendMessage(Text.literal("You feel no corruption to cleanse."), true);
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });

        // Every server tick: if a player has an ascension totem, strip out regular
        // totems
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getInventory().contains(new ItemStack(ModItems.ASCENSION_TOTEM))) {
                    for (int i = 0; i < player.getInventory().size(); i++) {
                        ItemStack s = player.getInventory().getStack(i);
                        if (s.getItem() == Items.TOTEM_OF_UNDYING) {
                            player.getInventory().removeStack(i);
                        }
                    }
                }
            }
        });
    }
}