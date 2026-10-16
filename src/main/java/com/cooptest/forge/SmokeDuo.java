package com.cooptest.forge;

import com.cooptest.client.ChargedDapClientHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CI-only two-player harness (-Dcoopmoves.smoke=duo -Dcoopmoves.smoke.role=Alice|Bob).
 * Both clients join a dedicated server; Alice arranges positions with op commands, then both
 * hold the dap key and release on the same server tick, exercising the real dap flow end to end.
 */
final class SmokeDuo {
    private static final Logger LOG = LoggerFactory.getLogger("coopmoves/smoke");
    private static final String ROLE = System.getProperty("coopmoves.smoke.role", "Alice");
    private static int ticksInWorld = -1;
    private static long rounds = 0;
    private static long holdStart = -1;
    private static boolean setupSent = false;
    private static long finishAt = -1;

    static void init() {
        LOG.info("SMOKE duo harness enabled as {}", ROLE);
        MinecraftForge.EVENT_BUS.addListener(SmokeDuo::onTick);
    }

    private static void shot(MinecraftClient mc, String name) {
        String file = "duo_" + ROLE + "_" + name + ".png";
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, file, mc.getFramebuffer(), text -> LOG.info("SMOKE screenshot {}", file));
    }

    private static void cmd(MinecraftClient mc, String command) {
        LOG.info("SMOKE {} runs /{}", ROLE, command);
        mc.player.networkHandler.sendChatCommand(command);
    }

    private static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        ticksInWorld++;
        try {
            int players = mc.world.getPlayers().size();
            if (ticksInWorld == 1) {
                LOG.info("SMOKE {} in world", ROLE);
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
            if (ticksInWorld % 100 == 0) {
                StringBuilder sb = new StringBuilder();
                for (AbstractClientPlayerEntity p : mc.world.getPlayers()) sb.append(p.getName().getString()).append(' ');
                LOG.info("SMOKE {} sees {} players: {}", ROLE, players, sb);
            }
            if (players < 2) return;

            // Alice sets the stage once both are online.
            if ("Alice".equals(ROLE) && !setupSent && ticksInWorld > 60) {
                setupSent = true;
                cmd(mc, "time set day");
                cmd(mc, "weather clear");
                cmd(mc, "tp Alice ~ ~ ~ 0 0");
                cmd(mc, "execute at Alice run tp Bob ^ ^ ^1.6 facing entity Alice eyes");
            }

            // Shared schedule on the server-synced world clock: dap rounds every 200 ticks.
            long time = mc.world.getTime();
            KeyBinding dap = ChargedDapClientHandler.getChargedDapKey();
            if (ticksInWorld > 200 && holdStart < 0 && time % 200 == 0 && rounds < 2) {
                holdStart = time;
                rounds++;
                LOG.info("SMOKE {} round {} start holding dap at worldTime {}", ROLE, rounds, time);
            }
            if (holdStart >= 0) {
                long dt = time - holdStart;
                if (dt < 40) {
                    dap.setPressed(true);
                    if (dt == 20) shot(mc, "r" + rounds + "_charging");
                } else if (dt == 40) {
                    dap.setPressed(false);
                    LOG.info("SMOKE {} released dap at worldTime {}", ROLE, time);
                } else if (dt == 43 || dt == 47 || dt == 53 || dt == 65) {
                    shot(mc, "r" + rounds + "_after_" + dt);
                } else if (dt >= 120) {
                    holdStart = -1;
                    if (rounds >= 2) finishAt = time + 20;
                }
            }
            if (finishAt > 0 && time >= finishAt) {
                LOG.info("SMOKE_DONE");
                finishAt = Long.MAX_VALUE;
                mc.scheduleStop();
            }
        } catch (Throwable ex) {
            LOG.error("SMOKE duo failure", ex);
        }
    }
}
