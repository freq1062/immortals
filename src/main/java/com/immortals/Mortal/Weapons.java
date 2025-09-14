package com.immortals.Mortal;

import com.immortals.Main;
import com.immortals.Utils;
import com.immortals.api.ImmortalsData;
import com.immortals.api.EquipmentVisibility;
import com.immortals.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.text.Text;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Weapons {
    // Track each mortal’s Fractal Edge count
    private static final Map<UUID, Integer> fractalCount = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PHASE_CHANGE_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> OVERCLOCK_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> BLINK_COOLDOWNS = new ConcurrentHashMap<>();
    private static final long PHASE_CHANGE_COOLDOWN_MS = Main.CONFIG.getInt("phaseChangeCooldown");
    private static final long OVERCLOCK_COOLDOWN_MS = Main.CONFIG.getInt("overclockCooldown");
    private static final long BLINK_COOLDOWN_MS = Main.CONFIG.getInt("blinkCooldown");

    public static void register() {
        // Handle special item usage
        UseItemCallback.EVENT.register((sp, world, hand) -> {
            if (!(sp instanceof ServerPlayerEntity) || ((ImmortalsData) sp).isImmortal())
                return ActionResult.PASS;
            ServerPlayerEntity player = (ServerPlayerEntity) sp;
            // If the player is mortal and uses the dragon egg, they unlock phasebreaker
            ItemStack inHand = player.getStackInHand(hand);
            if (inHand.getItem() == Items.DRAGON_EGG) {
                inHand.decrement(1);
                player.getInventory().offerOrDrop(new ItemStack(ModItems.PHASEBREAKER));
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sound.SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE,
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
                Utils.grant((ServerPlayerEntity) player, "master_of_space");
                player.sendMessage(Text.literal("§aYou have constructed the DRK-01 Phasebreaker!"), true);
                return ActionResult.SUCCESS;
            } else if (inHand.getItem() == ModItems.TIMEKEEPER) {
                inHand.decrement(1);
                player.getInventory().offerOrDrop(new ItemStack(ModItems.CHRONOREAVER));
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sound.SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE,
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
                Utils.grant((ServerPlayerEntity) player, "master_of_time");
                player.sendMessage(Text.literal("§aYou have constructed the NUL-02 Chronoreaver!"), true);
                return ActionResult.SUCCESS;
            } else if (inHand.getItem() == ModItems.PHASEBREAKER && player.isSneaking()) {
                UUID id = player.getUuid();
                long now = System.currentTimeMillis();
                Long last = PHASE_CHANGE_COOLDOWNS.get(id);
                if (last != null && now - last < PHASE_CHANGE_COOLDOWN_MS) {
                    return ActionResult.FAIL;
                }

                // Compute teleport target
                Vec3d eye = player.getCameraPosVec(1.0f);
                Vec3d look = player.getRotationVec(1.0f).normalize();
                Vec3d far = eye.add(look.multiply(15.0));

                // Raycast (stop at first block hit)
                BlockHitResult hit = world.raycast(new RaycastContext(
                        eye, far,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        player));

                Vec3d target = hit.getType() == HitResult.Type.BLOCK
                        // Back off one step so we don’t end up inside a block
                        ? hit.getPos().subtract(look.multiply(1.0))
                        : far;

                DustParticleEffect purple = new DustParticleEffect(0xDE7AFA, 3f);
                int steps = 20;
                for (int i = 0; i <= steps; i++) {
                    double t = i / (double) steps;
                    Vec3d point = eye.lerp(target, t);
                    if (world instanceof ServerWorld sw) {
                        sw.spawnParticles(purple,
                                point.x, point.y, point.z,
                                1, 0, 0, 0, 0);
                    }
                }

                // Teleport on server
                // Make an empty flag set so you can teleport to the air
                Set<PositionFlag> flags = EnumSet.noneOf(PositionFlag.class);
                sp.teleport(
                        (ServerWorld) world,
                        target.x, target.y, target.z,
                        flags,
                        player.getYaw(), player.getPitch(),
                        false // Don't reset camera
                );
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sound.SoundEvents.ENTITY_ENDER_EYE_DEATH,
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);

                // Record cooldown
                PHASE_CHANGE_COOLDOWNS.put(id, now);
                player.sendMessage(Text.literal("§aPhase Changed!"), true);
                player.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

                return ActionResult.SUCCESS;
            } else if (inHand.getItem() == ModItems.CHRONOREAVER && player.isSneaking()) {
                UUID id = player.getUuid();
                long now = System.currentTimeMillis();
                Long last = OVERCLOCK_COOLDOWNS.get(id);
                long cooldown = 45000; // 45 seconds in ms
                if (last != null && now - last < cooldown) {
                    return ActionResult.FAIL;
                }
                int duration = Main.CONFIG.getInt("overclockDuration");

                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sound.SoundEvents.ITEM_TRIDENT_THUNDER,
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SPEED, duration, 2)); // Speed 3 (amplifier is
                                                                                        // 0-based)
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.HASTE, duration, 4)); // Haste 5
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.GLOWING, duration, 0));

                // Start cooldown after effects are done
                Main.scheduler.schedule(() -> {
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            net.minecraft.sound.SoundEvents.BLOCK_BEACON_DEACTIVATE,
                            net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
                    OVERCLOCK_COOLDOWNS.put(id, System.currentTimeMillis());
                    player.sendMessage(Text.literal("§bOverclock Recharging!"), true);
                }, duration * 50); // duration is in ticks, convert to ms

                player.sendMessage(Text.literal("§bOverclock Activated!"), true);
                player.playSound(SoundEvents.ITEM_TOTEM_USE, 1f, 1f);

                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        // Transform back into special items on death
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity victim) || ((ImmortalsData) victim).isImmortal())
                return true;

            // Don't transform if death was prevented by a totem pop
            if (victim.getHealth() > 0.0f || victim.isDead()) {
                // If the player is still alive after the event, it was a totem pop or similar
                return true;
            }

            // Scan each slot, clear Phasebreaker(s)
            var inv = victim.getInventory();
            boolean hadPhasebreaker = false;
            boolean hadChronoreaver = false;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.getItem() == ModItems.PHASEBREAKER) {
                    hadPhasebreaker = true;
                    inv.setStack(i, ItemStack.EMPTY);
                } else if (stack.getItem() == ModItems.CHRONOREAVER) {
                    hadChronoreaver = true;
                    inv.setStack(i, ItemStack.EMPTY);
                }
            }

            if (hadPhasebreaker) {
                // Drop dragon egg
                ServerWorld w = (ServerWorld) victim.getWorld();
                w.spawnEntity(new ItemEntity(
                        w,
                        victim.getX(), victim.getY(), victim.getZ(),
                        new ItemStack(Items.DRAGON_EGG)));
            }

            if (hadChronoreaver) {
                // Drop timekeeper
                ServerWorld w = (ServerWorld) victim.getWorld();
                w.spawnEntity(new ItemEntity(
                        w,
                        victim.getX(), victim.getY(), victim.getZ(),
                        new ItemStack(ModItems.TIMEKEEPER)));
            }

            return true; // Allow the death to proceed
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!(entity instanceof ServerPlayerEntity player) || ((ImmortalsData) player).isImmortal())
                return;

            if (Utils.inventoryHas(player, ModItems.CHRONOREAVER) == null)
                return;

            // Only trigger if health just dropped below 5 hearts (10 HP)
            float newHealth = entity.getHealth();
            float oldHealth = newHealth + damageTaken;
            final UUID playerId = player.getUuid();
            long now = System.currentTimeMillis();
            Long last = BLINK_COOLDOWNS.get(playerId);
            if ((last == null || now - last >= BLINK_COOLDOWN_MS) && oldHealth >= 10.0f && newHealth < 10.0f) {
                int blinkDuration = Main.CONFIG.getInt("blinkDuration"); // in ticks
                player.sendMessage(Text.literal("§bBlink Activated!"), true);

                // Show glow ink particle rings every 10t
                for (int t = 0; t < blinkDuration; t += 10) {
                    Main.scheduler.schedule(() -> {
                        EquipmentVisibility.hide(player);
                        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.INVISIBILITY, blinkDuration, 0, false, false,
                                false));
                        player.setInvisible(true);

                        Vec3d pos = player.getPos();
                        double height = player.getHeight();
                        double width = player.getWidth();
                        int points = 6;
                        for (int i = 0; i < points; i++) {
                            double y = pos.y + 0.1 + (height - 0.2) * i / (points - 1);
                            int ringPoints = 8;
                            for (int j = 0; j < ringPoints; j++) {
                                double angle = 2 * Math.PI * j / ringPoints;
                                double dx = Math.cos(angle) * width * 0.3;
                                double dz = Math.sin(angle) * width * 0.3;
                                ((ServerWorld) player.getWorld()).spawnParticles(
                                        ParticleTypes.GLOW,
                                        pos.x + dx, y, pos.z + dz,
                                        1, 0, 0, 0, 0.0);
                            }
                        }
                    }, t);
                }

                // Restore visibility at the end
                Main.scheduler.schedule(() -> {
                    BLINK_COOLDOWNS.put(playerId, System.currentTimeMillis());
                    player.sendMessage(Text.literal("§bBlink Recharging!"), true);
                    EquipmentVisibility.show(player);
                    player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY);
                    player.setInvisible(false);
                }, blinkDuration);
            }
        });

        // Fractal Edge ability
        AttackEntityCallback.EVENT.register((attacker, world, hand, victim, hitResult) -> {
            if (!(attacker instanceof ServerPlayerEntity sp)
                    || !(victim instanceof ServerPlayerEntity))
                return ActionResult.PASS;

            if (!((ImmortalsData) attacker).isImmortal())
                return ActionResult.PASS;

            ItemStack weapon = sp.getStackInHand(hand);
            if (weapon.getItem() != ModItems.PHASEBREAKER)
                return ActionResult.PASS;

            // Increment by one if it's a fully charged attack and not a weak hit or shield
            // block
            if (sp.getAttackCooldownProgress(0.5F) < 0.84F) {
                return ActionResult.PASS;
            }
            // Pass if the target is blocking with a shield AND the attack is coming from
            // the front
            if (victim instanceof LivingEntity living && living.isBlocking()) {
                // Check if attacker is in front of the shielded entity
                Vec3d attackerToTarget = sp.getPos().subtract(living.getPos()).normalize();
                Vec3d targetLook = living.getRotationVec(1.0F).normalize();
                double dot = attackerToTarget.dotProduct(targetLook);
                // dot > 0.3 means attacker is generally in front (adjust threshold as needed)
                if (dot > 0.3) {
                    return ActionResult.PASS;
                }
            }

            UUID id = sp.getUuid();
            int count = fractalCount.getOrDefault(id, 0) + 1;
            fractalCount.put(id, count);

            if (count >= 7) {
                if (victim instanceof LivingEntity ent) {
                    // Propel target 5 blocks away
                    Vec3d dir = ent.getPos().subtract(sp.getPos()).normalize();
                    ent.setVelocity(dir.x * 2.5, 0.5, dir.z * 2.5);
                    ent.velocityModified = true;

                    float maxHealth = (victim instanceof LivingEntity le) ? le.getMaxHealth() : 20.0f;
                    float damage = maxHealth * ((Number) Main.CONFIG.getDouble("fractalTotalDmg")).floatValue() / 4.0f;
                    for (int i = 0; i < 4; i++) {
                        final int index = i;
                        int delay = index * 500;
                        Main.scheduler.schedule(() -> {
                            ent.damage((ServerWorld) world, Utils.of(world, Utils.SPELL_DAMAGE_TYPE, (Entity) attacker),
                                    damage);

                            // Spawn sweep attack particles in front of the entity
                            for (int j = 0; j < 3; j++) {
                                Main.scheduler.schedule(() -> {
                                    Vec3d forward = ent.getRotationVec(1.0F).normalize();
                                    Vec3d particlePos = ent.getPos().add(forward.multiply(1.0));
                                    ((ServerWorld) world).spawnParticles(
                                            ParticleTypes.SWEEP_ATTACK,
                                            particlePos.x, particlePos.y + ent.getHeight() * 0.5, particlePos.z,
                                            3, 0.5, 0.5, 0.5, 0.0);
                                    world.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                                            net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                                            net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
                                }, j * (500 / 3));
                            }

                        }, delay);
                    }
                }
                fractalCount.put(id, 0);
            }
            return ActionResult.PASS;
        });

        // Display cooldown messages for Phasebreaker
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0)
                return;

            long now = System.currentTimeMillis();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                Long last = PHASE_CHANGE_COOLDOWNS.get(id);

                // Only show if holding Phasebreaker
                if (last == null || player.getMainHandStack().getItem() != ModItems.PHASEBREAKER)
                    continue;

                long elapsed = now - last;
                long remainingMs = PHASE_CHANGE_COOLDOWN_MS - elapsed;

                if (remainingMs > 0) {
                    long secsLeft = (remainingMs + 999) / 1000;
                    player.sendMessage(Text.literal("§ePhase Change: " + secsLeft + "s"), true);
                } else {
                    player.sendMessage(Text.literal("§aPhase Change ready!"), true);
                    // Remove entry to prevent further messages
                    PHASE_CHANGE_COOLDOWNS.remove(id);
                }
            }
        });

        // Display cooldown messages for Chronoreaver
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0)
                return;

            long now = System.currentTimeMillis();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                Long last = OVERCLOCK_COOLDOWNS.get(id);

                // Only show if holding Chronoreaver
                if (last == null || player.getMainHandStack().getItem() != ModItems.CHRONOREAVER)
                    continue;

                long elapsed = now - last;
                long remainingMs = OVERCLOCK_COOLDOWN_MS - elapsed;

                if (remainingMs > 0) {
                    long secsLeft = (remainingMs + 999) / 1000;
                    player.sendMessage(Text.literal("§bOverclock: " + secsLeft + "s"), true);
                } else {
                    player.sendMessage(Text.literal("§aOverclock ready!"), true);
                    // Remove entry to prevent further messages
                    OVERCLOCK_COOLDOWNS.remove(id);
                }
            }
        });

        // Display cooldown messages for Blink
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0)
                return;

            long now = System.currentTimeMillis();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                Long last = BLINK_COOLDOWNS.get(id);

                // Only show if holding Chronoreaver
                if (last == null || player.getMainHandStack().getItem() != ModItems.CHRONOREAVER)
                    continue;

                long elapsed = now - last;
                long remainingMs = BLINK_COOLDOWN_MS - elapsed;

                if (remainingMs <= 0) {
                    player.sendMessage(Text.literal("§bBlink ready!"), true);
                    // Remove entry to prevent further messages
                    BLINK_COOLDOWNS.remove(id);
                }
            }
        });

        // Check if Phasebreaker's owner has changed and transform it into a dragon egg
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    if (((ImmortalsData) player).isImmortal()) {
                        var inv = player.getInventory();
                        for (int i = 0; i < inv.size(); i++) {
                            ItemStack stack = inv.getStack(i);
                            if (stack.getItem() == ModItems.PHASEBREAKER) {
                                inv.setStack(i, new ItemStack(Items.DRAGON_EGG));
                                player.sendMessage(
                                        Text.literal("§cPhasebreaker transformed back into the Dragon Egg!"),
                                        true);
                                break;
                            }
                            if (stack.getItem() == ModItems.CHRONOREAVER) {
                                inv.setStack(i, new ItemStack(ModItems.TIMEKEEPER));
                                player.sendMessage(
                                        Text.literal("§cChronoreaver transformed back into the Timekeeper!"),
                                        true);
                                break;
                            }
                        }
                        // Also check offhand
                        ItemStack offhand = player.getOffHandStack();
                        if (offhand.getItem() == ModItems.PHASEBREAKER) {
                            player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, new ItemStack(Items.DRAGON_EGG));
                            player.sendMessage(
                                    Text.literal("§cPhasebreaker transformed back into the Dragon Egg!"),
                                    true);
                        } else if (offhand.getItem() == ModItems.CHRONOREAVER) {
                            player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, new ItemStack(ModItems.TIMEKEEPER));
                            player.sendMessage(
                                    Text.literal("§cChronoreaver transformed back into the Timekeeper!"),
                                    true);
                        }
                    }
                }
            }
        });
    }
}
