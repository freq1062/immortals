package com.immortals.SupplyDrop;

import com.immortals.Main;
import com.immortals.Utils;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.loot.LootTable;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
import java.util.ArrayList;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

public class SupplyDropEvents {
    private static SupplyDropState state;
    private static boolean drop = false;
    private static final Map<BlockPos, SupplyDrop> drops = new ConcurrentHashMap<>();
    private static int supplyDropRadius = (Integer) Main.CONFIG.get("supplyDropRadius");
    private static long supplyDropIntervalMs = (Long) Main.CONFIG.get("supplyDropIntervalMs");
    private static long supplyDropUnlockTime = (Integer) Main.CONFIG.get("supplyDropUnlockTime");
    private static long supplyDropResendInterval = (Long) Main.CONFIG.get("supplyDropResendInterval");

    private static class SupplyDrop {
        final BlockPos pos;
        boolean unlocked;
        long unlockAt;
        ServerBossBar bar;

        SupplyDrop(BlockPos pos) {
            this.pos = pos;
            this.unlocked = false;
            this.unlockAt = 0;
            this.bar = new ServerBossBar(
                    Text.literal("Supply Drop at [" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "]"),
                    BossBar.Color.WHITE,
                    BossBar.Style.PROGRESS);
        }
    }

    // Spawns the supply drop chest and broadcasts its location
    private static int spawnSupplyAt(ServerWorld world, BlockPos pos)
            throws CommandSyntaxException {
        world.setBlockState(pos, Blocks.CHEST.getDefaultState());
        SupplyDropEvents.drops.put(pos, new SupplyDropEvents.SupplyDrop(pos));
        System.out.println("Spawning supply drop at " + pos);
        world.getServer().getCommandManager().executeWithPrefix(
                world.getServer().getCommandSource().withLevel(4),
                "say §6Supply Drop Incoming at x=" + pos.getX() + ", y=" + pos.getY() + ", z=" + pos.getZ() + "!");
        Utils.sendDiscordWebhook(
                "Supply Drop Incoming at x=" + pos.getX() + ", y=" + pos.getY() + ", z=" + pos.getZ() + "!");
        return 1;
    }

