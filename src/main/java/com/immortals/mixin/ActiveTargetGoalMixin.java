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

@Mixin(ActiveTargetGoal.class)
public abstract class ActiveTargetGoalMixin {
    @Shadow
    @Nullable
    protected LivingEntity targetEntity;

    @Inject(method = "findClosestTarget", at = @At("RETURN"))
    private void afterFindClosestTarget(CallbackInfo ci) {
        try {
            if (this.targetEntity == null)
                return;
            if (!(this.targetEntity instanceof ServerPlayerEntity sp))
                return;
            ImmortalsData data = (ImmortalsData) sp;
            if (!data.isImmortal())
                return;

            MobEntity mob = ((TrackTargetGoalAccessor) this).getMob();
            if (mob == null)
                return;

            if (mob instanceof WardenEntity)
                return;

            if (mob instanceof HostileEntity) {
                ((ActiveTargetGoal<?>) (Object) this).setTargetEntity(null);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}