package com.immortals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
// Removed incorrect import for EntityExperienceOrbEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import com.immortals.item.ModItems;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

public class ModEvents {

    private static final Map<UUID, Integer> lastSlot = new ConcurrentHashMap<>();
    public static final Map<UUID, Map<Spell, Integer>> pendingCooldownNotifications = new ConcurrentHashMap<>();

    // Represents an active rune effect
    private static class RuneInstance {
        Vec3d center;
        int ticksLeft;

        RuneInstance(Vec3d c, int t) {
            center = c;
            ticksLeft = t;
        }
    }

    // Active runes per player
    private static final Map<UUID, RuneInstance> ACTIVE_RUNES = new ConcurrentHashMap<>();

    // simple struct for pending lightning strikes
    private record LightningTask(ServerWorld world, Vec3d pos, int delay) {
    }

    private static final List<LightningTask> pendingLightning = new ArrayList<>();

    /** Called from the spell to schedule a 3-second rune at `pos` */
    public static void spawnPersistentRune(UUID playerId, Vec3d pos) {
        ACTIVE_RUNES.put(playerId, new RuneInstance(pos, 8));
    }

    // === General Pending Particles ===
    private record ParticleTask(DustParticleEffect effect, Vec3d pos, int ticksLeft, ServerWorld world) {
    }

    private static final List<ParticleTask> pendingParticles = new ArrayList<>();

    private static final Set<Integer> scaledOrbIds = ConcurrentHashMap.newKeySet();

    /**
     * Schedule a DustParticleEffect at pos for duration ticks (one spawn per tick)
     */
    public static void scheduleParticle(DustParticleEffect effect, Vec3d pos, int durationTicks, ServerWorld world) {
        pendingParticles.add(new ParticleTask(effect, pos, durationTicks, world));
    }

    /** Schedule a bolt at `pos` in `delay` ticks */
    public static void scheduleLightning(ServerWorld world, Vec3d pos, int delay) {
        pendingLightning.add(new LightningTask(world, pos, delay));
    }

    /** Draws one layer of circle + two rotated squares (exact same code you had) */
    private static void drawRune(Vec3d pos, World world) {
        double y = pos.y;
        // Circle
        int circlePts = 256; // Increase points for smoother circle
        double circleR = 5.0;
        for (int i = 0; i < circlePts; i++) {
            double ang = 2 * Math.PI * i / circlePts;
            double x = pos.x + Math.cos(ang) * circleR;
            double z = pos.z + Math.sin(ang) * circleR;
            if (world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.PORTAL, x, y, z, 1, 0, 0, 0, 0); // Set motion to 0 to prevent
                                                                                          // falling
            }
        }

