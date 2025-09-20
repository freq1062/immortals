package com.immortals;

import net.minecraft.entity.SpawnReason;
import com.immortals.network.NetworkChannels;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import com.immortals.Immortal.Spell;
import com.immortals.Immortal.SpellRegistry;
import com.immortals.api.ImmortalsData;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.Entity;

public class Utils {

    // Custom damage type for spells, bypasses armor
    public static final RegistryKey<DamageType> SPELL_DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of("immortals", "spell"));

    public static DamageSource of(World world, RegistryKey<DamageType> key, Entity attacker) {
        return new DamageSource(world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(key),
                attacker);
    }

    // Returns player data from api
    public static ImmortalsData getPlayerData(ServerPlayerEntity player) {
        if (player instanceof ImmortalsData data) {
            return data;
        }
        throw new IllegalArgumentException("Player does not implement ImmortalsData");
    }

    // Helper function for getting the next shard cost based on the corruption level
    public static int nextShardCost(int level) {
        return switch (level) {
            case 0, -3, -2, -1 -> 1;
            case 1 -> 2;
            case 2 -> 2;
            case 3 -> 2;
            case 4 -> 3;
            default -> 0; // at max (+5)
        };
    }

    // Returns the minimum corruption required to use the spell
    public static int getRequiredCorr(SpellRegistry spell) {
        return switch (spell) {
            case DASH, GLOW -> 1;
            case BLACKOUT, FROSTBITE -> 2;
            case PERSIST, SPLINTER_BLOW -> 3;
            case LINK, WAVE -> 4;
            case FRAGMENT, LOCK -> 5;
            default -> 0;
        };
    }

    // Apply passive corruption effects based on level
    public static void applyCorruptionEffects(ServerPlayerEntity player) {
        ImmortalsData data = (ImmortalsData) player;
        int level = data.getCorruption();
        int duration = 60; // 3 seconds, refreshed every second

        if (level <= -2) {
            // -2: Slowness I
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 0, false, false));
        }

        if (level <= -3) {
            // -3: Weakness I
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.WEAKNESS, duration, 0, false, false));
        }

        if (level <= -1) {
            // -1: 9 max hearts
            player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(18.0);
        }

        if (level >= 1) {
            // +1: Fire resistance
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, duration, 0, false, false));
        }

        if (level >= 2) {
            // +2: Speed II
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 1, false, false));
        }

        if (level >= 3) {
            // +3: Strength 2
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.STRENGTH, duration, 1, false, false));
        }

        if (level >= 4) {
            // +4: Weaving I
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.WEAVING, duration, 0, false, false));
        }
    }

    // Array of spells by corruption level
    public static String[][] spellsByCorr = {
            { "dash", "glow" },
            { "frostbite", "blackout" },
            { "persist", "splinter_blow" },
            { "link", "wave" },
            { "fragment", "lock" }
    };

    // Returns a formatted text listing the spells unlocked for a given corruption
    // level, honestly I put extra cases that aren't necessary
    public static Text getSpellDescriptions(int corr) {
        // Check if the corruption level is valid
        if (corr < 1 || corr > spellsByCorr.length) {
            return Text.literal("No spells unlocked at this level.");
        }

        String[] spells = spellsByCorr[corr - 1];

        // If no spells at this level
        if (spells.length == 0) {
            return Text.literal("No spells unlocked at this level.");
        }

        MutableText message = Text.literal("Unlocked ");

        // Format based on number of spells
        if (spells.length == 1) {
            // For a single spell
            String spellName = spells[0];
            SpellRegistry spell = SpellRegistry.valueOf(spellName.toUpperCase());
            MutableText spellText = Text.literal(spellName)
                    .styled(style -> style.withHoverEvent(
                            new HoverEvent.ShowText(Text.literal(spell.getDescription())))
                            .withColor(0xFFAA00)); // Gold color

            return message.append(spellText).append("! Hover to see details!");

        } else if (spells.length == 2) {
            // For exactly two spells
            String spellName1 = spells[0];
            String spellName2 = spells[1];

            SpellRegistry spell1 = SpellRegistry.valueOf(spellName1.toUpperCase());
            SpellRegistry spell2 = SpellRegistry.valueOf(spellName2.toUpperCase());

            MutableText spellText1 = Text.literal(spellName1)
                    .styled(style -> style.withHoverEvent(
                            new HoverEvent.ShowText(Text.literal(spell1.getDescription())))
                            .withColor(0xFFAA00)); // Gold color

            MutableText spellText2 = Text.literal(spellName2)
                    .styled(style -> style.withHoverEvent(
                            new HoverEvent.ShowText(Text.literal(spell2.getDescription())))
                            .withColor(0xFFAA00)); // Gold color

            return message.append(spellText1)
                    .append(" and ")
                    .append(spellText2)
                    .append("! Hover to see details!");

        } else {
            // For 3 or more spells
            for (int i = 0; i < spells.length; i++) {
                String spellName = spells[i];
                SpellRegistry spell = SpellRegistry.valueOf(spellName.toUpperCase());

                MutableText spellText = Text.literal(spellName)
                        .styled(style -> style.withHoverEvent(
                                new HoverEvent.ShowText(Text.literal(spell.getDescription())))
                                .withColor(0xFFAA00)); // Gold color

                if (i == spells.length - 1) {
                    // Last spell
                    message.append(" and ").append(spellText);
                } else if (i > 0) {
                    // Middle spells
                    message.append(", ").append(spellText);
                } else {
                    // First spell
                    message.append(spellText);
                }
            }

            return message.append("! Hover to see details!");
        }
    }

    public static int currentCooldown(ServerPlayerEntity player, SpellRegistry spell) {
        Map<SpellRegistry, Integer> spellEntry = Spell.pendingCooldownNotifications.getOrDefault(player.getUuid(),
                null);
        if (spellEntry == null)
            return 0;
        Integer currCooldown = spellEntry.get(spell);
        if (currCooldown == null || currCooldown == -1)
            return 0;
        return currCooldown;
    }

    public static String currentSpellState(ServerPlayerEntity player, SpellRegistry spell) {
        // Check if the spell is currently active
        Map<SpellRegistry, Integer> spellEntry = Spell.pendingCooldownNotifications.getOrDefault(player.getUuid(),
                null);
        if (spellEntry == null)
            return "ready";
        Integer currCooldown = spellEntry.get(spell);
        if (currCooldown != null && currCooldown == -1)
            return "in_use";
        return currCooldown == null ? "ready" : "cooldown";
    }

    /*
     * Sends spell info to player for HUD display
     * Updated when:
     * 1) Player changes hotbar slots
     * 2) Player casts a spell (currently active)
     * 3) Spell ends (cooldown starts)
     * 4) Spell cooldown ends (ready again)
     */
    public static void sendSpellInfoToPlayer(ServerPlayerEntity player, @Nullable String spellId,
            String state, int remainingTicks, int maxTicks) {
        NetworkChannels.SpellHudS2CPayload payload = new NetworkChannels.SpellHudS2CPayload(
                spellId == null ? "empty" : spellId, state, remainingTicks, maxTicks);

        ServerPlayNetworking.send(player, payload);
    }

    // Check if the player's inventory has at least one of the given item, and
    // return the slot index or -1 if not found
    public static Integer inventoryHas(ServerPlayerEntity player, Item item) {
        int size = player.getInventory().size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    // Animation for augmenting an item, renders the two items that merge to make
    // the new one
    public static void augmentationAnimation(ItemStack copy, Item item, ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld serverWorld))
            return;

        // Get the player's eye position (at eye level, not just where they're looking)
        Vec3d eyePos = player.getEyePos();

        // Calculate spawn positions to the left and right of the player at eye level
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d left = look.crossProduct(new Vec3d(0, 1, 0)).normalize().multiply(0.7);
        Vec3d right = left.multiply(-1);

        Vec3d pos1 = eyePos.add(left);
        Vec3d pos2 = eyePos.add(right);

        // The final position is the midpoint between pos1 and pos2
        Vec3d finalPos = pos1.add(pos2).multiply(0.5);

        int animDuration = 60;

        // Send two packets: one for the left item, one for the right item
        NetworkChannels.ItemS2CPayload payloadLeft = new NetworkChannels.ItemS2CPayload(
                pos1.x, pos1.y, pos1.z, finalPos.x, finalPos.y, finalPos.z, Item.getRawId(item), (float) 1.0,
                animDuration);
        NetworkChannels.ItemS2CPayload payloadRight = new NetworkChannels.ItemS2CPayload(
                pos2.x, pos2.y, pos2.z, finalPos.x, finalPos.y, finalPos.z, Item.getRawId(ModItems.AUGMENTATION_CORE),
                (float) 1.0, animDuration);

        for (ServerPlayerEntity p : PlayerLookup.world((ServerWorld) player.getWorld())) {
            ServerPlayNetworking.send(p, payloadLeft);
            ServerPlayNetworking.send(p, payloadRight);
        }

        // Animate a trail of particles from pos1 and pos2 to finalPos
        int steps = 30;
        for (int i = 0; i <= steps; i++) {
            final int step = i;
            Main.scheduler.schedule(() -> {
                double t = step / (double) steps;
                Vec3d trail1 = pos1.lerp(finalPos, t);
                Vec3d trail2 = pos2.lerp(finalPos, t);
                serverWorld.spawnParticles(ParticleTypes.ENCHANT, trail1.x, trail1.y, trail1.z, 2, 0, 0, 0, 0.01);
                serverWorld.spawnParticles(ParticleTypes.ENCHANT, trail2.x, trail2.y, trail2.z, 2, 0, 0, 0, 0.01);
            }, animDuration / 20 * step);
        }

        Main.scheduler.schedule(() -> {
            // Remove the items and spawn a blue particle explosion at eye level
            serverWorld.getServer().execute(() -> {
                serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                        eyePos.x, eyePos.y, eyePos.z, 60, 0.4, 0.4, 0.4, 0.15);
                serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
                        eyePos.x, eyePos.y, eyePos.z, 2, 0, 0, 0, 0.1);
                serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANT,
                        eyePos.x, eyePos.y, eyePos.z, 30, 0.3, 0.3, 0.3, 0.2);
            });
            Entity summonedItem = net.minecraft.entity.EntityType.ITEM.create(
                    serverWorld,
                    null,
                    new BlockPos((int) eyePos.x, (int) eyePos.y, (int) eyePos.z),
                    SpawnReason.TRIGGERED,
                    false,
                    false);
            if (summonedItem != null) {
                summonedItem.setPosition(eyePos);
                ((ItemEntity) summonedItem).setStack(copy);
                serverWorld.spawnEntity(summonedItem);
                // Add a burst of particles to highlight the new item
                serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                        eyePos.x, eyePos.y, eyePos.z, 20, 0.2, 0.2, 0.2, 0.2);
            }
        }, animDuration);
    }

    // Sends a client payload to all players nearby including the player
    public static void sendPayloadToNearby(ServerPlayerEntity player, CustomPayload payload) {
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayerEntity p : PlayerLookup
                .tracking(player)) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    // Ascending particles at pos
    public static void drawImmortalEvent(Vec3d pos, World world) {
        if (!(world instanceof ServerWorld serverWorld))
            return;
        double y = pos.y;
        double radius = 2.0;
        double height = 3.0;
        int rings = 8;
        int particlesPerRing = 32;
        for (int i = 0; i < rings; i++) {
            double ringY = y + (i * height / (rings - 1));
            for (int j = 0; j < particlesPerRing; j++) {
                double angle = 2 * Math.PI * j / particlesPerRing;
                double x = pos.x + radius * Math.cos(angle);
                double z = pos.z + radius * Math.sin(angle);
                serverWorld.spawnParticles(ParticleTypes.CRIMSON_SPORE, x, ringY, z, 1, 0, 0, 0, 0);
            }
        }
    }

    // Add an attribute modifier to an ItemStack
    public static void addModifier(
            ItemStack itemStack,
            String identifier,
            AttributeModifierSlot slot,
            RegistryEntry<EntityAttribute> attribute,
            double amount,
            EntityAttributeModifier.Operation operation) {

        AttributeModifiersComponent existingComponent = itemStack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);

        AttributeModifiersComponent.Builder modifierComponent = AttributeModifiersComponent.builder();
        if (existingComponent != null) {
            existingComponent.modifiers().forEach(entry -> {
                modifierComponent.add(entry.attribute(), entry.modifier(), entry.slot());
            });
        }

        EntityAttributeModifier modifier = new EntityAttributeModifier(
                net.minecraft.util.Identifier.of(identifier),
                amount,
                operation);
        modifierComponent.add(attribute, modifier, slot);
        itemStack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, modifierComponent.build());
    }

    public static void removeModifierById(ItemStack itemStack, String id) {
        AttributeModifiersComponent existingComponent = itemStack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (existingComponent == null)
            return;

        AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder();
        existingComponent.modifiers().forEach(entry -> {
            if (!id.equals(entry.modifier().id().toString())) {
                builder.add(entry.attribute(), entry.modifier(), entry.slot());
            }
        });
        itemStack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
    }

    public static boolean hasAttribute(ItemStack itemStack, String attributeIdOrModifierId) {
        AttributeModifiersComponent existingComponent = itemStack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (existingComponent == null)
            return false;

        return existingComponent.modifiers().stream()
                .anyMatch(entry -> attributeIdOrModifierId.equals(entry.attribute().getKey().get().toString())
                        || attributeIdOrModifierId.equals(entry.modifier().id().toString()));
    }

    // Grant an achievement from immortals
    public static void grant(ServerPlayerEntity player, String id) {
        Identifier advId = Identifier.of("immortals", id);
        var advEntry = player.getServer().getAdvancementLoader().get(advId);

        if (advEntry != null) {
            AdvancementProgress progress = player.getAdvancementTracker().getProgress(advEntry);
            if (!progress.isDone()) {
                for (String criterion : progress.getUnobtainedCriteria()) {
                    player.getAdvancementTracker().grantCriterion(advEntry, criterion);
                }
            }
        }
    }

    // Send message through webhook for supply drops
    public static void sendDiscordWebhook(String content) {
        System.out.println("Sending Discord webhook with content: " + content);
        try {
            URL url = java.net.URI.create((String) Main.CONFIG.getString("supplyDropWebhookURL")).toURL();
            System.out.println("Discord webhook URL: " + url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            System.out.println("Sending Discord webhook to: " + url);

            String jsonPayload = "{\"content\":\"" + content.replace("\"", "\\\"") + "\"}";

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204) {
                System.out.println("Discord webhook failed with code: " + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}