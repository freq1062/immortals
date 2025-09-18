package com.immortals;

import org.joml.Matrix4f;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class ClientUtils {

        public static void renderRune(WorldRenderContext context, Identifier texture, double px, double py, double pz,
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
                ClientUtils.renderQuad(matrices, vc, size);
                matrices.pop();
        }

        // Renders a quad(flat square) of the given size centered at the origin
        public static void renderQuad(MatrixStack matrices, VertexConsumer vc, float size) {
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

        // Renders a sphere of the given size at (x,y,z)
        public static void renderSphere(WorldRenderContext context, double x, double y, double z, float[] color,
                        float size) {
                // Log the initial parameters
                System.out.println("Rendering sphere at position: [" + x + ", " + y + ", " + z + "]");
                System.out.println("Sphere size: " + size + ", color: [" +
                                java.util.stream.IntStream.range(0, color.length)
                                                .mapToObj(i -> String.valueOf(color[i]))
                                                .collect(java.util.stream.Collectors.joining(", "))
                                + "]");

                ClientWorld world = context.world();
                Camera camera = context.camera();
                Vec3d camPos = camera.getPos();
                MatrixStack matrices = context.matrixStack();
                VertexConsumerProvider consumers = context.consumers();
                if (world == null || consumers == null) {
                        System.out.println("Error: world or consumers is null");
                        return;
                }

                float camX = (float) camPos.x, camY = (float) camPos.y, camZ = (float) camPos.z;
                System.out.println("Camera position: [" + camX + ", " + camY + ", " + camZ + "]");

                matrices.push();
                matrices.translate((float) (x - camX), (float) (y - camY), (float) (z - camZ));
                long time = System.currentTimeMillis();
                float rotation = (float) ((time / 20.0) % 360.0);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
                System.out.println("Applied rotation: " + rotation);

                VertexConsumer vc = context.consumers()
                                .getBuffer(RenderLayer.getEntitySolid(
                                                Identifier.of("immortals", "textures/quads/white.png")));

                MatrixStack.Entry entry = matrices.peek();
                Matrix4f modelMat = entry.getPositionMatrix();

                // Increase these for a smoother sphere (more costly)
                final int latitudeBands = 12; // Increased for smoother sphere
                final int longitudeBands = 24; // Increased for smoother sphere
                final float radius = size / 2f;

                System.out.println("Sphere rendering parameters: radius=" + radius +
                                ", latitudeBands=" + latitudeBands +
                                ", longitudeBands=" + longitudeBands);

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

                                // Debug first and last vertices
                                if (lat == 0 && lon == 0) {
                                        System.out.println("First quad calculation: lat=" + lat + ", lon=" + lon);
                                        System.out.println("Angles: theta1=" + theta1 + ", theta2=" + theta2 +
                                                        ", phi1=" + phi1 + ", phi2=" + phi2);
                                }

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

                                // Print first quad vertex info
                                if (lat == 0 && lon == 0) {
                                        System.out.println("First quad vertices:");
                                        System.out.println("v1: [" + x1 + ", " + y1 + ", " + z1 + "]");
                                        System.out.println("v2: [" + x2 + ", " + y2 + ", " + z2 + "]");
                                        System.out.println("v3: [" + x3 + ", " + y3 + ", " + z3 + "]");
                                        System.out.println("v4: [" + x4 + ", " + y4 + ", " + z4 + "]");
                                }

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

                                int light = 0xF000F0; // Fullbright

                                // First triangle - v1, v2, v3 (corrected winding order for counterclockwise)
                                vc.vertex(modelMat, x1, y1, z1).color(r, g, b, a).texture(u1, v1)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx1, ny1, nz1);
                                vc.vertex(modelMat, x2, y2, z2).color(r, g, b, a).texture(u2, v2)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx2, ny2, nz2);
                                vc.vertex(modelMat, x3, y3, z3).color(r, g, b, a).texture(u3, v3)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx3, ny3, nz3);

                                // Second triangle - v1, v3, v4 (counterclockwise winding)
                                vc.vertex(modelMat, x1, y1, z1).color(r, g, b, a).texture(u1, v1)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx1, ny1, nz1);
                                vc.vertex(modelMat, x3, y3, z3).color(r, g, b, a).texture(u3, v3)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx3, ny3, nz3);
                                vc.vertex(modelMat, x4, y4, z4).color(r, g, b, a).texture(u4, v4)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx4, ny4, nz4);
                        }
                }

                System.out.println("Sphere rendering completed.");
                matrices.pop();
        }

        public static void renderFragments() {
                // rotate 45 on z
        }

        public static void renderItem(WorldRenderContext context, Item item, double x1, double y1, double z1, double x2,
                        double y2, double z2, float size) {
                var itemRenderer = MinecraftClient.getInstance().getItemRenderer();
                var matrices = context.matrixStack();
                var consumers = context.consumers();
                var world = context.world();
                if (consumers == null || world == null)
                        return;

                var camPos = context.camera().getPos();
                matrices.push();
                matrices.translate((float) (x1 - camPos.x), (float) (y1 - camPos.y), (float) (z1 - camPos.z));
                matrices.scale(size, size, size);
                // Apply rotation to face the target point
                Vec3d dir = new Vec3d(x2 - x1, y2 - y1, z2 - z1).normalize();
                float yaw = (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90f;
                float pitch = (float) -Math.toDegrees(Math.asin(dir.y));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
                // Use the item's default model transformation
                itemRenderer.renderItem(
                                item.getDefaultStack(),
                                ItemDisplayContext.GROUND,
                                0xF000F0,
                                OverlayTexture.DEFAULT_UV,
                                matrices,
                                consumers,
                                world,
                                0);
                matrices.pop();
        }

        // Render a block at the given position
        public void renderBlock(WorldRenderContext context, BlockState blockState, double x1, double y1, double z1) {
                var matrices = context.matrixStack();
                var consumers = context.consumers();
                var world = context.world();
                if (consumers == null || world == null)
                        return;

                var camPos = context.camera().getPos();
                matrices.push();
                matrices.translate((float) (x1 - camPos.x), (float) (y1 - camPos.y), (float) (z1 - camPos.z));
                MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(
                                blockState,
                                matrices,
                                consumers,
                                0xF000F0,
                                OverlayTexture.DEFAULT_UV);
                matrices.pop();
        }
}
