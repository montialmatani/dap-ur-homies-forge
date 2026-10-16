package com.cooptest.client.anim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Single-layer animation controller with the subset of Player Animation Library's
 * PlayerAnimationController behaviour this mod relies on: triggerAnimation / stop,
 * PLAY_ONCE / LOOP / HOLD_ON_LAST_FRAME, and first-person mode configuration.
 */
public class PlayerAnimationController {
    public enum State { RUNNING, PAUSED, STOPPED }

    @FunctionalInterface
    public interface AnimationStateHandler {
        PlayState handle(PlayerAnimationController controller, Object state, Object animationSetter);
    }

    /** Final transform for one bone, in PAL's bone space (radians / pixels / scale). */
    public static final class Bone {
        public float posX, posY, posZ, rotX, rotY, rotZ, scaleX = 1, scaleY = 1, scaleZ = 1;
        boolean posXOn, posYOn, posZOn, rotXOn, rotYOn, rotZOn, scaleXOn, scaleYOn, scaleZOn;

        void reset() {
            posX = posY = posZ = rotX = rotY = rotZ = 0;
            scaleX = scaleY = scaleZ = 1;
        }

        void setEnabled(boolean on) {
            posXOn = posYOn = posZOn = rotXOn = rotYOn = rotZOn = scaleXOn = scaleYOn = scaleZOn = on;
        }

        boolean anyEnabled() {
            return posXOn || posYOn || posZOn || rotXOn || rotYOn || rotZOn || scaleXOn || scaleYOn || scaleZOn;
        }
    }

    private static final String[] BONE_NAMES = {"body", "right_arm", "left_arm", "right_leg", "left_leg", "head", "torso", "right_item", "left_item", "cape", "elytra"};

    protected final AbstractClientPlayerEntity player;
    private final AnimationStateHandler stateHandler;
    private final Map<String, Bone> bones = new HashMap<>();

    private AnimData current;
    private AnimData triggered;
    private State state = State.STOPPED;
    private int tick;
    private float startAnimFrom;
    private FirstPersonMode firstPersonMode = FirstPersonMode.NONE;
    private FirstPersonConfiguration firstPersonConfiguration = FirstPersonConfiguration.DEFAULT;

    public PlayerAnimationController(AbstractClientPlayerEntity player, AnimationStateHandler stateHandler) {
        this.player = player;
        this.stateHandler = stateHandler;
        for (String n : BONE_NAMES) bones.put(n, new Bone());
    }

    public AbstractClientPlayerEntity getPlayer() {
        return player;
    }

    // ---- public API used by the mod ----

    public boolean triggerAnimation(Identifier id) {
        return triggerAnimation(id, 0);
    }

    public boolean triggerAnimation(Identifier id, float startAnimFrom) {
        AnimData anim = AnimationRegistry.get(id);
        if (anim == null) {
            AnimationRegistry.LOGGER.error("Could not find animation with the name:{}", id);
            return false;
        }
        this.triggered = anim;
        this.current = anim;
        this.tick = 0;
        this.startAnimFrom = startAnimFrom;
        this.state = State.RUNNING;
        setupNewAnimation();
        return true;
    }

    public void stop() {
        this.state = State.STOPPED;
    }

    public void pause() {
        this.state = State.PAUSED;
    }

    public boolean isActive() {
        return state != State.STOPPED;
    }

    public State getAnimationState() {
        return state;
    }

    public boolean hasAnimationFinished() {
        return current != null && state == State.STOPPED;
    }

    public FirstPersonMode getFirstPersonMode() {
        return firstPersonMode;
    }

    public void setFirstPersonMode(FirstPersonMode mode) {
        this.firstPersonMode = mode == null ? FirstPersonMode.NONE : mode;
    }

    public FirstPersonConfiguration getFirstPersonConfiguration() {
        return firstPersonConfiguration;
    }

    public void setFirstPersonConfiguration(FirstPersonConfiguration config) {
        this.firstPersonConfiguration = config == null ? FirstPersonConfiguration.DEFAULT : config;
    }

    // ---- driven by CoopAnimLib ----

    /** Called once per client tick. */
    void tick() {
        handleAnimation();
        if (state == State.RUNNING) tick++;
    }

    /** Mirrors PAL's handleAnimation: a finished triggered animation hands control back to the state handler. */
    private void handleAnimation() {
        if (triggered != null) {
            if (!hasAnimationFinished()) return;
            triggered = null;
        }
        PlayState ps = stateHandler == null ? PlayState.STOP : stateHandler.handle(this, null, null);
        if (ps == PlayState.STOP) state = State.STOPPED;
    }

