package com.immortals.Mortal;

import com.immortals.Utils;
import com.immortals.api.ImmortalsData;
import com.immortals.Main;
import com.immortals.network.NetworkChannels;
import com.immortals.Immortal.SpellRegistry;
import com.immortals.ModItems;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.command.argument.EntityArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import java.util.HashSet;
import java.util.Set;

/* Implements the Mortals' Lifesteal system. */
public class Mortals {

    // Class-level field for scaledOrbIds
    private static final Set<Integer> scaledOrbIds = new HashSet<>();

    public static void register() {
        // Register events and logic

        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {

            // Loop through all players and apply effects
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!((ImmortalsData) player).isImmortal()) {
                    player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                            net.minecraft.entity.effect.StatusEffects.HERO_OF_THE_VILLAGE, 60, 2, false, false));
                    // Hero of the village 3

                    // Add Luck 1 if the player has >= 11 hearts
                    double maxHealth = player.getAttributeInstance(EntityAttributes.MAX_HEALTH).getBaseValue();
                    if (maxHealth >= 22.0) {
                        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.LUCK, 60, 0, false, false));
                    }
                }
            }

            for (ServerWorld world : server.getWorlds()) {
                for (ExperienceOrbEntity orb : world.getEntitiesByType(
                        EntityType.EXPERIENCE_ORB, o -> !o.isRemoved())) {

                    int id = orb.getId();
                    if (scaledOrbIds.contains(id))
                        continue;

                    PlayerEntity picker = world.getClosestPlayer(orb, 2.5);
                    if (!(picker instanceof ServerPlayerEntity player)
                            || ((ImmortalsData) player).isImmortal()) {
                        continue;
                    }

                    int orig = orb.getValue();
                    int bumped = orig;
                    EntityAttributeInstance healthAttr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                    double maxHealth = healthAttr.getBaseValue();
                    int hearts = (int) (maxHealth / 2.0); // Convert health to hearts

                    // Scale from -mortalMaxXpGain at 0 hearts to +mortalMaxXpGain at 20 hearts
                    double multiplier = ((hearts / 20.0) * 2.0 - 1.0) * (Main.CONFIG.getDouble("mortalMaxXpGain"));
                    bumped = (int) Math.ceil(orig + orig * multiplier);
                    // Replace the old experience orb with scaled new one
                    ExperienceOrbEntity newOrb = new ExperienceOrbEntity(
                            world, orb.getX(), orb.getY(), orb.getZ(), bumped);
                    world.spawnEntity(newOrb);
                    orb.discard();

                    scaledOrbIds.add(id);
                    scaledOrbIds.add(newOrb.getId());

                    // Clear the array, this means every 250 orbs might not be scaled but whatever
                    if (scaledOrbIds.size() > 500) {
                        scaledOrbIds.clear();
                    }
                }
            }
        });

        // Lose 1 heart on death
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive)
                return;
            if (!((ImmortalsData) oldPlayer).isImmortal()
                    && oldPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH).getBaseValue() > 2.0) {
                EntityAttributeInstance old_hp = oldPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                EntityAttributeInstance new_hp = newPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                new_hp.setBaseValue(old_hp.getBaseValue() - 2.0);
                newPlayer.sendMessage(
                        Text.literal("§bYou lost a heart."),
                        true);
            }
        });

        // Mortals' lifesteal
        ServerLivingEntityEvents.AFTER_DEATH.register((ent, src) -> {
            if (!ent.getWorld().isClient() && ent instanceof ServerPlayerEntity victim) {
                Entity attacker = src.getAttacker();
                ImmortalsData victimData = (ImmortalsData) victim;

                // Mortal killed by mortal or natural causes
                if (attacker instanceof ServerPlayerEntity k
                        && !((ImmortalsData) k).isImmortal()) {
                    victim.dropItem(new ItemStack(ModItems.HEART, 1), false);
                    // Check if the victim has no hearts left
                    EntityAttributeInstance mhVic = victim.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                    if (mhVic.getBaseValue() <= 2.0) {
                        // banned ):
                        mhVic.setBaseValue(6.0); // Restart player at 3 hearts
                        String playerName = victim.getName().getString();
                        String reason = "You have run out of hearts!";
                        String command = String.format("tempban %s 24h %s", playerName, reason);
                        MinecraftServer server = victim.getServer();
                        server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
                        return;
                    }
                    if (victimData.isImmortal()) {
                        if (!(attacker instanceof ServerPlayerEntity)) {
                            // Victim immortal, but killed by non player
                            victim.dropItem(new ItemStack(ModItems.SOUL_SHARD, 1), false);
                        } else {
                            // Victim immortal, killed by mortal player
                            victim.dropItem(new ItemStack(ModItems.HEART, 1), false);
                        }
                    } else {
                        victim.dropItem(new ItemStack(ModItems.HEART, 1), false);
                    }
                }
            }
        });

        // Item use events
        UseItemCallback.EVENT.register((p, world, hand) -> {
            if (world.isClient() || !(p instanceof ServerPlayerEntity player)) {
                return ActionResult.PASS;
            }
            ItemStack stack = player.getStackInHand(hand);
            ImmortalsData data = (ImmortalsData) player;

            if (stack.getItem() == ModItems.HEART) {
                if (data.isImmortal()) {
                    player.sendMessage(
                            Text.literal(
                                    "§cAn Immortal does not require hearts to become strong."),
                            false);
                    return ActionResult.PASS;
                }
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
                    player.sendMessage(Text.literal("§bYou have reached the maximum number of hearts."),
                            true);
                    return ActionResult.FAIL;
                }
            } else if (stack.getItem() == ModItems.ARTIFICIAL_HEART) {
                if (data.isImmortal()) {
                    player.sendMessage(
                            Text.literal(
                                    "§cYou scratched your head and couldn't figure out how to use the item."),
                            false);
                    return ActionResult.PASS;
                }
                EntityAttributeInstance curr_hp = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                if (curr_hp.getBaseValue() < 14.0) { // 7 hearts
                    curr_hp.setBaseValue(curr_hp.getBaseValue() + 2.0);
                    stack.decrement(1);
                    return ActionResult.SUCCESS;
                } else {
                    player.sendMessage(Text.literal("§bArtificial heart can only recover up to 7 hearts."),
                            true);
                    return ActionResult.FAIL;
                }
            } else if (stack.getItem() == Items.TOTEM_OF_UNDYING && stack.contains(DataComponentTypes.CUSTOM_DATA)) {
                if (data.isImmortal()) {
                    player.sendMessage(
                            Text.literal(
                                    "§cThe talisman rejects your corrupted touch."),
                            false);
                    return ActionResult.PASS;
                }
                // Cooldown logic: use a persistent NBT tag to store last use timestamp
                long currentTime = world.getTime(); // world time in ticks
                NbtCompound customData = stack.get(DataComponentTypes.CUSTOM_DATA).copyNbt();
                if (customData != null && customData.contains("augment")) {
                    long lastUsed = customData.contains("lastTalismanUse")
                            ? customData.getLong("lastTalismanUse").orElse(0L)
                            : 0L;
                    if (currentTime - lastUsed < 100) { // 5 seconds = 100 ticks
                        player.sendMessage(Text.literal("Talisman is recharging!"), true);
                        return ActionResult.FAIL;
                    }
                    net.minecraft.nbt.NbtList augmentList = customData.getList("augment")
                            .orElse(new net.minecraft.nbt.NbtList());
                    java.util.Set<String> augments = new java.util.HashSet<>();
                    for (int i = 0; i < augmentList.size(); i++) {
                        augmentList.getString(i).ifPresent(augments::add);
                    }
                    if (augments.contains("Strength")) {
                        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.STRENGTH, 20 * 90, 1));
                    }
                    if (augments.contains("Regeneration")) {
                        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.REGENERATION, 20 * 90, 0));
                    }
                    if (augments.contains("Speed")) {
                        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.SPEED, 20 * 90, 0));
                    }
                    if (augments.contains("Fire Resistance")) {
                        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE, 20 * 90, 0));
                    }
                    // Update last use time
                    customData.putLong("lastTalismanUse", currentTime);
                    stack.set(DataComponentTypes.CUSTOM_DATA,
                            net.minecraft.component.type.NbtComponent.of(customData));
                    // Spawn a small particle indicator around the player
                    ((net.minecraft.server.world.ServerWorld) world).spawnParticles(
                            net.minecraft.particle.ParticleTypes.ENCHANT,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            20,
                            0.5, 0.5, 0.5,
                            0.1);
                    player.sendMessage(Text.literal("§aTalisman activated!"), true);
                    return ActionResult.SUCCESS;
                }
            } else if (stack.getItem() == ModItems.AUGMENTATION_CORE) {
                if (data.isImmortal()) {
                    player.sendMessage(
                            Text.literal(
                                    "§cYou scratched your head and couldn't figure out how to use the core."),
                            false);
                    return ActionResult.PASS;
                }
                ItemStack stackToAugment = player.getOffHandStack();
                if (stackToAugment.isEmpty()) {
                    player.sendMessage(Text.literal("§bHold the item you would like to augment in your offhand!"),
                            false);
                    return ActionResult.PASS;
                } else if (stackToAugment.getCount() != 1) {
                    player.sendMessage(Text.literal("§bYou can only augment an item stack of size 1."), false);
                    return ActionResult.PASS;
                } else if (Utils.hasAttribute(stackToAugment, "immortals:augmented")) {
                    player.sendMessage(Text.literal("§bThis item has already been augmented."), true);
                    return ActionResult.PASS;
                }

                stack.decrement(1);

                if (stackToAugment.getItem() == Items.TOTEM_OF_UNDYING) {
                    // Turn totem into a talisman
                    Utils.addModifier(
                            stackToAugment,
                            "immortals:augmented",
                            AttributeModifierSlot.ANY,
                            EntityAttributes.LUCK,
                            1.0,
                            EntityAttributeModifier.Operation.ADD_VALUE);
                    // Add "Strength" and one random augment to the talisman
                    java.util.List<String> augments = new java.util.ArrayList<>();
                    augments.add("Strength");
                    String[] possible = { "Speed", "Regeneration", "Fire Resistance" };
                    String chosen = possible[new java.util.Random().nextInt(possible.length)];
                    augments.add(chosen);

                    // Create a new NBT tag with augment as a string list
                    NbtCompound augmentTag = new NbtCompound();
                    net.minecraft.nbt.NbtList augmentList = new net.minecraft.nbt.NbtList();
                    for (String s : augments) {
                        augmentList.add(net.minecraft.nbt.NbtString.of(s));
                    }
                    augmentTag.put("augment", augmentList);

                    // Set it under the CUSTOM_DATA component
                    stackToAugment.set(DataComponentTypes.CUSTOM_DATA,
                            net.minecraft.component.type.NbtComponent.of(augmentTag));

                    // Set custom name and lore
                    int color;
                    switch (chosen) {
                        case "Speed" -> color = 0x3498DB; // blue
                        case "Regeneration" -> color = 0xFF69B4; // pink
                        case "Fire Resistance" -> color = 0xFFA500; // orange
                        default -> color = 0x3498DB; // fallback to blue
                    }
                    String talismanName = "Talisman of " + chosen;
                    stackToAugment.set(DataComponentTypes.CUSTOM_NAME,
                            Text.literal(talismanName)
                                    .styled(style -> style.withItalic(false)
                                            .withColor(color)
                                            .withBold(true)));

                    java.util.List<Text> lore = java.util.List.of(
                            Text.literal(
                                    "Use to replenish Strength II and " + chosen + " for 1 minute 30 seconds.")
                                    .styled(style -> style.withItalic(false).withColor(0xAAAAAA)));
                    stackToAugment.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
                    Main.scheduler.schedule(() -> {
                        player.sendMessage(Text.literal("§bAugmented into a " + talismanName + "!"), false);
                    }, 60);

                } else {
                    // Augment as usual
                    for (java.util.AbstractMap.SimpleEntry<RegistryEntry<EntityAttribute>, Float> entry : Augmentation
                            .rollAttributes(stackToAugment.getItem())) {
                        Utils.addModifier(
                                stackToAugment,
                                "immortals:augmented",
                                AttributeModifierSlot.ANY,
                                entry.getKey(),
                                entry.getValue(),
                                EntityAttributeModifier.Operation.ADD_VALUE);
                    }
                    Main.scheduler.schedule(() -> {
                        player.sendMessage(Text.literal("§aAugmented!"), false);
                    }, 60);

                }
                Utils.augmentationAnimation(stackToAugment.copy(), stackToAugment.getItem(), player);
                stackToAugment.decrement(1);
                Utils.grant((ServerPlayerEntity) player, "augmenter");
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("withdraw")
                    .then(CommandManager.argument("hearts/corruption levels", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                ImmortalsData data = (ImmortalsData) player;

                                int amntToWithdraw = IntegerArgumentType.getInteger(ctx, "hearts/corruption levels");

                                if (data.isImmortal()) {
                                    int maxWithdraw = 0;
                                    for (int i = data.getCorruption(); i > -4; i--) {
                                        maxWithdraw += Utils.nextShardCost(i);
                                    }

                                    if (amntToWithdraw > maxWithdraw) {
                                        player.sendMessage(
                                                Text.literal("Invalid number of corruption levels to withdraw."),
                                                false);
                                        return 0;
                                    } else {
                                        int numShards = 0;
                                        int numPurifiers = 0;

                                        while (data.getCorruption() > 0 && amntToWithdraw > 0) {
                                            numShards += Utils.nextShardCost(data.getCorruption() - 1);
                                            data.setCorruption(data.getCorruption() - 1);
                                            amntToWithdraw -= 1;
                                        }

                                        while (data.getCorruption() <= 0 && amntToWithdraw > 0) {
                                            numPurifiers += 1;
                                            data.setCorruption(data.getCorruption() - 1);
                                            amntToWithdraw -= 1;
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
                                        player.sendMessage(Text.literal(
                                                "Withdrew " + amntToWithdraw + " corruption level(s) and received "
                                                        + (numShards > 0 ? numShards + " soul shard(s)" : "")
                                                        + (numShards > 0 && numPurifiers > 0 ? " and " : "")
                                                        + (numPurifiers > 0 ? numPurifiers + " soul purifier(s)" : "")
                                                        + "."),
                                                false);
                                        return 1;
                                    }
                                } else {
                                    double currentHealth = player.getHealth();
                                    double maxHealth = player.getAttributeInstance(EntityAttributes.MAX_HEALTH)
                                            .getBaseValue();

                                    if (amntToWithdraw < 1 || maxHealth - (amntToWithdraw * 2) < 1) {
                                        player.sendMessage(Text.literal("Invalid amount of hearts to withdraw."),
                                                false);
                                        return 0;
                                    }
                                    double totalHpToWithdraw = amntToWithdraw * 2;

                                    int currentHearts = (int) (maxHealth / 2);
                                    int minHearts = 7;
                                    int regularHearts = Math.max(0, currentHearts - minHearts);
                                    int regularToGive = Math.min(amntToWithdraw, regularHearts);
                                    int artificialToGive = amntToWithdraw - regularToGive;

                                    player.getAttributeInstance(EntityAttributes.MAX_HEALTH)
                                            .setBaseValue(maxHealth - totalHpToWithdraw);
                                    player.setHealth((float) Math.min(currentHealth, maxHealth - totalHpToWithdraw));

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
                                    return 1;
                                }
                            })));
            // Debug command to set ascendance state
            dispatcher.register(CommandManager.literal("setAscendance")
                    .requires(source -> source.hasPermissionLevel(4))
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                            .then(CommandManager.argument("state", IntegerArgumentType.integer(0, 1))
                                    .executes(ctx -> {
                                        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(ctx, "target");
                                        ImmortalsData data = (ImmortalsData) targetPlayer;
                                        boolean newState = IntegerArgumentType.getInteger(ctx, "state") == 1;
                                        if (data.isImmortal() == newState) {
                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                    targetPlayer.getName().getString() + " is already "
                                                            + (newState ? "immortal" : "mortal") + "."),
                                                    false);
                                            return 0;
                                        }
                                        data.setImmortal(newState);
                                        targetPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH)
                                                .setBaseValue(20.0);
                                        ;
                                        Utils.sendSpellInfoToPlayer(targetPlayer, null, "", 0, 0);
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
        });
        // Register 'render' command (debug)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("render").requires(source -> source.hasPermissionLevel(4))
                    .then(CommandManager.literal("sphere")
                            .then(CommandManager.argument("r", IntegerArgumentType.integer(0, 255))
                                    .then(CommandManager.argument("g", IntegerArgumentType.integer(0, 255))
                                            .then(CommandManager.argument("b", IntegerArgumentType.integer(0, 255))
                                                    .then(CommandManager
                                                            .argument("a", IntegerArgumentType.integer(0, 255))
                                                            .then(CommandManager
                                                                    .argument("size",
                                                                            IntegerArgumentType.integer(1, 100))
                                                                    .executes(ctx -> {
                                                                        ServerPlayerEntity player = ctx.getSource()
                                                                                .getPlayer();
                                                                        float r = IntegerArgumentType.getInteger(ctx,
                                                                                "r") / 255.0f;
                                                                        float g = IntegerArgumentType.getInteger(ctx,
                                                                                "g") / 255.0f;
                                                                        float b = IntegerArgumentType.getInteger(ctx,
                                                                                "b") / 255.0f;
                                                                        float a = IntegerArgumentType.getInteger(ctx,
                                                                                "a") / 255.0f;
                                                                        float size = IntegerArgumentType.getInteger(ctx,
                                                                                "size");
                                                                        NetworkChannels.SphereS2CPayload payload = new NetworkChannels.SphereS2CPayload(
                                                                                r, g, b, a,
                                                                                (float) player.getX(),
                                                                                (float) player.getY(),
                                                                                (float) player.getZ(),
                                                                                size, 500);
                                                                        Utils.sendPayloadToNearby(player, payload);
                                                                        player.sendMessage(
                                                                                Text.literal("§aRendered sphere."),
                                                                                false);
                                                                        return 1;
                                                                    })))))))
                    .then(CommandManager.literal("rune")
                            .then(CommandManager
                                    .argument("id", StringArgumentType.string())
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        String id = StringArgumentType.getString(ctx,
                                                "id");
                                        NetworkChannels.RuneS2CPayload payload = new NetworkChannels.RuneS2CPayload(
                                                id, player.getX(), player.getY(), player.getZ(), 14.0f, 500);
                                        Utils.sendPayloadToNearby(player, payload);
                                        player.sendMessage(Text.literal("§aRendered rune: " + id), false);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("item")
                            .then(CommandManager
                                    .argument("itemId", IntegerArgumentType.integer(0))
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        int itemId = IntegerArgumentType.getInteger(ctx, "itemId");
                                        NetworkChannels.ItemS2CPayload payload = new NetworkChannels.ItemS2CPayload(
                                                player.getX(), player.getY(), player.getZ(), player.getX(),
                                                player.getY() + 5, player.getZ(), itemId, 2.0f, 250);
                                        Utils.sendPayloadToNearby(player, payload);
                                        player.sendMessage(Text.literal("§aRendered item: " + itemId), false);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("ghost")
                            .then(CommandManager
                                    .argument("duration", IntegerArgumentType.integer(1, 600))
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        NetworkChannels.GhostS2CPayload payload = new NetworkChannels.GhostS2CPayload(
                                                (float) player.getX(), (float) player.getY(), (float) player.getZ(),
                                                IntegerArgumentType.getInteger(ctx, "duration"));
                                        Utils.sendPayloadToNearby(player, payload);
                                        player.sendMessage(Text.literal("ghost rendered"), false);
                                        return 1;
                                    }))));
        });
    }
}
