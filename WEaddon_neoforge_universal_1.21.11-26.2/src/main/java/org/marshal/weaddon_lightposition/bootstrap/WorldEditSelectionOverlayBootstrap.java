package org.marshal.weaddon_lightposition.bootstrap;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.enginehub.worldeditcui.neoforge.protocol.BundledProtocolBootstrap;

import java.lang.reflect.InvocationTargetException;

@Mod(WorldEditSelectionOverlayBootstrap.MOD_ID)
public final class WorldEditSelectionOverlayBootstrap {
    public static final String MOD_ID = "worldedit_selection_overlay";

    public WorldEditSelectionOverlayBootstrap(IEventBus modEventBus) {
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            return;
        }

        RuntimeSelector.Target target = RuntimeSelector.current();
        BundledProtocolBootstrap.initialize(modEventBus);
        String initializerName = target.runtimePackage() + ".client.WorldEditSelectionOverlayClient";
        try {
            Class<?> initializer = Class.forName(initializerName, true, getClass().getClassLoader());
            initializer.getMethod("initialize", IEventBus.class).invoke(null, modEventBus);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Unable to initialize " + target + " runtime", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Unable to initialize " + target + " runtime", cause);
        }
    }

}
