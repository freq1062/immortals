package com.immortals.Mortal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import com.mojang.datafixers.util.Pair;

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
        real.add(new Pair<>(EquipmentSlot.HEAD, inv.getArmorStack(3)));
        real.add(new Pair<>(EquipmentSlot.CHEST, inv.getArmorStack(2)));
        real.add(new Pair<>(EquipmentSlot.LEGS, inv.getArmorStack(1)));
        real.add(new Pair<>(EquipmentSlot.FEET, inv.getArmorStack(0)));

        EntityEquipmentUpdateS2CPacket pkt = new EntityEquipmentUpdateS2CPacket(target.getId(), real);

        target.getServer()
                .getPlayerManager()
                .getPlayerList()
                .stream()
                .filter(p -> p != target)
                .forEach(other -> other.networkHandler.sendPacket(pkt));
    }
}
