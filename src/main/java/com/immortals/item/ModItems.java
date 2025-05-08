package com.immortals.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import com.immortals.Main;

import java.util.function.Function;

public class ModItems {
    public static final Item ASCENSION_TOTEM = registerItem("ascension_totem", Item::new,
            new Item.Settings()
                    .rarity(Rarity.EPIC)
                    .maxCount(1)); // Unstackable

    public static final Item ASCENSION_RELIC = registerItem("ascension_relic", Item::new,
            new Item.Settings()
                    .rarity(Rarity.RARE));

    public static final Item SOUL_PURIFIER = registerItem("soul_purifier", Item::new, new Item.Settings());
    public static final Item SOUL_SHARD = registerItem("soul_shard", Item::new, new Item.Settings());
    public static final Item HEART = registerItem("heart", Item::new, new Item.Settings());

    public static final Item PHASEBREAKER = registerItem("phasebreaker",
            settings -> new SwordItem(
                    ToolMaterial.NETHERITE,
                    8 - 5, // same attack as netherite sword, idk why this specifically but it works ok
                    -2.4F,
                    settings),
            new Item.Settings()
                    .rarity(Rarity.EPIC)
                    .enchantable(15)); // Enchantable with enchantability of 15

    public static Item registerItem(String name, Function<Item.Settings, Item> factory, Item.Settings settings) {
        final RegistryKey<Item> registerKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Main.MOD_ID, name));
        return Items.register(registerKey, factory, settings);
    }

    private static void customIngredients(FabricItemGroupEntries entries) {
        entries.add(ASCENSION_TOTEM);
        entries.add(ASCENSION_RELIC);
        entries.add(SOUL_PURIFIER);
        entries.add(SOUL_SHARD);
        entries.add(HEART);
        entries.add(PHASEBREAKER);
    }

    public static void registerModItems() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(ModItems::customIngredients);
    }
}