        // Rotated squares
        double[] sqR = { 5.0, 4.0, 3.0 };
        double baseRotation = 30;
        for (int i = 0; i < sqR.length; i++) {
            double r = sqR[i];
            double rot = baseRotation + i * 45; // 0°, 45°, 90° etc.
            Vec3d[] corners = new Vec3d[4];
            for (int c = 0; c < 4; c++) {
                double ang = Math.toRadians(rot + 45 + 90 * c);
                corners[c] = pos.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            }
            int steps = 40; // Number of steps for line interpolation
            for (int c = 0; c < 4; c++) {
                Vec3d a = corners[c], b = corners[(c + 1) % 4];
                for (int s = 0; s <= steps; s++) {
                    double t = s / (double) steps;
                    Vec3d pt = a.lerp(b, t);
                    if (world instanceof ServerWorld serverWorld) {
                        serverWorld.spawnParticles(ParticleTypes.PORTAL, pt.x, y, pt.z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

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
            Spell.bind(player, 2, Spell.DRAGON_ASCENT);
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
            } else {
                EntityAttributeInstance old_hp = oldPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                EntityAttributeInstance new_hp = newPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                new_hp.setBaseValue(old_hp.getBaseValue() - 2.0);
                newPlayer.sendMessage(
                        Text.literal("§5You lost a heart."),
                        true);
            }
        });

        // On player death: decrement corruption, drop one soul shard if the killer has
        // ascended or the victim was ascended
        ServerLivingEntityEvents.AFTER_DEATH.register((ent, src) -> {
            if (!ent.getWorld().isClient() && ent instanceof ServerPlayerEntity victim) {
                Entity attacker = src.getAttacker();
                boolean victimImmortal = AscensionUtils.getAscended(victim) == 1;
                boolean attackerImmortal = attacker instanceof ServerPlayerEntity k
                        && AscensionUtils.getAscended(k) == 1;

                // ——— Mortals’ lifesteal ———
                if (!victimImmortal && ((attacker instanceof ServerPlayerEntity killer
                        && AscensionUtils.getAscended(killer) != 1)
                        || (attacker == null || !(attacker instanceof ServerPlayerEntity)))) {
                    // mortal killed by mortal or natural causes
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

                    // 2) drop a heart item at victim’s death spot
                    victim.getWorld().spawnEntity(new ItemEntity(
                            victim.getWorld(),
                            victim.getX(), victim.getY(), victim.getZ(),
                            new ItemStack(ModItems.HEART)));
                }

                // ——— Mortals kill Immortals: scaled hearts ———
                if (victimImmortal && attacker instanceof ServerPlayerEntity killer
                        && AscensionUtils.getAscended(killer) != 1) {
                    int corr = AscensionUtils.getCorruption(victim); // victim’s corruption
                    int heartsToGive = switch (corr) {
                        case 2 -> 2;
                        case 3 -> 3;
                        default -> 1;
                    };

                    // drop that many heart items
                    for (int i = 0; i < heartsToGive; i++) {
                        victim.getWorld().spawnEntity(new ItemEntity(
                                victim.getWorld(),
                                victim.getX(), victim.getY(), victim.getZ(),
                                new ItemStack(ModItems.HEART)));
                    }
                }

                // Immortal kills
                if (victimImmortal || attackerImmortal) {
                    // base drops one shard…
                    int dropCount = 1;
                    double maxHearts = victim.getAttributeInstance(EntityAttributes.MAX_HEALTH).getBaseValue() / 2.0;
                    if (maxHearts >= 14 && maxHearts <= 16)
                        dropCount = 2;
                    else if (maxHearts >= 17)
                        dropCount = 3;

                    victim.dropItem(new ItemStack(ModItems.SOUL_SHARD, dropCount), false);

                    if (AscensionUtils.getCorruption(victim) <= -3) {
                        // banned ):
                        String playerName = victim.getNameForScoreboard();
                        String reason = "You have lost all your corruption levels!";
                        String command = String.format("tempban %s 24h %s", playerName, reason);
                        MinecraftServer server = victim.getServer();
                        server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
                    }
                }

                if (attacker instanceof ServerPlayerEntity killer
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
                    // Give starting corruption levels based on hearts
                    double curr_hp = player.getAttributeInstance(EntityAttributes.MAX_HEALTH).getBaseValue();
                    int start_level = 0;
                    EntityAttributeInstance maxHearts = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                    maxHearts.setBaseValue(20.0);
                    if (curr_hp <= 10 * 2) {
                        start_level = 0;
                    } else if (curr_hp <= 14 * 2) {
                        start_level = 1;
                    } else if (curr_hp <= 18 * 2) {
                        start_level = 2;
                    } else {
                        start_level = 3;
                    }

                    AscensionUtils.addCorruption((ServerPlayerEntity) player, start_level);
                    player.sendMessage(
                            Text.literal("You feel a surge of divine power! Began at " + start_level + " corruption."),
                            true);
                    sb.getOrCreateScore(player, obj).setScore(1);

                    // Play totem animation and particles
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

            if (stack.getItem() == ModItems.HEART) {
                if (AscensionUtils.getAscended((ServerPlayerEntity) player) != 1) {
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
                player.sendMessage(Text.literal("An immortal does not need extra hearts to be strong."), true);
                return ActionResult.FAIL;
            }

            if (player.isSneaking()) {
                return AscensionUtils.tryCastSpell((ServerPlayerEntity) player, (ServerWorld) world);
            }

            return ActionResult.PASS;
        });

        // Server tick events
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            for (ServerWorld world : server.getWorlds()) {
                for (ExperienceOrbEntity orb : world.getEntitiesByType(
                        EntityType.EXPERIENCE_ORB, o -> !o.isRemoved())) {

                    int id = orb.getId();

                    // 1) Skip if we've already bumped this orb
                    if (scaledOrbIds.contains(id))
                        continue;

                    // 2) Only bump when an ascended player is in pickup range
                    PlayerEntity picker = world.getClosestPlayer(orb, 2.5);
                    if (!(picker instanceof ServerPlayerEntity sp)
                            || AscensionUtils.getAscended(sp) != 1) {
                        continue;
                    }

                    // 3) Compute the new XP value
                    int orig = orb.getExperienceAmount();
                    int bumped = orig;
                    if (AscensionUtils.getCorruption((ServerPlayerEntity) picker) >= 1) {
                        bumped = (int) Math.ceil(orig * 1.10);
                    } else if (AscensionUtils.getCorruption((ServerPlayerEntity) picker) <= -1) {
                        bumped = (int) Math.ceil(orig * 0.9);
                    }
                    // inside your tick handler, right after you compute `bumped`:
                    System.out.printf(
                            "Scaling orb %d at %s: original=%d, bumped=%d%n",
                            orb.getId(),
                            orb.getBlockPos(),
                            orig,
                            bumped);

                    // 4) Spawn a new orb and discard the old one
                    ExperienceOrbEntity newOrb = new ExperienceOrbEntity(
                            world, orb.getX(), orb.getY(), orb.getZ(), bumped);
                    world.spawnEntity(newOrb);
                    orb.discard();

                    // 5) Remember that we’ve scaled this orb’s original ID
                    scaledOrbIds.add(id);
                    scaledOrbIds.add(newOrb.getId());

                    // (Optional) prune old IDs every now and then to avoid unbounded growth
                    if (scaledOrbIds.size() > 10_000) {
                        scaledOrbIds.clear();
                    }
                }
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                int current = player.getInventory().selectedSlot;
                int previous = lastSlot.getOrDefault(id, -1);

                // Remove normal totems if ascended
                if (AscensionUtils.getAscended(player) == 1) {
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
                return; // Run once per second

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID playerId = player.getUuid();

                // Handle pending cooldown notifications
                Map<Spell, Integer> cooldowns = pendingCooldownNotifications.get(playerId);
                if (cooldowns != null) {
                    Iterator<Map.Entry<Spell, Integer>> cooldownIterator = cooldowns.entrySet().iterator();
                    Spell currentSpell = Spell.getBound(player, player.getInventory().selectedSlot);

                    while (cooldownIterator.hasNext()) {
                        Map.Entry<Spell, Integer> entry = cooldownIterator.next();
                        Spell spell = entry.getKey();
                        int secondsLeft = entry.getValue();

                        if (secondsLeft > 0) {
                            if (spell.equals(currentSpell)) {
                                player.sendMessage(
                                        Text.literal("§c" + spell.getDisplayName() + ": " + secondsLeft + "s"), true);
                            }
                            entry.setValue(secondsLeft - 1);
                        } else {
                            player.sendMessage(Text.literal("§a" + spell.getDisplayName() + " ready!"), true);
                            cooldownIterator.remove();
                        }
                    }

                    if (cooldowns.isEmpty()) {
                        pendingCooldownNotifications.remove(playerId);
                    }
                }

                // Handle active runes
                RuneInstance rune = ACTIVE_RUNES.get(playerId);
                if (rune != null) {
                    drawRune(rune.center, player.getWorld());
                    rune.ticksLeft--;
                    if (rune.ticksLeft <= 0) {
                        ACTIVE_RUNES.remove(playerId);
                    }
                }
            }

            // Handle pending lightning tasks
            Iterator<LightningTask> lightningIterator = pendingLightning.iterator();
            while (lightningIterator.hasNext()) {
                LightningTask task = lightningIterator.next();
                if (task.delay <= 0) {
                    LightningEntity lightningBolt = EntityType.LIGHTNING_BOLT.create(
                            task.world,
                            bolt -> {
                            },
                            BlockPos.ofFloored(task.pos.x, task.pos.y, task.pos.z),
                            SpawnReason.TRIGGERED,
                            true,
                            true);
                    if (lightningBolt != null) {
                        task.world.spawnEntity(lightningBolt);
                        lightningIterator.remove();
                    }
                } else {
                    pendingLightning.set(pendingLightning.indexOf(task),
                            new LightningTask(task.world, task.pos, task.delay - 1));
                }
            }

            List<ParticleTask> toProcess = new ArrayList<>(pendingParticles);
            pendingParticles.clear();

            for (ParticleTask pt : toProcess) {
                // spawn the particle
                pt.world().spawnParticles(
                        pt.effect(),
                        pt.pos().x, pt.pos().y, pt.pos().z,
                        1, 0, 0, 0, 0);

                // 4) If it still has ticks, decrement and re-add to the main list
                if (pt.ticksLeft() > 1) {
                    pendingParticles.add(
                            new ParticleTask(
                                    pt.effect(),
                                    pt.pos(),
                                    pt.ticksLeft() - 1,
                                    pt.world()));
                }
            }

            // Active Runes
            ACTIVE_RUNES.forEach((id, rune) -> {
                drawRune(rune.center, server.getWorld(World.OVERWORLD));
                rune.ticksLeft--;
                if (rune.ticksLeft <= 0)
                    ACTIVE_RUNES.remove(id);
            });
        });

    }
}