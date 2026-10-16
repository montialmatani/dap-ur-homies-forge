package com.cooptest.fabricshim.loader.api;
import java.nio.file.Path;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
public final class FabricLoader {
    private static final FabricLoader INSTANCE = new FabricLoader();
    public static FabricLoader getInstance() { return INSTANCE; }
    public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
    public Path getGameDir() { return FMLPaths.GAMEDIR.get(); }
    public boolean isModLoaded(String id) { return ModList.get() != null && ModList.get().isLoaded(id); }
    public boolean isDevelopmentEnvironment() { return !FMLLoader.isProduction(); }
}
