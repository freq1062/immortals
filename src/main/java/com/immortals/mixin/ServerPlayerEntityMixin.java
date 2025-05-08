// src/main/java/com/immortals/mixin/ServerPlayerEntityMixin.java
package com.immortals.mixin;

import com.immortals.api.PlayerImmortalsData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements PlayerImmortalsData {
    @Unique
    private final Map<Integer, String> immortals_bindings = new HashMap<>();
    @Unique
    private int immortals_corruption = 0;
    private boolean is_immortal = false;

    // read on join / load
    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void readData(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("Immortals:Bindings")) {
            NbtCompound bb = nbt.getCompound("Immortals:Bindings");
            for (String key : bb.getKeys()) {
                immortals_bindings.put(
                        Integer.parseInt(key),
                        bb.getString(key));
            }
        }
        if (nbt.contains("Immortals:Corruption")) {
            immortals_corruption = nbt.getInt("Immortals:Corruption");
        }
        if (nbt.contains("Immortals:Ascended")) {
            is_immortal = nbt.getBoolean("Immortals:Ascended");
        }
    }

    // write on save
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void writeData(NbtCompound nbt, CallbackInfo ci) {
        NbtCompound bb = new NbtCompound();
        for (var e : immortals_bindings.entrySet()) {
            bb.putString(e.getKey().toString(), e.getValue());
        }
        nbt.put("Immortals:Bindings", bb);
        nbt.putInt("Immortals:Corruption", immortals_corruption);
        nbt.putBoolean("Immortals:Ascended", is_immortal);
    }

    // interface impl:
    @Override
    public Map<Integer, String> getSpellBindings() {
        return immortals_bindings;
    }

    @Override
    public boolean isImmortal() {
        return is_immortal;
    }

    @Override
    public void setImmortal(boolean immortal) {
        is_immortal = immortal;
    }

    @Override
    public void setCorruption(int lvl) {
        immortals_corruption = lvl;
    }

    @Override
    public int getCorruption() {
        return immortals_corruption;
    }
}
