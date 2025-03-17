package rynnavinx.sspb.neoforge.services;

import net.neoforged.fml.loading.FMLPaths;

import rynnavinx.sspb.common.services.IPlatformHelper;

import java.nio.file.Path;


public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
