package com.immortals.Immortal;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.immortals.Utils;
import com.immortals.item.ModItems;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class Spell {
    private static final Map<UUID, Integer> lastSlot = new ConcurrentHashMap<>();
    public static final Map<UUID, Map<SpellRegistry, Integer>> pendingCooldownNotifications = new ConcurrentHashMap<>();
    public static final Map<UUID, Queue<Task>> taskQueue = new ConcurrentHashMap<>();

    private static class Task {
        private final Runnable callback;
        private long remainingTime;

        public Task(Runnable callback, long remainingTime) {
            this.callback = callback;
            this.remainingTime = remainingTime;
        }

        public void execute() {
            callback.run();
        }

        public long getRemainingTime() {
            return remainingTime;
        }

        public void decrementTime(long delta) {
            this.remainingTime -= delta;
        }
    }

    public static void addTask(UUID playerId, Runnable callback, long timeInMs) {
        taskQueue
                .computeIfAbsent(playerId, id -> new ConcurrentLinkedQueue<>())
                .add(new Task(callback, timeInMs));
    }

    public static void processTasks(long deltaTime) {
        for (Map.Entry<UUID, Queue<Task>> entry : taskQueue.entrySet()) {
            Queue<Task> queue = entry.getValue();
            while (!queue.isEmpty() && queue.peek().getRemainingTime() <= deltaTime) {
                Task task = queue.poll();
                if (task != null) {
                    task.execute();
                }
            }
            if (!queue.isEmpty()) {
                queue.peek().decrementTime(deltaTime);
            }
        }
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Register bind command
            dispatcher.register(CommandManager.literal("bind")
                    .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 9))
                            .then(CommandManager.argument("spell", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        int corr = Utils.getCorruption(player);
                                        List<String> suggestions = new ArrayList<>();
                                        if (corr >= 1) {
                                            suggestions.add("dash");
                                            suggestions.add("glow");
                                        }
                                        if (corr >= 2) {
                                            suggestions.add("backdraft");
                                            suggestions.add("blackout");
                                        }
                                        if (corr >= 3) {
                                            suggestions.add("persist");
                                            suggestions.add("splinter_blow");
                                        }
                                        if (Utils.inventoryHas(player, Items.DRAGON_EGG) != null) {
                                            suggestions.add("dragon_ascent");
                                        }
                                        if (Utils.inventoryHas(player, ModItems.TIMEKEEPER) != null) {
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
                                        int corr = Utils.getCorruption(player);

                                        if (spell == null) {
                                            player.sendMessage(Text.literal("§cUnknown spell: " + spellId), false);
                                            return 0;
                                        }
                                        if (!Utils.getAscended(player)) {
                                            player.sendMessage(Text.literal("§cYou must be ascended to bind spells!"),
                                                    true);
                                            return 0;
                                        }
                                        // Special case for dragon_ascent and timeslow
                                        if ("dragon_ascent".equals(spell.getId()) || "timeslow".equals(spell.getId())) {
                                            SpellRegistry.bind(player, slot, spell);
                                            player.sendMessage(Text.literal("§aSpell bound to slot " + (slot + 1)),
                                                    true);
                                            return 1;
                                        }
                                        // Check corruption requirements
                                        int requiredCorr = 0;
                                        if (spell == SpellRegistry.DASH || spell == SpellRegistry.GLOW) {
                                            requiredCorr = 1;
                                        } else if (spell == SpellRegistry.BLACKOUT
                                                || spell == SpellRegistry.BACKDRAFT) {
                                            requiredCorr = 2;
                                        } else if (spell == SpellRegistry.PERSIST
                                                || spell == SpellRegistry.SPLINTER_BLOW) {
                                            requiredCorr = 3;
                                        }
                                        if (requiredCorr > 0 && corr < requiredCorr) {
                                            player.sendMessage(
                                                    Text.literal("§cYou need at least " + requiredCorr
                                                            + " corruption to bind this spell."),
                                                    true);
                                            return 0;
                                        }

                                        SpellRegistry bound = SpellRegistry.getBound(player, slot);
                                        // Refuse if the player doesn't have enough slots and is not replacing a
                                        // non-free spell (dragon ascent and timeslow)
                                        if (corr <= SpellRegistry.getNumBound(player)
                                                && (bound == null
                                                        || "dragon_ascent".equals(bound.getId())
                                                        || "timeslow".equals(bound.getId()))) {
                                            player.sendMessage(
                                                    Text.literal("§cYou have " + corr
                                                            + " available spell slots. Run /unbind [spell] to free up a slot!"),
                                                    true);
                                            return 0;
                                        }

                                        SpellRegistry.bind(player, slot, spell);
                                        player.sendMessage(Text.literal("§aSpell bound to slot " + (slot + 1)),
                                                true);
                                        return 1;
                                    }))));

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
                                        player.sendMessage(Text.literal("§aAll spells unbound."), true);
                                        return 1;
                                    } else {
                                        player.sendMessage(Text.literal("§eNo spells to unbind."), true);
                                        return 0;
                                    }
                                }

                                SpellRegistry spell = SpellRegistry.fromId(spellId);

                                if (spell == null) {
                                    player.sendMessage(Text.literal("Unknown spell: " + spellId), false);
                                    return 0;
                                }

                                SpellRegistry.unbind(player, spell);
                                player.sendMessage(Text.literal("§aSpell unbound: " + spell.getDisplayName()),
                                        true);
                                return 1;
                            })));
        });

        // Display spell name when switching hotbar slots
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                int current = player.getInventory().selectedSlot;
                int previous = lastSlot.getOrDefault(id, -1);

                // Display spell name when switching hotbar slots
                if (current != previous) {
                    lastSlot.put(id, current);

                    SpellRegistry bound = SpellRegistry.getBound(player, current);
                    if (bound != null && SpellRegistry.canUse(player, bound) && Utils.getAscended(player)) {
                        player.sendMessage(Text.literal("§e" + bound.getDisplayName()), true);
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
                    SpellRegistry currentSpell = SpellRegistry.getBound(player, player.getInventory().selectedSlot);

                    while (cooldownIterator.hasNext()) {
                        Map.Entry<SpellRegistry, Integer> entry = cooldownIterator.next();
                        SpellRegistry spell = entry.getKey();
                        int secondsLeft = entry.getValue();

                        if (secondsLeft > 0) {
                            if (spell.equals(currentSpell)) {
                                player.sendMessage(
                                        Text.literal("§c" + spell.getDisplayName() + ": " + secondsLeft + "s"), true);
                            }
                            entry.setValue(secondsLeft - 1);
                        } else {
                            if (!spell.getId().equals("splinter_blow")) {
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

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            long delta = 50; // 1 tick ≈ 50ms
            for (UUID playerId : taskQueue.keySet()) {
                Queue<Task> queue = taskQueue.get(playerId);
                if (queue == null)
                    continue;

                List<Task> stillWaiting = new ArrayList<>();
                for (Task t : queue) {
                    t.decrementTime(delta);
                    if (t.getRemainingTime() <= 0) {
                        t.execute();
                    } else {
                        stillWaiting.add(t);
                    }
                }

                // replace the queue contents atomically
                queue.clear();
                queue.addAll(stillWaiting);
            }
        });
    }
}