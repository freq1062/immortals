package com.immortals.Mortal;

import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import net.minecraft.component.ComponentType;
import net.minecraft.util.dynamic.Codecs;

public class ModComponents {
    public static final ComponentType<String> OWNER_COMPONENT = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("immortals", "owner_uuid"),
            ComponentType.<String>builder().codec(Codecs.NON_EMPTY_STRING).build());
}
