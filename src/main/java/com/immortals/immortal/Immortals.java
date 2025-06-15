package com.immortals.Immortal;

import com.immortals.Utils;
import com.immortals.Mortal.ModComponents;
import com.immortals.api.PlayerImmortalsData;
import com.immortals.item.ModItems;
import com.immortals.Main;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/*Implements the Immortals' corruption system.*/
public class Immortals {
	private static final Set<Integer> scaledOrbIds = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Integer> splinterCount = new ConcurrentHashMap<>();

	public static void register() {
		// AttackEntityCallback for Splinter Blow
		AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
			if (world.isClient || !(player instanceof ServerPlayerEntity sp) || !Utils.getAscended(sp)
					|| SpellRegistry.getSlot(sp, SpellRegistry.SPLINTER_BLOW) == -1)
				return ActionResult.PASS;
			ServerWorld serverWorld = (ServerWorld) world;

			// Only increment if it's a fully charged attack
			if (sp.getAttackCooldownProgress(0.5F) < 0.84F) {
				return ActionResult.PASS;
			}

			UUID id = sp.getUuid();
			int count = splinterCount.getOrDefault(id, 0) + 1;
			splinterCount.put(id, count);

			if (count >= 3) {
				SpellRegistry.tryActivate(sp, SpellRegistry.getSlot(sp, SpellRegistry.SPLINTER_BLOW));
				if (target instanceof ServerPlayerEntity targetPlayer) {
					// 15% of max health damage
					float damage = (float) (targetPlayer.getMaxHealth()
							* ((Number) Main.CONFIG.get("splinterBlowDmg")).floatValue());
					target.damage((ServerWorld) world, Utils.of(world, Utils.SPELL_DAMAGE_TYPE, (Entity) player),
							damage);

					// Spawn an X of critical particles in front of the player
					double yaw = Math.toRadians(sp.getYaw());
					double pitch = Math.toRadians(sp.getPitch());
					double x = sp.getX() - Math.sin(yaw) * Math.cos(pitch) * 1.5;
					double y = sp.getY() + sp.getStandingEyeHeight() - Math.sin(pitch) * 1.0;
					double z = sp.getZ() + Math.cos(yaw) * Math.cos(pitch) * 1.5;

					world.playSound(null, player.getX(), player.getY(), player.getZ(),
							net.minecraft.sound.SoundEvents.ITEM_WOLF_ARMOR_CRACK,
							net.minecraft.sound.SoundCategory.PLAYERS, 0.7f, 1.0f);

					// X shape: two crossing lines
					for (int i = -5; i <= 5; i++) {
						double t = i * 0.1;
						// First line
						serverWorld.spawnParticles(
								ParticleTypes.CRIT,
								x + t, y + t, z + t,
								1, 0, 0, 0, 0);
						// Second line
						serverWorld.spawnParticles(
								ParticleTypes.CRIT,
								x + t, y - t, z + t,
								1, 0, 0, 0, 0);
						double offset = 0.5; // distance to push the X shape forward
						double forwardX = x - Math.sin(yaw) * Math.cos(pitch) * offset;
						double forwardY = y - Math.sin(pitch) * offset;
						double forwardZ = z + Math.cos(yaw) * Math.cos(pitch) * offset;

						serverWorld.spawnParticles(
								ParticleTypes.ENCHANTED_HIT,
								forwardX + t, forwardY + t, forwardZ + t,
								1, 0, 0, 0, 0);
						// Second line
						serverWorld.spawnParticles(
								ParticleTypes.ENCHANTED_HIT,
								forwardX + t, forwardY - t, forwardZ + t,
								1, 0, 0, 0, 0);
					}
				}
				splinterCount.put(id, 0);
			}
			return ActionResult.PASS;
		});

		// Reset splinterCount on player being hit
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, amount, taken, blocked) -> {
			if (entity instanceof ServerPlayerEntity sp) {
				splinterCount.put(sp.getUuid(), 0);
			}
		});

		// Initialize the scoreboard objectives for timeslow, immortal and dragon_ascent
		// active
		ServerTickEvents.START_SERVER_TICK.register((MinecraftServer server) -> {
			Scoreboard sb = server.getScoreboard();
			if (sb.getNullableObjective("timeslow") == null) {
				sb.addObjective(
						"timeslow",
						ScoreboardCriterion.DUMMY,
						(Text) Text.literal("t"),
						ScoreboardCriterion.RenderType.INTEGER,
						true,
						null);
			}
			if (sb.getNullableObjective("dragon_ascent") == null) {
				sb.addObjective(
						"dragon_ascent",
						ScoreboardCriterion.DUMMY,
						(Text) Text.literal("d"),
						ScoreboardCriterion.RenderType.INTEGER,
						true,
						null);
			}
			if (sb.getNullableObjective("immortal") == null) {
				sb.addObjective(
						"immortal",
						ScoreboardCriterion.DUMMY,
						(Text) Text.literal("i"),
						ScoreboardCriterion.RenderType.INTEGER,
						true,
						null);
			}
		});

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

				int corruption = newData.getCorruption();
				if (corruption == 1) {
					newData.getSpellBindings().clear();
				} else if (corruption == 2) {
					// Only keep dash and splinter_blow
					newData.getSpellBindings().entrySet()
							.removeIf(e -> !e.getValue().equals("dash") && !e.getValue().equals("splinter_blow"));
				} else if (corruption == 3) {
					// Only keep dash, splinter_blow, glow, and backdraft
					newData.getSpellBindings().entrySet()
							.removeIf(e -> !e.getValue().equals("dash")
									&& !e.getValue().equals("splinter_blow")
									&& !e.getValue().equals("glow")
									&& !e.getValue().equals("backdraft"));
				}
			}
		});

		// Send decreased corruption message on respawn
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (Utils.getAscended(newPlayer) && Utils.getCorruption(oldPlayer) > -3) {
				Utils.addCorruption(newPlayer, -1);
				int lvl = Utils.getCorruption(newPlayer);
				if (lvl <= -1) {
					// -1: -1 heart, -10% XP gain (XP handled elsewhere)
					newPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(18.0);
				}
				int next = Utils.nextShardCost(lvl);
				newPlayer.sendMessage(
						Text.literal("§5You feel weakened. Corruption: §l" + lvl + "§r. Next: " + next),
						true);
			}
		});

		// Implement immortal victims and killers
		ServerLivingEntityEvents.AFTER_DEATH.register((ent, src) -> {
			if (!ent.getWorld().isClient() && ent instanceof ServerPlayerEntity victim) {
				Entity attacker = src.getAttacker();
				boolean attackerImmortal = attacker instanceof ServerPlayerEntity k
						&& Utils.getAscended(k);

				if (attackerImmortal) {
					// Scale the soul shard drop count based on victim's max health
					int dropCount = 1;
					double maxHearts = victim.getAttributeInstance(EntityAttributes.MAX_HEALTH).getBaseValue() / 2.0;
					// Don't drop if the victim has less than 5 hearts
					if (maxHearts <= 5)
						dropCount = 0;

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

					Utils.setCorruption((ServerPlayerEntity) player, start_level);
					Utils.setAscended((ServerPlayerEntity) player, true);
					// Reset health
					player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(20.0);
					player.sendMessage(
							Text.literal("You feel a surge of divine power! Began at " + start_level + " corruption."),
							true);

					// Play totem animation and particles
					world.sendEntityStatus(player, (byte) 35); // Totem pop
					// Render rune
					Utils.updateRune(player, "immortal", 1);
					Utils.drawImmortalEvent(player.getPos(), world);
					player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 1.0F, 1.0F);
					Spell.addTask(player.getUuid(), () -> {
						Utils.updateRune(player, "immortal", 0);
					}, 1500);

					stack.decrement(1);
					Utils.grant((ServerPlayerEntity) player, "an_immortal");
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
					// New corruption level
					int lvl = Utils.getCorruption((ServerPlayerEntity) player);
					int next = Utils.nextShardCost(lvl);
					player.sendMessage(Text.literal("§5You grow stronger. Corruption: §l" + lvl + "§r. Next: " + next),
							true);

					if (lvl == 0) {
						// Reset health to 10 hearts
						player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(20.0);
					}

					if (lvl == 1) {
						world.playSound(null, player.getX(), player.getY(), player.getZ(),
								net.minecraft.sound.SoundEvents.PARTICLE_SOUL_ESCAPE,
								net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
						player.sendMessage(
								Text.literal("§6Unlocked ")
										.append(
												Text.literal("§cDash")
														.styled(style -> style.withHoverEvent(
																new net.minecraft.text.HoverEvent(
																		net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
																		Text.literal(
																				"Propels you 10 blocks horizontally.\nCooldown "
																						+ (Main.CONFIG.get(
																								"dashCooldown") instanceof Number
																										? ((Number) Main.CONFIG
																												.get("dashCooldown"))
																												.doubleValue()
																												/ 1000.0
																										: Main.CONFIG
																												.get("dashCooldown")
																												.toString())
																						+ "s.\nRun /bind [slot] dash to use.")))))
										.append(Text.literal("§6 and "))
										.append(
												Text.literal("§cSplinter Blow")
														.styled(style -> style.withHoverEvent(
																new net.minecraft.text.HoverEvent(
																		net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
																		Text.literal(
																				"Deal " + ((int) Math
																						.round(((Number) Main.CONFIG
																								.get("splinterBlowDmg"))
																								.doubleValue() * 100))
																						+ "% of target's max health\nafter a 3-hit combo. \nRun /bind [slot] splinter_blow to use.")))))
										.append(Text.literal("§6! Hover to see details.")),
								false);
					}
					if (lvl == 2) {
						world.playSound(null, player.getX(), player.getY(), player.getZ(),
								net.minecraft.sound.SoundEvents.PARTICLE_SOUL_ESCAPE,
								net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
						player.sendMessage(
								Text.literal("§6Unlocked ")
										.append(
												Text.literal("§cGlow")
														.styled(style -> style.withHoverEvent(
																new net.minecraft.text.HoverEvent(
																		net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
																		Text.literal(
																				"Make all players within "
																						+ Main.CONFIG.get("glowRadius")
																						+ " blocks glow for "
																						+ ((Main.CONFIG.get(
																								"glowDuration") instanceof Number)
																										? ((Number) Main.CONFIG
																												.get("glowDuration"))
																												.doubleValue()
																												/ 1000.0
																										: Main.CONFIG
																												.get("glowDuration")
																												.toString())
																						+ " seconds.\nCooldown "
																						+ (Main.CONFIG.get(
																								"glowCooldown") instanceof Number
																										? ((Number) Main.CONFIG
																												.get("glowCooldown"))
																												.doubleValue()
																												/ 1000.0
																										: Main.CONFIG
																												.get("glowCooldown")
																												.toString())
																						+ "s.\nRun /bind [slot] glow to use.")))))
										.append(Text.literal("§6 and "))
										.append(
												Text.literal("§cBackdraft")
														.styled(style -> style.withHoverEvent(
																new net.minecraft.text.HoverEvent(
																		net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
																		Text.literal(
																				"Deal "
																						+ ((int) Math.round(
																								((Number) Main.CONFIG
																										.get("backdraftDmg"))
																										.doubleValue()
																										* 100))
																						+ "% of target's max health while\npropelling yourself 5 blocks backwards.\nCooldown "
																						+ (Main.CONFIG.get(
																								"backdraftCooldown") instanceof Number
																										? ((Number) Main.CONFIG
																												.get("backdraftCooldown"))
																												.doubleValue()
																												/ 1000.0
																										: Main.CONFIG
																												.get("backdraftCooldown")
																												.toString())
																						+ "s. \nRun /bind [slot] backdraft to use.")))))
										.append(Text.literal("§6! Hover to see details.")),
								false);
					}
					if (lvl == 3) {
						world.playSound(null, player.getX(), player.getY(), player.getZ(),
								net.minecraft.sound.SoundEvents.PARTICLE_SOUL_ESCAPE,
								net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
						Utils.grant((ServerPlayerEntity) player, "the_immortal");
						player.sendMessage(
								Text.literal("§6Unlocked ")
										.append(
												Text.literal("§cPersist")
														.styled(style -> style.withHoverEvent(
																new net.minecraft.text.HoverEvent(
																		net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
																		Text.literal(
																				"Resistance II for "
																						+ Main.CONFIG.get(
																								"persistResistance")
																						+ "s and 8 absorption hearts when below 3 hearts.\nCooldown "
																						+ Main.CONFIG.get(
																								"persistCooldown")
																						+ "s. \nRun /bind [slot] persist to use.")))))
										.append(Text.literal("§6 and "))
										.append(
												Text.literal("§cBlackout")
														.styled(style -> style.withHoverEvent(
																new net.minecraft.text.HoverEvent(
																		net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
																		Text.literal(
																				"Apply blindness and invisibility for "
																						+ Main.CONFIG
																								.get("blackoutBlind")
																						+ "s and\nWither II for "
																						+ Main.CONFIG
																								.get("blackoutWither")
																						+ "s to all enemies.\nCooldown "
																						+ Main.CONFIG
																								.get("blackoutCooldown")
																						+ "s.\nRun /bind [slot] blackout to use.")))))
										.append(Text.literal("§6! Hover to see details.")),
								false);
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
					if (lvl == 0) {
						// Reset health to 10 hearts
						player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(20.0);
					}
					stack.decrement(1);
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

					int orig = orb.getExperienceAmount();
					int bumped = orig;
					if (Utils.getCorruption((ServerPlayerEntity) picker) >= 1) {
						bumped = (int) Math.ceil(orig + orig * ((Double) Main.CONFIG.get("immortalXpMultiplier")));
					} else if (Utils.getCorruption((ServerPlayerEntity) picker) <= -1) {
						bumped = (int) Math.ceil(orig - orig * ((Double) Main.CONFIG.get("immortalXpMultiplier")));
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
				Integer found = Utils.inventoryHas(player, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
				if (found != null) {
					// If found is main inventory size, it's in the offhand
					if (found == player.getInventory().main.size()) {
						player.getInventory().offHand.set(0, ItemStack.EMPTY);
					} else {
						player.getInventory().removeStack(found);
					}
					player.sendMessage(Text.literal(
							"You accidentally dropped the Netherite Upgrade Template. (Netherite Upgrades Removed)"),
							false);
				}

				// Helper to remove "immortals:augmented" modifiers from a stack if owned by an
				// ascended player
				java.util.function.Consumer<ItemStack> removeAugmentedIfAscended = stack -> {
					if (Utils.hasAttribute(stack, "immortals:augmented")) {
						String ownerStr = stack.get(ModComponents.OWNER_COMPONENT);
						if (ownerStr != null) {
							try {
								UUID ownerUuid = UUID.fromString(ownerStr);
								ServerPlayerEntity owner = player.getServer().getPlayerManager().getPlayer(ownerUuid);
								if (owner != null && Utils.getAscended(owner)) {
									player.sendMessage(
											Text.literal("The owner, " + player.getName().getString()
													+ " of your augmented gear has ascended. (Augmentations removed)"),
											false);
									Utils.removeModifierById(stack, "immortals:augmented");
									stack.remove(ModComponents.OWNER_COMPONENT);
								}
							} catch (IllegalArgumentException ignored) {
								// Invalid UUID string, skip
							}
						}
					}
				};

				player.getInventory().armor.forEach(removeAugmentedIfAscended);
				removeAugmentedIfAscended.accept(player.getMainHandStack());
				removeAugmentedIfAscended.accept(player.getOffHandStack());

				if (Utils.getAscended(player)) {
					// Apply passive corruption effects
					if (server.getTicks() % 40 == 0) {
						Utils.applyCorruptionEffects(player);
					}
					// Remove normal totems if ascended
					found = Utils.inventoryHas(player, Items.TOTEM_OF_UNDYING);
					if (found != null) {
						if (found == player.getInventory().main.size()) {
							player.getInventory().offHand.set(0, ItemStack.EMPTY);
						} else {
							player.getInventory().removeStack(found);
						}
						player.sendMessage(Text.literal(
								"§cWhat, you're not immortal enough? (Totems Removed)"),
								false);
					}
					found = Utils.inventoryHas(player, Items.DRAGON_EGG);
					if (found != null) {
						Utils.grant(player, "dragon_ascent");
					}
					found = Utils.inventoryHas(player, ModItems.TIMEKEEPER);
					if (found != null) {
						Utils.grant(player, "timeslow");
					}
					found = Utils.inventoryHas(player, ModItems.AUGMENTATION_CORE);
					if (found != null) {
						if (found == player.getInventory().main.size()) {
							player.getInventory().offHand.set(0, ItemStack.EMPTY);
						} else {
							player.getInventory().removeStack(found);
						}
						player.sendMessage(Text.literal(
								"§cThe Augmentation Core shattered into pieces. (Augmentation cores removed)"),
								false);
					}
					// Activate Persist passive ability
					if (Utils.getCorruption(player) >= 3 &&
							player.getHealth() < 6.0f) {
						SpellRegistry.tryActivate(player, SpellRegistry.getSlot(player, SpellRegistry.PERSIST));
					}
				}
			}
		});
	}
}