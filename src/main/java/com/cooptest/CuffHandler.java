package com.cooptest;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.command.v2.CommandRegistrationCallback;
import com.cooptest.fabricshim.fabric.api.event.player.AttackEntityCallback;
import com.cooptest.fabricshim.fabric.api.event.player.PlayerBlockBreakEvents;
import com.cooptest.fabricshim.fabric.api.event.player.UseBlockCallback;
import com.cooptest.fabricshim.fabric.api.event.player.UseEntityCallback;
import com.cooptest.fabricshim.fabric.api.event.player.PlayerBlockBreakEvents.Before;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.potion.PotionUtil;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.FoodComponent;

public class CuffHandler {
    private static final int CUFF_ANIM_ORDINAL = 98;
    private static final Set<UUID> cuffedPlayers = new HashSet<>();
    private static final Map<UUID, UUID> cuffCarriers = new HashMap<>();
    private static final Map<UUID, UUID> cuffCarrying = new HashMap<>();

    public static void register() {
        registerCommand();
        registerInteraction();
        registerBlockClickDrop();
        registerBlockBreakBlock();
        registerBlockPlaceBlock();
        registerAttackBlock();
    }

    private static void registerCommand() {
        CommandRegistrationCallback.EVENT
            .register(
                (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
                    (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("cuff")
                                    .requires(src -> src.hasPermissionLevel(2)))
                                .then(CommandManager.literal("all").executes(ctx -> {
                                    List<ServerPlayerEntity> players = ((ServerCommandSource)ctx.getSource()).getServer().getPlayerManager().getPlayerList();
                                    ServerPlayerEntity sender = null;

                                    try {
                                        sender = ((ServerCommandSource)ctx.getSource()).getPlayerOrThrow();
                                    } catch (Exception var7) {
                                    }

                                    ServerPlayerEntity finalSender = sender;
                                    int count = 0;

                                    for (ServerPlayerEntity p : players) {
                                        if ((finalSender == null || !p.getUuid().equals(finalSender.getUuid())) && !isCuffed(p.getUuid())) {
                                            cuff(p);
                                            count++;
                                        }
                                    }

                                    int finalCount = count;
                                    ((ServerCommandSource)ctx.getSource())
                                        .sendFeedback(() -> Text.literal("§c[Cuff] Cuffed " + finalCount + " player(s)."), false);
                                    return finalCount;
                                })))
                            .then(CommandManager.literal("uncuffall").executes(ctx -> {
                                List<ServerPlayerEntity> players = ((ServerCommandSource)ctx.getSource()).getServer().getPlayerManager().getPlayerList();
                                int count = 0;

                                for (ServerPlayerEntity p : new ArrayList<>(players)) {
                                    if (isCuffed(p.getUuid())) {
                                        uncuff(p);
                                        count++;
                                    }
                                }

                                int finalCount = count;
                                ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("§a[Cuff] Freed " + finalCount + " player(s)."), false);
                                return finalCount;
                            })))
                        .then(
                            CommandManager.argument("player", EntityArgumentType.player())
                                .executes(
                                    ctx -> {
                                        ServerPlayerEntity target;
                                        try {
                                            target = EntityArgumentType.getPlayer(ctx, "player");
                                        } catch (Exception var3) {
                                            ((ServerCommandSource)ctx.getSource()).sendError(Text.literal("Player not found."));
                                            return 0;
                                        }

                                        if (isCuffed(target.getUuid())) {
                                            uncuff(target);
                                            ((ServerCommandSource)ctx.getSource())
                                                .sendFeedback(
                                                    () -> Text.literal("§a[Cuff] " + target.getName().getString() + " has been freed."), false
                                                );
                                        } else {
                                            cuff(target);
                                            ((ServerCommandSource)ctx.getSource())
                                                .sendFeedback(
                                                    () -> Text.literal("§c[Cuff] " + target.getName().getString() + " has been cuffed."), false
                                                );
                                        }

                                        return 1;
                                    }
                                )
                        )
                )
            );
    }

    private static void registerInteraction() {
        UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
            if (world.isClient) {
                return ActionResult.PASS;
            } else if (hand != Hand.field_5808) {
                return ActionResult.PASS;
            } else if (player instanceof ServerPlayerEntity clicker) {
                if (!(entity instanceof ServerPlayerEntity target)) {
                    return ActionResult.PASS;
                } else {
                    UUID clickerId = clicker.getUuid();
                    UUID targetId = target.getUuid();
                    if (cuffedPlayers.contains(targetId)) {
                        ItemStack held = clicker.getStackInHand(hand);
                        FoodComponent food = held.isEmpty() ? null : held.getItem().getFoodComponent();
                        if (food != null) {
                            target.getHungerManager().add(food.getHunger(), food.getSaturationModifier());
                            if (!clicker.isCreative()) {
                                held.decrement(1);
                            }

                            clicker.sendMessage(Text.literal("§a[Cuff] Fed " + target.getName().getString() + "."), true);
                            target.sendMessage(Text.literal("§aYou were fed."), true);
                            return ActionResult.SUCCESS;
                        }

                        if (!held.isEmpty() && held.getItem() instanceof PotionItem) {
                            java.util.List<StatusEffectInstance> potionEffects = PotionUtil.getPotionEffects(held);
                            if (!potionEffects.isEmpty()) {
                                for (StatusEffectInstance effect : potionEffects) {
                                    target.addStatusEffect(new StatusEffectInstance(effect));
                                }

                                if (!clicker.isCreative()) {
                                    held.decrement(1);
                                    ItemStack bottle = new ItemStack(Items.field_8469);
                                    if (!clicker.getInventory().insertStack(bottle)) {
                                        clicker.dropStack(bottle);
                                    }
                                }

                                clicker.sendMessage(Text.literal("§a[Cuff] Applied potion to " + target.getName().getString() + "."), true);
                                target.sendMessage(Text.literal("§aA potion was applied to you."), true);
                                return ActionResult.SUCCESS;
                            }
                        }
                    }

                    if (!clicker.isSneaking()) {
                        return ActionResult.PASS;
                    } else if (cuffCarrying.containsKey(clickerId)) {
                        dropCuffed(clicker);
                        return ActionResult.SUCCESS;
                    } else if (cuffedPlayers.contains(targetId) && !cuffCarriers.containsKey(targetId)) {
                        ItemStack heldx = clicker.getStackInHand(hand);
                        if (!heldx.isEmpty()) {
                            clicker.sendMessage(Text.literal("§cEmpty your hand to carry them."), true);
                            return ActionResult.PASS;
                        } else {
                            pickupCuffed(clicker, target);
                            return ActionResult.SUCCESS;
                        }
                    } else {
                        return ActionResult.PASS;
                    }
                }
            } else {
                return ActionResult.PASS;
            }
        });
    }

    private static void registerBlockClickDrop() {
        UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            } else if (hand != Hand.field_5808) {
                return ActionResult.PASS;
            } else if (player instanceof ServerPlayerEntity carrier) {
                if (!carrier.isSneaking()) {
                    return ActionResult.PASS;
                } else if (!cuffCarrying.containsKey(carrier.getUuid())) {
                    return ActionResult.PASS;
                } else {
                    dropCuffed(carrier);
                    return ActionResult.SUCCESS;
                }
            } else {
                return ActionResult.PASS;
            }
        });
    }

    private static void registerBlockBreakBlock() {
        PlayerBlockBreakEvents.BEFORE.register((Before)(world, player, pos, state, blockEntity) -> {
            if (world.isClient()) {
                return true;
            } else if (player instanceof ServerPlayerEntity sp) {
                if (!cuffedPlayers.contains(sp.getUuid())) {
                    return true;
                } else {
                    sp.sendMessage(Text.literal("§c⛓ You can't break blocks while cuffed."), true);
                    return false;
                }
            } else {
                return true;
            }
        });
    }

    private static void registerBlockPlaceBlock() {
        UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            } else if (player instanceof ServerPlayerEntity sp) {
                if (!cuffedPlayers.contains(sp.getUuid())) {
                    return ActionResult.PASS;
                } else {
                    ItemStack held = player.getStackInHand(hand);
                    if (!held.isEmpty() && held.getItem() instanceof BlockItem) {
                        sp.sendMessage(Text.literal("§c⛓ You can't place blocks while cuffed."), true);
                        return ActionResult.FAIL;
                    } else {
                        return ActionResult.PASS;
                    }
                }
            } else {
                return ActionResult.PASS;
            }
        });
    }

    private static void registerAttackBlock() {
        AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, world, hand, entity, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            } else if (player instanceof ServerPlayerEntity sp) {
                if (!cuffedPlayers.contains(sp.getUuid())) {
                    return ActionResult.PASS;
                } else {
                    sp.sendMessage(Text.literal("§c⛓ You can't attack while cuffed."), true);
                    return ActionResult.FAIL;
                }
            } else {
                return ActionResult.PASS;
            }
        });
    }

    private static void cuff(ServerPlayerEntity target) {
        cuffedPlayers.add(target.getUuid());
        PoseNetworking.broadcastAnimState(target, 98);
        target.sendMessage(Text.literal("§c⛓ You have been cuffed. You cannot move."), false);
    }

    private static void uncuff(ServerPlayerEntity target) {
        UUID targetId = target.getUuid();
        if (cuffCarriers.containsKey(targetId)) {
            UUID carrierId = cuffCarriers.remove(targetId);
            cuffCarrying.remove(carrierId);
            target.stopRiding();
            if (target.getServer() != null) {
                ServerPlayerEntity carrier = target.getServer().getPlayerManager().getPlayer(carrierId);
                if (carrier != null) {
                    broadcastPassengers(carrier);
                }
            }
        }

        cuffedPlayers.remove(targetId);
        PoseNetworking.broadcastAnimState(target, 0);
        target.sendMessage(Text.literal("§a⛓ You have been uncuffed. You are free!"), false);
    }

    private static void pickupCuffed(ServerPlayerEntity carrier, ServerPlayerEntity cuffed) {
        if (cuffCarrying.containsKey(carrier.getUuid())) {
            carrier.sendMessage(Text.literal("§cYou're already carrying someone."), true);
        } else {
            boolean success = cuffed.startRiding(carrier, true);
            if (!success) {
                carrier.sendMessage(Text.literal("§cCouldn't pick them up — try standing closer."), true);
            } else {
                cuffCarriers.put(cuffed.getUuid(), carrier.getUuid());
                cuffCarrying.put(carrier.getUuid(), cuffed.getUuid());
                broadcastPassengers(carrier);
                carrier.sendMessage(Text.literal("§c[Cuff] Carrying. Shift+right-click to drop."), true);
                cuffed.sendMessage(Text.literal("§cYou are being carried!"), true);
            }
        }
    }

    private static void dropCuffed(ServerPlayerEntity carrier) {
        UUID cuffedId = cuffCarrying.remove(carrier.getUuid());
        if (cuffedId != null) {
            cuffCarriers.remove(cuffedId);
            if (carrier.getServer() != null) {
                ServerPlayerEntity cuffed = carrier.getServer().getPlayerManager().getPlayer(cuffedId);
                if (cuffed != null) {
                    cuffed.stopRiding();
                    broadcastPassengers(carrier);
                    cuffed.sendMessage(Text.literal("§cYou were dropped."), true);
                }
            }

            carrier.sendMessage(Text.literal("§a[Cuff] Dropped."), true);
        }
    }

    private static void broadcastPassengers(ServerPlayerEntity vehicle) {
        if (vehicle.getServer() != null) {
            EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(vehicle);

            for (ServerPlayerEntity p : vehicle.getServer().getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(packet);
            }
        }
    }

    public static void onPlayerLeave(UUID playerId) {
        if (cuffedPlayers.remove(playerId)) {
            UUID carrierId = cuffCarriers.remove(playerId);
            if (carrierId != null) {
                cuffCarrying.remove(carrierId);
            }
        }

        UUID cuffedId = cuffCarrying.remove(playerId);
        if (cuffedId != null) {
            cuffCarriers.remove(cuffedId);
        }
    }

    public static boolean isCuffed(UUID playerId) {
        return cuffedPlayers.contains(playerId);
    }

    public static boolean isBeingCarried(UUID cuffedPlayerId) {
        return cuffCarriers.containsKey(cuffedPlayerId);
    }

    public static boolean isCarryingCuffed(UUID carrierId) {
        return cuffCarrying.containsKey(carrierId);
    }
}
