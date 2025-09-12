package com.immortals.Mortal;

import java.util.Collections;
import java.util.List;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.item.*;

public class Augmentation {
    public enum AugmentItemType {
        SWORD, AXE, MACE, TRIDENT, TOOL, SHIELD, ARMOR, TOTEM, OTHER
    }

    // Define attribute object that stores a single value for each attribute
    private static class AttributeConfig {
        private final RegistryEntry<EntityAttribute> attribute;
        private final float value;

        public AttributeConfig(RegistryEntry<EntityAttribute> attribute, float value) {
            this.attribute = attribute;
            this.value = value;
        }

        public RegistryEntry<EntityAttribute> getAttribute() {
            return attribute;
        }

        public float getValue() {
            return value;
        }
    }

    // Map item types to their attributes (first attribute is guaranteed)
    private static final Map<AugmentItemType, List<AttributeConfig>> ITEM_ATTRIBUTES = new HashMap<>();

    static {
        // SWORD attributes
        List<AttributeConfig> swordAttrs = new ArrayList<>();
        // Guaranteed attribute
        swordAttrs.add(new AttributeConfig(EntityAttributes.ATTACK_DAMAGE, 1.0f));
        // Other attributes
        swordAttrs.add(new AttributeConfig(EntityAttributes.FOLLOW_RANGE, -8f));
        swordAttrs.add(new AttributeConfig(EntityAttributes.SWEEPING_DAMAGE_RATIO, 0.4f));
        ITEM_ATTRIBUTES.put(AugmentItemType.SWORD, swordAttrs);

        // AXE attributes
        List<AttributeConfig> axeAttrs = new ArrayList<>();
        // Guaranteed attribute
        axeAttrs.add(new AttributeConfig(EntityAttributes.ATTACK_DAMAGE, 1.0f));
        // Other attributes
        axeAttrs.add(new AttributeConfig(EntityAttributes.FOLLOW_RANGE, -8f));
        axeAttrs.add(new AttributeConfig(EntityAttributes.MOVEMENT_EFFICIENCY, 1f));
        axeAttrs.add(new AttributeConfig(EntityAttributes.SAFE_FALL_DISTANCE, 3f));
        ITEM_ATTRIBUTES.put(AugmentItemType.AXE, axeAttrs);

        // MACE attributes
        List<AttributeConfig> maceAttrs = new ArrayList<>();
        // Guaranteed attribute
        maceAttrs.add(new AttributeConfig(EntityAttributes.JUMP_STRENGTH, 0.15f));
        // Other attributes
        maceAttrs.add(new AttributeConfig(EntityAttributes.MOVEMENT_SPEED, 0.01f));
        ITEM_ATTRIBUTES.put(AugmentItemType.MACE, maceAttrs);

        // TRIDENT attributes
        List<AttributeConfig> tridentAttrs = new ArrayList<>();
        // Guaranteed attribute
        tridentAttrs.add(new AttributeConfig(EntityAttributes.WATER_MOVEMENT_EFFICIENCY, 0.5f));
        // Other attributes
        tridentAttrs.add(new AttributeConfig(EntityAttributes.OXYGEN_BONUS, 6f));
        tridentAttrs.add(new AttributeConfig(EntityAttributes.LUCK, 1.0f));
        tridentAttrs.add(new AttributeConfig(EntityAttributes.SUBMERGED_MINING_SPEED, 1f));
        tridentAttrs.add(new AttributeConfig(EntityAttributes.ATTACK_DAMAGE, 1.0f));
        ITEM_ATTRIBUTES.put(AugmentItemType.TRIDENT, tridentAttrs);

        // TOOL attributes
        List<AttributeConfig> toolAttrs = new ArrayList<>();
        // Guaranteed attribute
        toolAttrs.add(new AttributeConfig(EntityAttributes.BLOCK_BREAK_SPEED, 0.4f));
        // Other attributes
        toolAttrs.add(new AttributeConfig(EntityAttributes.ATTACK_KNOCKBACK, 1.0f));
        toolAttrs.add(new AttributeConfig(EntityAttributes.MINING_EFFICIENCY, 0.4f));
        toolAttrs.add(new AttributeConfig(EntityAttributes.LUCK, 1.0f));
        toolAttrs.add(new AttributeConfig(EntityAttributes.SUBMERGED_MINING_SPEED, 1f));
        ITEM_ATTRIBUTES.put(AugmentItemType.TOOL, toolAttrs);

        // SHIELD attributes
        List<AttributeConfig> shieldAttrs = new ArrayList<>();
        // Guaranteed attribute
        shieldAttrs.add(new AttributeConfig(EntityAttributes.ARMOR, 2f));
        // Other attributes
        shieldAttrs.add(new AttributeConfig(EntityAttributes.ATTACK_DAMAGE, 0.5f));
        shieldAttrs.add(new AttributeConfig(EntityAttributes.LUCK, 1.0f));
        shieldAttrs.add(new AttributeConfig(EntityAttributes.SNEAKING_SPEED, 0.6f));
        ITEM_ATTRIBUTES.put(AugmentItemType.SHIELD, shieldAttrs);

        // ARMOR attributes
        List<AttributeConfig> armorAttrs = new ArrayList<>();
        // Guaranteed attribute
        armorAttrs.add(new AttributeConfig(EntityAttributes.ARMOR_TOUGHNESS, 4f));
        // Other attributes
        armorAttrs.add(new AttributeConfig(EntityAttributes.BURNING_TIME, -0.5f));
        armorAttrs.add(new AttributeConfig(EntityAttributes.FALL_DAMAGE_MULTIPLIER, -0.4f));
        armorAttrs.add(new AttributeConfig(EntityAttributes.OXYGEN_BONUS, 6f));
        ITEM_ATTRIBUTES.put(AugmentItemType.ARMOR, armorAttrs);

        // TOTEM attributes
        List<AttributeConfig> totemAttrs = new ArrayList<>();
        // Guaranteed attribute
        totemAttrs.add(new AttributeConfig(EntityAttributes.LUCK, 1.0f));
        // Other attributes
        totemAttrs.add(new AttributeConfig(EntityAttributes.KNOCKBACK_RESISTANCE, 0.05f));
        totemAttrs.add(new AttributeConfig(EntityAttributes.ARMOR_TOUGHNESS, 2f));
        totemAttrs.add(new AttributeConfig(EntityAttributes.BURNING_TIME, -0.5f));
        totemAttrs.add(new AttributeConfig(EntityAttributes.FOLLOW_RANGE, -8f));
        ITEM_ATTRIBUTES.put(AugmentItemType.TOTEM, totemAttrs);

        // OTHER attributes
        List<AttributeConfig> otherAttrs = new ArrayList<>();
        // Guaranteed attribute
        otherAttrs.add(new AttributeConfig(EntityAttributes.MOVEMENT_EFFICIENCY, 1f));
        // Other attributes
        otherAttrs.add(new AttributeConfig(EntityAttributes.BURNING_TIME, -0.5f));
        otherAttrs.add(new AttributeConfig(EntityAttributes.FOLLOW_RANGE, -8f));
        otherAttrs.add(new AttributeConfig(EntityAttributes.MOVEMENT_EFFICIENCY, 1f));
        otherAttrs.add(new AttributeConfig(EntityAttributes.OXYGEN_BONUS, 6f));
        otherAttrs.add(new AttributeConfig(EntityAttributes.LUCK, 1.0f));
        otherAttrs.add(new AttributeConfig(EntityAttributes.KNOCKBACK_RESISTANCE, 0.05f));
        ITEM_ATTRIBUTES.put(AugmentItemType.OTHER, otherAttrs);
    }

