package com.immortals.api;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import com.mojang.datafixers.util.Pair;

// Grants a player true invisibility, even with armor on. Used for the Chronoreaver.
public class EquipmentVisibility {
    /**
     * Tell every other client that this player has no armor or items
     */
    public static void hide(ServerPlayerEntity target) {
        List<Pair<EquipmentSlot, ItemStack>> empty = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            empty.add(new Pair<>(slot, ItemStack.EMPTY));
        }
        EntityEquipmentUpdateS2CPacket pkt = new EntityEquipmentUpdateS2CPacket(target.getId(), empty);
        // send to everyone except the target
        target.getServer()
                .getPlayerManager()
                .getPlayerList()
                .stream()
                .filter(p -> p != target)
                .forEach(other -> other.networkHandler.sendPacket(pkt));
    }

    /**
     * Tell every other client the player’s real armor+items again
     */
    public static void show(ServerPlayerEntity target) {
        PlayerInventory inv = target.getInventory();
        List<Pair<EquipmentSlot, ItemStack>> real = new ArrayList<>();
        real.add(new Pair<>(EquipmentSlot.MAINHAND, target.getMainHandStack()));
        real.add(new Pair<>(EquipmentSlot.OFFHAND, target.getOffHandStack()));
        real.add(new Pair<>(EquipmentSlot.HEAD, inv.getStack(39)));
        real.add(new Pair<>(EquipmentSlot.CHEST, inv.getStack(40)));
        real.add(new Pair<>(EquipmentSlot.LEGS, inv.getStack(41)));
        real.add(new Pair<>(EquipmentSlot.FEET, inv.getStack(42)));

        EntityEquipmentUpdateS2CPacket pkt = new EntityEquipmentUpdateS2CPacket(target.getId(), real);

        target.getServer()
                .getPlayerManager()
                .getPlayerList()
                .stream()
                .filter(p -> p != target)
                .forEach(other -> other.networkHandler.sendPacket(pkt));
    }
}
