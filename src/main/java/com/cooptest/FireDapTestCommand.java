package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;

public class FireDapTestCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("testfiredap").requires(source -> source.hasPermissionLevel(2)))
                .executes(FireDapTestCommand::execute)
        );
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = (ServerCommandSource)context.getSource();

        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            Vec3d pos = player.getPos();
            Vec3d fakePartnerPos = pos.add(1.0, 0.0, 0.0);
            player.sendMessage(Text.literal("§a[TEST] Starting Fire Dap test..."), false);
            player.sendMessage(Text.literal("§a[TEST] You are P1, fake partner is P2"), false);
            player.sendMessage(Text.literal("§a[TEST] Press J when window opens!"), false);
            ChargedDapHandler.startFireDap(player, player, pos);
            player.sendMessage(Text.literal("§a[TEST] Fire dap started! Check console logs!"), false);
            return 1;
        } catch (Exception var5) {
            source.sendError(Text.literal("§cError: " + var5.getMessage()));
            var5.printStackTrace();
            return 0;
        }
    }
}
