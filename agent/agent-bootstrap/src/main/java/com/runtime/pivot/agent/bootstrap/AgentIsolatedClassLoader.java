package com.runtime.pivot.agent.bootstrap;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Loads Agent core, protocol and relocated third-party classes from the agent jar without
 * exposing them to the application classloader.
 */
public final class AgentIsolatedClassLoader extends URLClassLoader {
    public AgentIsolatedClassLoader(URL agentJar) {
        super(new URL[]{agentJar}, ClassLoader.getSystemClassLoader().getParent());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
                || name.startsWith("sun.") || name.startsWith("com.sun.")) {
            return super.loadClass(name, resolve);
        }
        if (isAgentClass(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                loaded = findClass(name);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
        return super.loadClass(name, resolve);
    }

    private static boolean isAgentClass(String name) {
        return name.startsWith("com.runtime.pivot.agent.core")
                || name.startsWith("com.runtime.pivot.agent.probe")
                || name.startsWith("com.runtime.pivot.agent.shaded")
                || name.startsWith("com.runtime.pivot.protocol")
                || name.startsWith("opamp.")
                || name.startsWith("com.google.protobuf")
                || name.startsWith("org.java_websocket")
                || name.startsWith("org.slf4j");
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
