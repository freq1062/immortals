package com.immortals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.util.math.Vec3d;

import com.immortals.entity.ImmortalEntity;
import com.immortals.entity.FragmentEntityRenderer;
import com.immortals.network.NetworkChannels;

import java.util.Map;

import org.lwjgl.glfw.GLFW;

public class ImmortalsClient implements ClientModInitializer {
    private Map<String, Identifier> spellTextures = Map.of(
            "timeslow", Identifier.of("immortals", "textures/quads/timeslow.png"),
            "dragon_ascent", Identifier.of("immortals",
                    "textures/quads/dragon_ascent.png"),
            "immortal", Identifier.of("immortals",
                    "textures/quads/immortal.png"));

    /**
     * Key binding for activating a spell. Defaults to the "B" key.
     * If left blank, the spell can be activated using Shift + Right-Click.
     */
    private static final KeyBinding SPELL_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.immortals.cast_spell", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.immortals"));

    // BlockPos only accepts ints, but Vec3d accepts doubles for positions
    private static record RuneData(Identifier texture, double x, double y, double z, float size, int lifetimeTicks,
            int ticksElapsed) {
    }

    private static record SphereData(float[] color, double x, double y, double z, float size, int lifetimeTicks,
            int ticksElapsed) {
    }

    // Use Vec3d for double-precision positions
    private static record ItemData(Vec3d pos1, Vec3d pos2, Item item, float size, int lifetimeTicks, int ticksElapsed) {
    }

    public static final java.util.List<Object> pendingObjects = new java.util.ArrayList<>();

    @Override
    public void onInitializeClient() {
        // Register fragment entity renderer
        // EntityRendererRegistry.register(Entity.FRAGMENT_ENTITY, (ctx) -> new
        // FragmentEntityRenderer(ctx));
        EntityRendererRegistry.register(ImmortalEntity.FRAGMENT_ENTITY, FragmentEntityRenderer::new);

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

        ClientPlayNetworking.registerGlobalReceiver(NetworkChannels.ItemS2CPayload.ID, (payload, context) -> {
            ClientWorld world = context.client().world;

            if (world == null) {
                return;
            }

            pendingObjects.add(new ItemData(
                    new Vec3d(payload.x1(), payload.y1(), payload.z1()),
                    new Vec3d(payload.x2(), payload.y2(), payload.z2()),
                    Item.byRawId(payload.rawItemId()),
                    payload.size(),
                    payload.lifetimeTicks(), 0));
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

                    // Calculate smooth progress using current time for frame interpolation
                    float tickDuration = 50.0f; // Milliseconds per tick (20 ticks per second)
                    float tickProgress = (System.currentTimeMillis() % tickDuration) / tickDuration;
                    float smoothElapsed = rune.ticksElapsed() + tickProgress;
                    float smoothProgress = smoothElapsed / (float) totalLifetime;

                    // Animate size: grow in first 15%, shrink in last 10% of lifetime
                    if (smoothProgress < 0.15f) {
                        displaySize = rune.size() * (smoothProgress / 0.15f); // Grow from 0 to full size
                    } else if (smoothProgress > 0.9f) {
                        displaySize = rune.size() * ((1.0f - smoothProgress) / 0.1f); // Shrink from full size to 0
                    }

                    ClientUtils.renderRune(context, rune.texture(), rune.x(), rune.y(), rune.z(), displaySize);
                } else if (o instanceof SphereData sphere) {
                    System.out.println("Rendering sphere at (" + sphere.x() + ", " + sphere.y() + ", " + sphere.z()
                            + ") with size " + sphere.size());
                    ClientUtils.renderSphere(context, sphere.x(), sphere.y(), sphere.z(), sphere.color(),
                            sphere.size());
                } else if (o instanceof ItemData itemData) {
                    // Calculate progress along the line based on elapsed time
                    float tickDuration = 50.0f; // ms per tick
                    float tickProgress = (System.currentTimeMillis() % (long) tickDuration) / tickDuration;
                    float smoothElapsed = itemData.ticksElapsed() + tickProgress;
                    float totalLifetime = itemData.lifetimeTicks();
                    float t = Math.min(smoothElapsed / totalLifetime, 1.0f);

                    Vec3d start = itemData.pos1();
                    Vec3d end = itemData.pos2();
                    double x = start.x + (end.x - start.x) * t;
                    double y = start.y + (end.y - start.y) * t;
                    double z = start.z + (end.z - start.z) * t;

                    ClientUtils.renderItem(context, itemData.item(), x, y, z, end.x, end.y, end.z,
                            itemData.size());
                }
            }
        });

        // Decrement lifetimes every tick and remove expired objects
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (!SPELL_KEY.isPressed() && SPELL_KEY.wasPressed()) {
                // Send a packet to the server when the key is released
                int selectedSlot = client.player.getInventory().getSelectedSlot();
                NetworkChannels.SpellC2SPayload payload = new NetworkChannels.SpellC2SPayload(selectedSlot);
                ClientPlayNetworking.send(payload);
            }

            if (!client.options.attackKey.isPressed() && client.options.attackKey.wasPressed()) {
                NetworkChannels.FragmentC2SPayload payload = new NetworkChannels.FragmentC2SPayload(0);
                ClientPlayNetworking.send(payload);
            }

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
                        System.out.println("Removing sphere at (" + sphere.x() + ", " + sphere.y() + ", " + sphere.z()
                                + ")");
                        pendingObjects.remove(i);
                    } else {
                        pendingObjects.set(i, new SphereData(sphere.color(), sphere.x(), sphere.y(), sphere.z(),
                                sphere.size(), sphere.lifetimeTicks(), newTicksElapsed));
                    }
                } else if (o instanceof ItemData item) {
                    int newTicksElapsed = item.ticksElapsed() + 1;
                    if (item.lifetimeTicks() - newTicksElapsed <= 0) {
                        pendingObjects.remove(i);
                    } else {
                        pendingObjects.set(i, new ItemData(item.pos1(), item.pos2(), item.item(), item.size(),
                                item.lifetimeTicks(), newTicksElapsed));
                    }
                }
            }
        });
    }
}