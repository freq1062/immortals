package com.immortals.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import com.immortals.Immortals;

import java.util.function.Function;

public class ModItems {

    public static final Item ASCENSION_TOTEM = registerItem("ascension_totem", Item::new, new Item.Settings());
    public static final Item ASCENSION_RELIC = registerItem("ascension_relic", Item::new, new Item.Settings());
    public static final Item SOUL_PURIFIER = registerItem("soul_purifier", Item::new, new Item.Settings());
    public static final Item SOUL_SHARD = registerItem("soul_shard", Item::new, new Item.Settings());

    public static Item registerItem(String name, Function<Item.Settings, Item> factory, Item.Settings settings) {
        final RegistryKey<Item> registerKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Immortals.MOD_ID, name));
        return Items.register(registerKey, factory, settings);
    }

    private static void customIngredients(FabricItemGroupEntries entries) {
        entries.add(ASCENSION_TOTEM);
        entries.add(ASCENSION_RELIC);
        entries.add(SOUL_PURIFIER);
        entries.add(SOUL_SHARD);
    }

    public static void registerModItems() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(ModItems::customIngredients);
    }
}
