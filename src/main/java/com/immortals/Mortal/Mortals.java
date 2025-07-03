package com.immortals.Mortal;

import com.immortals.Utils;
import com.immortals.Immortal.SpellRegistry;
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
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.registry.entry.RegistryEntry;

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

        // Mortal xp scaling system had to be combined with the Immortal one, so it's in
        // Immortals.java

        // Mortals' lifesteal
        ServerLivingEntityEvents.AFTER_DEATH.register((ent, src) -> {
            if (!ent.getWorld().isClient() && ent instanceof ServerPlayerEntity victim) {
                Entity attacker = src.getAttacker();
                boolean victimImmortal = Utils.getAscended(victim);
                boolean attackerImmortal = attacker instanceof ServerPlayerEntity k
                        && Utils.getAscended(k);

                // Mortal killed by mortal or natural causes
                if (!attackerImmortal) {
                    if (victimImmortal) {
                        int corr = Utils.getCorruption(victim);
                        if (!(attacker instanceof ServerPlayerEntity)) {
                            // Victim immortal, but killed by non player
                            int shardsToGive = switch (corr) {
                                case 3 -> 2;
                                default -> 1;
                            };
                            for (int i = 0; i < shardsToGive; i++) {
                                victim.getWorld().spawnEntity(new ItemEntity(
                                        victim.getWorld(),
                                        victim.getX(), victim.getY(), victim.getZ(),
                                        new ItemStack(ModItems.SOUL_SHARD)));
                            }
                        } else {
                            // Victim immortal, killed by mortal player
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
                    } else {
                        victim.getWorld().spawnEntity(new ItemEntity(
                                victim.getWorld(),
                                victim.getX(), victim.getY(), victim.getZ(),
                                new ItemStack(ModItems.HEART)));
                    }
                    // Check if the victim has no hearts left
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
                        if (curr_hp.getBaseValue() == 30.0) {
                            Utils.grant((ServerPlayerEntity) player, "15_hearts");
                        } else if (curr_hp.getBaseValue() == 40.0) {
                            Utils.grant((ServerPlayerEntity) player, "20_hearts");
                        }
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
            } else if (stack.getItem() == ModItems.ARTIFICIAL_HEART) {
                if (!Utils.getAscended((ServerPlayerEntity) player)) {
                    EntityAttributeInstance curr_hp = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                    if (curr_hp.getBaseValue() < 14.0) { // 7 hearts
                        curr_hp.setBaseValue(curr_hp.getBaseValue() + 2.0);
                        stack.decrement(1);
                        return ActionResult.SUCCESS;
                    } else {
                        player.sendMessage(Text.literal("§5Artificial heart can only recover up to 7 hearts."),
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
                    .then(CommandManager.argument("hearts/corruption levels", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();

                                int amntToWithdraw = IntegerArgumentType.getInteger(ctx, "hearts/corruption levels");

                                if (Utils.getAscended(player)) {
                                    int currentCorruption = Utils.getCorruption(player);
                                    int maxWithdraw = 3 + currentCorruption;
                                    if (amntToWithdraw > maxWithdraw) {
                                        player.sendMessage(
                                                Text.literal("Invalid number of corruption levels to withdraw."),
                                                false);
                                        return 0;
                                    } else {
                                        int numShards = 0;
                                        int numPurifiers = 0;
                                        for (int i = 0; i < amntToWithdraw; i++) {
                                            int newCorruption = currentCorruption - i;
                                            if (newCorruption > 0) {
                                                // Award soul shards for positive levels
                                                if (newCorruption == 3) {
                                                    numShards += 3;
                                                } else if (newCorruption == 2) {
                                                    numShards += 2;
                                                } else {
                                                    numShards += 1;
                                                }
                                            } else {
                                                // Award soul purifiers for negative levels
                                                numPurifiers += 1;
                                            }
                                        }
                                        if (numShards > 0) {
                                            ItemStack soulShards = new ItemStack(ModItems.SOUL_SHARD, numShards);
                                            if (!player.getInventory().insertStack(soulShards)) {
                                                player.dropItem(soulShards, false);
                                            }
                                        }
                                        if (numPurifiers > 0) {
                                            ItemStack purifiers = new ItemStack(ModItems.SOUL_PURIFIER, numPurifiers);
                                            if (!player.getInventory().insertStack(purifiers)) {
                                                player.dropItem(purifiers, false);
                                            }
                                        }
                                        Utils.setCorruption(player, currentCorruption - amntToWithdraw);
                                        player.sendMessage(Text.literal(
                                                "Withdrew " + amntToWithdraw + " corruption level(s) and received "
                                                        + (numShards > 0 ? numShards + " soul shard(s)" : "")
                                                        + (numShards > 0 && numPurifiers > 0 ? " and " : "")
                                                        + (numPurifiers > 0 ? numPurifiers + " soul purifier(s)" : "")
                                                        + "."),
                                                false);
                                        return 1;
                                    }
                                    // if (amntToWithdraw > 0) {
                                    // int maxWithdraw = 3 + currentCorruption;
                                    // if (amntToWithdraw > maxWithdraw) {
                                    // player.sendMessage(
                                    // Text.literal("Invalid number of corruption levels to withdraw."),
                                    // false);
                                    // return 0;
                                    // }

                                    // // Award soul shards for positive levels
                                    // int totalSoulShards = 0;
                                    // int from = currentCorruption;
                                    // int to = currentCorruption - amntToWithdraw;
                                    // if (to == -1) {
                                    // player.getAttributeInstance(EntityAttributes.MAX_HEALTH)
                                    // .setBaseValue(18.0);
                                    // }
                                    // for (int i = from; i > to; i--) {
                                    // if (i == 3) {
                                    // totalSoulShards += 3;
                                    // } else if (i == 2) {
                                    // totalSoulShards += 2;
                                    // } else {
                                    // totalSoulShards += 1;
                                    // }
                                    // }

                                    // // Give soul shards
                                    // ItemStack soulShards = new ItemStack(ModItems.SOUL_SHARD, totalSoulShards);
                                    // if (!player.getInventory().insertStack(soulShards)) {
                                    // player.dropItem(soulShards, false);
                                    // }

                                    // // Lower corruption
                                    // Utils.setCorruption(player, currentCorruption - amntToWithdraw);

                                    // player.sendMessage(Text.literal(
                                    // "Withdrew " + amntToWithdraw + " corruption level(s) and received "
                                    // + totalSoulShards + " soul shard(s)."),
                                    // false);
                                    // return 1;
                                    // } else if (amntToWithdraw < 0) {
                                    // // Award soul purifiers for negative levels
                                    // int absWithdraw = Math.abs(amntToWithdraw);
                                    // int current = currentCorruption;
                                    // int to = currentCorruption - absWithdraw;
                                    // int totalPurifiers = 0;
                                    // for (int i = current; i > to; i--) {
                                    // totalPurifiers += 1;
                                    // }
                                    // ItemStack purifiers = new ItemStack(ModItems.SOUL_PURIFIER, totalPurifiers);
                                    // if (!player.getInventory().insertStack(purifiers)) {
                                    // player.dropItem(purifiers, false);
                                    // }
                                    // Utils.setCorruption(player, currentCorruption - absWithdraw);
                                    // player.sendMessage(Text.literal(
                                    // "Withdrew " + absWithdraw
                                    // + " negative corruption level(s) and received "
                                    // + totalPurifiers + " soul purifier(s)."),
                                    // false);
                                    // return 1;
                                    // } else {
                                    // player.sendMessage(Text.literal("Invalid amount to withdraw."), false);
                                    // return 0;
                                    // }
                                }

                                double currentHealth = player.getHealth();
                                double maxHealth = player.getAttributeInstance(EntityAttributes.MAX_HEALTH)
                                        .getBaseValue();

                                if (amntToWithdraw < 1 || maxHealth - (amntToWithdraw * 2) < 1) {
                                    player.sendMessage(Text.literal("Invalid amount of hearts to withdraw."), false);
                                    return 0;
                                }
                                double totalHpToWithdraw = amntToWithdraw * 2;

                                // Calculate regular and artificial hearts
                                int currentHearts = (int) (maxHealth / 2);
                                int minHearts = 7;
                                int regularHearts = Math.max(0, currentHearts - minHearts);
                                int regularToGive = Math.min(amntToWithdraw, regularHearts);
                                int artificialToGive = amntToWithdraw - regularToGive;

                                // Reduce player's max health
                                player.getAttributeInstance(EntityAttributes.MAX_HEALTH)
                                        .setBaseValue(maxHealth - totalHpToWithdraw);
                                player.setHealth((float) Math.min(currentHealth, maxHealth - totalHpToWithdraw));

                                // Give the player hearts
                                if (regularToGive > 0) {
                                    ItemStack heartShard = new ItemStack(ModItems.HEART, regularToGive);
                                    if (!player.getInventory().insertStack(heartShard)) {
                                        player.dropItem(heartShard, false);
                                    }
                                }
                                if (artificialToGive > 0) {
                                    ItemStack artificialHeart = new ItemStack(ModItems.ARTIFICIAL_HEART,
                                            artificialToGive);
                                    if (!player.getInventory().insertStack(artificialHeart)) {
                                        player.dropItem(artificialHeart, false);
                                    }
                                }
                                player.sendMessage(Text.literal("Withdrew " + amntToWithdraw + " hearts (" +
                                        (regularToGive > 0 ? regularToGive + " regular" : "") +
                                        (regularToGive > 0 && artificialToGive > 0 ? ", " : "") +
                                        (artificialToGive > 0 ? artificialToGive + " artificial" : "") +
                                        ")."), false);

                                if (Utils.inventoryHas(player, ModItems.ASCENSION_TOTEM) != null) {
                                    player.sendMessage(Text.literal(
                                            "If you're about to exploit those hearts, just know that this is bannable. You're not slick with this dawg"),
                                            false);
                                    System.out.println("Suspicious activity detected: "
                                            + player.getName().getString()
                                            + " withdrew hearts while having an ascension totem.");
                                }
                                return 1;
                            })));
            // Debug command to set ascendance state
            dispatcher.register(CommandManager.literal("setAscendance")
                    .requires(source -> source.hasPermissionLevel(4))
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                            .then(CommandManager.argument("state", IntegerArgumentType.integer(0, 1))
                                    .executes(ctx -> {
                                        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(ctx, "target");
                                        boolean newState = IntegerArgumentType.getInteger(ctx, "state") == 1;
                                        if (Utils.getAscended(targetPlayer) == newState) {
                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                    targetPlayer.getName().getString() + " is already "
                                                            + (newState ? "immortal" : "mortal") + "."),
                                                    false);
                                            return 0;
                                        }
                                        Utils.setAscended(targetPlayer, newState);
                                        // Reset spell bindings
                                        for (int slot = 0; slot < 9; slot++) {
                                            SpellRegistry bound = SpellRegistry.getBound(targetPlayer, slot);
                                            if (bound != null) {
                                                SpellRegistry.unbind(targetPlayer, bound);
                                            }
                                        }

                                        String message = newState
                                                ? targetPlayer.getName().getString() + " is now immortal (+0)."
                                                : targetPlayer.getName().getString() + " is now mortal (10 hearts).";
                                        ctx.getSource().sendFeedback(() -> Text.literal(message), false);

                                        return 1;
                                    }))));
            dispatcher.register(CommandManager.literal("augment")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        ItemStack mainHand = player.getMainHandStack();

                        // Search for augmentation core in inventory

                        Integer slotWithCore = Utils.inventoryHas(player, ModItems.AUGMENTATION_CORE);

                        if (slotWithCore == null) {
                            player.sendMessage(Text.literal("You need an Augmentation Core to use this command."),
                                    false);
                            return 0;
                        }

                        if (mainHand.isEmpty()) {
                            player.sendMessage(Text.literal("You must be holding an item to augment."), false);
                            return 0;
                        }

                        if (mainHand.getCount() != 1) {
                            player.sendMessage(Text.literal("You can only augment an item stack of size 1."), false);
                            return 0;
                        }

                        if (Utils.hasAttribute(mainHand, "immortals:augmented")) {
                            player.sendMessage(Text.literal("This item has already been augmented."), true);
                            return 0; // Or early exit from the method
                        }
                        // Remove one augmentation core
                        if (slotWithCore == player.getInventory().main.size()) {
                            player.getInventory().offHand.get(0).decrement(1);
                        } else {
                            player.getInventory().getStack(slotWithCore).decrement(1);
                        }
                        for (java.util.AbstractMap.SimpleEntry<RegistryEntry<EntityAttribute>, Float> entry : Augmentation
                                .rollAttributes(mainHand.getItem())) {
                            Utils.addModifier(
                                    mainHand,
                                    "immortals:augmented",
                                    AttributeModifierSlot.ANY,
                                    entry.getKey(),
                                    entry.getValue(),
                                    EntityAttributeModifier.Operation.ADD_VALUE);
                        }
                        // Identify who augmented the item
                        mainHand.set(ModComponents.OWNER_COMPONENT, player.getUuid().toString());
                        Utils.grant(player, "augmenter");
                        player.sendMessage(Text.literal("§aAugmented!"), false);
                        return 1;
                    }));
        });
    }
}