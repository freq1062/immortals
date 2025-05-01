package com.immortals;

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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

/* Implements the Mortals' Lifesteal system.

When a mortal is killed by a mortal player, the victim loses 1 max heart and the killer gains 1 max heart (heart item dropped on ground).
When a mortal dies to natural causes, a heart is dropped where they died
When a mortal kills an immortal, the number of hearts they receive scales depending on the corruption level

<= +1: 1 heart
+2: 2 hearts
+3: 3 hearts

/withdraw [number]: Withdraws up to [number of hearts - 1] hearts and converts them to items.

 */

public class Mortals {
    public static void register() {
        // Lose 1 heart on death
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!Utils.getAscended(newPlayer)) {
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
                        mhVic.setBaseValue(8.0); // Restart player at 3 hearts, - 1 on respawn
                        String playerName = victim.getNameForScoreboard();
                        String reason = "You have run out of hearts!";
                        String command = String.format("tempban %s 24h %s", playerName, reason);
                        MinecraftServer server = victim.getServer();
                        server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
                    }

                    victim.getWorld().spawnEntity(new ItemEntity(
                            victim.getWorld(),
                            victim.getX(), victim.getY(), victim.getZ(),
                            new ItemStack(ModItems.HEART)));
                }

                // Mortals kill immortals
                if (victimImmortal && !attackerImmortal) {
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

                                if (heartsToWithdraw < 1 || currentHealth - heartsToWithdraw <= 0
                                        || maxHealth - heartsToWithdraw < 1) {
                                    player.sendMessage(Text.literal("Invalid amount of hearts to withdraw."), false);
                                    return 0;
                                }

                                // Reduce player's max health
                                player.getAttributeInstance(EntityAttributes.MAX_HEALTH)
                                        .setBaseValue(maxHealth - heartsToWithdraw);
                                player.setHealth((float) Math.min(currentHealth, maxHealth - (heartsToWithdraw * 2)));

                                // Give the player the withdrawn hearts as an item (e.g., Heart Shard)
                                // Assuming there's an item called "Heart Shard" in your mod
                                ItemStack heartShard = new ItemStack(ModItems.HEART, heartsToWithdraw);
                                if (!player.getInventory().insertStack(heartShard)) {
                                    player.dropItem(heartShard, false);
                                }

                                player.sendMessage(Text.literal("You have withdrawn " + heartsToWithdraw + " hearts."),
                                        false);
                                return 1;
                            })));
        });
    }
}