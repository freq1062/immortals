package com.immortals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;

import com.immortals.network.NetworkChannels;

import java.util.Map;
import net.minecraft.util.math.RotationAxis;

public class ImmortalsClient implements ClientModInitializer {
    private Map<String, Identifier> spellTextures = Map.of(
            "timeslow", Identifier.of("immortals", "textures/quads/timeslow.png"),
            "dragon_ascent", Identifier.of("immortals",
                    "textures/quads/dragon_ascent.png"),
            "immortal", Identifier.of("immortals",
                    "textures/quads/immortal.png"));

    private static record RuneData(Identifier texture, double x, double y, double z, float size, int lifetimeTicks,
            int ticksElapsed) {
    }

    private static record SphereData(float[] color, double x, double y, double z, float size, int lifetimeTicks,
            int ticksElapsed) {
    }

    private static final java.util.List<Object> pendingObjects = new java.util.ArrayList<>();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkChannels.RuneS2CPayload.ID, (payload, context) -> {
            ClientWorld world = context.client().world;

            if (world == null) {
                return;
            }

            Identifier tex = spellTextures.getOrDefault(payload.spellId(),
                    Identifier.of("minecraft", "textures/missing_texture.png"));
            pendingObjects.add(new RuneData(tex, payload.x(), payload.y(), payload.z(),
                    payload.maxSize(), payload.lifetimeTicks(), 0));
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkChannels.SphereS2CPayload.ID, (payload, context) -> {
            ClientWorld world = context.client().world;

            if (world == null) {
                return;
            }

            float[] color = new float[] { payload.r(), payload.g(), payload.b(), payload.a() };
            pendingObjects.add(new SphereData(
                    color, payload.x(), payload.y(), payload.z(),
                    payload.maxSize(), payload.lifetimeTicks(), 0));
        });

        // Render pending objects every frame
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (pendingObjects.isEmpty())
                return;