    /**
     * Determines the AugmentItemType for a given Item
     * 
     * @param item The Minecraft item to categorize
     * @return The appropriate AugmentItemType category
     */
    public static AugmentItemType getItemType(Item item) {
        // Using item registry ID/path for identification
        String itemId = item.toString().toLowerCase();

        Map<String, AugmentItemType> typeMapping = Map.ofEntries(
                Map.entry("sword", AugmentItemType.SWORD),
                Map.entry("axe", AugmentItemType.AXE),
                Map.entry("trident", AugmentItemType.TRIDENT),
                Map.entry("pickaxe", AugmentItemType.TOOL),
                Map.entry("shovel", AugmentItemType.TOOL),
                Map.entry("hoe", AugmentItemType.TOOL),
                Map.entry("shield", AugmentItemType.SHIELD),
                Map.entry("helmet", AugmentItemType.ARMOR),
                Map.entry("chestplate", AugmentItemType.ARMOR),
                Map.entry("leggings", AugmentItemType.ARMOR),
                Map.entry("boots", AugmentItemType.ARMOR),
                Map.entry("mace", AugmentItemType.MACE));

        for (Map.Entry<String, AugmentItemType> entry : typeMapping.entrySet()) {
            if (itemId.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        if (item == Items.TOTEM_OF_UNDYING) {
            return AugmentItemType.TOTEM;
        }

        return AugmentItemType.OTHER;
    }

    /**
     * Provides a guaranteed attribute plus two random attributes from the
     * appropriate pool
     * based on item type.
     *
     * @return three attributes - one guaranteed plus two randomly selected
     */
    public static List<AbstractMap.SimpleEntry<RegistryEntry<EntityAttribute>, Float>> rollAttributes(Item item) {

        AugmentItemType type = getItemType(item);

        // Get available attributes for this item type, or OTHER if not found
        List<AttributeConfig> attributes = ITEM_ATTRIBUTES.getOrDefault(type,
                ITEM_ATTRIBUTES.get(AugmentItemType.OTHER));

        if (attributes == null || attributes.size() < 1)
            return List.of();

        List<AbstractMap.SimpleEntry<RegistryEntry<EntityAttribute>, Float>> result = new ArrayList<>();

        // Add guaranteed attribute (first in the list)
        AttributeConfig guaranteed = attributes.get(0);
        result.add(new AbstractMap.SimpleEntry<>(guaranteed.getAttribute(), guaranteed.getValue()));

        // Handle case where there are not enough additional attributes
        if (attributes.size() < 3) {
            // Add all remaining attributes if there aren't enough for random selection
            for (int i = 1; i < attributes.size(); i++) {
                AttributeConfig config = attributes.get(i);
                result.add(new AbstractMap.SimpleEntry<>(config.getAttribute(), config.getValue()));
            }
            return result;
        }

        // Select two random attributes from the remaining list
        List<Integer> indices = new ArrayList<>();
        for (int i = 1; i < attributes.size(); i++) {
            indices.add(i);
        }

        Collections.shuffle(indices);

        // Add two random attributes
        for (int i = 0; i < 2; i++) {
            AttributeConfig config = attributes.get(indices.get(i));
            result.add(new AbstractMap.SimpleEntry<>(config.getAttribute(), config.getValue()));
        }

        return result;
    }
}