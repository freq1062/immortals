package com.immortals.Mortal;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.item.*;

public class Augmentation {
    public enum AugmentItemType {
        SWORD, AXE, MACE, TRIDENT, TOOL, SHIELD, ARMOR, TOTEM, OTHER
    }

    private static record AttributeRange(RegistryEntry<EntityAttribute> attribute, float min, float max) {
    }

    private static final Map<AugmentItemType, Map<Integer, List<AttributeRange>>> POOLS = new HashMap<>();

    static {
        // SWORD
        Map<Integer, List<AttributeRange>> sword = new HashMap<>();
        sword.put(10, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.1f, 0.3f),
                new AttributeRange(EntityAttributes.FOLLOW_RANGE, -2f, -5f),
                new AttributeRange(EntityAttributes.MOVEMENT_SPEED, 0.005f, 0.01f),
                new AttributeRange(EntityAttributes.SWEEPING_DAMAGE_RATIO, 0.1f, 0.2f)));
        sword.put(14, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.3f, 0.5f),
                new AttributeRange(EntityAttributes.FOLLOW_RANGE, -5f, -10f),
                new AttributeRange(EntityAttributes.MOVEMENT_SPEED, 0.01f, 0.02f),
                new AttributeRange(EntityAttributes.SWEEPING_DAMAGE_RATIO, 0.2f, 0.4f)));
        sword.put(18, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.5f, 0.8f),
                new AttributeRange(EntityAttributes.FOLLOW_RANGE, -10f, -15f),
                new AttributeRange(EntityAttributes.MOVEMENT_SPEED, 0.02f, 0.03f),
                new AttributeRange(EntityAttributes.SWEEPING_DAMAGE_RATIO, 0.4f, 0.6f)));
        sword.put(20, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.5f, 1.0f),
                new AttributeRange(EntityAttributes.FOLLOW_RANGE, -15f, -20f),
                new AttributeRange(EntityAttributes.MOVEMENT_SPEED, 0.03f, 0.05f),
                new AttributeRange(EntityAttributes.SWEEPING_DAMAGE_RATIO, 0.6f, 0.8f)));
        POOLS.put(AugmentItemType.SWORD, sword);

        // AXE
        Map<Integer, List<AttributeRange>> axe = new HashMap<>();
        axe.put(10, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.1f, 0.3f),
                new AttributeRange(EntityAttributes.FOLLOW_RANGE, -2f, -4f),
                new AttributeRange(EntityAttributes.MOVEMENT_EFFICIENCY, 0.01f, 0.03f),
                new AttributeRange(EntityAttributes.SAFE_FALL_DISTANCE, 1f, 2f)));
        axe.put(14, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.3f, 0.5f),
                new AttributeRange(EntityAttributes.FOLLOW_RANGE, -4f, -8f),
                new AttributeRange(EntityAttributes.MOVEMENT_EFFICIENCY, 0.03f, 0.05f),
                new AttributeRange(EntityAttributes.SAFE_FALL_DISTANCE, 3f, 5f)));
        axe.put(18, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.5f, 0.8f),
                new AttributeRange(EntityAttributes.FOLLOW_RANGE, -8f, -12f),
                new AttributeRange(EntityAttributes.MOVEMENT_EFFICIENCY, 0.05f, 0.08f),
                new AttributeRange(EntityAttributes.SAFE_FALL_DISTANCE, 5f, 7f)));
        axe.put(20, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.8f, 1.0f),
                new AttributeRange(EntityAttributes.FOLLOW_RANGE, -12f, -16f),
                new AttributeRange(EntityAttributes.MOVEMENT_EFFICIENCY, 0.08f, 0.1f),
                new AttributeRange(EntityAttributes.SAFE_FALL_DISTANCE, 7f, 10f)));
        POOLS.put(AugmentItemType.AXE, axe);

        // MACE
        Map<Integer, List<AttributeRange>> mace = new HashMap<>();
        mace.put(10, Arrays.asList(
                new AttributeRange(EntityAttributes.JUMP_STRENGTH, 0.033f, 0.1f),
                new AttributeRange(EntityAttributes.MOVEMENT_SPEED, 0.005f, 0.01f)));
        mace.put(14, Arrays.asList(
                new AttributeRange(EntityAttributes.JUMP_STRENGTH, 0.1f, 0.167f),
                new AttributeRange(EntityAttributes.MOVEMENT_SPEED, 0.01f, 0.015f)));
        mace.put(18, Arrays.asList(
                new AttributeRange(EntityAttributes.JUMP_STRENGTH, 0.167f, 0.233f),
                new AttributeRange(EntityAttributes.MOVEMENT_SPEED, 0.015f, 0.025f)));
        mace.put(20, Arrays.asList(
                new AttributeRange(EntityAttributes.JUMP_STRENGTH, 0.233f, 0.3f),
                new AttributeRange(EntityAttributes.MOVEMENT_SPEED, 0.025f, 0.035f)));
        POOLS.put(AugmentItemType.MACE, mace);

        // TRIDENT
        Map<Integer, List<AttributeRange>> trident = new HashMap<>();
        trident.put(10, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.1f, 0.3f),
                new AttributeRange(EntityAttributes.OXYGEN_BONUS, 2.5f, 5f),
                new AttributeRange(EntityAttributes.LUCK, 0.1f, 0.3f),
                new AttributeRange(EntityAttributes.SUBMERGED_MINING_SPEED, 1.1f, 1.2f),
                new AttributeRange(EntityAttributes.WATER_MOVEMENT_EFFICIENCY, 1.1f, 1.2f)));
        trident.put(14, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.3f, 0.5f),
                new AttributeRange(EntityAttributes.OXYGEN_BONUS, 5f, 10f),
                new AttributeRange(EntityAttributes.LUCK, 0.3f, 0.5f),
                new AttributeRange(EntityAttributes.SUBMERGED_MINING_SPEED, 1.2f, 1.4f),
                new AttributeRange(EntityAttributes.WATER_MOVEMENT_EFFICIENCY, 1.2f, 1.4f)));
        trident.put(18, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.5f, 0.8f),
                new AttributeRange(EntityAttributes.OXYGEN_BONUS, 10f, 15f),
                new AttributeRange(EntityAttributes.LUCK, 0.5f, 0.8f),
                new AttributeRange(EntityAttributes.SUBMERGED_MINING_SPEED, 1.4f, 1.6f),
                new AttributeRange(EntityAttributes.WATER_MOVEMENT_EFFICIENCY, 1.4f, 1.6f)));
        trident.put(20, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.8f, 1.0f),
                new AttributeRange(EntityAttributes.OXYGEN_BONUS, 15f, 20f),
                new AttributeRange(EntityAttributes.LUCK, 0.8f, 1.0f),
                new AttributeRange(EntityAttributes.SUBMERGED_MINING_SPEED, 1.6f, 1.8f),
                new AttributeRange(EntityAttributes.WATER_MOVEMENT_EFFICIENCY, 1.6f, 1.8f)));
        POOLS.put(AugmentItemType.TRIDENT, trident);

        // TOOL (Shovel/Pickaxe/Hoe/Shears)
        Map<Integer, List<AttributeRange>> tool = new HashMap<>();
        tool.put(10, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_KNOCKBACK, 0.1f, 0.3f),
                new AttributeRange(EntityAttributes.BLOCK_BREAK_SPEED, 0.2f, 0.4f),
                new AttributeRange(EntityAttributes.MINING_EFFICIENCY, 1.1f, 1.2f),
                new AttributeRange(EntityAttributes.LUCK, 0.1f, 0.3f),
                new AttributeRange(EntityAttributes.SUBMERGED_MINING_SPEED, 1.1f, 1.2f)));
        tool.put(14, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_KNOCKBACK, 0.3f, 0.5f),
                new AttributeRange(EntityAttributes.BLOCK_BREAK_SPEED, 0.4f, 0.7f),
                new AttributeRange(EntityAttributes.MINING_EFFICIENCY, 1.2f, 1.4f),
                new AttributeRange(EntityAttributes.LUCK, 0.3f, 0.5f),
                new AttributeRange(EntityAttributes.SUBMERGED_MINING_SPEED, 1.2f, 1.4f)));
        tool.put(18, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_KNOCKBACK, 0.5f, 0.8f),
                new AttributeRange(EntityAttributes.BLOCK_BREAK_SPEED, 0.7f, 1.0f),
                new AttributeRange(EntityAttributes.MINING_EFFICIENCY, 1.4f, 1.6f),
                new AttributeRange(EntityAttributes.LUCK, 0.5f, 0.8f),
                new AttributeRange(EntityAttributes.SUBMERGED_MINING_SPEED, 1.4f, 1.6f)));
        tool.put(20, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_KNOCKBACK, 0.8f, 1.0f),
                new AttributeRange(EntityAttributes.BLOCK_BREAK_SPEED, 1.0f, 1.2f),
                new AttributeRange(EntityAttributes.MINING_EFFICIENCY, 1.6f, 1.8f),
                new AttributeRange(EntityAttributes.LUCK, 0.8f, 1.0f),
                new AttributeRange(EntityAttributes.SUBMERGED_MINING_SPEED, 1.6f, 1.8f)));
        POOLS.put(AugmentItemType.TOOL, tool);

        // SHIELD
        Map<Integer, List<AttributeRange>> shield = new HashMap<>();
        shield.put(10, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.1f, 0.3f),
                new AttributeRange(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE, 0.01f, 0.02f),
                new AttributeRange(EntityAttributes.SNEAKING_SPEED, 1.05f, 1.05f)));
        shield.put(14, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.3f, 0.5f),
                new AttributeRange(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE, 0.02f, 0.04f),
                new AttributeRange(EntityAttributes.SNEAKING_SPEED, 1.1f, 1.1f)));
        shield.put(18, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.5f, 0.7f),
                new AttributeRange(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE, 0.04f, 0.06f),
                new AttributeRange(EntityAttributes.SNEAKING_SPEED, 1.2f, 1.2f)));
        shield.put(20, Arrays.asList(
                new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.7f, 0.9f),
                new AttributeRange(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE, 0.06f, 0.08f),
                new AttributeRange(EntityAttributes.SNEAKING_SPEED, 1.3f, 1.3f)));
        POOLS.put(AugmentItemType.SHIELD, shield);

        // ARMOR/ELYTRA
        Map<Integer, List<AttributeRange>> armor = new HashMap<>();
        armor.put(10, Arrays.asList(
                new AttributeRange(EntityAttributes.ARMOR_TOUGHNESS, 0.25f, 0.5f),
                new AttributeRange(EntityAttributes.BURNING_TIME, -0.1f, -0.2f),
                new AttributeRange(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE, 0.01f, 0.02f),
                new AttributeRange(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 1.1f, 1.05f),
                new AttributeRange(EntityAttributes.OXYGEN_BONUS, 2.5f, 2.5f)));
        armor.put(14, Arrays.asList(
                new AttributeRange(EntityAttributes.ARMOR_TOUGHNESS, 0.5f, 0.75f),
                new AttributeRange(EntityAttributes.BURNING_TIME, -0.3f, -0.4f),
                new AttributeRange(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE, 0.02f, 0.03f),
                new AttributeRange(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 1.2f, 1.2f),
                new AttributeRange(EntityAttributes.OXYGEN_BONUS, 5f, 5f)));
        armor.put(18, Arrays.asList(
                new AttributeRange(EntityAttributes.ARMOR_TOUGHNESS, 0.75f, 1.0f),
                new AttributeRange(EntityAttributes.BURNING_TIME, -0.5f, -0.6f),
                new AttributeRange(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE, 0.03f, 0.05f),
                new AttributeRange(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 1.3f, 1.3f),
                new AttributeRange(EntityAttributes.OXYGEN_BONUS, 7.5f, 7.5f)));
        armor.put(20, Arrays.asList(
                new AttributeRange(EntityAttributes.ARMOR_TOUGHNESS, 1.0f, 1.25f),
                new AttributeRange(EntityAttributes.BURNING_TIME, -0.7f, -0.8f),
                new AttributeRange(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE, 0.05f, 0.07f),
                new AttributeRange(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 1.5f, 1.5f),
                new AttributeRange(EntityAttributes.OXYGEN_BONUS, 10f, 10f)));
        POOLS.put(AugmentItemType.ARMOR, armor);

        // TOTEM
        Map<Integer, List<AttributeRange>> totem = new HashMap<>();
        totem.put(10, Arrays.asList(
                new AttributeRange(EntityAttributes.ARMOR_TOUGHNESS, 0.25f, 0.5f),
                new AttributeRange(EntityAttributes.KNOCKBACK_RESISTANCE, 0.01f, 0.02f),
                new AttributeRange(EntityAttributes.LUCK, 0.1f, 0.3f)));
        totem.put(14, Arrays.asList(
                new AttributeRange(EntityAttributes.ARMOR_TOUGHNESS, 0.5f, 0.75f),
                new AttributeRange(EntityAttributes.KNOCKBACK_RESISTANCE, 0.02f, 0.03f),
                new AttributeRange(EntityAttributes.LUCK, 0.3f, 0.5f)));
        totem.put(18, Arrays.asList(
                new AttributeRange(EntityAttributes.ARMOR_TOUGHNESS, 0.75f, 1.0f),
                new AttributeRange(EntityAttributes.KNOCKBACK_RESISTANCE, 0.03f, 0.04f),
                new AttributeRange(EntityAttributes.LUCK, 0.5f, 0.8f)));
        totem.put(20, Arrays.asList(
                new AttributeRange(EntityAttributes.ARMOR_TOUGHNESS, 1.0f, 1.25f),
                new AttributeRange(EntityAttributes.KNOCKBACK_RESISTANCE, 0.04f, 0.05f),
                new AttributeRange(EntityAttributes.LUCK, 0.8f, 1.0f)));
        POOLS.put(AugmentItemType.TOTEM, totem);

        // OTHER
        Map<Integer, List<AttributeRange>> other = new HashMap<>();
        other.put(10, Arrays.asList(
                new AttributeRange(EntityAttributes.BURNING_TIME, -0.1f, -0.1f),
                new AttributeRange(EntityAttributes.FOLLOW_RANGE, -2f, -4f),
                new AttributeRange(EntityAttributes.LUCK, 0.1f, 0.3f),
                new AttributeRange(EntityAttributes.MOVEMENT_EFFICIENCY, 1.05f, 1.1f),
                new AttributeRange(EntityAttributes.OXYGEN_BONUS, 2.5f, 5f),
                new AttributeRange(EntityAttributes.TEMPT_RANGE, 1f, 3f),
                new AttributeRange(EntityAttributes.KNOCKBACK_RESISTANCE, 0.01f, 0.02f)));
        other.put(14, Arrays.asList(
                new AttributeRange(EntityAttributes.BURNING_TIME, -0.2f, -0.2f),
                new AttributeRange(EntityAttributes.FOLLOW_RANGE, -4f, -6f),
                new AttributeRange(EntityAttributes.LUCK, 0.3f, 0.5f),
                new AttributeRange(EntityAttributes.MOVEMENT_EFFICIENCY, 1.1f, 1.2f),
                new AttributeRange(EntityAttributes.OXYGEN_BONUS, 5f, 7.5f),
                new AttributeRange(EntityAttributes.TEMPT_RANGE, 3f, 5f),
                new AttributeRange(EntityAttributes.KNOCKBACK_RESISTANCE, 0.02f, 0.03f)));
        other.put(18, Arrays.asList(
                new AttributeRange(EntityAttributes.BURNING_TIME, -0.4f, -0.4f),
                new AttributeRange(EntityAttributes.FOLLOW_RANGE, -6f, -8f),
                new AttributeRange(EntityAttributes.LUCK, 0.5f, 0.8f),
                new AttributeRange(EntityAttributes.MOVEMENT_EFFICIENCY, 1.2f, 1.3f),
                new AttributeRange(EntityAttributes.OXYGEN_BONUS, 7.5f, 10f),
                new AttributeRange(EntityAttributes.TEMPT_RANGE, 5f, 7f),
                new AttributeRange(EntityAttributes.KNOCKBACK_RESISTANCE, 0.03f, 0.04f)));
        other.put(20, Arrays.asList(
                new AttributeRange(EntityAttributes.BURNING_TIME, -0.6f, -0.6f),
                new AttributeRange(EntityAttributes.FOLLOW_RANGE, -8f, -10f),
                new AttributeRange(EntityAttributes.LUCK, 0.8f, 1.0f),
                new AttributeRange(EntityAttributes.MOVEMENT_EFFICIENCY, 1.3f, 1.5f),
                new AttributeRange(EntityAttributes.OXYGEN_BONUS, 10f, 12.5f),
                new AttributeRange(EntityAttributes.TEMPT_RANGE, 7f, 10f),
                new AttributeRange(EntityAttributes.KNOCKBACK_RESISTANCE, 0.04f, 0.05f)));
        POOLS.put(AugmentItemType.OTHER, other);
    }

    private static final Random RANDOM = new Random();

    /**
     * Determines the AugmentItemType for a given Item
     * 
     * @param item The Minecraft item to categorize
     * @return The appropriate AugmentItemType category
     */
    public static AugmentItemType getItemType(Item item) {
        if (item instanceof SwordItem) {
            return AugmentItemType.SWORD;
        } else if (item instanceof AxeItem) {
            return AugmentItemType.AXE;
        } else if (item instanceof TridentItem) {
            return AugmentItemType.TRIDENT;
        } else if (item instanceof PickaxeItem || item instanceof ShovelItem ||
                item instanceof HoeItem || item instanceof ShearsItem) {
            return AugmentItemType.TOOL;
        } else if (item instanceof ShieldItem) {
            return AugmentItemType.SHIELD;
        } else if (item instanceof ArmorItem) {
            return AugmentItemType.ARMOR;
        } else if (item instanceof Item && item == Items.TOTEM_OF_UNDYING) {
            return AugmentItemType.TOTEM;
        } else if (item.toString().toLowerCase().contains("mace")) { // Custom check for mace items
            return AugmentItemType.MACE;
        } else {
            return AugmentItemType.OTHER;
        }
    }

    /**
     * Rolls two random attribute ranges from the appropriate pool based on player's
     * max hearts and item type.
     *
     * @param numHearts total player hearts (e.g. getHealth() / 2)
     * @param item      the Minecraft item
     * @return two randomly selected AttributeRange entries
     */
    public static List<AbstractMap.SimpleEntry<RegistryEntry<EntityAttribute>, Float>> rollAttributes(
            double numHearts, Item item) {

        AugmentItemType type = getItemType(item);

        int key;
        if (numHearts <= 10)
            key = 10;
        else if (numHearts <= 14)
            key = 14;
        else if (numHearts <= 18)
            key = 18;
        else
            key = 20;

        List<AttributeRange> pool = POOLS.getOrDefault(type, POOLS.get(AugmentItemType.OTHER)).get(key);
        if (pool == null || pool.size() < 2)
            return List.of();

        int firstIdx = RANDOM.nextInt(pool.size());
        int secondIdx;
        do {
            secondIdx = RANDOM.nextInt(pool.size());
        } while (secondIdx == firstIdx);

        List<AttributeRange> selectedRanges = Arrays.asList(pool.get(firstIdx), pool.get(secondIdx));

        return selectedRanges.stream()
                .map(range -> {
                    float value = range.min + RANDOM.nextFloat() * (range.max - range.min);
                    return new AbstractMap.SimpleEntry<>(range.attribute(), value);
                })
                .toList();
    }
}
