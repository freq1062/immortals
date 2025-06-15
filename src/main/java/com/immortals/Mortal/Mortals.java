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

        // Mortals' lifesteal
        ServerLivingEntityEvents.AFTER_DEATH.register((ent, src) -> {
            if (!ent.getWorld().isClient() && ent instanceof ServerPlayerEntity victim) {
                Entity attacker = src.getAttacker();
                boolean victimImmortal = Utils.getAscended(victim);
                boolean attackerImmortal = attacker instanceof ServerPlayerEntity k
                        && Utils.getAscended(k);

                // Mortal killed by mortal or natural causes
                if (!attackerImmortal) {
                    // If victim is Immortal, scale extra hearts based on corruption
                    if (victimImmortal) {
                        int corr = Utils.getCorruption(victim);
                        System.out.println("Victim Corruption: " + corr);
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
                        double numHearts = player.getAttributeBaseValue(EntityAttributes.MAX_HEALTH) / 2;
                        for (java.util.AbstractMap.SimpleEntry<RegistryEntry<EntityAttribute>, Float> entry : Augmentation
                                .rollAttributes(numHearts, mainHand.getItem())) {
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