package com.cooptest.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A baked Blockbench/Bedrock player animation, parsed with the same rules Player Animation Library
 * (ZigyTheBird, MIT) uses for numeric keyframes: seconds -> ticks, degrees -> radians for rotation,
 * "pre"/"post" keys, catmull-rom spline arguments taken from the neighbouring keyframes.
 */
public final class AnimData {
    public enum Loop { PLAY_ONCE, LOOP, HOLD_ON_LAST_FRAME }
    enum Type { ROTATION, POSITION, SCALE }

    /** One keyframe segment for one axis. Values are already in radians (rotation) or pixels (position). */
    record Keyframe(float length, float start, float end, boolean catmull, float before, float after) {}

    /** Keyframes for the three axes of one channel. Empty list = axis not animated. */
    record Channel(List<Keyframe> x, List<Keyframe> y, List<Keyframe> z) {
        static final Channel EMPTY = new Channel(List.of(), List.of(), List.of());
        float lastKeyframeTime() {
            return Math.max(sum(x), Math.max(sum(y), sum(z)));
        }
        private static float sum(List<Keyframe> l) {
            float t = 0;
            for (Keyframe k : l) t += k.length();
            return t;
        }
    }

    record BoneAnim(Channel rotation, Channel position, Channel scale) {}

    private static final Pattern UPPER = Pattern.compile("([A-Z])");

    public final String name;
    public final float length;
    public final Loop loop;
    public final Map<String, BoneAnim> bones;

    private AnimData(String name, float length, Loop loop, Map<String, BoneAnim> bones) {
        this.name = name;
        this.length = length;
        this.loop = loop;
        this.bones = bones;
    }

    static String boneName(String raw) {
        return UPPER.matcher(raw).replaceAll("_$1").toLowerCase(Locale.ROOT);
    }

