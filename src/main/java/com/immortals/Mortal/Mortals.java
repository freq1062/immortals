package com.immortals.Mortal;

import com.immortals.Utils;
import com.immortals.item.ModItems;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

/* Implements the Mortals' Lifesteal system. */
public class Mortals {
    public static void register() {
        // Lose 1 heart on death
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!Utils.getAscended(newPlayer)
                    && oldPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH).getBaseValue() > 2.0) {
                EntityAttributeInstance old_hp = oldPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                EntityAttributeInstance new_hp = newPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                new_hp.setBaseValue(old_hp.getBaseValue() - 2.0);
                newPlayer.sendMessage(
                        Text.literal("§5You lost a heart."),
                        true);
            }
        });

        // Mortals' lifesteal
        ServerLivingEntityEvents.AFTER_DEATH.register((ent, src) -> {
            if (!ent.getWorld().isClient() && ent instanceof ServerPlayerEntity victim) {
                Entity attacker = src.getAttacker();
                boolean victimImmortal = Utils.getAscended(victim);
                boolean attackerImmortal = attacker instanceof ServerPlayerEntity k
                        && Utils.getAscended(k);

                // Mortal killed by mortal or natural causes
                if (!victimImmortal
                        && (!attackerImmortal || (attacker == null || !(attacker instanceof ServerPlayerEntity)))) {
                    EntityAttributeInstance mhVic = victim.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                    if (mhVic.getBaseValue() <= 2.0) {
                        // banned ):
                        mhVic.setBaseValue(6.0); // Restart player at 3 hearts
                        String playerName = victim.getNameForScoreboard();
                        String reason = "You have run out of hearts!";
                        String command = String.format("tempban %s 0 0 24 %s", playerName, reason);
                        MinecraftServer server = victim.getServer();
                        server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
                    }

                    victim.getWorld().spawnEntity(new ItemEntity(
                            victim.getWorld(),
                            victim.getX(), victim.getY(), victim.getZ(),
                            new ItemStack(ModItems.HEART)));
                }

                // Mortals kill immortals
                if (victimImmortal && attacker instanceof ServerPlayerEntity && !attackerImmortal) {
                    int corr = Utils.getCorruption(victim);
                    int heartsToGive = switch (corr) {
                        case 2 -> 2;
                        case 3 -> 3;
                        default -> 1;
                    };

                    for (int i = 0; i < heartsToGive; i++) {
                        victim.getWorld().spawnEntity(new ItemEntity(
                                victim.getWorld(),
                                victim.getX(), victim.getY(), victim.getZ(),
                                new ItemStack(ModItems.HEART)));
                    }
                }
            }
        });

        // Item use events
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            ItemStack stack = player.getStackInHand(hand);

            if (stack.getItem() == ModItems.HEART) {
                if (!Utils.getAscended((ServerPlayerEntity) player)) {
                    EntityAttributeInstance curr_hp = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                    if (curr_hp.getBaseValue() < 40.0) {
                        curr_hp.setBaseValue(curr_hp.getBaseValue() + 2.0);
                        stack.decrement(1);
                        return ActionResult.SUCCESS;
                    } else {
                        player.sendMessage(Text.literal("§5You have reached the maximum number of hearts."),
                                true);
                        return ActionResult.FAIL;
                    }
                }
                // Immortal tries to use a heart
                player.sendMessage(Text.literal("An immortal does not need extra hearts to be strong."), true);
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("withdraw")
                    .then(CommandManager.argument("hearts", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();

                                if (Utils.getAscended(player)) {
                                    player.sendMessage(Text.literal("You cannot withdraw hearts as an immortal!"),
                                            false);
                                    return 0;
                                }

                                int heartsToWithdraw = IntegerArgumentType.getInteger(ctx, "hearts");
                                double currentHealth = player.getHealth();
                                double maxHealth = player.getAttributeInstance(EntityAttributes.MAX_HEALTH)
                                        .getBaseValue();

                                if (heartsToWithdraw < 1 || maxHealth - (heartsToWithdraw * 2) < 1) {
                                    player.sendMessage(Text.literal("Invalid amount of hearts to withdraw."), false);
                                    return 0;
                                }
                                double totalHpToWithdraw = heartsToWithdraw * 2;

                                // Reduce player's max health
                                player.getAttributeInstance(EntityAttributes.MAX_HEALTH)
                                        .setBaseValue(maxHealth - totalHpToWithdraw);
                                player.setHealth((float) Math.min(currentHealth, maxHealth - totalHpToWithdraw));

                                // Give the player hearts
                                ItemStack heartShard = new ItemStack(ModItems.HEART, heartsToWithdraw);
                                if (!player.getInventory().insertStack(heartShard)) {
                                    player.dropItem(heartShard, false);
                                }

                                player.sendMessage(Text.literal("Withdrew " + heartsToWithdraw + " hearts."),
                                        false);
                                return 1;
                            })));
            dispatcher.register(CommandManager.literal("setAscendance")
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                            .then(CommandManager.argument("state", IntegerArgumentType.integer(0, 1))
                                    .executes(ctx -> {
                                        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(ctx, "target");
                                        boolean newState = IntegerArgumentType.getInteger(ctx, "state") == 1;
                                        EntityAttributeInstance maxHearts = targetPlayer
                                                .getAttributeInstance(EntityAttributes.MAX_HEALTH);
                                        if (maxHearts.getBaseValue() != 20.0) {
                                            maxHearts.setBaseValue(20.0);
                                        }

                                        Utils.setAscended(targetPlayer, newState);

                                        String message = newState
                                                ? targetPlayer.getName().getString() + " is now immortal."
                                                : targetPlayer.getName().getString() + " is now mortal.";
                                        ctx.getSource().sendFeedback(() -> Text.literal(message), false);

                                        return 1;
                                    }))));
        });
    }
}