    public static void register() {

        CommandRegistrationCallback.EVENT.register((dispatcher, environment, registryAccess) -> {
            // spawnsupply <x> <y> <z>: Spawns a locked supply drop at the given coordinates
            dispatcher.register(
                    CommandManager.literal("spawnsupply")
                            .requires(source -> source.hasPermissionLevel(4))
                            // no-arg version: spawns at the player
                            .executes(ctx -> {
                                ServerPlayerEntity p = ctx.getSource().getPlayer();
                                ServerWorld world = (ServerWorld) p.getWorld();
                                spawnSupplyAt(world, p.getBlockPos());
                                return 1;
                            })
                            // one blockPos argument, supports "~ ~ ~", "^ ^ ^", and absolutes
                            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                    .executes(
                                            ctx -> {
                                                ServerPlayerEntity p = ctx.getSource().getPlayer();
                                                ServerWorld world = (ServerWorld) p.getWorld();
                                                BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
                                                spawnSupplyAt(world, pos);
                                                ctx.getSource().sendFeedback(
                                                        () -> Text.literal("Spawned locked supply drop at " + pos),
                                                        false);
                                                return 1;
                                            })));
            // supplydrop <start|stop|next|reset>: Defaults to stop on server start
            dispatcher.register(
                    CommandManager.literal("supplydrop")
                            .requires(source -> source.hasPermissionLevel(4))
                            .then(CommandManager.literal("reset")
                                    .executes(ctx -> {
                                        if (state == null) {
                                            ctx.getSource().sendFeedback(
                                                    () -> Text.literal("Supply Drop state not initialized."), false);
                                            return 1;
                                        }
                                        state.lastSpawnTime = System.currentTimeMillis() - supplyDropIntervalMs;
                                        state.markDirty();
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal("Supply Drop timer reset!"), false);
                                        return 1;
                                    }))
                            .then(CommandManager.literal("start")
                                    .executes(ctx -> {
                                        drop = true;
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal("Supply Drop timer started!"), false);
                                        return 1;
                                    }))
                            .then(CommandManager.literal("stop")
                                    .executes(ctx -> {
                                        drop = false;
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal("Supply Drop timer stopped!"), false);
                                        return 1;
                                    }))
                            .then(CommandManager.literal("next")
                                    .executes(ctx -> {
                                        long now = System.currentTimeMillis();
                                        long nextTime;
                                        if (!drop) {
                                            ctx.getSource().sendFeedback(
                                                    () -> Text.literal("Supply Drop timer is not running."), false);
                                            return 1;
                                        }
                                        if (state == null) {
                                            ctx.getSource().sendFeedback(
                                                    () -> Text.literal("Supply Drop state not initialized."), false);
                                            return 1;
                                        }
                                        nextTime = state.lastSpawnTime + supplyDropIntervalMs;
                                        if (nextTime < now)
                                            nextTime = now;
                                        java.time.Instant instant = java.time.Instant.ofEpochMilli(nextTime);
                                        java.time.ZonedDateTime dateTime = java.time.ZonedDateTime.ofInstant(
                                                instant, java.time.ZoneId.systemDefault());
                                        String formatted = dateTime.toString().replace('T', ' ').substring(0, 19);
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal("Next Supply Drop: " + formatted), false);
                                        return 1;
                                    })));
        });

        // Disallow players from just breaking the supply drop chest
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            SupplyDrop sd = drops.get(pos);
            if (sd != null && !sd.unlocked) {
                player.sendMessage(Text.literal("You decided not to break the supply."), true);
                return false;
            }
            return true;
        });

        // Unlocking logic
        UseBlockCallback.EVENT.register((player, worldIn, hand, hit) -> {
            if (!(worldIn instanceof ServerWorld world))
                return ActionResult.PASS;
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            SupplyDrop sd = drops.get(pos);
            if (sd == null || sd.unlocked)
                return ActionResult.PASS;

            // lock still in progress
            if (sd.unlockAt == 0) {
                sd.unlockAt = System.currentTimeMillis() + supplyDropUnlockTime;
                sd.bar.setPercent(0f);
                // Broadcast the unlocking message
                world.getServer().getCommandManager().executeWithPrefix(
                        world.getServer().getCommandSource().withLevel(4),
                        "say §c" + player.getName().getString() + " is unlocking the supply drop at " +
                                "§l" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "§r!");
                Utils.sendDiscordWebhook(
                        player.getName().getString() + " is unlocking the supply drop at "
                                + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "!");
            }
            // Always update bossbar players within 200 blocks
            new ArrayList<>(sd.bar.getPlayers()).forEach(sd.bar::removePlayer);
            world.getPlayers().forEach(pl -> {
                if (pl.squaredDistanceTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) < 200 * 200)
                    sd.bar.addPlayer(pl);
            });
            player.sendMessage(Text.literal("Supply Drop is being unlocked."), true);
            return ActionResult.FAIL;
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {

            // Spawn indicator particles
            if (server.getTicks() % 40 == 0) {
                for (Map.Entry<BlockPos, SupplyDrop> entry : drops.entrySet()) {
                    BlockPos pos = entry.getKey();
                    ServerWorld world = server.getWorld(ServerWorld.OVERWORLD);
                    if (world != null) {
                        for (int y = 1; y <= 20; y++) {
                            world.spawnParticles(
                                    ParticleTypes.GLOW,
                                    pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5,
                                    5, 0.1, 0.0, 0.1, 0.0);
                        }
                    }
                }
            }

            // Resend location if not unlocked, supplyDropResendInterval is in ms
            if (server.getTicks() % Math.max(1, supplyDropResendInterval / 50) == 0) {
                for (Map.Entry<BlockPos, SupplyDrop> entry : drops.entrySet()) {
                    SupplyDrop sd = entry.getValue();
                    if (!sd.unlocked && sd.unlockAt == 0) {
                        ServerWorld world = server.getWorld(ServerWorld.OVERWORLD);
                        if (world != null) {
                            world.getServer().getCommandManager().executeWithPrefix(
                                    world.getServer().getCommandSource().withLevel(4),
                                    "say §6Supply Drop at x=" + sd.pos.getX() + ", y=" + sd.pos.getY() +
                                            ", z=" + sd.pos.getZ() + " is still locked!");
                        }
                    }
                }
            }

            // Timer logic
            ServerWorld overworld = server.getWorld(ServerWorld.OVERWORLD);
            if (state == null) {
                PersistentStateManager stateManager = overworld.getPersistentStateManager();
                state = stateManager.getOrCreate(SupplyDropState.TYPE, "immortals_supplydrop");
            }

            long now = System.currentTimeMillis();

            if (drop && now - state.lastSpawnTime >= supplyDropIntervalMs) {
                int needed = 3 - (int) drops.values().stream().filter(sd -> !sd.unlocked).count();

                if (needed > 0) {
                    Random rnd = new Random();
                    int x = rnd.nextInt(supplyDropRadius * 2 + 1) - supplyDropRadius;
                    int z = rnd.nextInt(supplyDropRadius * 2 + 1) - supplyDropRadius;
                    int y = overworld.getTopY(
                            Heightmap.Type.WORLD_SURFACE, x, z);
                    BlockPos pos = new BlockPos(x, y, z);

                    int attempts = 0;
                    boolean foundValid = false;
                    while (attempts < 10) {
                        // Check if the block is water, skip if it is
                        if (!overworld.getBlockState(pos).isOf(Blocks.WATER)) {
                            foundValid = true;
                            break;
                        }
                        // Retry with a new random position
                        x = rnd.nextInt(supplyDropRadius * 2 + 1) - supplyDropRadius;
                        z = rnd.nextInt(supplyDropRadius * 2 + 1) - supplyDropRadius;
                        y = overworld.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                        attempts++;
                    }
                    // If no valid position found after 10 attempts, pick one more and keep it
                    if (!foundValid) {
                        x = rnd.nextInt(supplyDropRadius * 2 + 1) - supplyDropRadius;
                        z = rnd.nextInt(supplyDropRadius * 2 + 1) - supplyDropRadius;
                        y = overworld.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                    }
                    // Move it to sea level if at bottom of world (Unloaded chunk)
                    if (y <= overworld.getBottomY()) {
                        y = overworld.getSeaLevel();
                    }
                    pos = new BlockPos(x, y, z);
                    System.out.println("Found valid at " + pos);
                    // Spawn the supply drop
                    try {
                        spawnSupplyAt(overworld, pos);
                    } catch (CommandSyntaxException e) {
                        e.printStackTrace();
                    }
                }
                state.lastSpawnTime = now;
                state.markDirty();
            }

            // Update each drop’s unlock bar & handle completion
            for (SupplyDrop sd : drops.values()) {
                if (sd.unlocked || sd.unlockAt == 0)
                    continue;
                long remaining = sd.unlockAt - now;
                if (remaining > 0) {
                    float pct = (float) (supplyDropUnlockTime - remaining) / supplyDropUnlockTime;
                    sd.bar.setPercent(pct);
                } else {
                    sd.unlocked = true;
                    // Spawn a new chest in case it was broken
                    overworld.setBlockState(sd.pos, Blocks.CHEST.getDefaultState());
                    if (overworld.getBlockEntity(sd.pos) instanceof ChestBlockEntity chestBE) {
                        // Loot table
                        RegistryKey<LootTable> lootTable = RegistryKey.of(
                                RegistryKeys.LOOT_TABLE,
                                Identifier.of("immortals", "chests/end_event"));
                        chestBE.setLootTable(lootTable, sd.pos.asLong());
                        // chestBE.setLootTable(LootTables.ANCIENT_CITY_CHEST, sd.pos.asLong());
                    }
                    sd.bar.setPercent(1f);
                    sd.bar.setName(Text.literal("Supply Drop Unlocked!"));
                    // Broadcast the unlocking message
                    overworld.getServer().getCommandManager().executeWithPrefix(
                            overworld.getServer().getCommandSource().withLevel(4),
                            "say §aSupply Drop at " + sd.pos + " has been opened!");
                    Utils.sendDiscordWebhook(
                            String.format(
                                    "Supply Drop at x=%d, y=%d, z=%d has been opened!",
                                    sd.pos.getX(), sd.pos.getY(), sd.pos.getZ()));
                    // Remove bossbar
                    new ArrayList<>(sd.bar.getPlayers()).forEach(sd.bar::removePlayer);
                }
            }
        });
    }
}