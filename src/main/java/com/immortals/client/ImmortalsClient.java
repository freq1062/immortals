package com.immortals.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.AbstractMap.SimpleEntry;

import net.minecraft.util.math.RotationAxis;

import com.immortals.Mortal.ModComponents;
import com.mojang.blaze3d.systems.RenderSystem;

public class ImmortalsClient implements ClientModInitializer {
  private static final float MAX_RUNE_SIZE = 14.0f; // Diameter in blocks
  private static final float ANIMATION_SPEED = 0.2f; // Size units per tick

  // Stores active spells with animation state:
  // [0]=PlayerEntity, [1]=X, [2]=Y, [3]=Z, [4]=Texture,
  // [5]=Current Size, [6]=Animation State (0=growing, 1=stable, 2=shrinking)
  private static final Map<java.util.AbstractMap.SimpleEntry<UUID, String>, Object[]> activeSpells = new ConcurrentHashMap<>();

  // Helper to get scoreboard score or -1 if not present
  int getScore(Scoreboard sb, PlayerEntity player, String objectiveName) {
    ScoreboardObjective obj = sb.getNullableObjective(objectiveName);
    if (obj != null) {
      var scoreObj = sb.getScore(player, obj);
      if (scoreObj != null) {
        return scoreObj.getScore();
      }
    }
    return -1;
  }

  @Override
  public void onInitializeClient() {
    WorldRenderEvents.AFTER_ENTITIES.register(context -> {

      // Cache UUID->name lookups to avoid repeated expensive calls
      final Map<UUID, String> ownerNameCache = new ConcurrentHashMap<>();

      ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
        String uuidStr = stack.getOrDefault(ModComponents.OWNER_COMPONENT, "");
        if (!uuidStr.isEmpty()) {
          boolean alreadyPresent = lines.stream()
              .anyMatch(line -> line.getString().startsWith("Owner: "));

          if (!alreadyPresent) {
            try {
              UUID uuid = UUID.fromString(uuidStr);

              // Try to get from cache first
              String name = ownerNameCache.computeIfAbsent(uuid, id -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.world != null) {
                  PlayerEntity player = client.world.getPlayerByUuid(id);
                  if (player != null) {
                    return player.getName().getString();
                  }
                }
                return uuidStr; // Store UUID as fallback
              });

              lines.add(Text.literal("Owner: " + name).formatted(Formatting.GOLD));
            } catch (IllegalArgumentException e) {
              // Fallback if uuidStr is not a valid UUID
              lines.add(Text.literal("Owner: " + uuidStr).formatted(Formatting.GOLD));
            }
          }
        }
      });

      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world == null)
        return;

      // Define spell types and their textures
      Map<String, Identifier> spellTextures = Map.of(
          "timeslow", Identifier.of("immortals", "textures/runecircles/timeslow.png"),
          "dragon_ascent", Identifier.of("immortals",
              "textures/runecircles/dragon_ascent.png"),
          "immortal", Identifier.of("immortals",
              "textures/runecircles/immortal.png"));

      for (PlayerEntity clientPlayer : client.world.getPlayers()) {
        Scoreboard sb = clientPlayer.getWorld().getScoreboard();
        UUID uuid = clientPlayer.getUuid();

        for (Map.Entry<String, Identifier> spell : spellTextures.entrySet()) {
          String spellType = spell.getKey();
          Identifier texture = spell.getValue();
          int score = getScore(sb, clientPlayer, spellType);

          SimpleEntry<UUID, String> key = new SimpleEntry<>(uuid, spellType);

          if (score == 1) {
            if (!activeSpells.containsKey(key)) {
              activeSpells.put(key, new Object[] {
                  clientPlayer,
                  clientPlayer.getX(),
                  clientPlayer.getBoundingBox().minY + 0.01,
                  clientPlayer.getZ(),
                  texture,
                  0.0f, // Starting size
                  0 // Growing state
              });
            }
          } else if (score == 0) {
            if (activeSpells.containsKey(key) && ((int) activeSpells.get(key)[6]) != 2) {
              activeSpells.get(key)[6] = 2; // Set to shrinking state
            }
          }
        }
      }

      // Update animations and render runes
      activeSpells.entrySet().removeIf(entry -> {
        Object[] spellData = entry.getValue();
        float size = (float) spellData[5];
        int animState = (int) spellData[6];

        // Update size based on animation state
        if (animState == 0) { // Growing
          size = Math.min(size + ANIMATION_SPEED, MAX_RUNE_SIZE);
          if (size >= MAX_RUNE_SIZE) {
            animState = 1; // Set to stable
          }
          spellData[5] = size;
          spellData[6] = animState;
        } else if (animState == 2) { // Shrinking
          size = Math.max(size - ANIMATION_SPEED, 0.0f);
          spellData[5] = size;

          // Remove if completely shrunk
          if (size <= 0.0f) {
            return true; // Remove from map
          }
        }

        // Render the rune if it has size
        if (size > 0) {
          double px = (double) spellData[1];
          double py = (double) spellData[2];
          double pz = (double) spellData[3];
          Identifier texture = (Identifier) spellData[4];
          renderRune(context, texture, px, py, pz, size);
        }

        return false; // Keep in map
      });
    });
  }

  private void renderRune(WorldRenderContext context, Identifier texture, double px, double py, double pz, float size) {
    ClientWorld world = context.world();
    Camera camera = context.camera();
    Vec3d camPos = camera.getPos();
    MatrixStack matrices = context.matrixStack();
    VertexConsumerProvider consumers = context.consumers();
    if (world == null || consumers == null)
      return;

    float camX = (float) camPos.x, camY = (float) camPos.y, camZ = (float) camPos.z;

    matrices.push();
    matrices.translate((float) (px - camX), (float) (py - camY), (float) (pz - camZ));

    long time = System.currentTimeMillis();
    float rotation = (float) ((time / 20.0) % 360.0);
    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));

    RenderSystem.setShaderTexture(0, texture);
    RenderSystem.disableCull();
    VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));

    // Use the current animated size
    renderQuad(matrices, vc, 1.0f, 1.0f, 1.0f, 1.0f, size);

    RenderSystem.enableCull();
    matrices.pop();
  }

  private void renderQuad(MatrixStack matrices, VertexConsumer vc, float r, float g, float b, float a, float size) {
    MatrixStack.Entry entry = matrices.peek();
    Matrix4f modelMat = entry.getPositionMatrix();

    float halfSize = size / 2; // Use the current animation size

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