package com.immortals.immortal;

import com.immortals.Utils;
import com.immortals.api.PlayerImmortalsData;
import com.immortals.item.ModItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*Implements the Immortals' corruption system.*/
public class Immortals {

	private static final Set<Integer> scaledOrbIds = ConcurrentHashMap.newKeySet();

	public static void register() {

		// Register corruption command
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("corruption")
					.executes(ctx -> {
						ServerPlayerEntity player = ctx.getSource().getPlayer();
						if (!Utils.getAscended(player)) {
							player.sendMessage(Text.literal("You do not have corruption as a mortal."),
									false);
							return 0;
						}
						int corruptionLevel = Utils.getCorruption(player);
						player.sendMessage(Text.literal("Your corruption level is: " + corruptionLevel), false);
						return 1;
					}));
		});

		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			// only copy on death, not when traveling dimensions
			if (!alive) {
				PlayerImmortalsData oldData = (PlayerImmortalsData) oldPlayer;
				PlayerImmortalsData newData = (PlayerImmortalsData) newPlayer;

				// copy ascension & corruption
				newData.setImmortal(oldData.isImmortal());
				newData.setCorruption(oldData.getCorruption());

				// copy spell bindings
				newData.getSpellBindings().clear();
				newData.getSpellBindings().putAll(oldData.getSpellBindings());
			}
		});

		// Send decreased corruption message on respawn
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (Utils.getAscended(newPlayer) && Utils.getCorruption(oldPlayer) > -3) {
				Utils.addCorruption(newPlayer, -1);
				int lvl = Utils.getCorruption(newPlayer);
				int next = Utils.nextShardCost(lvl);
				Utils.applyCorruptionEffects(newPlayer);
				newPlayer.sendMessage(
						Text.literal("§5You feel weakened. Corruption: §l" + lvl + "§r. Next: " + next),
						true);
			}
		});

		// Implement immortal victims and killers
		ServerLivingEntityEvents.AFTER_DEATH.register((ent, src) -> {
			if (!ent.getWorld().isClient() && ent instanceof ServerPlayerEntity victim) {
				Entity attacker = src.getAttacker();
				boolean victimImmortal = Utils.getAscended(victim);
				boolean attackerImmortal = attacker instanceof ServerPlayerEntity k
						&& Utils.getAscended(k);

				// Immortal kills or dies
				if (victimImmortal || attackerImmortal) {
					// Scale the soul shard drop count based on victim's max health
					int dropCount = 1;
					double maxHearts = victim.getAttributeInstance(EntityAttributes.MAX_HEALTH).getBaseValue() / 2.0;
					if (maxHearts >= 14 && maxHearts <= 16)
						dropCount = 2;
					else if (maxHearts >= 17)
						dropCount = 3;

					victim.dropItem(new ItemStack(ModItems.SOUL_SHARD, dropCount), false);

					if (Utils.getCorruption(victim) <= -3) {
						// banned ):
						String playerName = victim.getNameForScoreboard();
						String reason = "You have lost all your corruption levels!";
						String command = String.format("tempban %s 0 0 24 %s", playerName, reason);
						MinecraftServer server = victim.getServer();
						server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
					}
				}

				// +3 on-kill ability
				if (attacker instanceof ServerPlayerEntity killer
						&& Utils.getCorruption(killer) >= 3) {
					// heal 6.0f = 3 hearts
					killer.heal(6.0f);
					killer.sendMessage(Text.literal("§aYou are empowered on kill... (healed 3 hearts)"), true);
				}
			}

		});

		// Custom item events
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClient()) {
				return ActionResult.PASS;
			}
			ItemStack stack = player.getStackInHand(hand);

			// Ascension Totem: Update objective hasAscended
			if (stack.getItem() == ModItems.ASCENSION_TOTEM) {

				if (!Utils.getAscended((ServerPlayerEntity) player)) {
					// Give starting corruption levels based on hearts
					double curr_hp = player.getAttributeInstance(EntityAttributes.MAX_HEALTH).getBaseValue();
					int start_level = 0;
					EntityAttributeInstance maxHearts = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
					maxHearts.setBaseValue(20.0);
					if (curr_hp <= 10 * 2) {
						start_level = 0;
					} else if (curr_hp <= 14 * 2) {
						start_level = 1;
					} else if (curr_hp <= 18 * 2) {
						start_level = 2;
					} else {
						start_level = 3;
					}

					Utils.addCorruption((ServerPlayerEntity) player, start_level);
					Utils.setAscended((ServerPlayerEntity) player, true);
					player.sendMessage(
							Text.literal("You feel a surge of divine power! Began at " + start_level + " corruption."),
							true);

					// Play totem animation and particles
					world.sendEntityStatus(player, (byte) 35); // Totem pop
					if (world instanceof ServerWorld serverWorld) {
						serverWorld.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1, player.getZ(),
								50, 0.5, 0.5, 0.5, 0.01);
						serverWorld.spawnParticles(ParticleTypes.DRAGON_BREATH, player.getX(), player.getY() + 1,
								player.getZ(), 20, 0.5, 0.5, 0.5, 0.01);
					}
					player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 1.0F, 1.0F);

					stack.decrement(1);
					return ActionResult.SUCCESS;
				} else {
					player.sendMessage(Text.literal("You have already ascended. There is no going back!"), true);
					return ActionResult.FAIL;
				}
			}

			// Soul Shard: Update corruption level -3 up to +3
			if (stack.getItem() == ModItems.SOUL_SHARD) {
				boolean ascended = Utils.getAscended((ServerPlayerEntity) player);
				int corruption = Utils.getCorruption((ServerPlayerEntity) player);
				// Player has not ascended
				if (!ascended) {
					player.sendMessage(Text.literal("You must ascend to grow stronger..."), true);
					return ActionResult.FAIL;
				}
				// Player can increase corruption
				if (corruption < 3) {
					int cost = Utils.nextShardCost(corruption);

					if (stack.getCount() < cost) {
						player.sendMessage(Text.literal("Require " + cost + " Soul Shards to increase corruption."),
								true);
						return ActionResult.FAIL;
					}

					stack.decrement(cost);
					Utils.addCorruption((ServerPlayerEntity) player, 1);
					int lvl = Utils.getCorruption((ServerPlayerEntity) player);
					int next = Utils.nextShardCost(lvl);
					Utils.applyCorruptionEffects((ServerPlayerEntity) player);
					player.sendMessage(Text.literal("§5You grow stronger. Corruption: §l" + lvl + "§r. Next: " + next),
							true);

					if (!SpellRegistry.isSpellBound((ServerPlayerEntity) player,
							SpellRegistry.DASH) && lvl == 2) {
						player.sendMessage(Text.literal(
								"§6Learned dash spell! run /bind [slot] dash to rebind it."),
								false);
						SpellRegistry.bindDefault((ServerPlayerEntity) player, 0, SpellRegistry.DASH);
					}
					if (!SpellRegistry.isSpellBound((ServerPlayerEntity) player,
							SpellRegistry.GLOW) && lvl == 3) {
						player.sendMessage(Text.literal(
								"§6Learned glow spell! run /bind [slot] glow to rebind it."),
								false);
						SpellRegistry.bindDefault((ServerPlayerEntity) player, 1, SpellRegistry.GLOW);
					}
					return ActionResult.SUCCESS;
				}
				// Player has max corruption
				if (corruption >= 3) {
					player.sendMessage(Text.literal("Your soul is at its peak."), true);
					return ActionResult.FAIL;
				}
			}

			// Soul Purifier: Update corruption level +1 up to 0
			if (stack.getItem() == ModItems.SOUL_PURIFIER) {
				if (Utils.getCorruption((ServerPlayerEntity) player) < 0) {
					Utils.addCorruption((ServerPlayerEntity) player, 1);
					int lvl = Utils.getCorruption((ServerPlayerEntity) player);
					int next = Utils.nextShardCost(lvl);
					stack.decrement(1);
					Utils.applyCorruptionEffects((ServerPlayerEntity) player);
					player.sendMessage(Text.literal("§5You feel renewed. Corruption: §l" + lvl + "§r. Next: " + next),
							true);
					return ActionResult.SUCCESS;
				}
				player.sendMessage(Text.literal("Soul purifier cannot increase corruption beyond +0."), true);
				return ActionResult.FAIL;
			}

			if (player.isSneaking()) {
				int slot = player.getInventory().selectedSlot;
				return SpellRegistry.tryActivate((ServerPlayerEntity) player, slot)
						? ActionResult.SUCCESS
						: ActionResult.PASS;
			}

			return ActionResult.PASS;
		});

		// Passive abilities
		ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
			// Modify XP gain based on corruption level
			for (ServerWorld world : server.getWorlds()) {
				for (ExperienceOrbEntity orb : world.getEntitiesByType(
						EntityType.EXPERIENCE_ORB, o -> !o.isRemoved())) {

					int id = orb.getId();
					if (scaledOrbIds.contains(id))
						continue;

					PlayerEntity picker = world.getClosestPlayer(orb, 2.5);
					if (!(picker instanceof ServerPlayerEntity player)
							|| !Utils.getAscended(player)) {
						continue;
					}

					int orig = orb.getExperienceAmount(); // Move to config??
					int bumped = orig;
					if (Utils.getCorruption((ServerPlayerEntity) picker) >= 1) {
						bumped = (int) Math.ceil(orig * 1.25);
					} else if (Utils.getCorruption((ServerPlayerEntity) picker) <= -1) {
						bumped = (int) Math.ceil(orig * 0.75);
					}

					// Replace the old experience orb with scaled new one
					ExperienceOrbEntity newOrb = new ExperienceOrbEntity(
							world, orb.getX(), orb.getY(), orb.getZ(), bumped);
					world.spawnEntity(newOrb);
					orb.discard();

					scaledOrbIds.add(id);
					scaledOrbIds.add(newOrb.getId());

					// Clear the array, this means every 250 orbs might not be scaled but whatever
					if (scaledOrbIds.size() > 500) {
						scaledOrbIds.clear();
					}
				}
			}

			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (Utils.getAscended(player)) {
					// Remove normal totems if ascended
					for (int i = 0; i < player.getInventory().size(); i++) {
						ItemStack s = player.getInventory().getStack(i);
						if (s.getItem() == Items.TOTEM_OF_UNDYING) {
							player.getInventory().removeStack(i);
						} else if (s.getItem() == Items.DRAGON_EGG
								&& !SpellRegistry.isSpellBound((ServerPlayerEntity) player,
										SpellRegistry.DRAGON_ASCENT)) {
							player.sendMessage(Text.literal(
									"§6Dragon Ascent spell unlocked! run /bind [slot] dragon_ascent to rebind it."),
									false);
							SpellRegistry.bindDefault((ServerPlayerEntity) player, 2, SpellRegistry.DRAGON_ASCENT);
						}
					}
					// +3 corruption temporary resistance when below 3 hearts
					if (Utils.getCorruption(player) >= 3 &&
							player.getHealth() < 6.0f && !player.hasStatusEffect(StatusEffects.RESISTANCE)) {
						player.sendMessage(Text.literal("§aYour will strengthens... (+Resistance I)"), true);
						player.addStatusEffect(new StatusEffectInstance(
								StatusEffects.RESISTANCE,
								40, // lasts 2 seconds, refreshed each tick
								0,
								false, false));
					}
				}
			}
		});
	}
}