package com.cooptest.client.anim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads every assets/<ns>/player_animations/*.json, keyed as <ns>:<animation name>, like PAL does. */
public final class AnimationRegistry implements SynchronousResourceReloader {
    static final Logger LOGGER = LoggerFactory.getLogger("coopmoves/anim");
    private static volatile Map<Identifier, AnimData> animations = Map.of();

    public static AnimData get(Identifier id) {
        return animations.get(id);
    }

    public static boolean has(Identifier id) {
        return animations.containsKey(id);
    }

    @Override
    public void reload(ResourceManager manager) {
        Map<Identifier, AnimData> out = new HashMap<>();
        for (Map.Entry<Identifier, Resource> e : manager.findResources("player_animations", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = e.getKey();
            try (Reader reader = e.getValue().getReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has("animations")) continue;
                for (Map.Entry<String, JsonElement> a : root.getAsJsonObject("animations").entrySet()) {
                    try {
                        out.put(new Identifier(file.getNamespace(), a.getKey()), AnimData.parse(a.getKey(), a.getValue().getAsJsonObject()));
                    } catch (Exception ex) {
                        LOGGER.error("Unable to parse animation {} in {}", a.getKey(), file, ex);
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("Failed to load player animation file {}", file, ex);
            }
        }
        animations = out;
        LOGGER.info("Loaded {} player animations", out.size());
    }
}
