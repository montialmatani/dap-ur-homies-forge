package com.cooptest.fabricshim.loader.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Same idea as Fabric Loader's ObjectShare: a global key/value map for loose cross-mod hooks. */
public final class ObjectShare {
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    public Object get(String key) { return values.get(key); }

    public Object put(String key, Object value) { return values.put(key, value); }

    public Object remove(String key) { return values.remove(key); }
}
