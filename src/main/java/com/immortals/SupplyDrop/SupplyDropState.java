package com.immortals.SupplyDrop;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;

public class SupplyDropState extends PersistentState {
    public long lastSpawnTime = 0;

    public static final Type<SupplyDropState> TYPE = new Type<>(
            SupplyDropState::new,
            SupplyDropState::fromNbt,
            null);

    public SupplyDropState() {
        // Default constructor
    }

    public static SupplyDropState fromNbt(NbtCompound nbt, WrapperLookup registries) {
        SupplyDropState state = new SupplyDropState();
        state.lastSpawnTime = nbt.getLong("LastSpawnTime");
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, WrapperLookup registries) {
        nbt.putLong("LastSpawnTime", lastSpawnTime);
        return nbt;
    }
}
