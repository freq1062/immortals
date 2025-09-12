package com.immortals.mixin;

import com.immortals.api.ImmortalsData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.damage.DamageTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class FallDamageImmunityMixin {
    @Inject(method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void cancelFallDamage(ServerWorld world,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if (source.isOf(DamageTypes.FALL)
                && (LivingEntity) (Object) this instanceof ServerPlayerEntity player
                && !world.isClient
                && ((ImmortalsData) player).isImmortal()) {
            cir.setReturnValue(false);
        }
    }
}
