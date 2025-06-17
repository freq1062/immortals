package com.immortals.Mortal;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
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

    // Define attribute object that stores min/max values for each heart level
    private static class AttributeConfig {
        private final RegistryEntry<EntityAttribute> attribute;
        private final Map<Integer, float[]> heartValues = new HashMap<>();

        public AttributeConfig(RegistryEntry<EntityAttribute> attribute) {
            this.attribute = attribute;
        }

        // Add values for a specific heart level
        public AttributeConfig hearts(int hearts, float min, float max) {
            heartValues.put(hearts, new float[] { min, max });
            return this;
        }

        public RegistryEntry<EntityAttribute> getAttribute() {
            return attribute;
        }

        public float[] getRange(int hearts) {
            return heartValues.get(hearts);
        }
    }

    // Map item types to their attributes
    private static final Map<AugmentItemType, List<AttributeConfig>> ITEM_ATTRIBUTES = new HashMap<>();

    static {
        // SWORD attributes
        List<AttributeConfig> swordAttrs = new ArrayList<>();
        swordAttrs.add(new AttributeConfig(EntityAttributes.ATTACK_DAMAGE)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.8f)
                .hearts(20, 0.5f, 1.0f));

        swordAttrs.add(new AttributeConfig(EntityAttributes.FOLLOW_RANGE)
                .hearts(10, -2f, -5f)
                .hearts(14, -3f, -6f)
                .hearts(18, -5f, -7f)
                .hearts(20, -6f, -8f));

        swordAttrs.add(new AttributeConfig(EntityAttributes.MOVEMENT_SPEED)
                .hearts(10, 0.005f, 0.003f)
                .hearts(14, 0.002f, 0.005f)
                .hearts(18, 0.005f, 0.008f)
                .hearts(20, 0.006f, 0.01f));

        swordAttrs.add(new AttributeConfig(EntityAttributes.SWEEPING_DAMAGE_RATIO)
                .hearts(10, 0.01f, 0.1f)
                .hearts(14, 0.1f, 0.2f)
                .hearts(18, 0.2f, 0.3f)
                .hearts(20, 0.3f, 0.4f));

        ITEM_ATTRIBUTES.put(AugmentItemType.SWORD, swordAttrs);

        // AXE attributes
        List<AttributeConfig> axeAttrs = new ArrayList<>();
        axeAttrs.add(new AttributeConfig(EntityAttributes.ATTACK_DAMAGE)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.8f)
                .hearts(20, 0.8f, 1.0f));

        axeAttrs.add(new AttributeConfig(EntityAttributes.FOLLOW_RANGE)
                .hearts(10, -2f, -5f)
                .hearts(14, -3f, -6f)
                .hearts(18, -5f, -7f)
                .hearts(20, -6f, -8f));

        axeAttrs.add(new AttributeConfig(EntityAttributes.MOVEMENT_EFFICIENCY)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.7f)
                .hearts(20, 0.7f, 1f));

        axeAttrs.add(new AttributeConfig(EntityAttributes.SAFE_FALL_DISTANCE)
                .hearts(10, 0.5f, 0.5f)
                .hearts(14, 1f, 1f)
                .hearts(18, 2f, 2f)
                .hearts(20, 3f, 3f));

        ITEM_ATTRIBUTES.put(AugmentItemType.AXE, axeAttrs);

        // MACE attributes
        List<AttributeConfig> maceAttrs = new ArrayList<>();
        maceAttrs.add(new AttributeConfig(EntityAttributes.JUMP_STRENGTH)
                .hearts(10, 0.01f, 0.03f)
                .hearts(14, 0.03f, 0.05f)
                .hearts(18, 0.05f, 0.1f)
                .hearts(20, 0.08f, 0.15f));
        maceAttrs.add(new AttributeConfig(EntityAttributes.MOVEMENT_SPEED)
                .hearts(10, 0.005f, 0.003f)
                .hearts(14, 0.002f, 0.005f)
                .hearts(18, 0.005f, 0.008f)
                .hearts(20, 0.006f, 0.01f));
        ITEM_ATTRIBUTES.put(AugmentItemType.MACE, maceAttrs);

        // TRIDENT attributes
        List<AttributeConfig> tridentAttrs = new ArrayList<>();
        tridentAttrs.add(new AttributeConfig(EntityAttributes.ATTACK_DAMAGE)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.8f)
                .hearts(20, 0.8f, 1.0f));
        tridentAttrs.add(new AttributeConfig(EntityAttributes.OXYGEN_BONUS)
                .hearts(10, 1f, 2f)
                .hearts(14, 2f, 4f)
                .hearts(18, 3f, 5f)
                .hearts(20, 4f, 6f));
        tridentAttrs.add(new AttributeConfig(EntityAttributes.LUCK)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.8f)
                .hearts(20, 0.8f, 1.0f));
        tridentAttrs.add(new AttributeConfig(EntityAttributes.SUBMERGED_MINING_SPEED)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.7f)
                .hearts(20, 0.7f, 1f));
        tridentAttrs.add(new AttributeConfig(EntityAttributes.WATER_MOVEMENT_EFFICIENCY)
                .hearts(10, 0.05f, 0.1f)
                .hearts(14, 0.1f, 0.2f)
                .hearts(18, 0.2f, 0.4f)
                .hearts(20, 0.3f, 0.5f));
        ITEM_ATTRIBUTES.put(AugmentItemType.TRIDENT, tridentAttrs);

        // TOOL attributes (Shovel/Pickaxe/Hoe/Shears)
        List<AttributeConfig> toolAttrs = new ArrayList<>();
        toolAttrs.add(new AttributeConfig(EntityAttributes.ATTACK_KNOCKBACK)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.8f)
                .hearts(20, 0.8f, 1.0f));
        toolAttrs.add(new AttributeConfig(EntityAttributes.BLOCK_BREAK_SPEED)
                .hearts(10, 0.05f, 0.1f)
                .hearts(14, 0.1f, 0.2f)
                .hearts(18, 0.2f, 0.3f)
                .hearts(20, 0.2f, 0.4f));
        toolAttrs.add(new AttributeConfig(EntityAttributes.MINING_EFFICIENCY)
                .hearts(10, 0.05f, 0.1f)
                .hearts(14, 0.1f, 0.2f)
                .hearts(18, 0.2f, 0.3f)
                .hearts(20, 0.2f, 0.4f));
        toolAttrs.add(new AttributeConfig(EntityAttributes.LUCK)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.8f)
                .hearts(20, 0.8f, 1.0f));
        toolAttrs.add(new AttributeConfig(EntityAttributes.SUBMERGED_MINING_SPEED)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.7f)
                .hearts(20, 0.7f, 1f));
        ITEM_ATTRIBUTES.put(AugmentItemType.TOOL, toolAttrs);

        // SHIELD attributes
        List<AttributeConfig> shieldAttrs = new ArrayList<>();
        shieldAttrs.add(new AttributeConfig(EntityAttributes.ATTACK_DAMAGE)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.8f)
                .hearts(20, 0.5f, 1.0f));
        shieldAttrs.add(new AttributeConfig(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE)
                .hearts(10, 0.01f, 0.02f)
                .hearts(14, 0.02f, 0.04f)
                .hearts(18, 0.04f, 0.06f)
                .hearts(20, 0.06f, 0.08f));
        shieldAttrs.add(new AttributeConfig(EntityAttributes.SNEAKING_SPEED)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.2f, 0.4f)
                .hearts(18, 0.3f, 0.5f)
                .hearts(20, 0.4f, 0.6f));
        ITEM_ATTRIBUTES.put(AugmentItemType.SHIELD, shieldAttrs);

        // ARMOR/ELYTRA attributes
        List<AttributeConfig> armorAttrs = new ArrayList<>();
        armorAttrs.add(new AttributeConfig(EntityAttributes.ARMOR_TOUGHNESS)
                .hearts(10, 0.5f, 1f)
                .hearts(14, 1f, 2f)
                .hearts(18, 1f, 3f)
                .hearts(20, 2f, 4f));
        armorAttrs.add(new AttributeConfig(EntityAttributes.BURNING_TIME)
                .hearts(10, -0.1f, -0.2f)
                .hearts(14, -0.2f, -0.3f)
                .hearts(18, -0.3f, -0.4f)
                .hearts(20, -0.4f, -0.5f));
        armorAttrs.add(new AttributeConfig(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE)
                .hearts(10, 0.01f, 0.02f)
                .hearts(14, 0.02f, 0.03f)
                .hearts(18, 0.03f, 0.05f)
                .hearts(20, 0.05f, 0.07f));
        armorAttrs.add(new AttributeConfig(EntityAttributes.FALL_DAMAGE_MULTIPLIER)
                .hearts(10, -0.05f, -0.1f)
                .hearts(14, -0.1f, -0.2f)
                .hearts(18, -0.2f, -0.3f)
                .hearts(20, -0.2f, -0.4f));
        armorAttrs.add(new AttributeConfig(EntityAttributes.OXYGEN_BONUS)
                .hearts(10, 1f, 2f)
                .hearts(14, 2f, 4f)
                .hearts(18, 3f, 5f)
                .hearts(20, 4f, 6f));
        ITEM_ATTRIBUTES.put(AugmentItemType.ARMOR, armorAttrs);

        // TOTEM attributes
        List<AttributeConfig> totemAttrs = new ArrayList<>();
        totemAttrs.add(new AttributeConfig(EntityAttributes.ARMOR_TOUGHNESS)
                .hearts(10, 0.5f, 1f)
                .hearts(14, 1f, 2f)
                .hearts(18, 1f, 3f)
                .hearts(20, 2f, 4f));
        totemAttrs.add(new AttributeConfig(EntityAttributes.KNOCKBACK_RESISTANCE)
                .hearts(10, 0.01f, 0.02f)
                .hearts(14, 0.02f, 0.03f)
                .hearts(18, 0.03f, 0.04f)
                .hearts(20, 0.04f, 0.05f));
        totemAttrs.add(new AttributeConfig(EntityAttributes.LUCK)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.8f)
                .hearts(20, 0.8f, 1.0f));
        ITEM_ATTRIBUTES.put(AugmentItemType.TOTEM, totemAttrs);

        // OTHER attributes
        List<AttributeConfig> otherAttrs = new ArrayList<>();
        otherAttrs.add(new AttributeConfig(EntityAttributes.BURNING_TIME)
                .hearts(10, -0.1f, -0.2f)
                .hearts(14, -0.2f, -0.3f)
                .hearts(18, -0.3f, -0.4f)
                .hearts(20, -0.4f, -0.5f));
        otherAttrs.add(new AttributeConfig(EntityAttributes.FOLLOW_RANGE)
                .hearts(10, -2f, -5f)
                .hearts(14, -3f, -6f)
                .hearts(18, -5f, -7f)
                .hearts(20, -6f, -8f));
        otherAttrs.add(new AttributeConfig(EntityAttributes.LUCK)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.8f)
                .hearts(20, 0.8f, 1.0f));
        otherAttrs.add(new AttributeConfig(EntityAttributes.MOVEMENT_EFFICIENCY)
                .hearts(10, 0.1f, 0.3f)
                .hearts(14, 0.3f, 0.5f)
                .hearts(18, 0.5f, 0.7f)
                .hearts(20, 0.7f, 1f));
        otherAttrs.add(new AttributeConfig(EntityAttributes.OXYGEN_BONUS)
                .hearts(10, 1f, 2f)
                .hearts(14, 2f, 4f)
                .hearts(18, 3f, 5f)
                .hearts(20, 4f, 6f));
        otherAttrs.add(new AttributeConfig(EntityAttributes.KNOCKBACK_RESISTANCE)
                .hearts(10, 0.01f, 0.02f)
                .hearts(14, 0.02f, 0.03f)
                .hearts(18, 0.03f, 0.04f)
                .hearts(20, 0.04f, 0.05f));
        ITEM_ATTRIBUTES.put(AugmentItemType.OTHER, otherAttrs);
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

        // Determine heart level
        int heartLevel;
        if (numHearts <= 10)
            heartLevel = 10;
        else if (numHearts <= 14)
            heartLevel = 14;
        else if (numHearts <= 18)
            heartLevel = 18;
        else
            heartLevel = 20;

        // Get available attributes for this item type, or OTHER if not found
        List<AttributeConfig> attributes = ITEM_ATTRIBUTES.getOrDefault(type,
                ITEM_ATTRIBUTES.get(AugmentItemType.OTHER));

        if (attributes == null || attributes.size() < 2)
            return List.of();

        // Select two random attributes
        int firstIdx = RANDOM.nextInt(attributes.size());
        int secondIdx;
        do {
            secondIdx = RANDOM.nextInt(attributes.size());
        } while (secondIdx == firstIdx);

        List<AttributeConfig> selected = Arrays.asList(attributes.get(firstIdx), attributes.get(secondIdx));

        // Generate random values within the specified ranges
        return selected.stream()
                .map(config -> {
                    float[] range = config.getRange(heartLevel);
                    float value = range[0] + RANDOM.nextFloat() * (range[1] - range[0]);
                    return new AbstractMap.SimpleEntry<>(config.getAttribute(), value);
                })
                .toList();
    }
}
