package com.immortals.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.AbstractMap.SimpleEntry;

import net.minecraft.util.math.RotationAxis;
import com.mojang.blaze3d.systems.RenderSystem;

public class ImmortalsClient implements ClientModInitializer {
  private static final Identifier TIMESLOW_TEX = Identifier.of("immortals", "textures/runecircles/timeslow.png");
  private static final Identifier DRAGONASCENT_TEX = Identifier.of("immortals",
      "textures/runecircles/dragon_ascent.png");

  // Stores active spells for each player UUID, with their rune position and spell
  // set
  private static final Map<java.util.AbstractMap.SimpleEntry<UUID, String>, Object[]> activeSpells = new ConcurrentHashMap<>();

  @Override
  public void onInitializeClient() {
    WorldRenderEvents.AFTER_ENTITIES.register(context -> {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world == null)
        return;

      // Store player UUID and their rune position if scoreboard is 1
      for (PlayerEntity clientPlayer : client.world.getPlayers()) {
        Scoreboard sb = clientPlayer.getWorld().getScoreboard();
        ScoreboardObjective timeSlowObjective = sb.getNullableObjective("timeslow");
        int timeSlowScore = -1;
        if (timeSlowObjective != null) {
          var scoreObj = sb.getScore(clientPlayer, timeSlowObjective);
          if (scoreObj != null) {
            timeSlowScore = scoreObj.getScore();
          }
        }
        ScoreboardObjective dragonAscentObj = sb.getNullableObjective("dragon_ascent");
        int dragonAscentScore = -1;
        if (dragonAscentObj != null) {
          var scoreObj = sb.getScore(clientPlayer, dragonAscentObj);
          if (scoreObj != null) {
            dragonAscentScore = scoreObj.getScore();
          }
        }
        UUID uuid = clientPlayer.getUuid();

        if (timeSlowScore == 1) {
          String spellType = "timeslow";
          Map.Entry<SimpleEntry<UUID, String>, Object[]> existingEntry = activeSpells.entrySet()
              .stream()
              .filter(e -> e.getKey().getKey().equals(uuid) && e.getKey().getValue().equals(spellType))
              .findFirst().orElse(null);

          if (existingEntry == null) {
            activeSpells.put(new SimpleEntry<>(uuid, spellType), new Object[] {
                clientPlayer,
                clientPlayer.getX(),
                clientPlayer.getBoundingBox().minY + 0.01,
                clientPlayer.getZ(),
                TIMESLOW_TEX
            });
          }
        } else if (timeSlowScore == 0) {
          // Remove if not active
          activeSpells.entrySet()
              .removeIf(e -> e.getKey().getKey().equals(uuid) && e.getKey().getValue().equals("timeslow"));
        }
        if (dragonAscentScore == 1) {
          String spellType = "dragon_ascent";
          Map.Entry<SimpleEntry<UUID, String>, Object[]> existingEntry = activeSpells.entrySet()
              .stream()
              .filter(e -> e.getKey().getKey().equals(uuid) && e.getKey().getValue().equals(spellType))
              .findFirst().orElse(null);

          if (existingEntry == null) {
            activeSpells.put(new SimpleEntry<>(uuid, spellType), new Object[] {
                clientPlayer,
                clientPlayer.getX(),
                clientPlayer.getBoundingBox().minY + 0.01,
                clientPlayer.getZ(),
                DRAGONASCENT_TEX
            });
          }
        } else if (dragonAscentScore == 0) {
          // Remove if not active
          activeSpells.entrySet()
              .removeIf(e -> e.getKey().getKey().equals(uuid) && e.getKey().getValue().equals("dragon_ascent"));
        }
      }

      // Render runes at stored positions for all active players
      for (Object[] entry : activeSpells.values()) {
        double px = (double) entry[1];
        double py = (double) entry[2];
        double pz = (double) entry[3];
        Identifier texture = (Identifier) entry[4];
        renderRune(context, texture, px, py, pz);
      }
    });
  }

  private void renderRune(WorldRenderContext context, Identifier texture, double px, double py, double pz) {
    ClientWorld world = context.world();
    Camera camera = context.camera();
    Vec3d camPos = camera.getPos();
    MatrixStack matrices = context.matrixStack();
    VertexConsumerProvider consumers = context.consumers();
    if (world == null || consumers == null)
      return;

    // Fixed position in the world (0, 80, 0)
    float camX = (float) camPos.x, camY = (float) camPos.y, camZ = (float) camPos.z;

    // Translate to the fixed position relative to camera
    matrices.push();
    matrices.translate((float) (px - camX), (float) (py - camY), (float) (pz - camZ));

    // Apply rotation
    long time = System.currentTimeMillis();
    float rotation = (float) ((time / 20.0) % 360.0); // Rotate smoothly and continuously
    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));

    // Set up texture and rendering
    RenderSystem.setShaderTexture(0, texture);
    RenderSystem.disableCull(); // Allow seeing the quad from both sides
    VertexConsumer vc = consumers.getBuffer(
        RenderLayer.getEntityCutoutNoCull(texture));

    // Render a horizontal quad (3x3 blocks)
    renderQuad(matrices, vc, 1.0f, 1.0f, 1.0f, 1.0f);

    RenderSystem.enableCull();
    matrices.pop();
  }

  private void renderQuad(MatrixStack matrices, VertexConsumer vc, float r, float g, float b, float a) {
    MatrixStack.Entry entry = matrices.peek();
    Matrix4f modelMat = entry.getPositionMatrix();

    float halfSize = 7.0f; // 7x7 blocks

    // Bottom-left
    vc.vertex(modelMat, -halfSize, 0, -halfSize).color(r, g, b, a).texture(0, 1)
        .overlay(OverlayTexture.DEFAULT_UV).light(240, 240).normal(entry, 0.0f, 1.0f, 0.0f);
    // Bottom-right
    vc.vertex(modelMat, halfSize, 0, -halfSize).color(r, g, b, a).texture(1, 1)
        .overlay(OverlayTexture.DEFAULT_UV).light(240, 240).normal(entry, 0.0f, 1.0f, 0.0f);
    // Top-right
    vc.vertex(modelMat, halfSize, 0, halfSize).color(r, g, b, a).texture(1, 0)
        .overlay(OverlayTexture.DEFAULT_UV).light(240, 240).normal(entry, 0.0f, 1.0f, 0.0f);
    // Top-left
    vc.vertex(modelMat, -halfSize, 0, halfSize).color(r, g, b, a).texture(0, 0)
        .overlay(OverlayTexture.DEFAULT_UV).light(240, 240).normal(entry, 0.0f, 1.0f, 0.0f);
  }
}