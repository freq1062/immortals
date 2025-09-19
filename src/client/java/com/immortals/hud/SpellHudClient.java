package com.immortals.hud;

import com.immortals.Immortal.SpellRegistry;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class SpellHudClient {
    private static volatile String currentSpellId = null;
    private static volatile String currentState = "ready"; // "ready","in_use","cooldown"
    private static volatile int remainingTicks = 0;
    private static volatile int maxTicks = 0;

    public static void displaySpell(String spellId, String state, int remaining, int max) {
        currentSpellId = spellId;
        currentState = state;
        remainingTicks = remaining;
        maxTicks = max;
    }

    public static void clearDisplayedSpell() {
        currentSpellId = null;
        currentState = "ready";
        remainingTicks = 0;
        maxTicks = 0;
    }

    // call this each client tick (register via ClientTickEvents.END_CLIENT_TICK)
    public static void tick(MinecraftClient client) {
        // optionally check if player changed slot client-side and ask server for
        // immediate update,
        // but per your design server already pushes on slot-change so you may not need
        // this.
        // Local countdown smoothing:
        if (currentSpellId != null && "cooldown".equals(currentState) && remainingTicks > 0) {
            remainingTicks = Math.max(0, remainingTicks - 1);
            if (remainingTicks == 0)
                currentState = "ready";
        }
    }

    // render called from HudRenderCallback
    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null)
            return;
        if (currentSpellId == null || SpellRegistry.fromId(currentSpellId) == null)
            return; // nothing to render

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        int barWidth = 60;
        int barHeight = 10;
        int x = screenWidth / 2 + 95; // right of hotbar
        int y = screenHeight - 40;

        int fillW = 0;
        int color = 0xFF00FF00; // default green filled

        switch (currentState) {
            case "ready" -> {
                fillW = barWidth; // fully filled when ready
                color = 0xFF00FF00; // green
            }
            case "in_use" -> {
                fillW = 0; // empty when active
                color = 0xFFFF0000; // red
            }
            case "cooldown" -> {
                System.out.println("Cooldown: " + remainingTicks);
                if (maxTicks > 0) {
                    float progress = (float) remainingTicks / (float) maxTicks;
                    fillW = (int) (barWidth * (progress)); // percentage filled based on cooldown
                }
                color = 0xFFFFFF00; // yellow
            }
        }

        // Draw border
        int borderColor = 0x88000000; // white border
        ctx.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, borderColor);

        // Fill the bar
        ctx.fill(x, y, x + fillW, y + barHeight, color);

        // draw spell display name — map id -> display name locally or include
        // displayName in packet
        String displayName = SpellRegistry.fromId(currentSpellId).getDisplayName();
        ctx.drawText(client.textRenderer, displayName, x, y - 10, 0xFFFFFFFF, true);
    }
}
