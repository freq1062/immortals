package com.immortals.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import com.immortals.Utils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(PlayerEntity.class)
public abstract class PlayerSwingMixin {
    @Inject(method = "swingHand", at = @At("HEAD"), cancellable = true)
    private void onSwingHand(Hand hand, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient() && hand == Hand.MAIN_HAND) {
            // Call your fragment-spawn code here
            Utils.spawnFragment((ServerPlayerEntity) player, (ServerWorld) player.getWorld());
        }
    }
}
