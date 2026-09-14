package com.runtime.pivot.agent.bootstrap;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;

/**
 * Premain/agentmain entry. Uses only JDK types so the application classpath is not polluted
 * before the isolated agent classloader takes over.
 */
public final class AgentMain {
    private AgentMain() {
    }

    public static void premain(String args, Instrumentation instrumentation) {
        start(args, instrumentation);
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        start(args, instrumentation);
    }

    static void start(String args, Instrumentation instrumentation) {
        try {
            URL jar = locateAgentJar();
            AgentIsolatedClassLoader loader = new AgentIsolatedClassLoader(jar);
            Class<?> starter = Class.forName("com.runtime.pivot.agent.core.AgentStarter", true, loader);
            Method method = starter.getMethod("start", String.class, Instrumentation.class);
            method.invoke(null, args, instrumentation);
        } catch (Throwable error) {
            System.err.println("[runtime-pivot-agent] failed to start: " + error.getMessage());
        }
    }

    private static URL locateAgentJar() throws Exception {
        CodeSource source = AgentMain.class.getProtectionDomain().getCodeSource();
        if (source != null && source.getLocation() != null) {
            return source.getLocation();
        }
        String path = AgentMain.class.getResource("AgentMain.class").toURI().toString();
        if (path.startsWith("jar:")) {
            String file = path.substring(4, path.indexOf("!"));
            return new URI(file).toURL();
        }
        return new File(".").toURI().toURL();
    }
}
