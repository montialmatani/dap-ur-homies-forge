package com.cooptest.bros;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;

public final class BrosArmorStandTest {
    public static final BrosHandler.Input DUMMY_INPUT = new BrosHandler.Input();
    private static final List<ArmorStandEntity> DUMMIES = new ArrayList<>();
    private static final Map<ArmorStandEntity, UUID> OWNERS = new HashMap<>();

    private BrosArmorStandTest() {
    }

    public static List<ArmorStandEntity> dummies() {
        DUMMIES.removeIf(s -> {
            boolean dead = s.isRemoved() || !s.isAlive();
            if (dead) {
                OWNERS.remove(s);
            }

            return dead;
        });
        return DUMMIES;
    }

    static void register() {
        CommandRegistrationCallback.EVENT
            .register(
                (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
                    (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                "testbros"
                                            )
                                            .requires(src -> src.hasPermissionLevel(2)))
                                        .executes(ctx -> spawn((ServerCommandSource)ctx.getSource())))
                                    .then(
                                        CommandManager.literal("input")
                                            .then(
                                                CommandManager.argument("forward", FloatArgumentType.floatArg(-1.0F, 1.0F))
                                                    .then(CommandManager.argument("right", FloatArgumentType.floatArg(-1.0F, 1.0F)).executes(ctx -> {
                                                        DUMMY_INPUT.forward = FloatArgumentType.getFloat(ctx, "forward");
                                                        DUMMY_INPUT.right = FloatArgumentType.getFloat(ctx, "right");
                                                        return say(
                                                            (ServerCommandSource)ctx.getSource(),
                                                            "Bro input: fwd " + DUMMY_INPUT.forward + ", right " + DUMMY_INPUT.right
                                                        );
                                                    }))
                                            )
                                    ))
                                .then(
                                    CommandManager.literal("turn")
                                        .then(CommandManager.argument("deg", FloatArgumentType.floatArg(-20.0F, 20.0F)).executes(ctx -> {
                                            DUMMY_INPUT.turn = FloatArgumentType.getFloat(ctx, "deg");
                                            return say((ServerCommandSource)ctx.getSource(), "Bro turn vote: " + DUMMY_INPUT.turn + " deg/tick");
                                        }))
                                ))
                            .then(CommandManager.literal("sprint").then(CommandManager.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                                DUMMY_INPUT.sprint = BoolArgumentType.getBool(ctx, "on");
                                return say((ServerCommandSource)ctx.getSource(), "Bro sprint: " + DUMMY_INPUT.sprint);
                            }))))
                        .then(CommandManager.literal("clear").executes(ctx -> {
                            clear();
                            return say((ServerCommandSource)ctx.getSource(), "Bro removed.");
                        }))
                )
            );
    }

    private static int spawn(ServerCommandSource src) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            return 0;
        } else {
            clear();
            resetInput();
            float yaw = player.getYaw();
            double r = Math.toRadians(yaw);
            double rightX = -Math.cos(r);
            double rightZ = -Math.sin(r);
            ServerWorld world = (ServerWorld)player.getWorld();
            ArmorStandEntity stand = new ArmorStandEntity(world, player.getX() + rightX * 1.5, player.getY(), player.getZ() + rightZ * 1.5);
            stand.setYaw(yaw);
            stand.setBodyYaw(yaw);
            stand.setHeadYaw(yaw);
            stand.setNoGravity(true);
            stand.setCustomName(Text.literal("Bro"));
            stand.setCustomNameVisible(true);
            world.spawnEntity(stand);
            DUMMIES.add(stand);
            OWNERS.put(stand, player.getUuid());
            src.sendFeedback(
                () -> Text.literal("Bro spawned on your right (always armed). Hold ")
                    .append(Text.keybind("key.coopmoves.dap"))
                    .append(Text.literal(", then "))
                    .append(Text.keybind("key.coopmoves.highfive"))
                    .append(Text.literal(".")),
                false
            );
            return 1;
        }
    }

    private static void clear() {
        for (ArmorStandEntity s : DUMMIES) {
            try {
                if (!s.isRemoved()) {
                    s.discard();
                }
            } catch (Throwable var3) {
            }
        }

        DUMMIES.clear();
        OWNERS.clear();
    }

    static void onOwnerLeft(UUID owner) {
        for (ArmorStandEntity s : new ArrayList<>(DUMMIES)) {
            if (owner.equals(OWNERS.get(s))) {
                try {
                    if (!s.isRemoved()) {
                        s.discard();
                    }
                } catch (Throwable var4) {
                }

                DUMMIES.remove(s);
                OWNERS.remove(s);
            }
        }
    }

    static void clearAll() {
        clear();
        resetInput();
    }

    private static void resetInput() {
        DUMMY_INPUT.forward = 0.0F;
        DUMMY_INPUT.right = 0.0F;
        DUMMY_INPUT.turn = 0.0F;
        DUMMY_INPUT.sprint = false;
    }

    private static int say(ServerCommandSource src, String msg) {
        src.sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }
}
