package com.immortals.mixin;

import com.immortals.api.ImmortalsData; // adjust import to whatever your ImmortalsData accessor is
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
import java.lang.reflect.Field;

@Mixin(ActiveTargetGoal.class)
public abstract class ActiveTargetGoalMixin {
    // ActiveTargetGoal defines targetEntity — safe to shadow
    @Shadow
    @Nullable
    protected LivingEntity targetEntity;

    @Inject(method = "findClosestTarget", at = @At("RETURN"))
    private void afterFindClosestTarget(CallbackInfo ci) {
        try {
            // if findClosestTarget didn't pick anyone, nothing to do
            if (this.targetEntity == null)
                return;

            // only care about player targets
            if (!(this.targetEntity instanceof ServerPlayerEntity sp))
                return;

            // check whether player is immortal
            ImmortalsData data = (ImmortalsData) sp;
            if (!data.isImmortal())
                return;

            // reflectively access the 'mob' field declared in TrackTargetGoal (superclass)
            Field mobField = ActiveTargetGoal.class.getSuperclass().getDeclaredField("mob");
            mobField.setAccessible(true);
            MobEntity mob = (MobEntity) mobField.get(this);
            if (mob == null)
                return;

            // Wardens are allowed to target immortals — leave them alone
            if (mob instanceof WardenEntity)
                return;

            // If the owner is a hostile mob, cancel target acquisition (neutral until
            // provoked)
            if (mob instanceof HostileEntity) {
                // clear the candidate target so the ActiveTargetGoal won't start
                ((ActiveTargetGoal<?>) (Object) this).setTargetEntity(null);
                // debug log (optional)
                System.out.println("[Immortals] Cleared targetEntity of " + mob.getClass().getSimpleName()
                        + " because target is Immortal " + sp.getName().getString());
            }

        } catch (NoSuchFieldException e) {
            // if reflection fails (field name changed), fail silently but log to console
            e.printStackTrace();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}