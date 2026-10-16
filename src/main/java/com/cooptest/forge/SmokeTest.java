package com.cooptest.forge;

import com.cooptest.client.anim.CoopAnimLib;
import com.cooptest.client.anim.FirstPersonConfiguration;
import com.cooptest.client.anim.FirstPersonMode;
import com.cooptest.client.anim.PlayerAnimationController;
import java.io.File;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CI-only harness (enabled with -Dcoopmoves.smoke=true): once in a world it plays a few animations
 * in third and first person, saves screenshots, then quits. Inert in normal play.
 */
final class SmokeTest {
    private static final Logger LOG = LoggerFactory.getLogger("coopmoves/smoke");
    private static int ticksInWorld = -1;

    private static final String[] ANIMS = {"dap_hit", "highfive_start", "grab_ready", "dap_charge", "superman", "hugging"};

    static void init() {
        LOG.info("SMOKE harness enabled");
        MinecraftForge.EVENT_BUS.addListener(SmokeTest::onTick);
    }

    private static void shot(MinecraftClient mc, String name) {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, "smoke_" + name + ".png", mc.getFramebuffer(), text -> LOG.info("SMOKE screenshot {}", name));
    }

    private static void play(MinecraftClient mc, String anim, boolean firstPerson) {
        PlayerAnimationController c = CoopAnimLib.controller(mc.player);
        boolean ok = c.triggerAnimation(new Identifier("testcoop", anim));
        if (firstPerson) {
            c.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            c.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        } else {
            c.setFirstPersonMode(FirstPersonMode.NONE);
        }
        LOG.info("SMOKE trigger {} firstPerson={} ok={}", anim, firstPerson, ok);
    }

    private static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        ticksInWorld++;
        int t = ticksInWorld;
        try {
            if (t == 1) {
                LOG.info("SMOKE in world");
                mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                mc.options.hudHidden = false;
            }
            if (t == 100) shot(mc, "00_idle_front");
            // third person: each animation gets 40 ticks, screenshots at +6 and +16
            int base = 110;
            for (int i = 0; i < ANIMS.length; i++) {
                int start = base + i * 40;
                if (t == start) play(mc, ANIMS[i], false);
                if (t == start + 6) shot(mc, String.format("%02d_%s_a", i + 1, ANIMS[i]));
                if (t == start + 16) shot(mc, String.format("%02d_%s_b", i + 1, ANIMS[i]));
            }
            int fp = base + ANIMS.length * 40;
            if (t == fp) {
                mc.options.setPerspective(Perspective.FIRST_PERSON);
                shot(mc, "20_firstperson_idle");
            }
            if (t == fp + 10) play(mc, "dap_charge", true);
            if (t == fp + 18) shot(mc, "21_firstperson_dap_charge");
            if (t == fp + 30) play(mc, "dap_hit", true);
            if (t == fp + 36) shot(mc, "22_firstperson_dap_hit");
            if (t == fp + 60) {
                PlayerAnimationController c = CoopAnimLib.controller(mc.player);
                c.stop();
                c.setFirstPersonMode(FirstPersonMode.NONE);
                shot(mc, "23_firstperson_after_stop");
            }
            if (t == fp + 80) {
                LOG.info("SMOKE_DONE");
                mc.scheduleStop();
            }
        } catch (Throwable ex) {
            LOG.error("SMOKE failure at tick {}", t, ex);
        }
    }
}
