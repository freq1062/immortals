package com.immortals.Mortal;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;

public class Augmentation {
    private static record AttributeRange(RegistryEntry<EntityAttribute> attribute, float min, float max) {
    }

    private static final List<AttributeRange> AUGMENT_POOL_1 = Arrays.asList(
            new AttributeRange(EntityAttributes.MOVEMENT_SPEED, 0.005f, 0.015f), // 5 - 15% faster (speed 1 is 20%)
            new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.1f, 0.3f), // 10% - 30% of a heart more damage
            new AttributeRange(EntityAttributes.ATTACK_KNOCKBACK, 0.05f, 0.1f), // generally bad attribute, default
                                                                                // sword: 0.4
            new AttributeRange(EntityAttributes.BLOCK_BREAK_SPEED, 0.01f, 0.05f), // 1 - 5% faster
            new AttributeRange(EntityAttributes.FOLLOW_RANGE, -3f, -8f), // 3 - 8 blocks less follow range
            new AttributeRange(EntityAttributes.JUMP_STRENGTH, 0.01f, 0.05f), // Adds 0.01 - 0.05 to initial jump
                                                                              // velocity, base is 0.42
            new AttributeRange(EntityAttributes.KNOCKBACK_RESISTANCE, 0.01f, 0.05f), // 1% - 5% percent of kb reduced
            new AttributeRange(EntityAttributes.LUCK, 0.01f, 0.05f), // 1% - 5% luck bonus, honesly idk exactly what
                                                                     // this means
            new AttributeRange(EntityAttributes.OXYGEN_BONUS, 0.01f, 0.05f), // 1% - 5% less chance of your air going
                                                                             // down per tick, in theory that much
                                                                             // longer underwater
            new AttributeRange(EntityAttributes.SNEAKING_SPEED, 0.2f, 0.5f)); // 20% - 50% more of walking speed, base
                                                                              // is 30% 0: no movement 1: normal walking
                                                                              // speed

    private static final List<AttributeRange> AUGMENT_POOL_2 = Arrays.asList(
            new AttributeRange(EntityAttributes.MOVEMENT_SPEED, 0.01f, 0.02f), // 10% - 20% faster (speed 1 is 20%)
            new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.1f, 0.5f), // 10% - 30% of a heart more damage
            new AttributeRange(EntityAttributes.BLOCK_BREAK_SPEED, 0.05f, 0.10f), // 5 - 10% faster
            new AttributeRange(EntityAttributes.JUMP_STRENGTH, 0.05f, 0.10f), // Adds 0.05 - 0.1 to initial jump
                                                                              // velocity, base is 0.42
            new AttributeRange(EntityAttributes.KNOCKBACK_RESISTANCE, 0.05f, 0.10f), // 3% - 5% percent of kb reduced
            new AttributeRange(EntityAttributes.LUCK, 0.05f, 0.10f), // again no clue what this does exactly
            new AttributeRange(EntityAttributes.OXYGEN_BONUS, 0.05f, 0.15f) // 5% - 15% less chance of your air going
                                                                            // down per tick, in theory that much longer
                                                                            // underwater
    );

    private static final List<AttributeRange> AUGMENT_POOL_3 = Arrays.asList(
            new AttributeRange(EntityAttributes.MOVEMENT_SPEED, 0.02f, 0.04f), // 20% - 40% faster (between speed 1 and
                                                                               // 2)
            new AttributeRange(EntityAttributes.ATTACK_DAMAGE, 0.3f, 1f), // 30% - 100% of a heart more damage
            new AttributeRange(EntityAttributes.JUMP_STRENGTH, 0.10f, 0.20f), // Adds 0.1 - 0.2 to initial jump
                                                                              // velocity, base is 0.42
            new AttributeRange(EntityAttributes.LUCK, 0.10f, 0.20f), // wack
            new AttributeRange(EntityAttributes.BLOCK_BREAK_SPEED, 0.05f, 0.1f), // 5 - 10% faster
            new AttributeRange(EntityAttributes.OXYGEN_BONUS, 0.1f, 0.2f)); // 10% - 20% less chance of your air going
                                                                            // down per tick, in theory that much longer
                                                                            // underwater

    private static final Random RANDOM = new Random();

    /**
     * Rolls two random attribute ranges from the appropriate pool based on player's
     * max hearts.
     * 
     * @param numHearts total player hearts (e.g. getHealth() / 2)
     * @return two randomly selected AttributeRange entries
     */
    public static List<java.util.AbstractMap.SimpleEntry<RegistryEntry<EntityAttribute>, Float>> rollAttributes(
            Double numHearts) {
        List<AttributeRange> selectedRanges;
        if (numHearts <= 10) {
            // Pick 2 distinct from AUGMENT_POOL_1
            int size = AUGMENT_POOL_1.size();
            int firstIdx = RANDOM.nextInt(size);
            int secondIdx;
            do {
                secondIdx = RANDOM.nextInt(size);
            } while (secondIdx == firstIdx);
            selectedRanges = Arrays.asList(AUGMENT_POOL_1.get(firstIdx), AUGMENT_POOL_1.get(secondIdx));
        } else if (numHearts <= 13) {
            // Pick 2 distinct from AUGMENT_POOL_2
            int size = AUGMENT_POOL_2.size();
            int firstIdx = RANDOM.nextInt(size);
            int secondIdx;
            do {
                secondIdx = RANDOM.nextInt(size);
            } while (secondIdx == firstIdx);
            selectedRanges = Arrays.asList(AUGMENT_POOL_2.get(firstIdx), AUGMENT_POOL_2.get(secondIdx));
        } else {
            // Pick 2 distinct from AUGMENT_POOL_3 and 1 distinct from AUGMENT_POOL_1
            int size3 = AUGMENT_POOL_3.size();
            int firstIdx3 = RANDOM.nextInt(size3);
            int secondIdx3;
            do {
                secondIdx3 = RANDOM.nextInt(size3);
            } while (secondIdx3 == firstIdx3);

            int size1 = AUGMENT_POOL_1.size();
            int idx1 = RANDOM.nextInt(size1);

            selectedRanges = Arrays.asList(
                    AUGMENT_POOL_3.get(firstIdx3),
                    AUGMENT_POOL_3.get(secondIdx3),
                    AUGMENT_POOL_1.get(idx1));
        }

        // Map AttributeRange to (attribute, random float in [min, max])
        return selectedRanges.stream()
                .map(range -> {
                    float value = range.min + RANDOM.nextFloat() * (range.max - range.min);
                    return new java.util.AbstractMap.SimpleEntry<>(range.attribute(), value);
                })
                .toList();
    }
}
