package com.immortals.Immortal;

import com.immortals.Utils;
import com.immortals.api.ImmortalsData;
import com.immortals.ModItems;
import com.immortals.Main;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

import java.util.UUID;

/*Implements the Immortals' corruption system.*/
public class Immortals {
    public static void register() {
        // Passive abilities and spell activation
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // Grant haste 2 if player has Timekeeper or Chronoreaver
                if (Utils.inventoryHas(player, ModItems.TIMEKEEPER) != null
                        || Utils.inventoryHas(player, ModItems.CHRONOREAVER) != null) {
                    player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                            net.minecraft.entity.effect.StatusEffects.HASTE, 60, 1, true, false, true));
                }
                // Grant +1 attack damage if player has Dragon Egg
                EntityAttributeInstance attackAttr = player.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
                double baseAttack = 1.0; // Default base value
                if (Utils.inventoryHas(player, Items.DRAGON_EGG) != null) {
                    // Only increase if not already increased
                    if (attackAttr != null && attackAttr.getBaseValue() <= baseAttack) {
                        attackAttr.setBaseValue(baseAttack + 1.0);
                    }
                } else {
                    // Only decrease if currently increased
                    if (attackAttr != null && attackAttr.getBaseValue() > baseAttack) {
                        attackAttr.setBaseValue(baseAttack);
                    }
                }

                // Remove "immortals:augmented" modifiers from all equipped items if player is
                // ascended
                if (((ImmortalsData) player).isImmortal()) {
                    java.util.function.Consumer<ItemStack> removeAugmented = stack -> {
                        if (Utils.hasAttribute(stack, "immortals:augmented")) {
                            Utils.removeModifierById(stack, "immortals:augmented");
                            player.sendMessage(Text.literal("§cAugmentations removed from item."), false);
                        }
                    };
                    for (int i = 0; i < player.getInventory().size(); i++) {
                        ItemStack stack = player.getInventory().getStack(i);
                        removeAugmented.accept(stack);
                    }
                    // Apply passive corruption effects
                    if (server.getTicks() % 40 == 0) {
                        Utils.applyCorruptionEffects(player);
                    }

                    // Automatically use Persist if health < 3 hearts
                    if (player.getHealth() < 6.0f) {
                        SpellRegistry.tryActivate(player, null, SpellRegistry.getSlot(player, SpellRegistry.PERSIST));
                    }
                }
            }
        });

        // Splinter blow and on-hit spell type activations
        AttackEntityCallback.EVENT.register((attacker, world, hand, victim, hitResult) -> {
            // Check if attacker is an ascended player with Splinter Blow and it was a full
            // swing
            if (world.isClient || !(attacker instanceof ServerPlayerEntity sp) || !((ImmortalsData) sp).isImmortal()
                    || sp.getAttackCooldownProgress(0.5F) < 0.84F)
                return ActionResult.PASS;

            // Check if the target is a player and is actively blocking with a shield
            if (victim instanceof PlayerEntity targetPlayer && targetPlayer.isBlocking()) {
                return ActionResult.PASS;
            }
            ImmortalsData user = (ImmortalsData) attacker;
            ServerPlayerEntity target = (ServerPlayerEntity) victim;

            // Accumulate damage for Surge
            float accumulatedDamage = (float) user.getAccumulatedDamage();
            if (accumulatedDamage > 0) {
                float attackDamage = (float) attacker.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).getValue();
                user.setAccumulatedDamage(accumulatedDamage + attackDamage);
            }

            // Activate on-hit spell if set
            String onHitSpell = user.onHitSpell();
            if (onHitSpell != "") {
                switch (onHitSpell) {
                    case "frostbite" -> SpellRegistry.FROSTBITE.activate(sp, target);
                    case "echo" -> SpellRegistry.ECHO.activate(sp, target);
                    case "lock" -> SpellRegistry.LOCK.activate(sp, target);
                    default -> {
                        // Invalid spell, do nothing
                        return ActionResult.PASS;
                    }
                }
                SpellRegistry.recordUse((ServerPlayerEntity) attacker, SpellRegistry.fromId(onHitSpell));
                user.setLastSpell(onHitSpell);
                user.setOnHitSpell("");
            }

            if (SpellRegistry.getSlot(sp, SpellRegistry.SPLINTER_BLOW) == -1)
                return ActionResult.PASS;

            UUID targetId = target.getUuid();
            // Check if it's been more than 3 seconds since the last hit
            long currentTime = System.currentTimeMillis();
            long lastTime = user.getLastHitTime(targetId);

            int count = user.getComboCount(targetId);
            if (currentTime - lastTime > 3000) {
                // Reset splinter count to 1 if too much time passed
                user.setComboCount(targetId, 1);
                user.setOnHitSpell("");
            } else {
                // Increment the counter if within time window
                user.setComboCount(targetId, count + 1);
            }

            user.resetLastHitTime(targetId);
            // Success and resetting in .activate()
            SpellRegistry.tryActivate(sp, target, SpellRegistry.getSlot(sp, SpellRegistry.SPLINTER_BLOW));
            return ActionResult.PASS;
        });

        // Reset combo count on player being hit by another player
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, amount, taken, blocked) -> {
            if (entity instanceof ServerPlayerEntity sp && !blocked
                    && source.getAttacker() instanceof ServerPlayerEntity) {
                UUID id = source.getAttacker().getUuid();
                ((ImmortalsData) sp).setComboCount(id, 0);
            }
        });

        // On-hit spell controller
        AttackEntityCallback.EVENT.register((attacker, world, hand, victim, hitResult) -> {
            if (world.isClient || !(attacker instanceof ServerPlayerEntity sp)
                    || !(victim instanceof ServerPlayerEntity)
                    || !((ImmortalsData) sp).isImmortal())
                return ActionResult.PASS;
            ImmortalsData user = (ImmortalsData) attacker;

            if (user.onHitSpell().isEmpty())
                return ActionResult.PASS;
            int slot = SpellRegistry.getSlot(sp, SpellRegistry.fromId(user.onHitSpell()));
            if (slot == -1)
                return ActionResult.PASS;
            SpellRegistry.tryActivate(sp, (ServerPlayerEntity) victim, slot);
            user.setOnHitSpell("");
            return ActionResult.PASS;
        });

        // Initialize the scoreboard objectives for timeslow, immortal and dragon_ascent
        // for client rendering (yeah i still dont know how to send actual packets)
        ServerTickEvents.START_SERVER_TICK.register((MinecraftServer server) -> {
            Scoreboard sb = server.getScoreboard();
            if (sb.getNullableObjective("timeslow") == null) {
                sb.addObjective(
                        "timeslow",
                        ScoreboardCriterion.DUMMY,
                        (Text) Text.literal("t"),
                        ScoreboardCriterion.RenderType.INTEGER,
                        true,
                        null);
            }
            if (sb.getNullableObjective("dragon_ascent") == null) {
                sb.addObjective(
                        "dragon_ascent",
                        ScoreboardCriterion.DUMMY,
                        (Text) Text.literal("d"),
                        ScoreboardCriterion.RenderType.INTEGER,
                        true,
                        null);
            }
            if (sb.getNullableObjective("immortal") == null) {
                sb.addObjective(
                        "immortal",
                        ScoreboardCriterion.DUMMY,
                        (Text) Text.literal("i"),
                        ScoreboardCriterion.RenderType.INTEGER,
                        true,
                        null);
            }
        });

        // Register corruption command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("corruption")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        ImmortalsData playerData = (ImmortalsData) player;
                        if (!playerData.isImmortal()) {
                            player.sendMessage(Text.literal("You do not have corruption as a mortal."),
                                    false);
                            return 0;
                        }
                        int corruptionLevel = playerData.getCorruption();
                        player.sendMessage(Text.literal("Your corruption level is: " + corruptionLevel), false);
                        return 1;
                    }));
        });

        // Copy data on respawn / dimension change
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {

            ImmortalsData oldData = (ImmortalsData) oldPlayer;
            ImmortalsData newData = (ImmortalsData) newPlayer;

            // copy ascension & corruption
            newData.setImmortal(oldData.isImmortal());
            newData.setCorruption(oldData.getCorruption() - 1); // lose 1 corruption on death

            // copy spell bindings
            newData.getSpellBindings().clear();
            newData.getSpellBindings().putAll(oldData.getSpellBindings());

            // Remove any bindings the player can no longer use
            int corruption = newData.getCorruption();
            newData.getSpellBindings().entrySet()
                    .removeIf(e -> corruption < Utils.getRequiredCorr(SpellRegistry.fromId(e.getValue()))
                            || e.getValue().equals("dragon_ascent")
                            || e.getValue().equals("timeslow"));

            // copy trusted list
            newData.getTrusted().clear();
            newData.getTrusted().addAll(oldData.getTrusted());
        });

        // Send decreased corruption message on respawn
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // Get the player's UUID and reset their tick rate
            ImmortalsData newPlayerData = (ImmortalsData) newPlayer;
            Main.api.rateEntity(newPlayer, 20);
            // Lose a heart if on -1 or lower corruption
            int corr = newPlayerData.getCorruption();
            if (newPlayerData.isImmortal() && corr > -3) {
                if (corr <= -1) {
                    // -1: -1 heart
                    newPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(18.0);
                }
                int next = Utils.nextShardCost(corr);
                newPlayer.sendMessage(
                        Text.literal("§5You feel weakened. Corruption: §l" + corr + "§r. Next: " + next),
                        true);
            }
        });

        // Implement immortal victims and killers
        ServerLivingEntityEvents.AFTER_DEATH.register((ent, src) -> {
            if (!ent.getWorld().isClient() && ent instanceof ServerPlayerEntity victim) {
                Entity attacker = src.getAttacker();
                boolean attackerImmortal = attacker instanceof ServerPlayerEntity k
                        && ((ImmortalsData) k).isImmortal();

                if (((ImmortalsData) victim).getCorruption() <= -3) {
                    // banned ):
                    MinecraftServer server = victim.getServer();
                    if (server != null) {
                        // Schedule the ban on the next server tick to avoid race conditions
                        server.execute(() -> {
                            String playerName = victim.getNameForScoreboard();
                            String reason = "\"You have lost all your corruption levels!\"";
                            // Fix command syntax - may need to be adjusted based on your server type
                            String command = String.format("tempban %s 0 0 24 %s", playerName, reason);
                            server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
                        });
                    }
                } else {
                    if (attackerImmortal) {
                        // Scale the soul shard drop count based on victim's max health
                        int dropCount = 1;
                        double maxHearts = victim.getAttributeInstance(EntityAttributes.MAX_HEALTH).getBaseValue()
                                / 2.0;
                        // Don't drop if the victim has less than 5 hearts
                        if (maxHearts <= 5)
                            dropCount = 0;
                        victim.dropItem(new ItemStack(ModItems.SOUL_SHARD, dropCount), false);
                    }
                }

                // +3 on-kill ability
                if (attacker instanceof ServerPlayerEntity killer
                        && ((ImmortalsData) killer).getCorruption() >= 3) {
                    // heal 6.0f = 3 hearts
                    killer.heal(6.0f);
                    killer.sendMessage(Text.literal("§aYou are empowered on kill... (healed 3 hearts)"), true);
                }
            }
        });

        // Prevent ascended players from using Totem of Undying
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                if (((ImmortalsData) player).isImmortal()) {
                    // If player is ascended, prevent totem from saving them
                    ItemStack totem = null;
                    // Check main hand and offhand for totem
                    if (player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
                        totem = player.getMainHandStack();
                    } else if (player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
                        totem = player.getOffHandStack();
                    }
                    if (totem != null && !totem.isEmpty()) {
                        // Remove the totem and block its effect
                        totem.decrement(1);
                        player.sendMessage(Text.literal("§cYour totem broke lol"), true);
                        return false; // Prevent totem from working
                    }
                }
            }
            return true; // Allow normal behavior otherwise
        });

        // Custom item events
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            ItemStack stack = player.getStackInHand(hand);
            ImmortalsData playerData = (ImmortalsData) player;

            // Ascension Totem: Update objective hasAscended
            if (stack.getItem() == ModItems.ASCENSION_TOTEM) {

                if (!playerData.isImmortal()) {
                    // Give starting corruption levels based on hearts
                    double curr_hp = player.getAttributeInstance(EntityAttributes.MAX_HEALTH).getBaseValue();
                    int start_level = 0;
                    switch ((int) curr_hp / 2) {
                        case 11, 12 -> start_level = 1;
                        case 13, 14 -> start_level = 2;
                        case 15, 16 -> start_level = 3;
                        case 17, 18 -> start_level = 4;
                        case 19, 20 -> start_level = 5;
                        default -> start_level = 0;
                    }
                    System.out.println(curr_hp + " hearts, starting at " + start_level + " corruption.");
                    EntityAttributeInstance maxHearts = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                    maxHearts.setBaseValue(20.0);

                    playerData.setImmortal(true);
                    playerData.setCorruption(start_level);

                    // Reset health
                    player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(20.0);
                    player.sendMessage(
                            Text.literal("You feel a surge of divine power! Began at " + start_level + " corruption."),
                            true);

                    // Play totem animation and particles
                    world.sendEntityStatus(player, (byte) 35); // Totem pop
                    // Render rune
                    Utils.updateRune(player, "immortal", 1);
                    Utils.drawImmortalEvent(player.getPos(), world);
                    player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 1.0F, 1.0F);
                    Main.scheduler.schedule(() -> {
                        Utils.updateRune(player, "immortal", 0);
                    }, 1500);

                    stack.decrement(1);
                    Utils.grant((ServerPlayerEntity) player, "an_immortal");
                    return ActionResult.SUCCESS;
                } else {
                    player.sendMessage(Text.literal("You have already ascended. There is no going back!"), true);
                    return ActionResult.FAIL;
                }
            }

            // Soul Shard: Increase your corruption
            if (stack.getItem() == ModItems.SOUL_SHARD) {
                if (!playerData.isImmortal()) {
                    player.sendMessage(Text.literal("You must ascend to grow stronger..."), true);
                    return ActionResult.FAIL;
                }
                int corruption = playerData.getCorruption();
                // Player can increase corruption
                if (corruption < 5) {
                    int cost = Utils.nextShardCost(corruption);

                    if (stack.getCount() < cost) {
                        player.sendMessage(Text.literal("Require " + cost + " Soul Shards to increase corruption."),
                                true);
                        return ActionResult.FAIL;
                    }

                    stack.decrement(cost);
                    playerData.addCorruption(1);
                    // New corruption level
                    corruption = playerData.getCorruption();
                    player.sendMessage(
                            Text.literal("§5You grow stronger. Corruption: §l" + corruption + "§r. Next: "
                                    + Utils.nextShardCost(corruption)),
                            true);

                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            net.minecraft.sound.SoundEvents.PARTICLE_SOUL_ESCAPE,
                            net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);

                    switch (corruption) {
                        case 0 -> player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(20.0);
                        case 1 -> {
                            player.sendMessage(
                                    Text.literal("Unlocked dash and glow!"),
                                    true);
                        }
                        case 2 -> {
                            player.sendMessage(
                                    Text.literal("Unlocked frostbite and blackout!"),
                                    true);
                        }
                        case 3 -> {
                            player.sendMessage(
                                    Text.literal("Unlocked persist and splinter blow!"),
                                    true);
                        }
                        case 4 -> {
                            player.sendMessage(
                                    Text.literal("Unlocked shrink and echo!"),
                                    true);
                        }
                        case 5 -> {
                            player.sendMessage(
                                    Text.literal("Unlocked surge and lock!"),
                                    true);
                        }
                        default -> {
                        }
                    }
                    return ActionResult.SUCCESS;
                } else {
                    player.sendMessage(Text.literal("Your soul has reached its peak."), true);
                    return ActionResult.FAIL;
                }
            }

            if (player.isSneaking()) {
                int slot = player.getInventory().getSelectedSlot();
                return SpellRegistry.tryActivate((ServerPlayerEntity) player, null, slot)
                        ? ActionResult.SUCCESS
                        : ActionResult.PASS;
            }
            return ActionResult.PASS;
        });
    }
}