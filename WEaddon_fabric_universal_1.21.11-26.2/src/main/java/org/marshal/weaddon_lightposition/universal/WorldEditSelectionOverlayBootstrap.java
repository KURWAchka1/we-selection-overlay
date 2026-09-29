package org.marshal.weaddon_lightposition.universal;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

import java.lang.reflect.InvocationTargetException;

public final class WorldEditSelectionOverlayBootstrap implements ModInitializer, ClientModInitializer {
    private static boolean commonInitialized;
    private static boolean clientInitialized;

    @Override
    public void onInitialize() {
        if (commonInitialized) {
            return;
        }
        commonInitialized = true;

        RuntimeTarget target = RuntimeTarget.current();
        invoke(target.applicationPackage() + ".WorldEditSelectionOverlayMod", "onInitialize");
    }

    @Override
    public void onInitializeClient() {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;

        RuntimeTarget target = RuntimeTarget.current();
        invoke(target.applicationPackage() + ".client.WorldEditSelectionOverlayClient", "onInitializeClient");
    }

    private static void invoke(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className, true, WorldEditSelectionOverlayBootstrap.class.getClassLoader());
            Object instance = type.getDeclaredConstructor().newInstance();
            type.getMethod(methodName).invoke(instance);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Failed to initialize " + className, cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to initialize " + className, exception);
        }
    }
}
