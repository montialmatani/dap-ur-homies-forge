package com.cooptest.fabricshim.fabric.api.client.keybinding.v1;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
public final class KeyBindingHelper {
    public static final List<KeyBinding> PENDING = new CopyOnWriteArrayList<>();
    public static KeyBinding registerKeyBinding(KeyBinding keyBinding) { PENDING.add(keyBinding); return keyBinding; }
    public static InputUtil.Key getBoundKeyOf(KeyBinding keyBinding) { return keyBinding.getKey(); }
}