            for (Object o : pendingObjects) {
                if (o == null)
                    continue;
                if (o instanceof RuneData rune) {
                    // Calculate display size based on lifetime
                    float displaySize = rune.size();
                    int totalLifetime = rune.lifetimeTicks();
                    int elapsedLifetime = rune.size() > 0 ? totalLifetime - rune.ticksElapsed() : 0;

                    // Animate size: grow in first 10%, shrink in last 10% of lifetime
                    float progress = elapsedLifetime / (float) totalLifetime;
                    if (progress < 0.1f) {
                        displaySize = rune.size() * (progress / 0.1f); // Grow from 0 to full size
                    } else if (progress > 0.9f) {
                        displaySize = rune.size() * ((1.0f - progress) / 0.1f); // Shrink from full size to 0
                    }

                    renderRune(context, rune.texture(), rune.x(), rune.y(), rune.z(), displaySize);
                } else if (o instanceof SphereData sphere) {
                    ClientWorld world = context.world();
                    Camera camera = context.camera();
                    Vec3d camPos = camera.getPos();
                    MatrixStack matrices = context.matrixStack();
                    VertexConsumerProvider consumers = context.consumers();
                    if (world == null || consumers == null)
                        return;

                    float camX = (float) camPos.x, camY = (float) camPos.y, camZ = (float) camPos.z;

                    matrices.push();
                    matrices.translate((float) (sphere.x - camX), (float) (sphere.y - camY), (float) (sphere.z - camZ));
                    long time = System.currentTimeMillis();
                    float rotation = (float) ((time / 20.0) % 360.0);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
                    renderSphere(context.matrixStack(),
                            context.consumers()
                                    .getBuffer(RenderLayer.getEntityTranslucent(
                                            Identifier.of("immortals", "textures/quads/white.png"))),
                            sphere.color(), sphere.size());
                    matrices.pop();
                }
            }
        });

        // Decrement lifetimes every tick and remove expired objects
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingObjects.isEmpty())
                return;

            for (int i = pendingObjects.size() - 1; i >= 0; i--) {
                Object o = pendingObjects.get(i);
                if (o instanceof RuneData rune) {
                    int newTicksElapsed = rune.ticksElapsed() + 1;
                    if (rune.lifetimeTicks() - newTicksElapsed <= 0) {
                        pendingObjects.remove(i);
                    } else {
                        pendingObjects.set(i,
                                new RuneData(rune.texture(), rune.x(), rune.y(), rune.z(), rune.size(),
                                        rune.lifetimeTicks(), newTicksElapsed));
                    }
                } else if (o instanceof SphereData sphere) {
                    int newTicksElapsed = sphere.ticksElapsed() + 1;
                    if (sphere.lifetimeTicks() - newTicksElapsed <= 0) {
                        pendingObjects.remove(i);
                    } else {
                        pendingObjects.set(i, new SphereData(sphere.color(), sphere.x(), sphere.y(), sphere.z(),
                                sphere.size(), sphere.lifetimeTicks(), newTicksElapsed));
                    }
                }
            }
        });
    }

    private void renderRune(WorldRenderContext context, Identifier texture, double px, double py, double pz,
            float size) {
        ClientWorld world = context.world();
        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (world == null || consumers == null)
            return;

        float camX = (float) camPos.x, camY = (float) camPos.y, camZ = (float) camPos.z;

        matrices.push();
        matrices.translate((float) (px - camX), (float) (py - camY) + 0.1f, (float) (pz - camZ));
        long time = System.currentTimeMillis();
        float rotation = (float) ((time / 20.0) % 360.0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
        // Modern rendering approach for 1.21.8
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));

        // Set up shader parameters directly with render layer
        // RenderSystem calls are largely deprecated in newer versions

        // Use the current animated size
        renderQuad(matrices, vc, size);
        matrices.pop();
    }

    // Renders a quad(flat square) of the given size centered at the origin
    private void renderQuad(MatrixStack matrices, VertexConsumer vc, float size) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f modelMat = entry.getPositionMatrix();

        float halfSize = size / 2; // Use the current animation size

        // Bottom-left
        vc.vertex(modelMat, -halfSize, 0, -halfSize).color(255, 255, 255, 255).texture(0, 1)
                .overlay(OverlayTexture.DEFAULT_UV).light(240, 240).normal(entry, 0.0f, 1.0f, 0.0f);
        // Bottom-right
        vc.vertex(modelMat, halfSize, 0, -halfSize).color(255, 255, 255, 255).texture(1, 1)
                .overlay(OverlayTexture.DEFAULT_UV).light(240, 240).normal(entry, 0.0f, 1.0f, 0.0f);
        // Top-right
        vc.vertex(modelMat, halfSize, 0, halfSize).color(255, 255, 255, 255).texture(1, 0)
                .overlay(OverlayTexture.DEFAULT_UV).light(240, 240).normal(entry, 0.0f, 1.0f, 0.0f);
        // Top-left
        vc.vertex(modelMat, -halfSize, 0, halfSize).color(255, 255, 255, 255).texture(0, 0)
                .overlay(OverlayTexture.DEFAULT_UV).light(240, 240).normal(entry, 0.0f, 1.0f, 0.0f);
    }

    // Renders a sphere of the given size centered at the origin
    private void renderSphere(MatrixStack matrices, VertexConsumer vc, float[] color, float size) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f modelMat = entry.getPositionMatrix();

        // Increase these for a smoother sphere (more costly)
        final int latitudeBands = 24;
        final int longitudeBands = 24;
        final float radius = size / 2f;

        float r = color[0];
        float g = color[1];
        float b = color[2];
        float a = color.length > 3 ? color[3] : 1.0f;

        for (int lat = 0; lat < latitudeBands; lat++) {
            float theta1 = (float) (lat * Math.PI / latitudeBands);
            float theta2 = (float) ((lat + 1) * Math.PI / latitudeBands);

            for (int lon = 0; lon < longitudeBands; lon++) {
                float phi1 = (float) (lon * 2.0 * Math.PI / longitudeBands);
                float phi2 = (float) ((lon + 1) * 2.0 * Math.PI / longitudeBands);

                // Four vertices of the quad
                float x1 = (float) (radius * Math.sin(theta1) * Math.cos(phi1));
                float y1 = (float) (radius * Math.cos(theta1));
                float z1 = (float) (radius * Math.sin(theta1) * Math.sin(phi1));

                float x2 = (float) (radius * Math.sin(theta1) * Math.cos(phi2));
                float y2 = (float) (radius * Math.cos(theta1));
                float z2 = (float) (radius * Math.sin(theta1) * Math.sin(phi2));

                float x3 = (float) (radius * Math.sin(theta2) * Math.cos(phi2));
                float y3 = (float) (radius * Math.cos(theta2));
                float z3 = (float) (radius * Math.sin(theta2) * Math.sin(phi2));

                float x4 = (float) (radius * Math.sin(theta2) * Math.cos(phi1));
                float y4 = (float) (radius * Math.cos(theta2));
                float z4 = (float) (radius * Math.sin(theta2) * Math.sin(phi1));

                // Normals (normalized vertex positions)
                float nx1 = x1 / radius, ny1 = y1 / radius, nz1 = z1 / radius;
                float nx2 = x2 / radius, ny2 = y2 / radius, nz2 = z2 / radius;
                float nx3 = x3 / radius, ny3 = y3 / radius, nz3 = z3 / radius;
                float nx4 = x4 / radius, ny4 = y4 / radius, nz4 = z4 / radius;

                // UV coordinates
                float u1 = (float) lon / longitudeBands;
                float v1 = (float) lat / latitudeBands;
                float u2 = (float) (lon + 1) / longitudeBands;
                float v2 = v1;
                float u3 = u2;
                float v3 = (float) (lat + 1) / latitudeBands;
                float u4 = u1;
                float v4 = v3;

                int light = 0x00F000F0; // Fullbright

                // First triangle (v1, v2, v3) - properly ordered counterclockwise
                vc.vertex(modelMat, x1, y1, z1).color(r, g, b, a).texture(u1, v1)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx1, ny1, nz1);
                vc.vertex(modelMat, x2, y2, z2).color(r, g, b, a).texture(u2, v2)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx2, ny2, nz2);
                vc.vertex(modelMat, x3, y3, z3).color(r, g, b, a).texture(u3, v3)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx3, ny3, nz3);

                // Second triangle (v1, v4, v3) - adjusted for counterclockwise winding
                vc.vertex(modelMat, x1, y1, z1).color(r, g, b, a).texture(u1, v1)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx1, ny1, nz1);
                vc.vertex(modelMat, x4, y4, z4).color(r, g, b, a).texture(u4, v4)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx4, ny4, nz4);
                vc.vertex(modelMat, x3, y3, z3).color(r, g, b, a).texture(u3, v3)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx3, ny3, nz3);
            }
        }
    }
}