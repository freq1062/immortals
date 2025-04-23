package com.immortals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import com.immortals.item.ModItems;

public class ModEvents {
    public static void register() {
        // On player death: decrement corruption, drop one soul shard
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!entity.getWorld().isClient() && entity instanceof ServerPlayerEntity player) {
                PlayerCorruption.decrement(player);
                player.dropItem(new ItemStack(ModItems.SOUL_SHARD), false);
            }
        });

        // On right‐click with soul shard or purifier
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            ItemStack stack = player.getStackInHand(hand);

            // Soul Shard: +1 up to +3
            if (stack.getItem() == ModItems.SOUL_SHARD) {
                if (PlayerCorruption.get((ServerPlayerEntity) player) < 3) {
                    PlayerCorruption.increment((ServerPlayerEntity) player);
                    player.sendMessage(Text.literal("You feel your soul strengthening..."), true);
                    stack.decrement(1);
                    return ActionResult.SUCCESS;
                }
                player.sendMessage(Text.literal("Your soul is already at its peak."), true);
                return ActionResult.FAIL;
            }

            // Soul Purifier: +1 up to 0
            if (stack.getItem() == ModItems.SOUL_PURIFIER) {
                if (PlayerCorruption.get((ServerPlayerEntity) player) < 0) {
                    PlayerCorruption.increment((ServerPlayerEntity) player);
                    player.sendMessage(Text.literal("The purifier calms your inner darkness."), true);
                    stack.decrement(1);
                    return ActionResult.SUCCESS;
                }
                player.sendMessage(Text.literal("You feel no corruption to cleanse."), true);
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });

        // Every server tick: if a player has an ascension totem, strip out regular
        // totems
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getInventory().contains(new ItemStack(ModItems.ASCENSION_TOTEM))) {
                    for (int i = 0; i < player.getInventory().size(); i++) {
                        ItemStack s = player.getInventory().getStack(i);
                        if (s.getItem() == Items.TOTEM_OF_UNDYING) {
                            player.getInventory().removeStack(i);
                        }
                    }
                }
            }
        });
    }
}
