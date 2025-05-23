package com.immortals;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.biome.Biome;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.RabbitEntity.RabbitType;
import net.minecraft.entity.mob.ZombieHorseEntity;
import net.minecraft.entity.mob.IllusionerEntity;
import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.entity.mob.MobEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.entity.Entity;

public class NullSpace {

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getWorld().isClient())
                return;

            Biome biome = world.getBiome(entity.getBlockPos()).value();
            String biomeName = world.getRegistryManager()
                    .getOrThrow(RegistryKeys.BIOME)
                    .getEntry(biome)
                    .getIdAsString();

            // Freeze mobs in the broken plains
            if ("immortals:broken_plains".equals(biomeName) && entity instanceof MobEntity mob) {
                mob.setAiDisabled(true);
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.isClient() || world.getTime() % 400 != 0)
                return;
            ServerWorld server = (ServerWorld) world;
            for (ServerPlayerEntity player : server.getPlayers()) {

                String biomeName = server.getRegistryManager()
                        .getOrThrow(RegistryKeys.BIOME)
                        .getEntry(world.getBiome(player.getBlockPos()).value())
                        .getIdAsString();

                if (!"immortals:desert_of_time".equals(biomeName)) {
                    continue;
                }

                // Spawn manually because I can't change the mob spawn conditions
                int spawnCount = 1 + world.random.nextInt(3); // 1 to 3 times
                for (int i = 0; i < spawnCount; i++) {

                    // Pick a random spot within the server's view distance
                    int viewDist = server.getServer().getPlayerManager().getViewDistance() * 16;
                    int dx = world.random.nextInt(viewDist * 2 + 1) - viewDist;
                    int dz = world.random.nextInt(viewDist * 2 + 1) - viewDist;
                    BlockPos spawnPos = player.getBlockPos().add(dx, 0, dz);

                    // Make sure it’s not in mid‐air
                    spawnPos = server.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawnPos);

                    // Weighted mob selection
                    double roll = world.random.nextDouble();
                    Entity entityToSpawn = null;
                    if (roll < 0.25) { // 25% Zombie Horse
                        entityToSpawn = new ZombieHorseEntity(EntityType.ZOMBIE_HORSE, server);
                    } else if (roll < 0.40) { // 15% Illusioner
                        entityToSpawn = new IllusionerEntity(EntityType.ILLUSIONER, server);
                    } else if (roll < 0.50) { // 10% Giant
                        entityToSpawn = new GiantEntity(EntityType.GIANT, server);
                    } else { // 50% Killer Bunny
                        entityToSpawn = new RabbitEntity(EntityType.RABBIT, server);
                        ((RabbitEntity) entityToSpawn).setVariant(RabbitType.EVIL);
                    }

                    if (entityToSpawn != null) {
                        entityToSpawn.refreshPositionAndAngles(spawnPos, 0, 0);
                        server.spawnEntity(entityToSpawn);
                    }
                }
            }
        });
    }
}