    static AnimData parse(String name, JsonObject obj) {
        Map<String, BoneAnim> bones = new HashMap<>();
        JsonObject bonesObj = obj.has("bones") ? obj.getAsJsonObject("bones") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : bonesObj.entrySet()) {
            JsonObject b = e.getValue().getAsJsonObject();
            bones.put(boneName(e.getKey()), new BoneAnim(
                    channel(b.get("rotation"), Type.ROTATION),
                    channel(b.get("position"), Type.POSITION),
                    channel(b.get("scale"), Type.SCALE)));
        }
        float length;
        if (obj.has("animation_length")) {
            length = obj.get("animation_length").getAsFloat() * 20f;
        } else {
            length = 0;
            for (BoneAnim b : bones.values()) {
                length = Math.max(length, b.rotation().lastKeyframeTime());
                length = Math.max(length, b.position().lastKeyframeTime());
                length = Math.max(length, b.scale().lastKeyframeTime());
            }
            if (length == 0) length = Float.MAX_VALUE;
        }
        return new AnimData(name, length, loop(obj.get("loop")), bones);
    }

    private static Loop loop(JsonElement e) {
        if (e == null || e.isJsonNull()) return Loop.PLAY_ONCE;
        if (e instanceof JsonPrimitive p) {
            if (p.isBoolean()) return p.getAsBoolean() ? Loop.LOOP : Loop.PLAY_ONCE;
            String s = p.getAsString();
            if (s.equals("true") || s.equals("loop")) return Loop.LOOP;
            if (s.equals("hold_on_last_frame")) return Loop.HOLD_ON_LAST_FRAME;
        }
        return Loop.PLAY_ONCE;
    }

    /** (timestamp seconds, vector, easing-is-catmull) */
    private record RawFrame(float time, JsonArray vector, boolean catmull) {}

    private static Channel channel(JsonElement element, Type type) {
        List<RawFrame> frames = rawFrames(element);
        if (frames.isEmpty()) return Channel.EMPTY;
        return new Channel(axis(frames, 0, type), axis(frames, 1, type), axis(frames, 2, type));
    }

    private static List<RawFrame> rawFrames(JsonElement element) {
        List<RawFrame> list = new ArrayList<>();
        if (element == null) return list;
        if (element instanceof JsonPrimitive p) {
            JsonArray a = new JsonArray();
            a.add(p); a.add(p); a.add(p);
            list.add(new RawFrame(0, a, false));
            return list;
        }
        if (element instanceof JsonArray a) {
            list.add(new RawFrame(0, a, false));
            return list;
        }
        JsonObject obj = element.getAsJsonObject();
        if (obj.has("vector")) {
            list.add(new RawFrame(0, obj.getAsJsonArray("vector"), isCatmull(obj, "easing")));
            return list;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            float t;
            try {
                t = Float.parseFloat(entry.getKey());
            } catch (NumberFormatException ex) {
                t = 0;
            }
            JsonElement v = entry.getValue();
            if (v instanceof JsonArray a) {
                list.add(new RawFrame(t, a, false));
            } else if (v instanceof JsonObject kf) {
                if (kf.has("vector")) {
                    list.add(new RawFrame(t, kf.getAsJsonArray("vector"), isCatmull(kf, "easing") || isCatmull(kf, "lerp_mode")));
                    continue;
                }
                // Blockbench "pre"/"post" keyframe
                if (kf.has("pre")) {
                    list.add(new RawFrame(t == 0 ? t : t - 0.001f, extract(kf.get("pre")), false));
                }
                if (kf.has("post")) {
                    list.add(new RawFrame(t, extract(kf.get("post")), isCatmull(kf, "lerp_mode")));
                }
            }
        }
        return list;
    }

    private static boolean isCatmull(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && "catmullrom".equals(o.get(key).getAsString());
    }

    private static JsonArray extract(JsonElement e) {
        if (e.isJsonArray()) return e.getAsJsonArray();
        if (e.isJsonPrimitive()) {
            JsonArray a = new JsonArray();
            a.add(e.getAsFloat()); a.add(0); a.add(0);
            return a;
        }
        JsonObject o = e.getAsJsonObject();
        if (o.has("vector")) return o.getAsJsonArray("vector");
        if (o.has("pre")) return o.getAsJsonArray("pre");
        return o.getAsJsonArray("post");
    }

    private static float value(JsonArray vec, int axis, Type type) {
        float v;
        if (axis >= vec.size()) {
            v = type == Type.SCALE ? 1 : 0;
        } else {
            JsonElement el = vec.get(axis);
            try {
                v = el.getAsFloat();
            } catch (Exception ex) {
                v = type == Type.SCALE ? 1 : 0; // molang expressions are not used by this mod's animations
            }
        }
        return type == Type.ROTATION ? (float) Math.toRadians(v) : v;
    }

    private static List<Keyframe> axis(List<RawFrame> frames, int axis, Type type) {
        List<Keyframe> out = new ArrayList<>();
        Float prev = null;
        float prevTime = 0;
        for (RawFrame f : frames) {
            float v = value(f.vector(), axis, type);
            out.add(new Keyframe((f.time() - prevTime) * 20f, prev == null ? v : prev, v, f.catmull(), 0, 0));
            prev = v;
            prevTime = f.time();
        }
        // A single non-linear keyframe degrades to linear; catmull frames get their spline anchors.
        if (out.size() == 1) {
            Keyframe k = out.get(0);
            out.set(0, new Keyframe(k.length(), k.start(), k.end(), false, 0, 0));
            return out;
        }
        for (int i = 0; i < out.size(); i++) {
            Keyframe k = out.get(i);
            if (k.catmull()) {
                float before = i == 0 ? k.start() : out.get(i - 1).end();
                float after = i + 1 >= out.size() ? k.end() : out.get(i + 1).end();
                out.set(i, new Keyframe(k.length(), k.start(), k.end(), true, before, after));
            }
        }
        return out;
    }
}
