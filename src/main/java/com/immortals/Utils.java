package com.immortals;

import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import com.immortals.Immortal.SpellRegistry;
import com.immortals.api.ImmortalsData;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.Entity;

public class Utils {

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
            case FRAGMENT, BEAM -> 5;
            default -> 0;
        };
    }

    public static void applyCorruptionEffects(ServerPlayerEntity player) {
        ImmortalsData data = (ImmortalsData) player;
        int level = data.getCorruption();
        int duration = 60; // 3 seconds (60 ticks)

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
            // +2: Speed II, Strength II
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 1, false, false));
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.STRENGTH, duration, 1, false, false));
        }
    }

    public static String[][] spellsByCorr = {
            { "dash", "glow" },
            { "frostbite", "blackout" },
            { "persist", "splinter_blow" },
            {},
            { "fragment", "beam" }
    };

    public static Text getSpellDescriptions(int corr) {
        // Check if the corruption level is valid
        if (corr < 1 || corr > spellsByCorr.length) {
            return Text.literal("No spells unlocked at this level.");
        }

        // Get the spells for the given corruption level (subtract 1 since array is
        // 0-indexed)
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
                            .withColor(0xFF0000)); // Red color

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

    public static Integer inventoryHas(ServerPlayerEntity player, Item item) {
        // offhand and armor slots are 0-7
        int size = player.getInventory().size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                return i;
            }
        }
        return null;
    }

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

        // Spawn the two item entities
        Entity itemEntity1 = net.minecraft.entity.EntityType.ITEM.create(
                serverWorld,
                null,
                new BlockPos((int) pos1.x, (int) pos1.y, (int) pos1.z),
                SpawnReason.TRIGGERED,
                false,
                false);
        Entity itemEntity2 = net.minecraft.entity.EntityType.ITEM.create(
                serverWorld,
                null,
                new BlockPos((int) pos2.x, (int) pos2.y, (int) pos2.z),
                SpawnReason.TRIGGERED,
                false,
                false);

        if (itemEntity1 == null || itemEntity2 == null)
            return;

        itemEntity1.setPosition(pos1);
        itemEntity2.setPosition(pos2);

        ((net.minecraft.entity.ItemEntity) itemEntity1).setStack(new ItemStack(item));
        ((net.minecraft.entity.ItemEntity) itemEntity2)
                .setStack(new ItemStack(com.immortals.ModItems.AUGMENTATION_CORE));

        // Make the item entities ignore gravity
        ((net.minecraft.entity.ItemEntity) itemEntity1).setNoGravity(true);
        ((net.minecraft.entity.ItemEntity) itemEntity2).setNoGravity(true);

        serverWorld.spawnEntity(itemEntity1);
        serverWorld.spawnEntity(itemEntity2);

        // Animate the items moving together over a longer duration (e.g., 40 steps)
        final int steps = 100;
        for (int i = 1; i <= steps; i++) {
            final int step = i;
            Main.scheduler.schedule(() -> {
                double t = step / (double) steps;
                Vec3d interp1 = pos1.lerp(eyePos, t);
                Vec3d interp2 = pos2.lerp(eyePos, t);
                serverWorld.getServer().execute(() -> {
                    itemEntity1.setPosition(interp1);
                    itemEntity2.setPosition(interp2);
                    // Add some particles along the path for visual effect
                    serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                            interp1.x, interp1.y, interp1.z, 2, 0.05, 0.05, 0.05, 0.01);
                    serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                            interp2.x, interp2.y, interp2.z, 2, 0.05, 0.05, 0.05, 0.01);
                });
            }, step);
        }

        Main.scheduler.schedule(() -> {
            // Remove the items and spawn a blue particle explosion at eye level
            serverWorld.getServer().execute(() -> {
                itemEntity1.discard();
                itemEntity2.discard();
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
                ((net.minecraft.entity.ItemEntity) summonedItem).setStack(copy);
                serverWorld.spawnEntity(summonedItem);
                // Add a burst of particles to highlight the new item
                serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                        eyePos.x, eyePos.y, eyePos.z, 20, 0.2, 0.2, 0.2, 0.2);
            }
        }, steps + 20);
    }

    // Dragon ascent breath particles at pos
    public static void drawDragonAscent(Vec3d pos, World world) {
        if (!(world instanceof ServerWorld serverWorld))
            return;
        double y = pos.y;
        int particleCount = 100; // Number of particles to spawn
        double radius = 30.0;
        for (int i = 0; i < particleCount; i++) {
            double angle = Math.random() * 2 * Math.PI;
            double dist = Math.sqrt(Math.random()) * radius; // Uniform distribution in circle
            double x = pos.x + Math.cos(angle) * dist;
            double z = pos.z + Math.sin(angle) * dist;
            double py = y + (Math.random() - 0.5) * 2; // Small vertical variation
            serverWorld.spawnParticles(ParticleTypes.PORTAL, x, py, z, 1, 0, 0, 0, 0);
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

    // Strikes visual-only lightning at a given position
    public static void strikeLightning(ServerWorld world, Vec3d position) {
        LightningEntity lightningBolt = EntityType.LIGHTNING_BOLT.create(
                world,
                entity -> {
                }, // No-op consumer
                new BlockPos((int) position.x, (int) position.y, (int) position.z),
                SpawnReason.TRIGGERED,
                true,
                true);
        if (lightningBolt != null) {
            lightningBolt.setCosmetic(true); // Mark the lightning as cosmetic to prevent damage
            world.spawnEntity(lightningBolt);
        }
    }

    // Timeslow dynamic clock particles
    public static void drawTimeslow(Vec3d pos, ServerWorld world, double radius, int particles, double handAngle) {
        // Draw the circle
        for (int i = 0; i < particles; i++) {
            double angle = 2 * Math.PI * i / particles;
            double x = pos.x + radius * Math.cos(angle);
            double z = pos.z + radius * Math.sin(angle);
            double y = pos.y + 0.1;
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0, 0, 0, 0f);
        }

        // Draw 8 clock lines (static)
        int clockLines = 8;
        double lineLength = radius * 0.9;
        for (int i = 0; i < clockLines; i++) {
            double angle = 2 * Math.PI * i / clockLines;
            double x1 = pos.x + (radius - 0.2) * Math.cos(angle);
            double z1 = pos.z + (radius - 0.2) * Math.sin(angle);
            double x2 = pos.x + lineLength * Math.cos(angle);
            double z2 = pos.z + lineLength * Math.sin(angle);
            double y = pos.y + 0.1;
            // Draw a line from x1,z1 to x2,z2 (5 particles)
            for (int j = 0; j <= 5; j++) {
                double frac = j / 5.0;
                double px = x1 + (x2 - x1) * frac;
                double pz = z1 + (z2 - z1) * frac;
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, y, pz, 1, 0, 0, 0, 0f);
            }
        }

        // Draw the moving minute hand
        double hx1 = pos.x;
        double hz1 = pos.z;
        double hx2 = pos.x + (radius - 0.3) * Math.cos(handAngle);
        double hz2 = pos.z + (radius - 0.3) * Math.sin(handAngle);
        double hy = pos.y + 0.12;
        // Draw the hand as a line
        for (int j = 0; j <= 10; j++) {
            double frac = j / 10.0;
            double px = hx1 + (hx2 - hx1) * frac;
            double pz = hz1 + (hz2 - hz1) * frac;
            world.spawnParticles(ParticleTypes.GLOW, px, hy, pz, 1, 0, 0, 0, 0f);
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