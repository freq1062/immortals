package com.immortals;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;

import java.util.function.Function;

public class ModItems {

        public static LoreComponent getFormattedLore(String itemName) {
                String lore;
                switch (itemName) {
                        case "ascension_totem" -> {
                                lore = """
                                                §cSome say that the First Immortal lives on in this item.
                                                §bOnce you ascend, you will never be able to return to your mortal life.
                                                        """;

                        }
                        case "ascension_relic" -> {
                                lore = """
                                                §cThese relics were manifested long ago
                                                §cfrom an intense fear of death.
                                                """;
                        }
                        case "soul_purifier" -> {
                                lore = """
                                                §cA concentrated piece of positive magic. However,
                                                The soul purifier can only return your
                                                soul to its original state.
                                                """;
                        }
                        case "soul_shard" -> {
                                lore = """
                                                §cA shard of the victim's soul, corrupted by Immortal power.
                                                """;
                        }
                        case "heart" -> {
                                lore = """
                                                §bA piece of mortal essence.
                                                """;
                        }
                        case "artificial_heart" -> {
                                lore = """
                                                §bA piece of natural essence,
                                                carefully crafted to imitate a heart.
                                                It can only heal you to your base state.
                                                """;
                        }
                        case "augmentation_core" -> {
                                lore = """

                                                §bTo compete with magical powers, Mortal ingenuity
                                                discovered how to augment the attributes of various items.

                                                §fHold an unstackable item in your offhand while holding this
                                                item in your main hand, and right click to augment it. Does not stack.
                                                """;
                        }
                        case "phasebreaker" -> {
                                lore = String.format("""
                                                §dA blade forged from space folded into itself.

                                                §6§lFRACTAL EDGE: §fEvery %d hits, the sword induces a
                                                flurry of hits on the target dealing %.2f%%
                                                of their max health as damage.

                                                §6§lPHASE CHANGE: §fPress your activate spell key or
                                                Shift + right click to teleport in the direction
                                                you are facing. Does not go through walls.
                                                §a %d s seconds cooldown.
                                                """,
                                                Main.CONFIG.getInt("fractalEdgeHits"),
                                                Main.CONFIG.getDouble("fractalEdgeDmg") * 100,
                                                Main.CONFIG.getInt("phaseChangeCooldown") / 20);
                        }
                        case "chronoreaver" -> {
                                lore = String.format("""
                                                §iCrafted in the Null Space where time collapses,
                                                Each strike lands before it is swung.

                                                §6§lOVERCLOCK: §fShift + right click to apply haste 5 and speed
                                                3 for %d seconds. §a%d second cooldown.

                                                §6§lBLINK: §fWhen below %.2f health, the axe applies true
                                                invisibility for %d seconds.
                                                §a%d second cooldown.
                                                """,
                                                Main.CONFIG.getInt("overclockDuration") / 20,
                                                Main.CONFIG.getInt("overclockCooldown") / 20,
                                                Main.CONFIG.getDouble("blinkThreshold"),
                                                Main.CONFIG.getInt("blinkDuration") / 20,
                                                Main.CONFIG.getInt("blinkCooldown") / 20);
                        }
                        case "timekeeper" -> {
                                lore = """
                                                §eThe timekeeper runs, maintaining the flow of time in the universe.
                                                §eThose who possess it have the power to manipulate time itself.
                                                """;
                        }
                        default -> {
                                lore = "No lore found for item: " + itemName;
                        }
                }
                String[] lines = lore.split("\n");
                java.util.List<net.minecraft.text.Text> formattedLore = new java.util.ArrayList<>();
                String previousFormatting = ""; // Keep track of the previous formatting style

                for (String line : lines) {
                        line = line.replaceAll("\\s+", " ").trim();
                        String[] parts = line.split("(?=§[0-9a-fk-or])");
                        for (String part : parts) {
                                if (part.startsWith("§")) {
                                        previousFormatting = part.substring(0, 2); // Update formatting style
                                } else {
                                        part = previousFormatting + part; // Apply previous formatting if none is found
                                }
                                formattedLore.add(net.minecraft.text.Text.literal(part));
                        }
                }
                return new LoreComponent(formattedLore);
        }

        public static final Item ASCENSION_TOTEM = registerItem("ascension_totem", Item::new,
                        new Item.Settings()
                                        .rarity(Rarity.EPIC)
                                        .component(DataComponentTypes.LORE, getFormattedLore("ascension_totem"))
                                        .maxCount(1)
                                        .fireproof());

        public static final Item ASCENSION_RELIC = registerItem("ascension_relic", Item::new,
                        new Item.Settings()
                                        .component(DataComponentTypes.LORE, getFormattedLore("ascension_relic"))
                                        .rarity(Rarity.RARE));

        public static final Item SOUL_PURIFIER = registerItem("soul_purifier", Item::new,
                        new Item.Settings()
                                        .component(DataComponentTypes.LORE,
                                                        getFormattedLore("soul_purifier")));
        public static final Item SOUL_SHARD = registerItem("soul_shard", Item::new, new Item.Settings()
                        .component(DataComponentTypes.LORE, getFormattedLore("soul_shard")));
        public static final Item HEART = registerItem("heart", Item::new, new Item.Settings()
                        .component(DataComponentTypes.LORE, getFormattedLore("heart")));
        public static final Item ARTIFICIAL_HEART = registerItem("artificial_heart", Item::new, new Item.Settings()
                        .component(DataComponentTypes.LORE, getFormattedLore("artificial_heart")));
        public static final Item AUGMENTATION_CORE = registerItem("augmentation_core", Item::new, new Item.Settings()
                        .component(DataComponentTypes.LORE, getFormattedLore("augmentation_core")));

        public static final Item PHASEBREAKER = registerItem(
                        "phasebreaker",
                        settings -> new net.minecraft.item.Item(
                                        settings),
                        new Item.Settings()
                                        .rarity(Rarity.EPIC)
                                        .sword(ToolMaterial.NETHERITE, 8 - 1, -2.4F)
                                        .component(
                                                        DataComponentTypes.LORE,
                                                        getFormattedLore("phasebreaker"))
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
                                                        getFormattedLore("chronoreaver")

                                        )
                                        .enchantable(15));

        public static final Item TIMEKEEPER = registerItem("timekeeper", Item::new,
                        new Item.Settings()
                                        .rarity(Rarity.EPIC)
                                        .component(DataComponentTypes.LORE,
                                                        getFormattedLore("timekeeper"))
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
                entries.add(ARTIFICIAL_HEART);
                entries.add(AUGMENTATION_CORE);
                entries.add(PHASEBREAKER);
                entries.add(CHRONOREAVER);
                entries.add(TIMEKEEPER);
        }

        public static void registerModItems() {
                ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(ModItems::customIngredients);
        }
}
