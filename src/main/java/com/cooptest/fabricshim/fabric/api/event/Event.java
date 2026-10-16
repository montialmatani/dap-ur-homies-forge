package com.cooptest.fabricshim.fabric.api.event;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
/** Minimal stand-in for Fabric's Event: a listener list that the Forge bridge iterates. */
public final class Event<T> {
    private final List<T> listeners = new CopyOnWriteArrayList<>();
    public void register(T listener) { listeners.add(listener); }
    public List<T> listeners() { return listeners; }
}
