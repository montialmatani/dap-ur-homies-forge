package com.cooptest.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import com.cooptest.fabricshim.api.EnvType;
import com.cooptest.fabricshim.api.Environment;
import com.cooptest.fabricshim.fabric.api.client.command.v2.ClientCommandManager;
import com.cooptest.fabricshim.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import com.cooptest.fabricshim.fabric.api.client.command.v2.FabricClientCommandSource;
import com.cooptest.fabricshim.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.CyclingButtonWidget;

@Environment(EnvType.CLIENT)
public class CoopClientSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("coopmoves_client.json").toFile();
    private static CoopClientSettings INSTANCE;
    public boolean cameraShakeEnabled = true;
    public boolean impactFramesEnabled = true;

    public static CoopClientSettings get() {
        if (INSTANCE == null) {
            load();
        }

        return INSTANCE;
    }

    public static void load() {
        if (FILE.exists()) {
            try (FileReader r = new FileReader(FILE)) {
                CoopClientSettings loaded = (CoopClientSettings)GSON.fromJson(r, CoopClientSettings.class);
                INSTANCE = loaded != null ? loaded : new CoopClientSettings();
            } catch (Exception var5) {
                System.err.println("[CoopMoves] Client settings corrupted, resetting: " + var5.getMessage());
                INSTANCE = new CoopClientSettings();
            }
        } else {
            INSTANCE = new CoopClientSettings();
        }

        save();
    }

    public static void save() {
        if (INSTANCE == null) {
            INSTANCE = new CoopClientSettings();
        }

        try {
            FILE.getParentFile().mkdirs();

            try (FileWriter w = new FileWriter(FILE)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (Exception var5) {
            System.err.println("[CoopMoves] Failed to save client settings: " + var5.getMessage());
        }
    }

    public static void register() {
        load();
        ClientCommandRegistrationCallback.EVENT
            .register(
                (ClientCommandRegistrationCallback)(dispatcher, access) -> {
                    dispatcher.register(
                        (LiteralArgumentBuilder)ClientCommandManager.literal("dap")
                            .then(
                                ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("settings")
                                                .executes(ctx -> open((FabricClientCommandSource)ctx.getSource())))
                                            .then(
                                                ((LiteralArgumentBuilder)ClientCommandManager.literal("shake")
                                                        .then(
                                                            ClientCommandManager.literal("on")
                                                                .executes(c -> setShake((FabricClientCommandSource)c.getSource(), true))
                                                        ))
                                                    .then(
                                                        ClientCommandManager.literal("off")
                                                            .executes(c -> setShake((FabricClientCommandSource)c.getSource(), false))
                                                    )
                                            ))
                                        .then(
                                            ((LiteralArgumentBuilder)ClientCommandManager.literal("impactframes")
                                                    .then(
                                                        ClientCommandManager.literal("on")
                                                            .executes(c -> setFrames((FabricClientCommandSource)c.getSource(), true))
                                                    ))
                                                .then(
                                                    ClientCommandManager.literal("off")
                                                        .executes(c -> setFrames((FabricClientCommandSource)c.getSource(), false))
                                                )
                                        ))
                                    .then(ClientCommandManager.literal("reset").executes(c -> {
                                        get().cameraShakeEnabled = true;
                                        get().impactFramesEnabled = true;
                                        save();
                                        msg((FabricClientCommandSource)c.getSource(), "§7Comfort settings reset.");
                                        return 1;
                                    }))
                            )
                    );
                    dispatcher.register(
                        (LiteralArgumentBuilder)ClientCommandManager.literal("coopshake")
                            .executes(c -> setShake((FabricClientCommandSource)c.getSource(), !get().cameraShakeEnabled))
                    );
                    dispatcher.register(
                        (LiteralArgumentBuilder)ClientCommandManager.literal("coopflash")
                            .executes(c -> setFrames((FabricClientCommandSource)c.getSource(), !get().impactFramesEnabled))
                    );
                }
            );
    }

    private static int open(FabricClientCommandSource src) {
        MinecraftClient client = src.getClient();
        client.execute(() -> client.setScreen(new CoopClientSettings.SettingsScreen(null)));
        return 1;
    }

    private static int setShake(FabricClientCommandSource src, boolean on) {
        get().cameraShakeEnabled = on;
        save();
        msg(src, "§6Screen shake: " + state(on));
        return 1;
    }

    private static int setFrames(FabricClientCommandSource src, boolean on) {
        get().impactFramesEnabled = on;
        save();
        msg(src, "§6Impact frames: " + state(on));
        return 1;
    }

    private static String state(boolean on) {
        return on ? "§aON" : "§cOFF";
    }

    private static void msg(FabricClientCommandSource src, String text) {
        src.sendFeedback(Text.literal(text));
    }

    @Environment(EnvType.CLIENT)
    public static class SettingsScreen extends Screen {
        private final Screen parent;

        public SettingsScreen(Screen parent) {
            super(Text.literal("Coop Moves — Comfort"));
            this.parent = parent;
        }

        protected void init() {
            CoopClientSettings s = CoopClientSettings.get();
            int cx = this.width / 2;
            int y = this.height / 4 + 24;
            this.addDrawableChild(
                CyclingButtonWidget.onOffBuilder(s.cameraShakeEnabled).build(cx - 110, y, 220, 20, Text.literal("Screen shake"), (button, value) -> {
                    CoopClientSettings.get().cameraShakeEnabled = value;
                    CoopClientSettings.save();
                })
            );
            this.addDrawableChild(
                CyclingButtonWidget.onOffBuilder(s.impactFramesEnabled)
                    .build(cx - 110, y + 28, 220, 20, Text.literal("Impact frames (flashing)"), (button, value) -> {
                        CoopClientSettings.get().impactFramesEnabled = value;
                        CoopClientSettings.save();
                    })
            );
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> {
                CoopClientSettings.get().cameraShakeEnabled = true;
                CoopClientSettings.get().impactFramesEnabled = true;
                CoopClientSettings.save();
                this.clearAndInit();
            }).dimensions(cx - 110, y + 64, 108, 20).build());
            this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), b -> this.close()).dimensions(cx + 2, y + 64, 108, 20).build()
            );
        }

        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            int cx = this.width / 2;
            ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, this.height / 4 - 4, -1);
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7These apply to your client only"), cx, this.height / 4 + 8, -5592406);
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§8config/coopmoves_client.json"), cx, this.height / 4 + 104, -7829368);
        }

        public void close() {
            CoopClientSettings.save();
            this.client.setScreen(this.parent);
        }
    }
}
