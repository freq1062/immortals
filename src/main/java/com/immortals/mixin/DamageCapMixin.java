package com.immortals.mixin;

import com.immortals.Utils;
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
            if (Utils.getAscended(player)) {
                float maxAllowed = player.getMaxHealth() * 0.6f;
                float capped = Math.min(cir.getReturnValue(), maxAllowed);
                cir.setReturnValue(capped);
            }
        }
    }
}
