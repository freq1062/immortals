// Not using this anymore, I want it to be a server side mod ONLY

package com.immortals.mixin;

import com.immortals.AscensionUtils;
import com.immortals.item.ModItems;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.client.gui.DrawContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public class InventoryMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void renderCorruptionHud(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		var clientPlayer = mc.player;
		if (clientPlayer == null)
			return;

		// Get the player's corruption level from the scoreboard
		Scoreboard sb = MinecraftClient.getInstance().world.getScoreboard();
		ScoreboardObjective obj = sb.getNullableObjective("corruptionLevel");
		// use the player’s name key
		ReadableScoreboardScore score = sb.getScore(clientPlayer, obj);

		int corruption = score == null ? 0 : score.getScore();
		int next = AscensionUtils.nextShardCost(corruption); // Example next shard cost

		// Position below the 2×2 crafting grid
		int x = mc.getWindow().getScaledWidth() / 2 + 20;
		int y = mc.getWindow().getScaledHeight() / 2 - 10;

		obj = sb.getNullableObjective("hasAscended");
		ReadableScoreboardScore ascended = sb.getScore(clientPlayer, obj);

		if (ascended != null && ascended.getScore() == 1) {
			// Draw the totem icon
			context.drawItem(new ItemStack(ModItems.ASCENSION_TOTEM), x, y);

			// Draw corruption level and next shard cost
			context.drawTextWithShadow(
					mc.textRenderer,
					String.valueOf(corruption),
					x, y,
					0xFFFFFF);
			context.drawTextWithShadow(
					mc.textRenderer,
					next > 0 ? "Next: " + next : "Max",
					x + 20, y + 10,
					0xAAAAAA);
		}
	}
}
