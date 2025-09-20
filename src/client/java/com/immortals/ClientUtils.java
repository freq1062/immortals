package com.immortals;

import org.joml.Matrix4f;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
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

        // Renders a sphere of the given size at (x,y,z) with redundant panels for full
        // coverage
        public static void renderSphere(WorldRenderContext context, double x, double y, double z, float[] color,
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
                matrices.translate((float) (x - camX), (float) (y - camY), (float) (z - camZ));

                // Get color components with default alpha of 1.0 if not specified
                float r = color[0];
                float g = color[1];
                float b = color[2];
                float a = color.length > 3 ? color[3] : 1.0f;

                // Create a vertex consumer for our sphere
                VertexConsumer vc = context.consumers()
                                .getBuffer(RenderLayer.getEntityTranslucent(
                                                Identifier.of("immortals", "textures/quads/white.png")));

                MatrixStack.Entry entry = matrices.peek();
                Matrix4f modelMat = entry.getPositionMatrix();

                // Use more bands for a smoother sphere
                final int latitudeBands = 12;
                final int longitudeBands = 24;
                final float radius = size / 2f;

                int light = 0xF000F0; // Full brightness

                for (int lat = 0; lat < latitudeBands; lat++) {
                        float theta1 = (float) (lat * Math.PI / latitudeBands);
                        float theta2 = (float) ((lat + 1) * Math.PI / latitudeBands);

                        for (int lon = 0; lon < longitudeBands; lon++) {
                                float phi1 = (float) (lon * 2.0 * Math.PI / longitudeBands);
                                float phi2 = (float) ((lon + 1) * 2.0 * Math.PI / longitudeBands);

                                // Calculate the four vertices of the quad
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

                                // normals (pointing outward)
                                float nx1 = x1 / radius, ny1 = y1 / radius, nz1 = z1 / radius;
                                float nx2 = x2 / radius, ny2 = y2 / radius, nz2 = z2 / radius;
                                float nx3 = x3 / radius, ny3 = y3 / radius, nz3 = z3 / radius;
                                float nx4 = x4 / radius, ny4 = y4 / radius, nz4 = z4 / radius;

                                // uvs
                                float u1 = (float) lon / longitudeBands;
                                float v1 = (float) lat / latitudeBands;
                                float u2 = (float) (lon + 1) / longitudeBands;
                                float v2 = v1;
                                float u3 = u2;
                                float v3 = (float) (lat + 1) / latitudeBands;
                                float u4 = u1;
                                float v4 = v3;

                                // Ensure consistent UVs at the poles
                                if (lat == 0) {
                                        v1 = 0;
                                } else if (lat == latitudeBands - 1) {
                                        v3 = 1;
                                }

                                // First triangle (v1, v2, v3) - clockwise winding
                                vc.vertex(modelMat, x1, y1, z1).color(r, g, b, a).texture(u1, v1)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx1, ny1, nz1);
                                vc.vertex(modelMat, x2, y2, z2).color(r, g, b, a).texture(u2, v2)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx2, ny2, nz2);
                                vc.vertex(modelMat, x3, y3, z3).color(r, g, b, a).texture(u3, v3)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx3, ny3, nz3);

                                // Second triangle (v1, v3, v4) - clockwise winding
                                // vc.vertex(modelMat, x1, y1, z1).color(r, g, b, a).texture(u1, v1)
                                // .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                // .normal(entry, nx1, ny1, nz1);
                                // vc.vertex(modelMat, x3, y3, z3).color(r, g, b, a).texture(u3, v3)
                                // .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                // .normal(entry, nx3, ny3, nz3);
                                // vc.vertex(modelMat, x4, y4, z4).color(r, g, b, a).texture(u4, v4)
                                // .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                // .normal(entry, nx4, ny4, nz4);

                                // Additional redundant quads in different orders for full coverage
                                // Triangle (v2, v3, v4)
                                vc.vertex(modelMat, x2, y2, z2).color(r, g, b, a).texture(u2, v2)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx2, ny2, nz2);
                                vc.vertex(modelMat, x3, y3, z3).color(r, g, b, a).texture(u3, v3)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx3, ny3, nz3);
                                vc.vertex(modelMat, x4, y4, z4).color(r, g, b, a).texture(u4, v4)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx4, ny4, nz4);

                                // Triangle (v1, v2, v4)
                                vc.vertex(modelMat, x1, y1, z1).color(r, g, b, a).texture(u1, v1)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx1, ny1, nz1);
                                vc.vertex(modelMat, x2, y2, z2).color(r, g, b, a).texture(u2, v2)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx2, ny2, nz2);
                                vc.vertex(modelMat, x4, y4, z4).color(r, g, b, a).texture(u4, v4)
                                                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                                                .normal(entry, nx4, ny4, nz4);
                        }
                }

                matrices.pop();
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

        public static void renderPlayerGhost(WorldRenderContext context,
                        ClientPlayerEntity player,
                        double x, double y, double z,
                        float[] rgba) {
                if (player == null || context.consumers() == null || context.world() == null)
                        return;

                MinecraftClient client = MinecraftClient.getInstance();
                Camera camera = context.camera();
                MatrixStack matrices = context.matrixStack();
                Vec3d camPos = camera.getPos();

                // RGBA
                float r = rgba[0];
                float g = rgba[1];
                float b = rgba[2];
                float a = rgba.length > 3 ? rgba[3] : 1.0f;

                // Get player renderer and model
                EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
                PlayerEntityRenderer playerRenderer = (PlayerEntityRenderer) dispatcher.getRenderer(player);
                if (playerRenderer == null)
                        return;

                // Use the renderer's model instance (do NOT create a new model each frame)
                PlayerEntityModel model = playerRenderer.getModel();

                // Prepare vertex consumer and transform to the snapshot position
                VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders()
                                .getEntityVertexConsumers();

                matrices.push();

                // Translate to desired world position relative to camera
                matrices.translate((float) (x - camPos.x), (float) (y - camPos.y + 1), (float) (z - camPos.z));

                // Rotate world yaw so the player faces the same direction as the live player.
                // Use negative yaw because matrix rotation is clockwise for positive degrees;
                // player yaw is standard world heading
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-player.getYaw()));

                // Correct the upside-down issue by rotating the model 180 degrees around the
                // X-axis
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));

                // Choose a blank white texture and render layer
                Identifier blankTexture = Identifier.of("immortals", "textures/quads/white.png");
                RenderLayer layer = RenderLayer.getEntityTranslucent(blankTexture);

                VertexConsumer consumer = immediate.getBuffer(layer);

                int light = 0xF000F0; // fullbright for ghost effect — change if you want world lighting

                int color = ((int) (r * 255) << 24) | ((int) (g * 255) << 16) | ((int) (b * 255) << 8)
                                | (int) (a * 255);
                // Render the model geometry with color + alpha
                model.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, color);

                // flush buffers of the immediate provider
                immediate.draw();

                matrices.pop();
        }
}
