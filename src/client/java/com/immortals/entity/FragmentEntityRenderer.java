package com.immortals.entity;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Vector3f;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class FragmentEntityRenderer extends EntityRenderer<FragmentEntity, FragmentEntityRenderState> {

    public FragmentEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
        this.shadowOpacity = 0.75F;
    }

    @Override
    public FragmentEntityRenderState createRenderState() {
        return new FragmentEntityRenderState();
    }

    @Override
    public void updateRenderState(FragmentEntity entity, FragmentEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.fragmentEntity = entity;

        // Handle yaw interpolation
        float prevYaw = entity.lastYaw;
        float currYaw = entity.getYaw();
        state.yaw = MathHelper.lerp(tickDelta, prevYaw, currYaw);

        // Handle pitch interpolation
        float prevPitch = entity.lastPitch;
        float currPitch = entity.getPitch();
        state.pitch = MathHelper.lerp(tickDelta, prevPitch, currPitch);
    }

    private void rotateAroundPivot(MatrixStack ms, Vector3f pivot, float yawDeg, float pitchDeg, float rollDeg) {
        // move pivot to origin
        ms.translate(pivot.x(), pivot.y(), pivot.z());

        // apply rotations (order: yaw (Y) then pitch (X) then roll (Z) — change if you
        // want different local axes)
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDeg));
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rollDeg));

        // move back
        ms.translate(-pivot.x(), -pivot.y(), -pivot.z());
    }

    @Override
    public void render(FragmentEntityRenderState state, MatrixStack matrices,
            VertexConsumerProvider consumers, int light) {

        matrices.push();

        // overall scale for the fragment
        matrices.scale(1.0F, 1.0F, 1.0F);

        // --- pick a pivot in model-space to rotate around ---
        // These numbers are in Minecraft world units (1.0 = 1 block).
        // You'll tune these empirically. Good starting guesses:
        Vector3f pivot = new Vector3f(0.0F, 0.25F, 0.0F); // try (0,0.25,0) or (0,0.125,0) etc.

        // Interpolated yaw/pitch already computed in state (ensure you set them in
        // extractRenderState)
        float yawForRender = state.yaw; // degrees
        float pitchForRender = state.pitch; // degrees

        // Rotate around chosen pivot so the blade points toward yaw/pitch
        rotateAroundPivot(matrices, pivot, -yawForRender + 90.0F, pitchForRender, 0.0F);

        // Optional additional fixed rotations to make the sword oriented how you like
        // (these rotate around the model origin, so they run after pivot adjustments)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(60.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0F));

        // Now a small positional tweak after rotations (useful to centre visually)
        matrices.translate(0.0F, 0.10F, 0.0F); // tune Y/Z/X here

        MinecraftClient.getInstance().getItemRenderer().renderItem(
                state.stack,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                light,
                net.minecraft.client.render.OverlayTexture.DEFAULT_UV,
                matrices,
                consumers,
                null,
                0);

        matrices.pop();
    }

    public Identifier getTexture(FragmentEntity entity) {
        return Identifier.of("textures/item/netherite_sword.png");
    }
}