    /** Called every render frame before the model is posed. */
    void process(float partialTick) {
        float adjustedTick = Math.max(0.0F, partialTick + startAnimFrom + tick);
        handleAnimation();
        if (current == null) {
            state = State.STOPPED;
            return;
        }
        if (state != State.RUNNING) return;

        AnimData anim = current;
        if (adjustedTick >= anim.length) {
            switch (anim.loop) {
                case LOOP -> {
                    tick = 0;
                    startAnimFrom = -partialTick;
                    adjustedTick = 0;
                }
                case HOLD_ON_LAST_FRAME -> state = State.PAUSED; // bones get the final frame below, then freeze
                case PLAY_ONCE -> {
                    state = State.STOPPED;
                    for (Bone b : bones.values()) b.reset();
                    return;
                }
            }
        }

        for (Bone b : bones.values()) b.reset();
        for (Map.Entry<String, AnimData.BoneAnim> e : anim.bones.entrySet()) {
            Bone bone = bones.get(e.getKey());
            if (bone == null) continue;
            AnimData.BoneAnim ba = e.getValue();
            bone.rotX = sample(ba.rotation().x(), adjustedTick, 0);
            bone.rotY = sample(ba.rotation().y(), adjustedTick, 0);
            bone.rotZ = sample(ba.rotation().z(), adjustedTick, 0);
            bone.posX = sample(ba.position().x(), adjustedTick, 0);
            bone.posY = sample(ba.position().y(), adjustedTick, 0);
            bone.posZ = sample(ba.position().z(), adjustedTick, 0);
            bone.scaleX = sample(ba.scale().x(), adjustedTick, 1);
            bone.scaleY = sample(ba.scale().y(), adjustedTick, 1);
            bone.scaleZ = sample(ba.scale().z(), adjustedTick, 1);
        }
    }

    private void setupNewAnimation() {
        for (Map.Entry<String, Bone> e : bones.entrySet()) {
            e.getValue().setEnabled(current != null && current.bones.containsKey(e.getKey()));
            e.getValue().reset();
        }
    }

    private static float sample(List<AnimData.Keyframe> frames, float tick, float def) {
        if (frames.isEmpty()) return def;
        float total = 0;
        AnimData.Keyframe frame = null;
        float localTick = tick;
        for (AnimData.Keyframe k : frames) {
            total += k.length();
            if (total > tick) {
                frame = k;
                localTick = tick - (total - k.length());
                break;
            }
        }
        if (frame == null) {
            frame = frames.get(frames.size() - 1);
            localTick = tick;
        }
        return interpolate(frame, localTick);
    }

    private static float interpolate(AnimData.Keyframe k, float currentTick) {
        float length = k.length();
        if (k.catmull()) {
            if (currentTick >= length) return k.end();
            float t = currentTick / length;
            return catmullRom(t, k.before(), k.start(), k.end(), k.after());
        }
        float t = currentTick / length;
        if (t >= 1) return k.end();
        if (Float.isNaN(t)) return k.start();
        return k.start() + (k.end() - k.start()) * t;
    }

    private static float catmullRom(float delta, float p0, float p1, float p2, float p3) {
        return 0.5f * (2f * p1 + (p2 - p0) * delta
                + (2f * p0 - 5f * p1 + 4f * p2 - p3) * delta * delta
                + (3f * p1 - p0 - 3f * p2 + p3) * delta * delta * delta);
    }

    /**
     * Writes this controller's transform for the named bone onto {@code target} for every axis the
     * current animation drives (PAL's copyOtherBoneIfNotDisabled). Returns false if the bone isn't animated.
     */
    boolean apply(String name, Bone target) {
        if (!isActive()) return false;
        Bone b = bones.get(name);
        if (b == null || !b.anyEnabled()) return false;
        if (b.posXOn) target.posX = b.posX;
        if (b.posYOn) target.posY = b.posY;
        if (b.posZOn) target.posZ = b.posZ;
        if (b.rotXOn) target.rotX = b.rotX;
        if (b.rotYOn) target.rotY = b.rotY;
        if (b.rotZOn) target.rotZ = b.rotZ;
        if (b.scaleXOn) target.scaleX = b.scaleX;
        if (b.scaleYOn) target.scaleY = b.scaleY;
        if (b.scaleZOn) target.scaleZ = b.scaleZ;
        return true;
    }
}
