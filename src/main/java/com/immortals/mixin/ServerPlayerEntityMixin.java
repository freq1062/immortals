package com.immortals.mixin;

import com.immortals.api.ImmortalsData;
import com.mojang.serialization.Codec;

import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.UUID;
import org.apache.commons.lang3.tuple.Pair;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements ImmortalsData {
    @Unique
    private final Map<Integer, String> immortals_bindings = new HashMap<>();
    @Unique
    private int immortals_corruption = 0;
    @Unique
    private boolean is_immortal = false;
    @Unique
    private final List<UUID> immortals_trusted = new ArrayList<>();
    @Unique
    private boolean abilities_disabled = false;
    @Unique
    private String on_hit_spell = "";
    @Unique
    private String linked = null;
    @Unique
    private final Map<UUID, Pair<Integer, Long>> combo_counts = new HashMap<>();

    // read on join / load
    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void readData(ReadView view, CallbackInfo ci) {
        // primitives
        immortals_corruption = view.getOptionalInt("Immortals:Corruption").orElse(0);
        is_immortal = view.getBoolean("Immortals:Ascended", false);
        abilities_disabled = view.getBoolean("Immortals:AbilitiesDisabled", false);
        on_hit_spell = view.getString("Immortals:OnHitSpell", "");
        linked = view.getString("Immortals:Linked", null);

        // trusted: typed list of strings (UUID strings)
        view.getOptionalTypedListView("Immortals:Trusted", Codec.STRING).ifPresent(list -> {
            immortals_trusted.clear();
            for (String s : list) {
                try {
                    immortals_trusted.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                }
            }
        });

        // bindings: store as a list of compounds {slot:int, binding:string}
        view.getOptionalListReadView("Immortals:Bindings").ifPresent(listView -> {
            immortals_bindings.clear();
            for (ReadView elem : listView) {
                int slot = elem.getInt("slot", -1);
                String binding = elem.getString("binding", "");
                if (slot >= 0 && !binding.isEmpty())
                    immortals_bindings.put(slot, binding);
            }
        });

        // combo_counts: list of compounds {uuid:string, count:int, time:long}
        view.getOptionalListReadView("Immortals:ComboCounts").ifPresent(listView -> {
            combo_counts.clear();
            for (ReadView elem : listView) {
                String su = elem.getString("uuid", "");
                elem.getOptionalInt("count").ifPresent(count -> {
                    if (!su.isEmpty())
                        combo_counts.put(UUID.fromString(su), Pair.of(count, elem.getLong("time", 0L)));
                });
            }
        });
    }

    // write on save
    @Inject(method = "writeCustomData", at = @At("TAIL"))
    private void immortals$writeData(WriteView view, CallbackInfo ci) {
        // primitives
        view.putInt("Immortals:Corruption", immortals_corruption);
        view.putBoolean("Immortals:Ascended", is_immortal);
        view.putBoolean("Immortals:AbilitiesDisabled", abilities_disabled);
        view.putString("Immortals:OnHitSpell", on_hit_spell);
        if (linked != null)
            view.putString("Immortals:Linked", linked);

        // trusted: typed list of strings
        var trustedAppender = view.getListAppender("Immortals:Trusted", Codec.STRING);
        for (UUID u : immortals_trusted)
            trustedAppender.add(u.toString());

        // bindings: list of compounds
        var bindingsList = view.getList("Immortals:Bindings");
        for (var entry : immortals_bindings.entrySet()) {
            WriteView elem = bindingsList.add();
            elem.putInt("slot", entry.getKey());
            elem.putString("binding", entry.getValue());
        }

        // combo_counts: list of compounds
        var comboList = view.getList("Immortals:ComboCounts");
        for (var e : combo_counts.entrySet()) {
            WriteView elem = comboList.add();
            elem.putString("uuid", e.getKey().toString());
            elem.putInt("count", e.getValue().getLeft());
            elem.putLong("time", e.getValue().getRight());
        }
    }

    @Override
    public Map<Integer, String> getSpellBindings() {
        return immortals_bindings;
    }

    @Override
    public boolean isImmortal() {
        return is_immortal;
    }

    @Override
    public boolean areAbilitiesDisabled() {
        return abilities_disabled;
    }

    @Override
    public void setAbilitiesDisabled(boolean disabled) {
        abilities_disabled = disabled;
    }

    @Override
    public String onHitSpell() {
        return on_hit_spell;
    }

    @Override
    public void setOnHitSpell(String spellId) {
        on_hit_spell = spellId;
    }

    @Override
    public UUID getLinked() {
        return linked != null ? UUID.fromString(linked) : null;
    }

    @Override
    public void setLinked(UUID uuid) {
        linked = uuid != null ? uuid.toString() : null;
    }

    @Override
    public Map<UUID, Pair<Integer, Long>> getComboCounts() {
        return combo_counts;
    }

    @Override
    public void setComboCount(UUID target, int count) {
        if (count <= 0) {
            combo_counts.remove(target);
        } else {
            combo_counts.put(target, Pair.of(count, System.currentTimeMillis()));
        }
    }

    @Override
    public Integer getComboCount(UUID target) {
        return combo_counts.containsKey(target) ? combo_counts.get(target).getLeft() : 0;
    }

    @Override
    public Long getLastHitTime(UUID target) {
        long currentTime = System.currentTimeMillis();
        return combo_counts.containsKey(target) ? combo_counts.get(target).getRight() : currentTime;
    }

    @Override
    public void resetLastHitTime(UUID target) {
        if (combo_counts.containsKey(target)) {
            long currentTime = System.currentTimeMillis();
            Pair<Integer, Long> current = combo_counts.get(target);
            combo_counts.put(target, Pair.of(current.getLeft(), currentTime));
        }
    }

    @Override
    public void setImmortal(boolean immortal) {
        is_immortal = immortal;
        setCorruption(0);
    }

    @Override
    public void setCorruption(int lvl) {
        immortals_corruption = lvl;
    }

    @Override
    public void addCorruption(int amt) {
        immortals_corruption = getCorruption() + amt;
    }

    @Override
    public int getCorruption() {
        return immortals_corruption;
    }

    @Override
    public List<UUID> getTrusted() {
        return immortals_trusted;
    }

    @Override
    public void addTrusted(UUID uuid) {
        if (!immortals_trusted.contains(uuid)) {
            immortals_trusted.add(uuid);
        }
    }

    @Override
    public void removeTrusted(UUID uuid) {
        immortals_trusted.remove(uuid);
    }
}
