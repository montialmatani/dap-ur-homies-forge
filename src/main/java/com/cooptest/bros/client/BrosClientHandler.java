package com.cooptest.bros.client;

import com.cooptest.bros.BrosHandler;
import com.cooptest.bros.BrosPayloads;
import com.cooptest.bros.BrosPhysics;
import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import com.cooptest.fabricshim.api.EnvType;
import com.cooptest.fabricshim.api.Environment;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class BrosClientHandler {
    public static final long ARM_CHARGE_MS = 250L;
    public static final long ARM_H_GRACE_MS = 400L;
    public static boolean DEBUG = false;
    public static final long G_HOLD_MS = 300L;
    public static final long H_HOLD_MS = 300L;
    public static final int STALE_TICKS = 10;
    public static final double CORR_BLEND = 0.35;
    public static final double SNAP_DIST = 1.0;
    public static final float SNAP_DEG = 30.0F;
    public static final double LOCAL_Y_SNAP = 0.75;
    public static final double TURN_VOTE_SCALE = 1.0;
    public static final boolean DOME_PARTICLES = true;
    public static final int DOME_POINTS = 8;
    public static final int DOME_FULL_COLOR = 5629695;
    public static final int DOME_LOW_COLOR = 16728128;
    public static final float DOME_SCALE = 0.9F;
    public static final float LEG_AMPLITUDE = 1.0F;
    public static final float LEG_SMOOTHING = 0.4F;
    private static final double DOME_HEIGHT = 3.0;
    private static final Map<Integer, BrosClientHandler.Track> tracks = new HashMap<>();
    private static BrosClientHandler.Track localTrack = null;
    private static boolean armed;
    private static boolean prevH;
    private static boolean prevG;
    private static long gPressAt;
    private static long hPressAt;
    private static boolean gHoldSent;
    private static boolean hHoldSent;
    private static long gDownSince;
    private static long hDownSince;
    private static boolean hLock;
    private static boolean releasedSinceEngage;
    private static boolean prevSneak;
    private static double pendingTurn;
    private static final Set<Integer> legDriven = new HashSet<>();
    private static final Set<Integer> legDrivenNext = new HashSet<>();
    private static final int C_DIM = -7697782;
    private static final int C_GREEN = -9699462;
    private static final int C_BG = -2013265920;

    private BrosClientHandler() {
    }

    public static boolean isLegDriven(int entityId) {
        return legDriven.contains(entityId);
    }

    public static boolean isEngaged() {
        return localTrack != null;
    }

    public static boolean blocksDap() {
        return armed || localTrack != null;
    }

    public static boolean blocksGrab() {
        return armed || localTrack != null;
    }

    public static boolean blocksHighFive() {
        return armed || hLock || localTrack != null || isDown(chargeKey());
    }

    public static void addTurnVote(double deg) {
        if (Double.isFinite(deg)) {
            pendingTurn += deg;
        }
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BrosPayloads.State.ID, (p, ctx) -> {
            try {
                onState(p);
            } catch (Throwable var3) {
                fail("state packet", var3);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
            try {
                tick(mc);
            } catch (Throwable var2) {
                fail("tick", var2);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> resetAll());
        HudRenderCallback.EVENT.register((HudRenderCallback)(ctx, tickCounter) -> {
            try {
                renderHud(ctx);
            } catch (Throwable var3) {
            }
        });
    }

    private static void fail(String where, Throwable t) {
        System.err.println("[Bros] client " + where + " threw — resetting bros state");
        t.printStackTrace();
        resetAll();
    }

    private static void resetAll() {
        tracks.clear();
        localTrack = null;
        legDriven.clear();
        legDrivenNext.clear();
        armed = false;
        prevH = false;
        gDownSince = 0L;
        hDownSince = 0L;
        hLock = false;
        releasedSinceEngage = false;
        pendingTurn = 0.0;
    }

    private static void onState(BrosPayloads.State p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.world != null) {
            BrosClientHandler.Track t = tracks.get(p.idA());
            if (p.phase() == 0) {
                if (t != null && t.idB == p.idB()) {
                    removeTrack(t);
                }
            } else {
                if (t != null && t.idB == p.idB() && t.world == mc.world) {
                    double ex = p.cx() - t.cx;
                    double ez = p.cz() - t.cz;
                    float eh = MathHelper.wrapDegrees(p.heading() - t.h);
                    if (!(Math.hypot(ex, ez) > 1.0) && !(Math.abs(eh) > 30.0F)) {
                        t.corrX = ex;
                        t.corrZ = ez;
                        t.corrH = eh;
                    } else {
                        t.cx = p.cx();
                        t.cz = p.cz();
                        t.h = p.heading();
                        t.corrX = 0.0;
                        t.corrZ = 0.0;
                        t.corrH = 0.0F;
                    }

                    if (t.local) {
                        double sy = t.localIsA ? p.ya() : p.yb();
                        if (Math.abs(sy - t.localY) > 0.75) {
                            t.localY = sy;
                            t.localVy = 0.0;
                        }
                    }
                } else {
                    if (t != null) {
                        removeTrack(t);
                    }

                    t = new BrosClientHandler.Track();
                    t.idA = p.idA();
                    t.idB = p.idB();
                    t.aLeft = p.aLeft();
                    t.world = mc.world;
                    t.cx = p.cx();
                    t.cz = p.cz();
                    t.h = p.heading();
                    t.entryTicks = Math.max(1, p.entryTicks());
                    int me = mc.player.getId();
                    if (me == t.idA || me == t.idB) {
                        t.local = true;
                        t.localIsA = me == t.idA;
                        t.localPlayer = mc.player;
                        t.entryDone = p.phase() == 2;
                        t.entryStartX = mc.player.getX();
                        t.entryStartZ = mc.player.getZ();
                        t.entryStartYaw = mc.player.getYaw();
                        t.localY = mc.player.getY();
                        t.localPrevX = mc.player.getX();
                        t.localPrevY = mc.player.getY();
                        t.localPrevZ = mc.player.getZ();
                        t.camYawPrev = t.camYawNow = mc.player.getYaw();
                        if (localTrack != null && localTrack != t) {
                            tracks.remove(localTrack.idA);
                        }

                        localTrack = t;
                        armed = false;
                        releasedSinceEngage = false;
                        gHoldSent = false;
                        gPressAt = 0L;
                        hHoldSent = false;
                        hPressAt = 0L;
                        prevSneak = isDown(mc.options.sneakKey);
                        pendingTurn = 0.0;
                    }

                    tracks.put(t.idA, t);
                }

                t.phase = p.phase();
                t.vx = p.vx();
                t.vz = p.vz();
                t.turnRate = p.turnRate();
                t.shield = MathHelper.clamp(p.shield(), 0.0F, 1.0F);
                t.hud = p.hud();
                t.rushCd = p.rushCd();
                t.pulseCd = p.pulseCd();
                t.shieldMax = p.shieldMax();
                t.domeRadius = p.domeRadius();
                t.ticksSincePacket = 0;
            }
        }
    }

    private static void removeTrack(BrosClientHandler.Track t) {
        tracks.remove(t.idA);
        if (t == localTrack) {
            localTrack = null;
            pendingTurn = 0.0;
        }
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player != null && mc.world != null) {
            handleKeys(mc);
            legDrivenNext.clear();
            Iterator<BrosClientHandler.Track> it = tracks.values().iterator();

            while (it.hasNext()) {
                BrosClientHandler.Track t = it.next();
                boolean stale = ++t.ticksSincePacket > 10;
                boolean wrongWorld = t.world != mc.world;
                boolean respawned = t.local && t.localPlayer != mc.player;
                if (!stale && !wrongWorld && !respawned) {
                    integrate(t);
                    if (t.local) {
                        if (t.phase == 2) {
                            sendInput(mc);
                        } else {
                            pendingTurn = 0.0;
                        }
                    }

                    place(mc, t);
                    spawnDome(mc, t);
                } else {
                    if (t == localTrack) {
                        localTrack = null;
                        pendingTurn = 0.0;
                    }

                    it.remove();
                }
            }

            legDriven.clear();
            legDriven.addAll(legDrivenNext);
        } else {
            if (!tracks.isEmpty() || armed) {
                resetAll();
            }
        }
    }

    private static void handleKeys(MinecraftClient mc) {
        boolean screen = mc.currentScreen != null;
        boolean g = !screen && isDown(chargeKey());
        boolean h = !screen && isDown(handKey());
        long now = System.currentTimeMillis();
        if (g) {
            if (gDownSince == 0L) {
                gDownSince = now;
            }
        } else {
            gDownSince = 0L;
        }

        if (h) {
            if (hDownSince == 0L) {
                hDownSince = now;
            }
        } else {
            hDownSince = 0L;
        }

        if (!h) {
            hLock = false;
        }

        boolean sneak = !screen && isDown(mc.options.sneakKey);
        if (localTrack != null) {
            boolean gate = releasedSinceEngage;
            if (!g && !h) {
                releasedSinceEngage = true;
            }

            if (h) {
                hLock = true;
            }

            if (sneak && !prevSneak) {
                ClientPlayNetworking.send(BrosPayloads.End.INSTANCE);
            } else if (gate) {
                if (h && !prevH) {
                    hPressAt = now;
                    hHoldSent = false;
                }

                if (h && hPressAt != 0L && !hHoldSent && now - hPressAt >= 300L) {
                    send((byte)3, true);
                    hHoldSent = true;
                }

                if (!h && prevH && hPressAt != 0L) {
                    if (hHoldSent) {
                        send((byte)3, false);
                    } else {
                        send((byte)1, true);
                    }

                    hHoldSent = false;
                    hPressAt = 0L;
                }

                if (g && !prevG) {
                    gPressAt = now;
                    gHoldSent = false;
                }

                if (g && gPressAt != 0L && !gHoldSent && now - gPressAt >= 300L) {
                    send((byte)2, true);
                    gHoldSent = true;
                }

                if (!g && prevG && gPressAt != 0L) {
                    if (gHoldSent) {
                        send((byte)2, false);
                    } else {
                        send((byte)0, true);
                    }

                    gHoldSent = false;
                    gPressAt = 0L;
                }
            }
        } else if (!armed) {
            if (g && h && hDownSince >= gDownSince - 400L && now - gDownSince >= 250L) {
                armed = true;
                debug(mc, "§aARMED §7— walk to your bro");
                ChargedDapClientHandler.cancelChargeForBros();
                ClientPlayNetworking.send(new BrosPayloads.Arm(true));
            }
        } else if (!g || !h) {
            armed = false;
            debug(mc, "§7disarmed (" + (!g ? "G" : "H") + " released)");
            ClientPlayNetworking.send(new BrosPayloads.Arm(false));
        } else if (DEBUG && mc.player != null && mc.player.age % 20 == 0) {
            debug(mc, "§earmed, waiting for a bro");
        }

        if (DEBUG && !armed && localTrack == null && g && mc.player != null && mc.player.age % 20 == 0) {
            debug(mc, "§7G held " + (now - gDownSince) + "ms · H " + (h ? "§adown" : "§cup"));
        }

        prevH = h;
        prevG = g;
        prevSneak = sneak;
    }

    private static void debug(MinecraftClient mc, String text) {
        if (DEBUG && mc.player != null) {
            mc.player.sendMessage(Text.literal("§8[bros] " + text), true);
        }
    }

    private static void send(byte kind, boolean down) {
        ClientPlayNetworking.send(new BrosPayloads.Ability(kind, down));
    }

    private static void sendInput(MinecraftClient mc) {
        float f = 0.0F;
        float r = 0.0F;
        boolean sprint = false;
        if (mc.currentScreen == null) {
            if (isDown(mc.options.forwardKey)) {
                f++;
            }

            if (isDown(mc.options.backKey)) {
                f--;
            }

            if (isDown(mc.options.rightKey)) {
                r++;
            }

            if (isDown(mc.options.leftKey)) {
                r--;
            }

            sprint = isDown(mc.options.sprintKey);
        }

        float turn = (float)(pendingTurn * 1.0);
        pendingTurn = 0.0;
        ClientPlayNetworking.send(new BrosPayloads.Input(f, r, turn, sprint));
    }

    private static KeyBinding chargeKey() {
        return ChargedDapClientHandler.getChargedDapKey();
    }

    private static KeyBinding handKey() {
        return HighFiveClientHandler.getHighFiveKey();
    }

    private static boolean isDown(KeyBinding kb) {
        if (kb == null) {
            return false;
        } else {
            MinecraftClient mc = MinecraftClient.getInstance();
            int code = KeyBindingHelper.getBoundKeyOf(kb).getCode();
            if (code < 0) {
                return false;
            } else {
                long win = mc.getWindow().getHandle();
                return code <= 7 ? GLFW.glfwGetMouseButton(win, code) == 1 : GLFW.glfwGetKey(win, code) == 1;
            }
        }
    }

    private static void integrate(BrosClientHandler.Track t) {
        double dcx = t.corrX * 0.35;
        double dcz = t.corrZ * 0.35;
        float dch = (float)(t.corrH * 0.35);
        t.corrX -= dcx;
        t.corrZ -= dcz;
        t.corrH -= dch;
        t.cx = t.cx + (t.vx + dcx);
        t.cz = t.cz + (t.vz + dcz);
        t.h = MathHelper.wrapDegrees(t.h + t.turnRate + dch);
    }

    private static void place(MinecraftClient mc, BrosClientHandler.Track t) {
        Entity ea = mc.world.getEntityById(t.idA);
        Entity eb = mc.world.getEntityById(t.idB);
        double[] pa = BrosHandler.slotAt(t.cx, t.cz, t.h, t.aLeft);
        double[] pb = BrosHandler.slotAt(t.cx, t.cz, t.h, !t.aLeft);
        if (t.local && !t.entryDone) {
            t.entryTick++;
            double k = BrosHandler.smooth(Math.min(1.0, (double)t.entryTick / t.entryTicks));
            double[] target = t.localIsA ? pa : pb;
            double x = MathHelper.lerp(k, t.entryStartX, target[0]);
            double z = MathHelper.lerp(k, t.entryStartZ, target[1]);
            float yaw = t.entryStartYaw + MathHelper.wrapDegrees(t.h - t.entryStartYaw) * (float)k;
            writeLocal(mc.player, t, x, z, yaw);
            if (t.entryTick >= t.entryTicks) {
                t.entryDone = true;
            }
        } else if (t.phase == 2 || t.local) {
            if (ea != null) {
                if (ea == mc.player) {
                    writeLocal(mc.player, t, pa[0], pa[1], t.h);
                } else if (t.phase == 2) {
                    writeRemote(ea, t.havePrev ? t.prevAX : ea.getX(), t.havePrev ? t.prevAZ : ea.getZ(), pa[0], pa[1], t);
                }
            }

            if (eb != null) {
                if (eb == mc.player) {
                    writeLocal(mc.player, t, pb[0], pb[1], t.h);
                } else if (t.phase == 2) {
                    writeRemote(eb, t.havePrev ? t.prevBX : eb.getX(), t.havePrev ? t.prevBZ : eb.getZ(), pb[0], pb[1], t);
                }
            }

            t.prevAX = pa[0];
            t.prevAZ = pa[1];
            t.prevBX = pb[0];
            t.prevBZ = pb[1];
            t.prevH = t.h;
            t.havePrev = true;
        }
    }

    private static void writeLocal(ClientPlayerEntity p, BrosClientHandler.Track t, double x, double z, float yaw) {
        double prevX = t.localPrevX;
        double prevY = t.localPrevY;
        double prevZ = t.localPrevZ;
        BrosPhysics.Result r = BrosPhysics.resolve(p.getWorld(), p, x, z, t.localY, t.localVy, false);
        t.localY = r.y();
        t.localVy = r.vy();
        p.setPosition(x, t.localY, z);
        p.setVelocity(Vec3d.ZERO);
        p.prevX = prevX;
        p.prevY = prevY;
        p.prevZ = prevZ;
        p.lastRenderX = prevX;
        p.lastRenderY = prevY;
        p.lastRenderZ = prevZ;
        t.localPrevX = x;
        t.localPrevY = t.localY;
        t.localPrevZ = z;
        driveLegs(p, Math.hypot(x - prevX, z - prevZ));
        t.camYawPrev = t.camYawNow;
        t.camYawNow = yaw;
    }

    private static void writeRemote(Entity e, double prevX, double prevZ, double x, double z, BrosClientHandler.Track t) {
        e.setPosition(x, e.getY(), z);
        e.prevX = prevX;
        e.prevZ = prevZ;
        e.lastRenderX = prevX;
        e.lastRenderZ = prevZ;
        if (e instanceof LivingEntity le) {
            driveLegs(le, Math.hypot(x - prevX, z - prevZ));
        }

        float prevYaw = t.havePrev ? t.prevH : t.h;
        e.setYaw(t.h);
        e.prevYaw = prevYaw;
        if (e instanceof LivingEntity le) {
            le.setHeadYaw(t.h);
            le.setBodyYaw(t.h);
            le.prevHeadYaw = prevYaw;
            le.prevBodyYaw = prevYaw;
        }
    }

    private static void driveLegs(LivingEntity e, double movedThisTick) {
        float target = Math.min((float)movedThisTick * 4.0F, 1.0F) * 1.0F;
        e.limbAnimator.updateLimbs(target, 0.4F);
        legDrivenNext.add(e.getId());
    }

    private static void renderHud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        BrosClientHandler.Track t = localTrack;
        if (t != null && mc.player != null && !mc.options.hudHidden) {
            if (t.phase == 2) {
                TextRenderer tr = mc.textRenderer;
                int cx = ctx.getScaledWindowWidth() / 2;
                int bottom = ctx.getScaledWindowHeight() - 52;
                boolean filling = (t.hud & 64) != 0;
                int sx = 0;
                int sy = 0;
                if (filling) {
                    sx = mc.world.random.nextInt(3) - 1;
                    sy = mc.world.random.nextInt(3) - 1;
                }

                float hearts = t.shield * t.shieldMax / 2.0F;
                String label = fmtHearts(hearts) + "❤";
                int barW = 60;
                int barH = 3;
                int rowW = tr.getWidth(label) + 4 + barW;
                int x = cx - rowW / 2 + sx;
                int y = bottom - 20 + sy;
                boolean bunkered = (t.hud & 2048) != 0;
                int shieldColor = bunkered ? -1 : -9699462;
                ctx.drawText(tr, label, x, y, shieldColor, true);
                int bx = x + tr.getWidth(label) + 4;
                int by = y + 3;
                ctx.fill(bx - 1, by - 1, bx + barW + 1, by + barH + 1, -2013265920);
                int fillW = Math.round(barW * t.shield);
                if (fillW > 0) {
                    ctx.fill(bx, by, bx + fillW, by + barH, shieldColor);
                }

                String rush = "[" + keyName(chargeKey(), "G") + "] RUSH";
                String pulse = "[" + keyName(handKey(), "H") + "] PULSE";
                boolean pulseAffordable = (t.hud & 512) != 0;
                int gap = 12;
                int x2 = cx - (tr.getWidth(rush) + gap + tr.getWidth(pulse)) / 2;
                int y2 = bottom - 8;
                boolean rushAffordable = (t.hud & 1024) != 0;
                ctx.drawText(tr, rush, x2, y2, t.rushCd == 0 && rushAffordable ? -9699462 : -7697782, true);
                ctx.drawText(tr, pulse, x2 + tr.getWidth(rush) + gap, y2, t.pulseCd == 0 && pulseAffordable ? -9699462 : -7697782, true);
            }
        }
    }

    private static String keyName(KeyBinding kb, String fallback) {
        if (kb == null) {
            return fallback;
        } else {
            try {
                String n = KeyBindingHelper.getBoundKeyOf(kb).getLocalizedText().getString();
                return n.isEmpty() ? fallback : n.toUpperCase();
            } catch (Throwable var3) {
                return fallback;
            }
        }
    }

    private static String fmtHearts(float h) {
        return Math.abs(h - Math.round(h)) < 0.05F ? String.valueOf(Math.round(h)) : String.format("%.1f", h);
    }

    private static void spawnDome(MinecraftClient mc, BrosClientHandler.Track t) {
        if (mc.world != null) {
            Entity ea = mc.world.getEntityById(t.idA);
            Entity eb = mc.world.getEntityById(t.idB);
            double baseY;
            if (ea != null && eb != null) {
                baseY = Math.min(ea.getY(), eb.getY());
            } else if (ea != null) {
                baseY = ea.getY();
            } else {
                if (eb == null) {
                    return;
                }

                baseY = eb.getY();
            }

            int color = lerpColor(16728128, 5629695, t.shield);
            DustParticleEffect dust = new DustParticleEffect(new Vector3f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F), 0.9F);
            t.domeSpin += 0.09;

            for (int i = 0; i < 8; i++) {
                double a = t.domeSpin + (Math.PI * 2) * i / 8.0;
                double y = baseY + 0.1 + i % 3 * 1.35;
                double r = t.domeRadius * (i % 3 == 2 ? 0.8 : 1.0);
                mc.world.addParticle(dust, t.cx + Math.cos(a) * r, y, t.cz + Math.sin(a) * r, t.vx, 0.0, t.vz);
            }
        }
    }

    private static int lerpColor(int from, int to, float k) {
        int r = MathHelper.lerp(k, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int g = MathHelper.lerp(k, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int b = MathHelper.lerp(k, from & 0xFF, to & 0xFF);
        return r << 16 | g << 8 | b;
    }

    public static void applyLocalYaw() {
        BrosClientHandler.Track t = localTrack;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (t != null && mc.player != null && t.localPlayer == mc.player) {
            float td = com.cooptest.fabricshim.mc.RenderTickCounter.current().getTickDelta(true);
            float yaw = t.camYawPrev + MathHelper.wrapDegrees(t.camYawNow - t.camYawPrev) * td;
            ClientPlayerEntity p = mc.player;
            p.setYaw(yaw);
            p.prevYaw = yaw;
            p.setHeadYaw(yaw);
            p.prevHeadYaw = yaw;
            p.setBodyYaw(yaw);
            p.prevBodyYaw = yaw;
        }
    }

    private static final class Track {
        int idA;
        int idB;
        ClientWorld world;
        boolean aLeft;
        byte phase;
        double cx;
        double cz;
        double vx;
        double vz;
        float h;
        float turnRate;
        float shield = 1.0F;
        float shieldMax = 40.0F;
        float domeRadius = 2.2F;
        int hud;
        int rushCd;
        int pulseCd;
        double domeSpin;
        double corrX;
        double corrZ;
        float corrH;
        int ticksSincePacket;
        boolean local;
        boolean localIsA;
        ClientPlayerEntity localPlayer;
        boolean entryDone;
        int entryTick;
        int entryTicks;
        double entryStartX;
        double entryStartZ;
        float entryStartYaw;
        double localY;
        double localVy;
        double localPrevX;
        double localPrevY;
        double localPrevZ;
        boolean havePrev;
        double prevAX;
        double prevAZ;
        double prevBX;
        double prevBZ;
        float prevH;
        float camYawPrev;
        float camYawNow;
    }
}
