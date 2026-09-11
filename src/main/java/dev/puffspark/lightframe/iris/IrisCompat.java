package dev.puffspark.lightframe.iris;

import dev.puffspark.lightframe.LightFrame;
import dev.puffspark.lightframe.config.ColorLightConfig;

import java.lang.reflect.Method;

/**
 * Iris detection without a compile-time dependency.
 * Uses the stable Iris API v0 entry point reflectively.
 */
public final class IrisCompat {

    private static boolean initialized;
    private static boolean present;
    private static Method getInstance;
    private static Method isShaderPackInUse;
    private static boolean fallbackLogged;

    private IrisCompat() {}

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            getInstance = api.getMethod("getInstance");
            isShaderPackInUse = api.getMethod("isShaderPackInUse");
            present = true;
            LightFrame.LOGGER.info("Iris API detected; colored lights will use the documented fallback while a shaderpack is active");
        } catch (Throwable t) {
            present = false; // Iris not installed — vanilla backend handles everything
        }
    }

    /** True if Iris is installed AND a shaderpack is currently rendering. */
    public static boolean isShaderPackInUse() {
        init();
        if (!present) return false;
        try {
            Object apiInstance = getInstance.invoke(null);
            Object result = isShaderPackInUse.invoke(apiInstance);
            boolean inUse = result instanceof Boolean b && b;
            if (inUse && !fallbackLogged) {
                fallbackLogged = true;
                if (ColorLightConfig.get().logIrisFallback) {
                    LightFrame.LOGGER.info(
                            "Shaderpack active: hue tint is disabled (Iris fallback), dynamic light intensity stays available");
                }
            } else if (!inUse) {
                fallbackLogged = false;
            }
            return inUse;
        } catch (Throwable t) {
            return false; // never break the game because of reflection failures
        }
    }

    /** True if the Iris classes exist on the classpath (for the debug HUD). */
    public static boolean isIrisPresent() {
        init();
        return present;
    }
}

