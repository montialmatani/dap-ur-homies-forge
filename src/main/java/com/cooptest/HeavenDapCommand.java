package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;

public class HeavenDapCommand {
    private static final int OP_LEVEL = 2;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("heavendap").requires(src -> src.hasPermissionLevel(2)))
                    .executes(HeavenDapCommand::execute))
                .then(
                    ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("easy")
                                .executes(ctx -> setRecMode(ctx, !ChargedDapHandler.heavenRecMode)))
                            .then(CommandManager.literal("on").executes(ctx -> setRecMode(ctx, true))))
                        .then(CommandManager.literal("off").executes(ctx -> setRecMode(ctx, false)))
                )
        );
    }

    private static int setRecMode(CommandContext<ServerCommandSource> ctx, boolean on) {
        ChargedDapHandler.heavenRecMode = on;
        if (on) {
            ((ServerCommandSource)ctx.getSource())
                .sendFeedback(() -> Text.literal("§d§l✨ Heaven Rec Mode ON §7— range 4.0, perfect 400ms, release 1500ms, heaven ready in 2.0s"), true);
        } else {
            ((ServerCommandSource)ctx.getSource()).sendFeedback(() -> Text.literal("§7Heaven Rec Mode §cOFF §7— normal difficulty"), true);
        }

        return 1;
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayer();
        if (player == null) {
            return 0;
        } else {
            ServerPlayerEntity partner = null;
            double closest = 10.0;

            for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
                if (other != player) {
                    double d = player.distanceTo(other);
                    if (d < closest) {
                        closest = d;
                        partner = other;
                    }
                }
            }

            if (partner == null) {
                ((ServerCommandSource)context.getSource()).sendError(Text.literal("§cNo nearby player found (must be within 10 blocks)."));
                return 0;
            } else {
                Vec3d mid = player.getPos().add(partner.getPos()).multiply(0.5).add(0.0, 1.4, 0.0);
                ServerPlayerEntity finalPartner = partner;
                ChargedDapHandler.startHeavenDap(player, finalPartner, mid, player.getServerWorld());
                ((ServerCommandSource)context.getSource())
                    .sendFeedback(() -> Text.literal("§d§l✨ HEAVEN DAP triggered with " + finalPartner.getName().getString() + "!"), false);
                return 1;
            }
        }
    }
}
