package com.cooptest;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.command.v2.CommandRegistrationCallback;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.event.player.UseEntityCallback;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;

public class BlackHoodHandler {
    private static final Map<UUID, UUID> hoodedPlayers = new HashMap<>();

    public static void register() {
        registerInteraction();
        registerTickEnforcement();
        registerJoinSync();
        registerDeathListener();
        registerCommand();
    }

    private static void registerDeathListener() {
        ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                UUID playerId = player.getUuid();
                if (hoodedPlayers.containsKey(playerId)) {
                    hoodedPlayers.remove(playerId);
                    if (player.getServer() != null) {
                        BlackHoodNetworking.broadcastHoodState(player.getServer(), playerId, false);
                    }
                }
            }
        });
    }

    private static void registerCommand() {
        CommandRegistrationCallback.EVENT
            .register(
                (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
                    (LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("unhood").requires(src -> src.hasPermissionLevel(2)))
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

                                        if (!hoodedPlayers.containsKey(target.getUuid())) {
                                            ((ServerCommandSource)ctx.getSource())
                                                .sendError(Text.literal(target.getName().getString() + " is not hooded."));
                                            return 0;
                                        } else {
                                            removeHoodByCommand(((ServerCommandSource)ctx.getSource()).getServer(), target);
                                            ((ServerCommandSource)ctx.getSource())
                                                .sendFeedback(
                                                    () -> Text.literal("§a[Hood] Removed from " + target.getName().getString() + "."), false
                                                );
                                            return 1;
                                        }
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
            } else if (hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            } else if (!(player instanceof ServerPlayerEntity clicker)) {
                return ActionResult.PASS;
            } else if (entity instanceof ServerPlayerEntity target) {
                if (clicker.getUuid().equals(target.getUuid())) {
                    return ActionResult.PASS;
                } else {
                    UUID targetId = target.getUuid();
                    UUID clickerId = clicker.getUuid();
                    if (clicker.isSneaking() && hoodedPlayers.containsKey(targetId)) {
                        boolean isOriginalHooder = clickerId.equals(hoodedPlayers.get(targetId));
                        boolean isOp = clicker.hasPermissionLevel(2);
                        if (isOriginalHooder || isOp) {
                            removeHood(clicker.getServer(), target, clicker);
                            return ActionResult.SUCCESS;
                        }
                    }

                    if (!clicker.isSneaking()) {
                        ItemStack heldItem = clicker.getStackInHand(hand);
                        if (!heldItem.isEmpty() && heldItem.isOf(MahitoItems.BLACK_HOOD) && !hoodedPlayers.containsKey(targetId)) {
                            equipHood(clicker, target, hand);
                            return ActionResult.SUCCESS;
                        }
                    }

                    return ActionResult.PASS;
                }
            } else {
                return ActionResult.PASS;
            }
        });
    }

    private static void equipHood(ServerPlayerEntity equiper, ServerPlayerEntity target, Hand hand) {
        ItemStack existing = (ItemStack)target.getInventory().armor.get(3);
        if (!existing.isEmpty()) {
            target.dropStack(existing.copy());
            target.getInventory().armor.set(3, ItemStack.EMPTY);
        }

        target.getInventory().armor.set(3, new ItemStack(MahitoItems.BLACK_HOOD));
        target.getInventory().markDirty();
        target.getServerWorld()
            .getPlayers()
            .forEach(
                p -> p.networkHandler
                    .sendPacket(new EntityEquipmentUpdateS2CPacket(target.getId(), List.of(new Pair(EquipmentSlot.HEAD, new ItemStack(MahitoItems.BLACK_HOOD)))))
            );
        if (!equiper.isCreative()) {
            equiper.getStackInHand(hand).decrement(1);
        }

        hoodedPlayers.put(target.getUuid(), equiper.getUuid());
        MinecraftServer server = equiper.getServer();
        if (server != null) {
            BlackHoodNetworking.broadcastHoodState(server, target.getUuid(), true);
        }

        equiper.sendMessage(
            Text.literal("§c[Hood] ").append(target.getName()).append(Text.literal(" can't see a thing.")), true
        );
        target.sendMessage(Text.literal("§8[Hood] §cSomething was just put on your head..."), false);
    }

    private static void removeHoodByCommand(MinecraftServer server, ServerPlayerEntity target) {
        UUID targetId = target.getUuid();
        hoodedPlayers.remove(targetId);
        ItemStack headSlot = (ItemStack)target.getInventory().armor.get(3);
        if (!headSlot.isEmpty() && headSlot.isOf(MahitoItems.BLACK_HOOD)) {
            target.dropStack(headSlot.copy());
            target.getInventory().armor.set(3, ItemStack.EMPTY);
            target.getInventory().markDirty();
        }

        target.getServerWorld()
            .getPlayers()
            .forEach(p -> p.networkHandler.sendPacket(new EntityEquipmentUpdateS2CPacket(target.getId(), List.of(new Pair(EquipmentSlot.HEAD, ItemStack.EMPTY)))));
        if (server != null) {
            BlackHoodNetworking.broadcastHoodState(server, targetId, false);
        }

        target.sendMessage(Text.literal("§a[Hood] §fYour hood was removed by an admin."), false);
    }

    private static void removeHood(MinecraftServer server, ServerPlayerEntity target, ServerPlayerEntity remover) {
        UUID targetId = target.getUuid();
        hoodedPlayers.remove(targetId);
        ItemStack headSlot = (ItemStack)target.getInventory().armor.get(3);
        if (!headSlot.isEmpty() && headSlot.isOf(MahitoItems.BLACK_HOOD)) {
            target.getInventory().armor.set(3, ItemStack.EMPTY);
            target.getInventory().markDirty();
            ItemStack returnStack = new ItemStack(MahitoItems.BLACK_HOOD);
            if (!remover.getInventory().insertStack(returnStack)) {
                remover.dropStack(returnStack);
            }
        }

        target.getServerWorld()
            .getPlayers()
            .forEach(p -> p.networkHandler.sendPacket(new EntityEquipmentUpdateS2CPacket(target.getId(), List.of(new Pair(EquipmentSlot.HEAD, ItemStack.EMPTY)))));
        if (server != null) {
            BlackHoodNetworking.broadcastHoodState(server, targetId, false);
        }

        remover.sendMessage(
            Text.literal("§a[Hood] Removed from ").append(target.getName()).append(Text.literal(".")), true
        );
        target.sendMessage(Text.literal("§a[Hood] §fThe hood has been removed. You can see again!"), false);
    }

    private static void registerTickEnforcement() {
        ServerTickEvents.END_SERVER_TICK
            .register(
                (EndTick)server -> {
                    if (!hoodedPlayers.isEmpty()) {
                        for (UUID targetId : hoodedPlayers.keySet()) {
                            ServerPlayerEntity player = server.getPlayerManager().getPlayer(targetId);
                            if (player != null) {
                                ItemStack headSlot = (ItemStack)player.getInventory().armor.get(3);
                                boolean hoodPresent = !headSlot.isEmpty() && headSlot.isOf(MahitoItems.BLACK_HOOD);
                                if (!hoodPresent) {
                                    if (!headSlot.isEmpty()) {
                                        player.dropStack(headSlot.copy());
                                    }

                                    player.getInventory().armor.set(3, new ItemStack(MahitoItems.BLACK_HOOD));
                                    player.getInventory().markDirty();
                                    player.getServerWorld()
                                        .getPlayers()
                                        .forEach(
                                            p -> p.networkHandler
                                                .sendPacket(
                                                    new EntityEquipmentUpdateS2CPacket(
                                                        player.getId(), List.of(new Pair(EquipmentSlot.HEAD, new ItemStack(MahitoItems.BLACK_HOOD)))
                                                    )
                                                )
                                        );
                                }

                                for (int i = 0; i < player.getInventory().size(); i++) {
                                    ItemStack slot = player.getInventory().getStack(i);
                                    if (!slot.isEmpty() && slot.isOf(MahitoItems.BLACK_HOOD)) {
                                        player.getInventory().removeStack(i);
                                    }
                                }
                            }
                        }
                    }
                }
            );
    }

    private static void registerJoinSync() {
        ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
            ServerPlayerEntity newPlayer = handler.getPlayer();

            for (UUID hoodedId : hoodedPlayers.keySet()) {
                ServerPlayNetworking.send(newPlayer, new BlackHoodNetworking.HoodStatePayload(hoodedId, true));
            }
        });
    }

    public static void onPlayerLeave(UUID playerId) {
    }

    public static boolean isHooded(UUID playerId) {
        return hoodedPlayers.containsKey(playerId);
    }
}
