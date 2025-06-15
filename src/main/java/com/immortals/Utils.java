package com.immortals;

import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
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
import com.immortals.api.PlayerImmortalsData;
import com.immortals.Immortal.Spell;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.Entity;

public class Utils {

    public static final RegistryKey<DamageType> SPELL_DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of("immortals", "spell"));

    public static DamageSource of(World world, RegistryKey<DamageType> key, Entity attacker) {
        return new DamageSource(world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(key),
                attacker);
    }

    // Return true if the player is ascended (immortal), false otherwise
    public static boolean getAscended(ServerPlayerEntity player) {
        PlayerImmortalsData data = (PlayerImmortalsData) player;
        return data.isImmortal();
    }

    public static void setAscended(ServerPlayerEntity player, boolean ascended) {
        PlayerImmortalsData data = (PlayerImmortalsData) player;
        data.setImmortal(ascended);
        data.setCorruption(0);
        player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(20.0);
    }

    // Return the player's corruption level
    public static int getCorruption(ServerPlayerEntity player) {
        PlayerImmortalsData data = (PlayerImmortalsData) player;
        return data.getCorruption();
    }

    // Add [level] to the player's corruption
    public static void addCorruption(ServerPlayerEntity player, int level) {
        PlayerImmortalsData data = (PlayerImmortalsData) player;
        data.setCorruption(data.getCorruption() + level);
    }

    public static void setCorruption(ServerPlayerEntity player, int level) {
        PlayerImmortalsData data = (PlayerImmortalsData) player;
        data.setCorruption(level);
    }

    // Helper function for getting the next shard cost based on the corruption level
    public static int nextShardCost(int level) {
        return switch (level) {
            case 0, -3, -2, -1 -> 1;
            case 1 -> 2;
            case 2 -> 3;
            default -> 0; // at max (+3)
        };
    }

    public static void applyCorruptionEffects(ServerPlayerEntity player) {
        int level = getCorruption(player);
        int duration = 40; // 2 seconds (40 ticks)

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

        if (level >= 1) {
            // +1: Speed I
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, duration, 0, false, false));
        }

        if (level >= 2) {
            // +2: Speed II, Strength II
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 1, false, false));
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.STRENGTH, duration, 1, false, false));
        }
    }

    public static Integer inventoryHas(ServerPlayerEntity player, Item item) {
        // Check main inventory
        for (int i = 0; i < player.getInventory().main.size(); i++) {
            ItemStack stack = player.getInventory().main.get(i);
            if (stack.isOf(item)) {
                return i;
            }
        }
        // Check offhand (returns main.size() for offhand slot if found)
        ItemStack offhand = player.getInventory().offHand.get(0);
        if (offhand.isOf(item)) {
            return player.getInventory().main.size();
        }
        return null;
    }

    // Draws the dragon ascent rune circle at pos
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

    // Strikes lightning at a given position
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

    public static void drawTimeslow(Vec3d pos, ServerWorld world, int radius, int particles, double handAngle) {
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

    public static ItemStack findInInventory(PlayerEntity p, Item item) {
        for (ItemStack s : p.getInventory().main) {
            if (s.isOf(item))
                return s;
        }
        return null;
    }

    public static void updateRune(PlayerEntity player, String runeName, int value) {
        Scoreboard sb = player.getWorld().getScoreboard();
        ScoreboardObjective obj = sb.getNullableObjective(runeName);
        sb.getOrCreateScore(player, obj).setScore(value);
        sb.setObjectiveSlot(ScoreboardDisplaySlot.LIST, obj);
        Spell.addTask(player.getUuid(), () -> {
            sb.setObjectiveSlot(ScoreboardDisplaySlot.LIST, null);
        }, 250);
    }

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

    public static void grant(ServerPlayerEntity player, String id) {
        Identifier advId = Identifier.of("immortals", id);
        var advEntry = player.server.getAdvancementLoader().get(advId);

        if (advEntry != null) {
            AdvancementProgress progress = player.getAdvancementTracker().getProgress(advEntry);
            if (!progress.isDone()) {
                for (String criterion : progress.getUnobtainedCriteria()) {
                    player.getAdvancementTracker().grantCriterion(advEntry, criterion);
                }
            }
        }
    }
}