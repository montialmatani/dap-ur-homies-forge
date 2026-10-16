package com.cooptest.forge;

import com.cooptest.TestCoop;
import com.cooptest.fabricshim.forge.CoopNetwork;
import com.cooptest.fabricshim.forge.ForgeBridge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(CoopMovesForge.MOD_ID)
public final class CoopMovesForge {
    public static final String MOD_ID = "coopmoves";
    public static IEventBus MOD_BUS;

    public CoopMovesForge() {
        MOD_BUS = FMLJavaModLoadingContext.get().getModEventBus();
        CoopRegistries.register(MOD_BUS);
        CoopNetwork.init();
        ForgeBridge.register(MOD_BUS);
        new TestCoop().onInitialize();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CoopMovesForgeClient.init(MOD_BUS);
        }
    }
}
