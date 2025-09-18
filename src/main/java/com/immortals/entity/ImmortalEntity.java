package com.immortals.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ImmortalEntity {
    public static final EntityType<FragmentEntity> FRAGMENT_ENTITY = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of("immortals", "fragment"),
            EntityType.Builder.<FragmentEntity>create(FragmentEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5F, 0.5F)
                    .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of("immortals", "fragment"))));

    public static void register() {
    }
}
