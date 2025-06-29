package com.immortals.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import com.immortals.Main;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;

import java.util.function.Function;

public class ModItems {
        public static final Item ASCENSION_TOTEM = registerItem("ascension_totem", Item::new,
                        new Item.Settings()
                                        .rarity(Rarity.EPIC)
                                        .component(DataComponentTypes.LORE,
                                                        new LoreComponent(
                                                                        java.util.List.of(
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "§4Some say that the First Immortal lives on in this item."),
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "§4Once you ascend, you will never be able to return to your mortal life."))))
                                        .maxCount(1)
                                        .fireproof());

        public static final Item ASCENSION_RELIC = registerItem("ascension_relic", Item::new,
                        new Item.Settings()
                                        .component(DataComponentTypes.LORE,
                                                        new LoreComponent(
                                                                        java.util.List.of(
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "§4These relics were manifested from the intense fear of death."))))
                                        .rarity(Rarity.RARE));

        public static final Item SOUL_PURIFIER = registerItem("soul_purifier", Item::new,
                        new Item.Settings()
                                        .component(DataComponentTypes.LORE,
                                                        new LoreComponent(
                                                                        java.util.List.of(
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "The soul purifier can return your soul to its original state.")))));
        public static final Item SOUL_SHARD = registerItem("soul_shard", Item::new, new Item.Settings()
                        .component(DataComponentTypes.LORE,
                                        new LoreComponent(
                                                        java.util.List.of(
                                                                        net.minecraft.text.Text.literal(
                                                                                        "§4A shard of the victim's soul, corrupted by Immortal power.")))));
        public static final Item HEART = registerItem("heart", Item::new, new Item.Settings()
                        .component(DataComponentTypes.LORE,
                                        new LoreComponent(
                                                        java.util.List.of(
                                                                        net.minecraft.text.Text.literal(
                                                                                        "§cA piece of mortal essence.")))));
        public static final Item AUGMENTATION_CORE = registerItem("augmentation_core", Item::new,
                        new Item.Settings()
                                        .component(DataComponentTypes.LORE,
                                                        new LoreComponent(
                                                                        java.util.List.of(
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "To compete with magical powers, Mortal ingenuity"),
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "discovered how to augment the attributes of various items."),
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        ""),
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "Run /augment while holding your unstackable item of choice"),
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "in your main hand, and two random attributes will be applied."),
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "Does not stack.")))));

        public static final Item PHASEBREAKER = registerItem(
                        "phasebreaker",
                        settings -> new SwordItem(
                                        ToolMaterial.NETHERITE,
                                        8 - 5, // same attack as netherite sword
                                        -2.4F,
                                        settings),
                        new Item.Settings()
                                        .rarity(Rarity.EPIC)
                                        .component(
                                                        DataComponentTypes.LORE,
                                                        new LoreComponent(
                                                                        java.util.List.of(
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "§dForged from space folded into itself, the blade can cut through reality."),
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "§aFRACTAL EDGE: Every 7 hits, the sword induces a flurry of hits on the target."),
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "§aPHASE CHANGE: Shift + right click to teleport in the direction you are facing. ok so "
                                                                                                                        + ((Integer) Main.CONFIG
                                                                                                                                        .get("phaseChangeCooldown")
                                                                                                                                        / 1000)
                                                                                                                        + " seconds cooldown."))))
                                        .enchantable(15));

        public static final Item CHRONOREAVER = registerItem(
                        "chronoreaver",
                        settings -> new AxeItem(
                                        ToolMaterial.DIAMOND,
                                        5.0F, // Diamond Axe equivalents
                                        -3.0F,
                                        settings) {
                        },
                        new Item.Settings()
                                        .rarity(Rarity.EPIC)
                                        .component(
                                                        DataComponentTypes.LORE,
                                                        new LoreComponent(
                                                                        java.util.List.of(
                                                                                        net.minecraft.text.Text
                                                                                                        .literal(
                                                                                                                        "§eCrafted in the Null Space where time collapses,"),
                                                                                        net.minecraft.text.Text
                                                                                                        .literal(
                                                                                                                        "§eEach strike lands before it is swung."),
                                                                                        net.minecraft.text.Text
                                                                                                        .literal(
                                                                                                                        "§aOVERCLOCK: Shift + right click to apply haste 5 and speed 3 for "
                                                                                                                                        + ((Integer) Main.CONFIG
                                                                                                                                                        .get("overclockDuration")
                                                                                                                                                        / 1000)
                                                                                                                                        + " seconds."),
                                                                                        net.minecraft.text.Text
                                                                                                        .literal("§a" +
                                                                                                                        ((Integer) Main.CONFIG
                                                                                                                                        .get("overclockCooldown")
                                                                                                                                        / 1000)
                                                                                                                        + " second cooldown."),
                                                                                        net.minecraft.text.Text
                                                                                                        .literal(
                                                                                                                        "§aBLINK: When below 50% health, the axe applies true invisibility for "
                                                                                                                                        + ((Integer) Main.CONFIG
                                                                                                                                                        .get("blinkDuration")
                                                                                                                                                        / 1000)
                                                                                                                                        + " seconds."),
                                                                                        net.minecraft.text.Text
                                                                                                        .literal("§a" +
                                                                                                                        ((Integer) Main.CONFIG
                                                                                                                                        .get("blinkCooldown")
                                                                                                                                        / 1000)
                                                                                                                        + " second cooldown.")))

                                        )
                                        .enchantable(15));
        // of 15

        public static final Item TIMEKEEPER = registerItem("timekeeper", Item::new,
                        new Item.Settings()
                                        .rarity(Rarity.EPIC)
                                        .component(DataComponentTypes.LORE,
                                                        new LoreComponent(
                                                                        java.util.List.of(
                                                                                        net.minecraft.text.Text.literal(
                                                                                                        "§eThe timekeeper runs, maintaining the flow of time in the universe."))))
                                        .maxCount(1));

        public static Item registerItem(String name, Function<Item.Settings, Item> factory, Item.Settings settings) {
                final RegistryKey<Item> registerKey = RegistryKey.of(RegistryKeys.ITEM,
                                Identifier.of(Main.MOD_ID, name));
                return Items.register(registerKey, factory, settings);
        }

        private static void customIngredients(FabricItemGroupEntries entries) {
                entries.add(ASCENSION_TOTEM);
                entries.add(ASCENSION_RELIC);
                entries.add(SOUL_PURIFIER);
                entries.add(SOUL_SHARD);
                entries.add(HEART);
                entries.add(AUGMENTATION_CORE);
        }

        public static void registerModItems() {
                ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(ModItems::customIngredients);
        }
}
