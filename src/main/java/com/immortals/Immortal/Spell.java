package com.immortals.Immortal;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import com.immortals.Utils;
import com.immortals.ModItems;
import com.immortals.Main;
import com.immortals.api.ImmortalsData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.command.argument.EntityArgumentType;

public class Spell {
    private static final Map<UUID, Integer> lastSlot = new ConcurrentHashMap<>();
    public static final Map<UUID, Map<SpellRegistry, Integer>> pendingCooldownNotifications = new ConcurrentHashMap<>();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Register bind command
            dispatcher.register(CommandManager.literal("bind")
                    .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 9))
                            .then(CommandManager.argument("spell", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        int corr = ((ImmortalsData) player).getCorruption();
                                        List<String> suggestions = new ArrayList<>();

                                        for (int i = 0; i < Utils.spellsByCorr.length; i++) {
                                            if (corr >= i + 1) {
                                                suggestions.addAll(List.of(Utils.spellsByCorr[i]));
                                            }
                                        }

                                        if (Utils.inventoryHas(player, Items.DRAGON_EGG) != -1) {
                                            suggestions.add("dragon_ascent");
                                        }
                                        if (Utils.inventoryHas(player, ModItems.TIMEKEEPER) != -1) {
                                            suggestions.add("timeslow");
                                        }
                                        for (String spellId : suggestions) {
                                            builder.suggest(spellId);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
                                        String spellId = StringArgumentType.getString(ctx, "spell");
                                        SpellRegistry spell = SpellRegistry.fromId(spellId);
                                        int corr = ((ImmortalsData) player).getCorruption();

                                        if (spell == null) {
                                            player.sendMessage(Text.literal("§cUnknown spell: " + spellId), false);
                                            return 0;
                                        }
                                        if (!((ImmortalsData) player).isImmortal()) {
                                            player.sendMessage(Text.literal("§bYou must be ascended to bind spells!"),
                                                    true);
                                            return 0;
                                        }
                                        // Special case for dragon_ascent and timeslow
                                        if ("dragon_ascent".equals(spell.getId()) || "timeslow".equals(spell.getId())) {
                                            SpellRegistry.bind(player, slot, spell);
                                            player.sendMessage(Text.literal("Spell bound to slot " + (slot + 1)),
                                                    true);
                                            return 1;
                                        }
                                        // Check corruption requirements
                                        int requiredCorr = Utils.getRequiredCorr(spell);
                                        if (requiredCorr > 0 && corr < requiredCorr) {
                                            player.sendMessage(
                                                    Text.literal("§cYou require at least " + requiredCorr
                                                            + " corruption to bind this spell."),
                                                    true);
                                            return 0;
                                        }

                                        SpellRegistry bound = SpellRegistry.getBound(player, slot);
                                        // Refuse if the player doesn't have enough slots and is not replacing a
                                        // non-free spell (dragon ascent and timeslow)
                                        int allowedSlots = Math.min(corr, Main.CONFIG.getInt("maxSpellSlots"));
                                        if (allowedSlots < SpellRegistry.getNumBound(player)
                                                && (bound == null
                                                        || "dragon_ascent".equals(bound.getId())
                                                        || "timeslow".equals(bound.getId()))) {
                                            player.sendMessage(
                                                    Text.literal("§cYou have " + allowedSlots
                                                            + " available spell slots. Run /unbind [spell] to free up a slot!"),
                                                    false);
                                            return 0;
                                        }

                                        SpellRegistry.bind(player, slot, spell);
                                        player.sendMessage(Text.literal("Spell bound to slot " + (slot + 1)),
                                                true);
                                        return 1;
                                    }))));

            // Register trust command
            dispatcher.register(CommandManager.literal("trust")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(ctx, "player");

                                if (player.equals(targetPlayer)) {
                                    player.sendMessage(Text.literal("§cYou can't trust yourself!"), true);
                                    return 0;
                                }

                                // Get player data
                                ImmortalsData playerData = Utils.getPlayerData(player);
                                UUID targetUuid = targetPlayer.getUuid();

                                // Check if already trusted
                                if (playerData.getTrusted().contains(targetUuid)) {
                                    player.sendMessage(
                                            Text.literal(
                                                    "§c" + targetPlayer.getName().getString() + " is already trusted."),
                                            true);
                                    return 0;
                                }

                                // Add to trusted list
                                playerData.addTrusted(targetUuid);
                                player.sendMessage(Text.literal("Trusted " + targetPlayer.getName().getString()),
                                        true);
                                return 1;
                            })));

            // Register trust list command
            dispatcher.register(CommandManager.literal("trust")
                    .then(CommandManager.literal("list")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();

                                // Get player data
                                ImmortalsData playerData = Utils.getPlayerData(player);
                                List<UUID> trusted = playerData.getTrusted();

                                if (trusted.isEmpty()) {
                                    player.sendMessage(Text.literal("You haven't trusted any players yet."), false);
                                } else {
                                    player.sendMessage(Text.literal("§cTrusted players:"), false);
                                    MinecraftServer server = ctx.getSource().getServer();

                                    for (UUID uuid : trusted) {
                                        String name = server.getUserCache().getByUuid(uuid)
                                                .map(profile -> profile.getName())
                                                .orElse("Unknown Player");
                                        player.sendMessage(Text.literal("- " + name), false);
                                    }
                                }

                                return 1;
                            })));

            // Register untrust command
            dispatcher.register(CommandManager.literal("untrust")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(ctx, "player");

                                if (player.equals(targetPlayer)) {
                                    player.sendMessage(Text.literal("§cYou can't untrust yourself!"), true);
                                    return 0;
                                }

                                // Get player data
                                ImmortalsData playerData = Utils.getPlayerData(player);
                                UUID targetUuid = targetPlayer.getUuid();

                                // Check if trusted
                                if (!playerData.getTrusted().contains(targetUuid)) {
                                    player.sendMessage(
                                            Text.literal(
                                                    "§c" + targetPlayer.getName().getString() + " is not trusted."),
                                            true);
                                    return 0;
                                }

                                // Remove from trusted list
                                playerData.removeTrusted(targetUuid);
                                player.sendMessage(Text.literal("Untrusted " + targetPlayer.getName().getString()),
                                        true);
                                return 1;
                            })));

            dispatcher.register(CommandManager.literal("unbind")
                    // Register unbind command
                    .then(CommandManager.argument("spell", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                for (int slot = 0; slot < 9; slot++) {
                                    SpellRegistry bound = SpellRegistry.getBound(player, slot);
                                    if (bound != null) {
                                        builder.suggest(bound.getId());
                                    }
                                }
                                builder.suggest("all");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                String spellId = StringArgumentType.getString(ctx, "spell");

                                if ("all".equalsIgnoreCase(spellId)) {
                                    boolean anyUnbound = false;
                                    for (int slot = 0; slot < 9; slot++) {
                                        SpellRegistry bound = SpellRegistry.getBound(player, slot);
                                        if (bound != null) {
                                            SpellRegistry.unbind(player, bound);
                                            anyUnbound = true;
                                        }
                                    }
                                    if (anyUnbound) {
                                        player.sendMessage(Text.literal("All spells unbound."), true);
                                        return 1;
                                    } else {
                                        player.sendMessage(Text.literal("No spells to unbind."), true);
                                        return 0;
                                    }
                                }

                                SpellRegistry spell = SpellRegistry.fromId(spellId);

                                if (spell == null) {
                                    player.sendMessage(Text.literal("§cUnknown spell: " + spellId), false);
                                    return 0;
                                }

                                SpellRegistry.unbind(player, spell);
                                player.sendMessage(Text.literal("Spell unbound: " + spell.getDisplayName()),
                                        true);
                                return 1;
                            })));
        });

        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {

            if (server.getTicks() % 20 == 0) {
                Set<UUID> onlinePlayers = new HashSet<>();
                server.getPlayerManager().getPlayerList().forEach(player -> onlinePlayers.add(player.getUuid()));

                // Clear pending cooldown notifications and last slot for players who are not
                // online
                pendingCooldownNotifications.keySet().removeIf(playerId -> !onlinePlayers.contains(playerId));
                lastSlot.keySet().removeIf(playerId -> !onlinePlayers.contains(playerId));
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                int current = player.getInventory().getSelectedSlot();
                int previous = lastSlot.getOrDefault(id, -1);

                if (current != previous) {
                    lastSlot.put(id, current);
                    // Display spell name when switching hotbar slots
                    SpellRegistry bound = SpellRegistry.getBound(player, current);

                    if (bound != null && SpellRegistry.canUse(player, bound) && ((ImmortalsData) player).isImmortal()) {
                        Utils.sendSpellInfoToPlayer(player, bound.getId(), Utils.currentSpellState(player, bound),
                                Utils.currentCooldown(player, bound), bound.getCooldownTicks());
                        player.sendMessage(Text.literal("§6" + bound.getDisplayName()), true);
                    }
                }
            }
        });

        // Handle cooldown notifications
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0)
                return; // Run once per second

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID playerId = player.getUuid();
                Map<SpellRegistry, Integer> cooldowns = pendingCooldownNotifications.get(playerId);
                if (cooldowns != null) {
                    Iterator<Map.Entry<SpellRegistry, Integer>> cooldownIterator = cooldowns.entrySet().iterator();
                    SpellRegistry currentSpell = SpellRegistry.getBound(player,
                            player.getInventory().getSelectedSlot());

                    while (cooldownIterator.hasNext()) {
                        Map.Entry<SpellRegistry, Integer> entry = cooldownIterator.next();
                        SpellRegistry spell = entry.getKey();
                        int secondsLeft = entry.getValue();

                        if (secondsLeft > 0) {
                            entry.setValue(secondsLeft - 1);
                        } else {
                            if (secondsLeft != -1 && !spell.getId().equals("splinter_blow")
                                    && SpellRegistry.getSlot(player, spell) != -1) {
                                if (spell == currentSpell) {
                                    Utils.sendSpellInfoToPlayer(player, spell.getId(), "ready",
                                            0, spell.getCooldownTicks());
                                }
                                player.sendMessage(Text.literal("§a" + spell.getDisplayName() + " ready!"), true);
                                cooldownIterator.remove();
                            }
                        }
                    }

                    if (cooldowns.isEmpty()) {
                        pendingCooldownNotifications.remove(playerId);
                    }
                }
            }
        });
    }
}