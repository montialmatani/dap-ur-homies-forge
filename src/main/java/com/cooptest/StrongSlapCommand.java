package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;

public class StrongSlapCommand {
    private static final Set<UUID> armed = new HashSet<>();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder)CommandManager.literal("slap").executes(ctx -> {
            ServerCommandSource source = (ServerCommandSource)ctx.getSource();

            ServerPlayerEntity player;
            try {
                player = source.getPlayerOrThrow();
            } catch (Exception var4) {
                source.sendError(Text.literal("Must be run by a player."));
                return 0;
            }

            UUID id = player.getUuid();
            if (armed.contains(id)) {
                armed.remove(id);
                player.sendMessage(Text.literal("§7[Strong Slap §cDISARMED§7] — G behaves normally."), false);
            } else {
                armed.add(id);
                player.sendMessage(Text.literal("§6[Strong Slap §aARMED§6] — Hold G on a player's back then release to start the QTE."), false);
            }

            return 1;
        }));
    }

    public static boolean isArmed(UUID playerId) {
        return armed.contains(playerId);
    }

    public static void disarm(UUID playerId) {
        armed.remove(playerId);
    }

    public static void clearAll() {
        armed.clear();
    }
}
