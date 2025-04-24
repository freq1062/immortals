package com.immortals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Iterator;

import com.immortals.item.ModItems;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

public class ModEvents {

    private static final Map<UUID, Integer> lastSlot = new ConcurrentHashMap<>();
    public static final Map<UUID, Map<Spell, Integer>> pendingCooldownNotifications = new ConcurrentHashMap<>();

    public static void register() {
        // Initialize the scoreboard objectives for ascension and corruption
        ServerTickEvents.START_SERVER_TICK.register((MinecraftServer server) -> {
            Scoreboard sb = server.getScoreboard();
            // Ascended: 0 or null = not ascended, 1 = ascended
            if (sb.getNullableObjective("hasAscended") == null) {
                sb.addObjective(
                        "hasAscended",
                        ScoreboardCriterion.DUMMY,
                        (Text) Text.literal("Ascended"),
                        ScoreboardCriterion.RenderType.INTEGER,
                        false,
                        null);
            }
            // Corruption Level: -3 to +3 or null
            if (sb.getNullableObjective("corruptionLevel") == null) {
                sb.addObjective(
                        "corruptionLevel",
                        ScoreboardCriterion.DUMMY,
                        (Text) Text.literal("Corruption"),
                        ScoreboardCriterion.RenderType.INTEGER,
                        false,
                        null);
            }
        });

        // Default bindings for dash & glow
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            // slot indices are 0-8 for hotbar 1-9
            Spell.bind(player, 0, Spell.DASH);
            Spell.bind(player, 1, Spell.GLOW);
        });

        // Register bind command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AscensionUtils.registerCommands(dispatcher);
        });

        // Send corruption message on respawn
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (AscensionUtils.getAscended(newPlayer) == 1) {
                AscensionUtils.addCorruption(newPlayer, -1);
                int lvl = AscensionUtils.getCorruption(newPlayer);
                int next = AscensionUtils.nextShardCost(lvl);
                AscensionUtils.applyCorruptionEffects(newPlayer);
                newPlayer.sendMessage(
                        Text.literal("§5You feel weakened. Corruption: §l" + lvl + "§r. Next: " + next),
                        true);
            }
        });

        // On player death: decrement corruption, drop one soul shard if the killer has
        // ascended or the victim was ascended
        ServerLivingEntityEvents.AFTER_DEATH.register((ent, src) -> {
            if (!ent.getWorld().isClient() && ent instanceof ServerPlayerEntity victim) {
                Entity k = src.getAttacker();

                boolean victimAscended = AscensionUtils.getAscended(victim) == 1;
                boolean killerAscended = k instanceof ServerPlayerEntity killer
                        && AscensionUtils.getAscended(killer) != 0;

                if (victimAscended || killerAscended) {
                    victim.dropItem(new ItemStack(ModItems.SOUL_SHARD, 1), false);
                    if (AscensionUtils.getCorruption(victim) <= -3) {
                        // banned ):
                        String playerName = victim.getNameForScoreboard();
                        String reason = "You have lost all your corruption levels!";
                        String command = String.format("tempban %s 24h %s", playerName, reason);
                        MinecraftServer server = victim.getServer();
                        server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
                    }
                }

                if (k instanceof ServerPlayerEntity killer
                        && AscensionUtils.getCorruption(killer) >= 3) {
                    // heal 6.0f = 3 hearts
                    killer.heal(6.0f);
                    killer.sendMessage(Text.literal("§aYou are empowered on kill... (+3 hearts)"), true);
                }
            }
        });

        // Item use events
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            ItemStack stack = player.getStackInHand(hand);

            // Ascension Totem: Update objective hasAscended
            if (stack.getItem() == ModItems.ASCENSION_TOTEM) {
                Scoreboard sb = player.getWorld().getScoreboard();
                ScoreboardObjective obj = sb.getNullableObjective("hasAscended");

                if (AscensionUtils.getAscended((ServerPlayerEntity) player) != 1) {
                    player.sendMessage(Text.literal("You feel a surge of divine power!"), true);
                    sb.getOrCreateScore(player, obj).setScore(1);

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

                    stack.decrement(1);
                    return ActionResult.SUCCESS;
                } else {
                    player.sendMessage(Text.literal("You have already ascended."), true);
                    return ActionResult.FAIL;
                }
            }

            // Soul Shard: Update corruption level -3 up to +3
            if (stack.getItem() == ModItems.SOUL_SHARD) {
                int ascended = AscensionUtils.getAscended((ServerPlayerEntity) player);
                int corruption = AscensionUtils.getCorruption((ServerPlayerEntity) player);
                // Player has not ascended
                if (ascended != 1) {
                    player.sendMessage(Text.literal("You must ascend to grow stronger..."), true);
                    return ActionResult.FAIL;
                }
                // Player can increase corruption
                if (corruption < 3) {
                    int cost = AscensionUtils.nextShardCost(corruption);

                    if (stack.getCount() < cost) {
                        player.sendMessage(Text.literal("Require " + cost + " Soul Shards to grow stronger."), true);
                        return ActionResult.FAIL;
                    }

                    stack.decrement(cost);
                    AscensionUtils.addCorruption((ServerPlayerEntity) player, 1);
                    int lvl = AscensionUtils.getCorruption((ServerPlayerEntity) player);
                    int next = AscensionUtils.nextShardCost(lvl);
                    AscensionUtils.applyCorruptionEffects((ServerPlayerEntity) player);
                    player.sendMessage(Text.literal("§5You feel stronger. Corruption: §l" + lvl + "§r. Next: " + next),
                            true);
                    return ActionResult.SUCCESS;
                }
                // Player has max corruption
                if (corruption >= 3) {
                    player.sendMessage(Text.literal("Your soul is already at its peak."), true);
                    return ActionResult.FAIL;
                }
            }

            // Soul Purifier: Update corruption level +1 up to 0
            if (stack.getItem() == ModItems.SOUL_PURIFIER) {
                if (AscensionUtils.getCorruption((ServerPlayerEntity) player) < 0) {
                    AscensionUtils.addCorruption((ServerPlayerEntity) player, 1);
                    int lvl = AscensionUtils.getCorruption((ServerPlayerEntity) player);
                    int next = AscensionUtils.nextShardCost(lvl);
                    stack.decrement(1);
                    AscensionUtils.applyCorruptionEffects((ServerPlayerEntity) player);
                    player.sendMessage(Text.literal("§5You feel stronger. Corruption: §l" + lvl + "§r. Next: " + next),
                            true);
                    return ActionResult.SUCCESS;
                }
                player.sendMessage(Text.literal("Soul purifier cannot increase corruption beyond +0."), true);
                return ActionResult.FAIL;
            }

            if (player.isSneaking()) {
                return AscensionUtils.tryCastSpell((ServerPlayerEntity) player, (ServerWorld) world);
            }

            return ActionResult.PASS;
        });

        // Server tick events
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                int current = player.getInventory().selectedSlot;
                int previous = lastSlot.getOrDefault(id, -1);

                // Remove normal totems if ascended
                if (player.getInventory().contains(new ItemStack(ModItems.ASCENSION_TOTEM))) {
                    for (int i = 0; i < player.getInventory().size(); i++) {
                        ItemStack s = player.getInventory().getStack(i);
                        if (s.getItem() == Items.TOTEM_OF_UNDYING) {
                            player.getInventory().removeStack(i);
                        }
                    }
                }

                // Display spell name when switching hotbar slots
                if (current != previous) {
                    lastSlot.put(id, current);

                    Spell bound = Spell.getBound(player, current);
                    if (bound != null) {
                        int corr = AscensionUtils.getCorruption(player);
                        // only show if they meet the level requirement
                        boolean allowed = switch (bound) {
                            case DASH -> corr >= 2;
                            case GLOW -> corr >= 3;
                            default -> false; // future spells get gated here
                        };
                        if (allowed) {
                            player.sendMessage(Text.literal("§e" + bound.getDisplayName()), true);
                        }
                    }
                }

                if (AscensionUtils.getAscended(player) == 1
                        && AscensionUtils.getCorruption(player) >= 3
                        && player.getHealth() < 6.0f
                        && !player.hasStatusEffect(StatusEffects.RESISTANCE)) {
                    player.sendMessage(Text.literal("§aYour will strengthens... (+Resistance I)"), true);
                    player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.RESISTANCE,
                            40, // lasts 2 seconds, refreshed each tick
                            0,
                            false, false));
                }
            }
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0)
                return; // once per second

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                Map<Spell, Integer> map = pendingCooldownNotifications.get(id);
                if (map == null)
                    continue;

                // figure out which spell is bound right now
                int currentSlot = player.getInventory().selectedSlot;
                Spell boundNow = Spell.getBound(player, currentSlot);

                Iterator<Map.Entry<Spell, Integer>> it = map.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Spell, Integer> entry = it.next();
                    Spell spell = entry.getKey();
                    int secs = entry.getValue();

                    if (secs > 0) {
                        // only show countdown if this is the bound slot
                        if (spell.equals(boundNow)) {
                            player.sendMessage(
                                    Text.literal("§c" + spell.getDisplayName() + ": " + secs + "s"),
                                    true);
                        }
                        // decrement for next tick
                        entry.setValue(secs - 1);
                    } else {
                        // always announce ready, no matter your slot
                        player.sendMessage(
                                Text.literal("§a" + spell.getDisplayName() + " ready!"),
                                true);
                        it.remove();
                    }
                }

                if (map.isEmpty()) {
                    pendingCooldownNotifications.remove(id);
                }
            }
        });

    }
}