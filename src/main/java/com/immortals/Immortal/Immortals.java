package com.immortals.Immortal;

import com.immortals.Utils;
import com.immortals.Mortal.Weapons;
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
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/*Implements the Immortals' corruption and spell system.*/
public class Immortals {

    // Fragment related data structures
    public static final java.util.Map<ServerPlayerEntity, Entity> fragments = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<UUID, Integer> fragmentCount = new java.util.concurrent.ConcurrentHashMap<>();
    // Link related data structures
    public static final java.util.Map<ServerPlayerEntity, ServerPlayerEntity> parent = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<ServerPlayerEntity, Integer> size = new java.util.concurrent.ConcurrentHashMap<>();

    public static void register() {
        Main.LOGGER.info("Registering Immortals events");

        // Register spell activation from keybind
        ServerPlayNetworking.registerGlobalReceiver(NetworkChannels.SpellC2SPayload.ID, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayerEntity sp))
                return;
            if (((ImmortalsData) sp).isImmortal()) {
                if (SpellRegistry.getBound(sp, payload.slot()) == SpellRegistry.SPLINTER_BLOW)
                    return;
                SpellRegistry.tryActivate(sp, null, payload.slot());
            } else {
                ItemStack inHand = sp.getStackInHand(sp.getActiveHand());
                switch (inHand) {
                    case ItemStack stack when stack.getItem() == ModItems.PHASEBREAKER -> {
                        Weapons.phaseChange(sp, sp.getWorld());
                    }
                    case ItemStack stack when stack.getItem() == ModItems.CHRONOREAVER -> {
                        Weapons.overclock(sp, sp.getWorld());
                    }
                    default -> {
                    }
                }
            }

        });

        ServerPlayNetworking.registerGlobalReceiver(NetworkChannels.HandshakeC2SPayload.ID, (payload, context) -> {
            // Send handshake packet to validate client rendering
            NetworkChannels.HandshakeS2CPayload response = new NetworkChannels.HandshakeS2CPayload(0);
            ServerPlayNetworking.send(context.player(), response);
        });

        // Register fragment activation from punching
        ServerPlayNetworking.registerGlobalReceiver(NetworkChannels.FragmentC2SPayload.ID, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayerEntity user) || !((ImmortalsData) user).isImmortal())
                return;
            if (fragmentCount.getOrDefault(user.getUuid(), -1) == -1) {
                return;
            }
            int currentCount = fragmentCount.getOrDefault(user.getUuid(), 0);
            if (currentCount >= 3) {
                fragmentCount.put(user.getUuid(), -1);
                SpellRegistry.recordUse(user, SpellRegistry.FRAGMENT);
                return;
            }

            // Increment the fragment count first
            currentCount++;
            fragmentCount.put(user.getUuid(), currentCount);

            Vec3d playerPos = user.getPos();
            Vec3d lookVec = user.getRotationVec(1.0F).normalize();
            ServerWorld world = user.getWorld();

            FragmentEntity fragment = new FragmentEntity(ImmortalEntity.FRAGMENT_ENTITY, world);
            fragment.setPosition(playerPos.x, playerPos.y + user.getStandingEyeHeight() * 0.5,
                    playerPos.z);
            // Set yaw and pitch to match player's look direction
            fragment.setYaw(user.getYaw());
            fragment.setPitch(user.getPitch());
            fragment.setVelocity(lookVec.x * 2.0, lookVec.y * 2.0, lookVec.z * 2.0);
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

            int remainingFragments = 3 - currentCount;
            if (currentCount == 3) {
                SpellRegistry.recordUse(user, SpellRegistry.FRAGMENT);
                fragmentCount.put(user.getUuid(), -1);
            } else {
                user.sendMessage(Text.literal(
                        remainingFragments + " fragment" + (remainingFragments != 1 ? "s" : "") + " remaining!"), true);
            }
        });

        // Passive abilities and spell activation using shift + right click
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {

            if (server.getTicks() % 20 == 0) {
                // Ensure fragments, parent, and size are cleared for players who are not online
                Set<UUID> onlinePlayers = new HashSet<>();
                server.getPlayerManager().getPlayerList().forEach(player -> onlinePlayers.add(player.getUuid()));

                fragments.entrySet().removeIf(entry -> !onlinePlayers.contains(entry.getKey().getUuid()));
                parent.keySet().removeIf(player -> !onlinePlayers.contains(player.getUuid()));
                size.keySet().removeIf(player -> !onlinePlayers.contains(player.getUuid()));
            }

            // Fragment spell collision
            for (var entry : fragments.entrySet()) {
                ServerPlayerEntity sp = entry.getKey();
                Entity fragment = entry.getValue();
                if (fragment.isRemoved()) {
                    fragments.entrySet().removeIf(e -> e.getValue().equals(fragment));
                    continue;
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

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {

                ImmortalsData data = (ImmortalsData) player;
                if (!data.isImmortal())
                    continue;

                if (data.hasTimekeeper() != (Utils.inventoryHas(player, ModItems.TIMEKEEPER) != -1)
                        && data.hasTimekeeper() == false) {
                    MutableText message = Text.literal("Unlocked ");
                    MutableText spellText = Text.literal("Timeslow")
                            .styled(style -> style.withHoverEvent(
                                    new HoverEvent.ShowText(
                                            Text.literal(SpellRegistry.TIMESLOW.getDescription())))
                                    .withColor(0xFFD700)); // Gold color

                    player.sendMessage(message.append(spellText)
                            .append(" and gained Haste II! Hover to see details!"));
                }
                if (data.hasDragonEgg() != (Utils.inventoryHas(player, Items.DRAGON_EGG) != -1)
                        && data.hasDragonEgg() == false) {
                    MutableText message = Text.literal("Unlocked ");
                    MutableText spellText = Text.literal("Dragon Ascent")
                            .styled(style -> style.withHoverEvent(
                                    new HoverEvent.ShowText(
                                            Text.literal(SpellRegistry.DRAGON_ASCENT.getDescription())))
                                    .withColor(0x800080)); // Purple color

                    player.sendMessage(message.append(spellText)
                            .append(" and gained +1 attack damage! Hover to see details!"));
                }
                data.setTimekeeper(Utils.inventoryHas(player, ModItems.TIMEKEEPER) != -1);
                data.setDragonEgg(Utils.inventoryHas(player, Items.DRAGON_EGG) != -1);

                // Grant +1 attack damage if player has Dragon Egg and is immortal
                EntityAttributeInstance attackAttr = player.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);

                double baseAttack = 1.0; // Default base value
                if (data.hasDragonEgg()) {
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

                // Grant Haste II effect if player has Timekeeper and is immortal
                if (data.hasTimekeeper()) {
                    if (((ImmortalsData) player).isImmortal()) {
                        // Apply Haste II effect for 2 seconds
                        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.HASTE, 60, 1));
                    }
                }

                if (((ImmortalsData) player).isImmortal()) {
                    java.util.function.Consumer<ItemStack> removeAugmented = stack -> {
                        if (Utils.hasAttribute(stack, "immortals:augmented")) {
                            Utils.removeModifierById(stack, "immortals:augmented");
                            player.sendMessage(Text.literal("Augmentations removed from item."), false);
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
                        if (SpellRegistry.getSlot(player, SpellRegistry.PERSIST) == -1)
                            continue;
                        SpellRegistry.tryActivate(player, null, SpellRegistry.getSlot(player, SpellRegistry.PERSIST));
                    }
                }
            }
        });

        // Prevent using items on blocks if the item is on cooldown (ex. Lock spell)
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayerEntity sp
                    && sp.getItemCooldownManager().isCoolingDown(sp.getMainHandStack())) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
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
            if (!(world instanceof ServerWorld) || !((ImmortalsData) sp).isImmortal()
                    || sp.getAttackCooldownProgress(0.5F) < 0.85F)
                return ActionResult.PASS;

            // Check if the target is a player and is actively blocking with a shield
            if (victim instanceof PlayerEntity targetPlayer && targetPlayer.isBlocking()) {
                return ActionResult.PASS;
            }
            ImmortalsData user = (ImmortalsData) attacker;

            // Activate on-hit spell if previously activated
            String onHitSpell = user.onHitSpell();
            if (onHitSpell != "") {
                switch (onHitSpell) {
                    case "frostbite" -> SpellRegistry.FROSTBITE.activate(sp, tp);
                    case "lock" -> SpellRegistry.LOCK.activate(sp, tp);
                    case "link" -> {
                        if (user.getLinked() != null) {
                            sp.sendMessage(Text.literal("You are already linked to a player!"), true);
                            return ActionResult.PASS;
                        } else if (((ImmortalsData) tp).getLinked() != null) {
                            sp.sendMessage(Text.literal("Target player is already linked to someone!"), true);
                            return ActionResult.PASS;
                        } else {
                            SpellRegistry.LINK.activate(sp, tp);
                        }
                    }
                    default -> {
                        return ActionResult.PASS;
                    }
                }
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

        // Reset combo count for specific players
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
                            player.sendMessage(Text.literal("§bYou do not have corruption as a mortal."),
                                    false);
                            return 0;
                        }
                        int corruptionLevel = playerData.getCorruption();
                        player.sendMessage(Text.literal("§cYour corruption level is: " + corruptionLevel), false);
                        return 1;
                    }));
        });

        // Copy data on respawn / dimension change
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {

            ImmortalsData oldData = (ImmortalsData) oldPlayer;
            ImmortalsData newData = (ImmortalsData) newPlayer;

            // Copy ascension & corruption
            newData.setImmortal(oldData.isImmortal());
            newData.setCorruption(oldData.getCorruption() - 1); // Lose 1 corruption on death

            // Copy spell bindings
            newData.getSpellBindings().clear();
            newData.getSpellBindings().putAll(oldData.getSpellBindings());

            // Remove any bindings the player can no longer use
            int corruption = newData.getCorruption();
            newData.getSpellBindings().entrySet()
                    .removeIf(e -> corruption < Utils.getRequiredCorr(SpellRegistry.fromId(e.getValue()))
                            || e.getValue().equals("dragon_ascent")
                            || e.getValue().equals("timeslow"));

            // Copy trusted list
            newData.getTrusted().clear();
            newData.getTrusted().addAll(oldData.getTrusted());
        });

        // Send decreased corruption message on respawn, ignoring dimension travel
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // Check if the respawn is due to death and not dimension travel
            if (!oldPlayer.getWorld().getRegistryKey().equals(newPlayer.getWorld().getRegistryKey())) {
                return;
            }

            // Send handshake packet to validate client rendering
            NetworkChannels.HandshakeS2CPayload payload = new NetworkChannels.HandshakeS2CPayload(0);
            ServerPlayNetworking.send(newPlayer, payload);

            // Make sure tick rate is reset
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
                        Text.literal("§cYou feel weakened. Corruption: " + corr + " Next: " + next),
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
                        // Schedule the ban on the next server tick
                        server.execute(() -> {
                            String playerName = victim.getName().getString();
                            String reason = "\"You have lost all your corruption levels!\"";
                            // Fix command syntax - may need to be adjusted based on your server type
                            String command = String.format("tempban %s 24h %s", playerName, reason);
                            server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
                        });
                    }
                } else {
                    if (attackerImmortal) {
                        victim.dropItem(new ItemStack(ModItems.SOUL_SHARD, 1), false);
                    }
                }

                // +3 on-kill ability
                if (attacker instanceof ServerPlayerEntity killer
                        && ((ImmortalsData) killer).getCorruption() >= 3) {
                    // Heal to full health
                    killer.setHealth(killer.getMaxHealth());
                    // Apply Speed III effect for 3 seconds
                    killer.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                            net.minecraft.entity.effect.StatusEffects.SPEED, 60, 2));
                    killer.sendMessage(Text.literal("§cYou are empowered on kill..."), true);
                }
            }
        });

        // Prevent ascended players from using Totem of Undying
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                if (((ImmortalsData) player).isImmortal()) {
                    ItemStack totem = null;
                    if (player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
                        totem = player.getMainHandStack();
                    } else if (player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
                        totem = player.getOffHandStack();
                    }
                    if (totem != null && !totem.isEmpty()) {
                        totem.decrement(1);
                        player.sendMessage(Text.literal("§cYour totem broke lol"), true);
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

            // Totem of Ascension: Become immortal
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
                            Text.literal(
                                    "§cYou feel a surge of divine power! Began at " + start_level + " corruption."),
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
                    player.sendMessage(Text.literal("§cYou have already ascended. There is no going back!"), true);
                    return ActionResult.FAIL;
                }
            }

            // Soul Shard: Increase your corruption
            if (stack.getItem() == ModItems.SOUL_SHARD) {
                if (!playerData.isImmortal()) {
                    player.sendMessage(Text.literal("§cYou must ascend to absorb souls..."), true);
                    return ActionResult.FAIL;
                }
                int corruption = playerData.getCorruption();
                if (corruption < 5) {
                    int cost = Utils.nextShardCost(corruption);

                    if (stack.getCount() < cost) {
                        player.sendMessage(Text.literal("§cRequire " + cost + " Soul Shards to increase corruption."),
                                true);
                        return ActionResult.FAIL;
                    }

                    stack.decrement(cost);
                    playerData.addCorruption(1);
                    // New corruption level
                    corruption = playerData.getCorruption();
                    player.sendMessage(
                            Text.literal("§cYou grow stronger. Corruption: " + corruption + ". Next: "
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
                    player.sendMessage(Text.literal("§cYour soul has reached its peak."), true);
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
                        // Reset health to 10 hearts if coming from -1
                        player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(20.0);
                    }
                    stack.decrement(1);
                    player.sendMessage(
                            Text.literal("§cYou feel renewed. Corruption: " + newLvl + ". Next: " + next),
                            true);
                    return ActionResult.SUCCESS;
                }
                player.sendMessage(Text.literal("§cSoul purifier cannot increase corruption beyond +0."), true);
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