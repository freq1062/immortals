package com.immortals.Mortal;

import com.immortals.Main;
import com.immortals.Utils;
import com.immortals.immortal.Spell;
import com.immortals.item.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
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
    private static final long PHASE_CHANGE_COOLDOWN_MS = Main.CONFIG.phaseChangeCooldown;

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient || Utils.getAscended((ServerPlayerEntity) player))
                return ActionResult.PASS;
            // If the player is mortal and uses the dragon egg, they unlock phasebreaker
            ItemStack inHand = player.getStackInHand(hand);
            if (inHand.getItem() == Items.DRAGON_EGG && Utils.findInInventory(player, ModItems.PHASEBREAKER) == null) {
                inHand.decrement(1);
                player.getInventory().offerOrDrop(new ItemStack(ModItems.PHASEBREAKER));
                player.sendMessage(Text.literal("§aYou have constructed the DRK-07 Phasebreaker!"), true);
                return ActionResult.SUCCESS;
            } else if (inHand.getItem() == ModItems.PHASEBREAKER && player.isSneaking()) {
                UUID id = player.getUuid();
                long now = System.currentTimeMillis();
                Long last = PHASE_CHANGE_COOLDOWNS.get(id);
                if (last != null && now - last < PHASE_CHANGE_COOLDOWN_MS) {
                    long secsLeft = (PHASE_CHANGE_COOLDOWN_MS - (now - last) + 999) / 1000;
                    player.sendMessage(Text.literal("§cPhase Change ready in " + secsLeft + "s"), true);
                    return ActionResult.SUCCESS;
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
                if (player instanceof ServerPlayerEntity sp) {
                    // Make an empty flag set so you can teleport to the air
                    Set<PositionFlag> flags = EnumSet.noneOf(PositionFlag.class);
                    sp.teleport(
                            (ServerWorld) world,
                            target.x, target.y, target.z,
                            flags,
                            player.getYaw(), player.getPitch(),
                            false // Don't reset camera
                    );
                }

                // Record cooldown
                PHASE_CHANGE_COOLDOWNS.put(id, now);
                player.sendMessage(Text.literal("§aPhase Change!"), true);
                player.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        // If a mortal dies with Phasebreaker, it transforms back to a dragon egg
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity victim) || Utils.getAscended(victim))
                return true;

            // Scan each slot, clear Phasebreaker(s)
            var inv = victim.getInventory();
            boolean hadOne = false;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.getItem() == ModItems.PHASEBREAKER) {
                    hadOne = true;
                    inv.setStack(i, ItemStack.EMPTY);
                }
            }

            if (hadOne) {
                // Drop dragon egg
                ServerWorld w = (ServerWorld) victim.getWorld();
                w.spawnEntity(new ItemEntity(
                        w,
                        victim.getX(), victim.getY(), victim.getZ(),
                        new ItemStack(Items.DRAGON_EGG)));
            }

            return true; // Allow the death to proceed
        });

        // Fractal Edge ability
        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp) || Utils.getAscended(sp))
                return ActionResult.PASS;

            ItemStack weapon = sp.getStackInHand(hand);
            if (weapon.getItem() != ModItems.PHASEBREAKER)
                return ActionResult.PASS;

            // Increment by one if it's a fully charged attack and not a weak hit
            if (sp.getAttackCooldownProgress(0.5F) < 0.84F) {
                return ActionResult.PASS;
            }

            UUID id = sp.getUuid();
            int count = fractalCount.getOrDefault(id, 0) + 1;
            fractalCount.put(id, count);

            if (count >= 7) {
                if (target instanceof LivingEntity ent) {
                    // Propel target 5 blocks away
                    Vec3d dir = ent.getPos().subtract(sp.getPos()).normalize();
                    ent.setVelocity(dir.x * 2.5, 0.5, dir.z * 2.5);
                    ent.velocityModified = true;

                    // Deal 8 HP bypassing armor & magic resistance:
                    DamageSource ds = ((ServerWorld) world)
                            .getDamageSources()
                            .magic();
                    for (int i = 0; i < 4; i++) {
                        final int index = i;
                        int delay = index * 500;
                        UUID playerId = sp.getUuid();
                        Spell.addTask(playerId, () -> {
                            ent.damage((ServerWorld) world, ds, 10.0f);

                            // Spawn sweep attack particles in front of the entity
                            for (int j = 0; j < 3; j++) {
                                Spell.addTask(playerId, () -> {
                                    Vec3d forward = ent.getRotationVec(1.0F).normalize();
                                    Vec3d particlePos = ent.getPos().add(forward.multiply(1.0));
                                    ((ServerWorld) world).spawnParticles(
                                            ParticleTypes.SWEEP_ATTACK,
                                            particlePos.x, particlePos.y + ent.getHeight() * 0.5, particlePos.z,
                                            3, 0.5, 0.5, 0.5, 0.0);
                                    ent.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 1.0F);
                                }, j * (500 / 3));
                            }

                        }, delay);
                    }
                }
                fractalCount.put(id, 0);
            }
            return ActionResult.PASS;
        });

        // Display cooldown messages
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0)
                return;

            long now = System.currentTimeMillis();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                Long last = PHASE_CHANGE_COOLDOWNS.get(id);

                if (last == null) {
                    continue;
                }

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

        // Check if Phasebreaker's owner has changed and transform it into a dragon egg
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    if (Utils.getAscended(player)) {
                        var inv = player.getInventory().main;
                        for (int i = 0; i < inv.size(); i++) {
                            ItemStack stack = inv.get(i);
                            if (stack.getItem() == ModItems.PHASEBREAKER) {
                                inv.set(i, new ItemStack(Items.DRAGON_EGG));
                                player.sendMessage(
                                        Text.literal("§cPhasebreaker transformed back into a Dragon Egg!"),
                                        true);
                                break;
                            }
                        }
                    }
                }
            }
        });

    }
}
