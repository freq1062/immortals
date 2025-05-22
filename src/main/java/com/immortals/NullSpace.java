package com.immortals;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.biome.Biome;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.RabbitEntity.RabbitType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.entity.mob.MobEntity;

public class NullSpace {
    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getWorld().isClient())
                return;

            Biome biome = world.getBiome(entity.getBlockPos()).value();
            String biomeName = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME).getEntry(biome)
                    .getIdAsString();
            if ("desert_of_time".equals(biomeName) && entity.getType() == EntityType.RABBIT) {
                // Set the rabbit's variant to Killer Bunny
                if (entity instanceof net.minecraft.entity.passive.RabbitEntity rabbit) {
                    rabbit.setVariant(RabbitType.EVIL);
                }
            }

            // If biome is "broken_plains" and entity is a Mob, set NoAI
            if ("broken_plains".equals(biomeName) && entity instanceof MobEntity mob) {
                mob.setAiDisabled(true);
            }
        });
    }
}