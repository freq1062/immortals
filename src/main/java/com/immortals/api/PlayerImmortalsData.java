package com.immortals.api;

import java.util.Map;

public interface PlayerImmortalsData {
    Map<Integer, String> getSpellBindings();

    void setCorruption(int lvl);

    int getCorruption();

    boolean isImmortal();

    void setImmortal(boolean immortal);
}
