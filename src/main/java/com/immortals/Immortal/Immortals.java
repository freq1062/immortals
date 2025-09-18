package com.immortals.Immortal;

import com.immortals.Utils;
import com.immortals.api.ImmortalsData;
import com.immortals.entity.FragmentEntity;
import com.immortals.entity.ImmortalEntity;
import com.immortals.network.NetworkChannels;
import com.immortals.ModItems;
import com.immortals.Main;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/*Implements the Immortals' corruption system.*/
public class Immortals {

    // Fragment related data structures
    public static final java.util.Map<ServerPlayerEntity, Entity> fragments = new java.util.HashMap<>();
    public static final java.util.Map<UUID, Integer> fragmentCount = new java.util.HashMap<>();
    // Link related data structures
    public static final java.util.Map<ServerPlayerEntity, ServerPlayerEntity> parent = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<ServerPlayerEntity, Integer> size = new java.util.concurrent.ConcurrentHashMap<>();

    public static void register() {

        // Register spell activation
        ServerPlayNetworking.registerGlobalReceiver(NetworkChannels.SpellC2SPayload.ID, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayerEntity))
                return;
            SpellRegistry.tryActivate(context.player(), null, payload.slot());
        });

        ServerPlayNetworking.registerGlobalReceiver(NetworkChannels.FragmentC2SPayload.ID, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayerEntity user))
                return;
            if (fragmentCount.getOrDefault(user.getUuid(), 0) >= 3) {
                SpellRegistry.recordUse(user, SpellRegistry.FRAGMENT);
                return;
            }
            Vec3d playerPos = user.getPos();
            Vec3d lookVec = user.getRotationVec(1.0F).normalize();
            ServerWorld world = user.getWorld();

            FragmentEntity fragment = new FragmentEntity(ImmortalEntity.FRAGMENT_ENTITY, world);
            fragment.setPosition(playerPos.x, playerPos.y + user.getStandingEyeHeight() * 0.5,
                    playerPos.z);
            // Set yaw and pitch to match player's look direction
            fragment.setYaw(user.getYaw());
            fragment.setPitch(user.getPitch());
            // Use player's look direction directly for velocity
            fragment.setVelocity(lookVec.x * 1.5, lookVec.y * 1.5, lookVec.z * 1.5);
            fragment.setCustomNameVisible(true); // Make the ID visible
            fragment.setNoGravity(true); // Set no gravity
            world.spawnEntity(fragment);
            Immortals.fragments.put(user, fragment);
            // Play sound for fragment throw
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    net.minecraft.sound.SoundEvents.ITEM_TRIDENT_THROW,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);

            // Spawn glass breaking particles in front of the user
            Vec3d particlePos = user.getEyePos().add(user.getRotationVec(1.0F).multiply(2.0));
            for (int j = 0; j < 15; j++) {
                // Add some random spread to the particles
                double offsetX = (Math.random() - 0.5) * 0.5;
                double offsetY = (Math.random() - 0.5) * 0.5;
                double offsetZ = (Math.random() - 0.5) * 0.5;
                world.spawnParticles(
                        new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.GLASS)),
                        particlePos.x, particlePos.y, particlePos.z,
                        1, offsetX, offsetY, offsetZ,
                        0.1);
            }
            fragmentCount.put(user.getUuid(), fragmentCount.getOrDefault(user.getUuid(), 0) + 1);
            int remainingFragments = 3 - fragmentCount.getOrDefault(user.getUuid(), 0);
            user.sendMessage(Text.literal("§d" + remainingFragments + " fragments remaining!"), true);
        });

        // Passive abilities and spell activation
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            // Fragment spell collision
            for (var entry : fragments.entrySet()) {
                ServerPlayerEntity sp = entry.getKey();
                Entity fragment = entry.getValue();
                if (fragment.isRemoved()) {
                    fragments.entrySet().removeIf(e -> e.getValue().equals(fragment));
                }

                // Spawn red particles behind the fragment (trail effect)
                ServerWorld world = (ServerWorld) fragment.getWorld();
                world.spawnParticles(ParticleTypes.CRIMSON_SPORE, fragment.getX(), fragment.getY(), fragment.getZ(), 3,
                        0.05, 0.05, 0.05, 0.01);
                // Check for entity collisions
                world.getOtherEntities(fragment, fragment.getBoundingBox().expand(0.1),
                        entity -> entity != sp).forEach(hitEntity -> {
                            if (hitEntity instanceof net.minecraft.entity.LivingEntity livingEntity) {
                                float maxHealth = (float) livingEntity.getMaxHealth();
                                float damage = maxHealth * ((float) Main.CONFIG.getDouble("fragmentDmg"));
                                livingEntity.damage(world, Utils.of(world, Utils.SPELL_DAMAGE_TYPE, (Entity) sp),
                                        damage);
                                fragment.remove(Entity.RemovalReason.DISCARDED);
                            }
                        });
            }

            // Ensure fragments are removed if their owner disconnects
            server.getPlayerManager().getPlayerList().forEach(player -> {
                if (!fragments.containsKey(player)) {
                    fragments.entrySet().removeIf(entry -> entry.getKey().getUuid().equals(player.getUuid()));
                }
            });

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {

                // Make iron golems aggressive to immortal players, ignoring players in
                // spectator and creative mode
                if (((ImmortalsData) player).isImmortal() && !player.isSpectator() && !player.isCreative()) {
                    Box searchBox = player.getBoundingBox().expand(8.0);
                    player.getWorld().getEntitiesByType(
                            net.minecraft.entity.EntityType.IRON_GOLEM,
                            searchBox,
                            golem -> true).forEach(golem -> {
                                if (golem instanceof IronGolemEntity ironGolem) {
                                    ironGolem.setTarget(player);
                                }
                            });
                }

                // Grant +1 attack damage if player has Dragon Egg and is immortal
                EntityAttributeInstance attackAttr = player.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);

                if (attackAttr.getBaseValue() > 2.0) {
                    attackAttr.setBaseValue(1.0);
                    System.out.println("Attack damage: " + attackAttr.getValue());
                }

                double baseAttack = player.getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE); // Default base value
                if (Utils.inventoryHas(player, Items.DRAGON_EGG) != null && ((ImmortalsData) player).isImmortal()) {
                    // Only increase if not already increased
                    if (attackAttr != null && attackAttr.getValue() <= baseAttack) {
                        MutableText message = Text.literal("Unlocked ");
                        MutableText spellText = Text.literal("Dragon Ascent")
                                .styled(style -> style.withHoverEvent(
                                        new HoverEvent.ShowText(
                                                Text.literal(SpellRegistry.DRAGON_ASCENT.getDescription())))
                                        .withColor(0x800080)); // Purple color

                        player.sendMessage(
                                message.append(spellText)
                                        .append(" and gained +1 attack damage! Hover to see details!"));
                        attackAttr.setBaseValue(2.0);
                    }
                } else {
                    // Only decrease if currently increased
                    if (attackAttr != null && attackAttr.getValue() > baseAttack) {
                        attackAttr.setBaseValue(baseAttack);
                    }
                }

                EntityAttributeInstance blockBreakAttr = player
                        .getAttributeInstance(EntityAttributes.BLOCK_BREAK_SPEED);
                double baseBreakAttr = 1.0; // Default base value
                if (Utils.inventoryHas(player, ModItems.TIMEKEEPER) != null) {
                    // Only increase if not already increased
                    if (blockBreakAttr != null && blockBreakAttr.getValue() <= baseBreakAttr) {
                        if (((ImmortalsData) player).isImmortal()) {
                            MutableText message = Text.literal("Unlocked ");
                            MutableText spellText = Text.literal("Timeslow")
                                    .styled(style -> style.withHoverEvent(
                                            new HoverEvent.ShowText(
                                                    Text.literal(SpellRegistry.TIMESLOW.getDescription())))
                                            .withColor(0xFFD700)); // Gold color

                            player.sendMessage(message.append(spellText)
                                    .append(" and gained Haste 2 block break speed! Hover to see details!"));
                        }
                        blockBreakAttr.setBaseValue(baseBreakAttr + 2.0); // Adjust block break speed for Haste II
                    }
                } else {
                    // Only decrease if currently increased
                    if (blockBreakAttr != null && blockBreakAttr.getValue() > baseBreakAttr) {
                        blockBreakAttr.setBaseValue(baseBreakAttr);
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
            if (!(attacker instanceof ServerPlayerEntity sp)
                    || !(victim instanceof ServerPlayerEntity tp))
                return ActionResult.PASS;

            // Check if the item is on cooldown(ex. locked by lock)
            if (sp.getItemCooldownManager().isCoolingDown(sp.getMainHandStack())) {
                return ActionResult.FAIL;
            }

            // Check if attacker is an ascended player and it was a full swing
            if (!(world instanceof ServerWorld serverWorld) || !((ImmortalsData) sp).isImmortal()
                    || sp.getAttackCooldownProgress(0.5F) < 0.84F)
                return ActionResult.PASS;

            // Check if the target is a player and is actively blocking with a shield
            if (victim instanceof PlayerEntity targetPlayer && targetPlayer.isBlocking()) {
                return ActionResult.PASS;
            }
            ImmortalsData user = (ImmortalsData) attacker;

            // Activate on-hit spell if set
            String onHitSpell = user.onHitSpell();
            if (onHitSpell != "") {
                switch (onHitSpell) {
                    case "frostbite" -> SpellRegistry.FROSTBITE.activate(sp, tp);
                    case "lock" -> SpellRegistry.LOCK.activate(sp, tp);
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

            UUID targetId = tp.getUuid();
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
            SpellRegistry.tryActivate(sp, tp, SpellRegistry.getSlot(sp, SpellRegistry.SPLINTER_BLOW));
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
            try {
                Main.api.rateEntity(newPlayer, 20);
            } catch (Exception e) {
                Main.LOGGER.error("Failed to reset tickrate: " + e.getMessage());
            }
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
                            String playerName = victim.getName().getString();
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
                    NetworkChannels.RuneS2CPayload payload = new NetworkChannels.RuneS2CPayload(
                            "immortal", player.getX(), player.getY(), player.getZ(), 14.0f, 30);

                    for (ServerPlayerEntity other : PlayerLookup.world((ServerWorld) player.getWorld())) {
                        ServerPlayNetworking.send(other, payload);
                    }
                    Utils.drawImmortalEvent(player.getPos(), world);
                    player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 1.0F, 1.0F);

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
                        case 1, 2, 3, 4, 5 -> {
                            player.sendMessage(
                                    Utils.getSpellDescriptions(corruption),
                                    false);
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

            // Soul Purifier: Update corruption level +1 up to 0
            if (stack.getItem() == ModItems.SOUL_PURIFIER) {
                if (playerData.getCorruption() < 0) {
                    playerData.addCorruption(1);
                    ;
                    int newLvl = playerData.getCorruption();
                    int next = Utils.nextShardCost(newLvl);
                    if (newLvl == 0) {
                        // Reset health to 10 hearts
                        player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(20.0);
                    }
                    stack.decrement(1);
                    player.sendMessage(
                            Text.literal("§5You feel renewed. Corruption: §l" + newLvl + "§r. Next: " + next),
                            true);
                    return ActionResult.SUCCESS;
                }
                player.sendMessage(Text.literal("Soul purifier cannot increase corruption beyond +0."), true);
                return ActionResult.FAIL;
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