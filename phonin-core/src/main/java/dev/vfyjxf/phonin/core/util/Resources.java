package dev.vfyjxf.phonin.core.util;

import java.io.InputStream;

/**
 * Classpath resource access with classloader fallback. In jar-in-jar environments (NeoForge
 * jarJar, Fabric include) the context class loader may not see nested jars, so we try the anchor
 * class's own loader first, then the context loader, then the system loader.
 */
public final class Resources {

    private Resources() {}

    /**
     * Open a classpath resource, or {@code null} when absent. {@code anchor} is a class whose
     * loader definitely sees the resource (typically the caller's own class).
     */
    public static InputStream open(Class<?> anchor, String path) {
        ClassLoader[] loaders = {
            anchor.getClassLoader(),
            Thread.currentThread().getContextClassLoader(),
            ClassLoader.getSystemClassLoader(),
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            InputStream in = cl.getResourceAsStream(path);
            if (in != null) return in;
        }
        return null;
    }
}
