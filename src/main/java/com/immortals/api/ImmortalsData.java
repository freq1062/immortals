package com.immortals.api;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.tuple.Pair; // Use Apache Commons Lang Pair

public interface ImmortalsData {
    // Spell bindings: Map of hotbar slot to spell id
    Map<Integer, String> getSpellBindings();

    // Corruption: Integer -3 to +4 inclusive
    void setCorruption(int lvl);

    void addCorruption(int amt);

    int getCorruption();

    // Ascendance: boolean true for Immortal, false for Mortal
    boolean isImmortal();

    void setImmortal(boolean immortal);

    // Disabled Abilities: boolean true if abilities are disabled
    boolean areAbilitiesDisabled();

    void setAbilitiesDisabled(boolean disabled);

    // On-hit spell: id for spell to cast on hit or null
    String onHitSpell();

    void setOnHitSpell(String spellId);

    // Link: UUID
    UUID getLinked();

    void setLinked(UUID uuid);

    // Combo count on target: [UUID, combo count, last hit time]
    Map<UUID, Pair<Integer, Long>> getComboCounts();

    Integer getComboCount(UUID target);

    Long getLastHitTime(UUID target);

    void setComboCount(UUID target, int count);

    void resetLastHitTime(UUID target);

    // Trusted system: List of UUIDS of trusted players
    List<UUID> getTrusted();

    void addTrusted(UUID uuid);

    void removeTrusted(UUID uuid);
}