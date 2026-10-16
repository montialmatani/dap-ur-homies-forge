package com.cooptest.forge;

import com.cooptest.TestCoopClient;
import com.cooptest.fabricshim.forge.ForgeClientBridge;
import com.cooptest.client.anim.CoopAnimLib;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client entry point: only touched when running on the physical client. */
final class CoopMovesForgeClient {
    static void init(IEventBus modBus) {
        ForgeClientBridge.register(modBus);
        CoopAnimLib.init(modBus);
        new TestCoopClient().onInitializeClient();
    }
}
