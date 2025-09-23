package com.immortals.mixin;

import com.immortals.api.ImmortalsData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Safer version of your mixin:
 * - shadows setTargetEntity instead of casting this to ActiveTargetGoal
 * - uses the TrackTargetGoalAccessor to get the mob
 * - does several server-only / null / sanity checks before modifying state
 */
@Mixin(ActiveTargetGoal.class)
public abstract class ActiveTargetGoalMixin {
    @Shadow
    @Nullable
    protected LivingEntity targetEntity;

    /**
     * Shadow the setter so we don't cast the mixin object back to the target class.
     * If this method name differs in your mappings, update accordingly.
     */
    @Shadow
    protected abstract void setTargetEntity(@Nullable LivingEntity target);

    @Inject(method = "findClosestTarget", at = @At("RETURN"))
    private void afterFindClosestTarget(CallbackInfo ci) {
        try {
            // basic null checks
            if (this.targetEntity == null)
                return;
            if (!(this.targetEntity instanceof ServerPlayerEntity))
                return;

            ServerPlayerEntity sp = (ServerPlayerEntity) this.targetEntity;
            if (!(sp instanceof ImmortalsData))
                return; // safe instanceof check
            ImmortalsData data = (ImmortalsData) sp;

            if (!data.isImmortal())
                return;

            // Access the mob being controlled by this goal via accessor
            MobEntity mob = ((TrackTargetGoalAccessor) (Object) this).getMob();
            if (mob == null)
                return;

            // server-only checks (guard against client-side or removed entity)
            if (mob.getWorld() == null || mob.getWorld().isClient)
                return;
            if (mob.isRemoved() || !mob.isAlive())
                return;

            // don't touch wardens and make sure it's a hostile mob before clearing
            if (mob instanceof WardenEntity)
                return;

            if (mob instanceof HostileEntity) {
                // extra sanity: only clear the target if it's actually our player (avoid
                // clearing unintended targets)
                if (this.targetEntity == sp) {
                    this.setTargetEntity(null);
                }
            }
        } catch (Throwable t) {
            // keep printing but avoid crashing server if something unexpected happens
            t.printStackTrace();
        }
    }
}
