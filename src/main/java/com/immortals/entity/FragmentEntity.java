package com.immortals.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

// Fragment entity: Basically just an empty entity since ItemEntities can be picked up by hoppers
public class FragmentEntity extends Entity {
    private int age; // Add an age field
    private final ServerPlayerEntity creator; // The player who created this fragment

    public FragmentEntity(EntityType<? extends FragmentEntity> type, World world, ServerPlayerEntity creator) {
        super(type, world);
        this.age = 0; // Initialize age
        this.creator = creator; // Initialize creator
    }

    // This one is apparently required by the Entity superclass
    public FragmentEntity(EntityType<? extends FragmentEntity> type, World world) {
        super(type, world);
        this.age = 0; // Initialize age
        this.creator = null;
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readCustomData(ReadView nbt) {
        if (nbt.contains("Age")) {
            this.age = nbt.getInt("Age", 101);
        }
    }

    @Override
    protected void writeCustomData(WriteView nbt) {
        // Write age to NBT
        nbt.putInt("Age", this.age);
    }

    public void tick() {
        super.tick();

        // Increment age every tick
        this.age++;

        // read current velocity (set earlier via setVelocity or setDeltaMovement)
        Vec3d vel = this.getVelocity();

        // if there is motion, apply it
        if (!vel.equals(Vec3d.ZERO)) {
            // move the entity according to the velocity
            this.move(MovementType.SELF, vel);

            // simple gravity (tune or remove if you want no gravity)
            if (!this.hasNoGravity()) {
                this.setVelocity(this.getVelocity().add(0.0D, -0.04D, 0.0D)); // gravity
            }

            // simple drag so it eventually slows
            this.setVelocity(this.getVelocity().multiply(0.98D));
        }

        // Check for block collisions
        if (this.horizontalCollision || this.verticalCollision) {
            this.remove(Entity.RemovalReason.DISCARDED);
        }

        // Remove fragment after 5 seconds
        if (this.getAge() > 100) {
            this.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        return EntityDimensions.fixed(1.0F, 1.0F); // Set your size
    }

    public int getAge() {
        return this.age;
    }

    public ServerPlayerEntity getCreator() {
        return this.creator;
    }
}