package com.immortals.mixin;

import com.immortals.api.ImmortalsData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class DamageCapMixin {

    @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
    private void capDamage(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (!entity.getWorld().isClient() && entity instanceof net.minecraft.server.network.ServerPlayerEntity player) {
            ImmortalsData data = (ImmortalsData) player;
            if (data.isImmortal()) {
                // Allow /kill to still work
                if (cir.getReturnValue() >= 1_000_000.0f) {
                    return;
                }

                float maxAllowed = player.getMaxHealth() * 0.6f;
                float capped = Math.min(cir.getReturnValue(), maxAllowed);
                cir.setReturnValue(capped);
            }
        }
    }
}
