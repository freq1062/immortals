package com.immortals.entity;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

// I dont understand why tf you even need this but i followed this from a github repo :/
public final class FragmentEntityRenderState extends EntityRenderState {
    public FragmentEntity fragmentEntity;
    public ItemStack stack;
    public float yaw;
    public float pitch;

    public FragmentEntityRenderState() {
        this.stack = new ItemStack(Items.NETHERITE_SWORD);
    }
}