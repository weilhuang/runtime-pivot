package com.runtime.pivot.plugin.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolves a filesystem path for {@code -javaagent}. The agent jar is packaged as a plugin
 * resource and copied under {@code ~/.runtime-pivot/agent} so the target JVM never sees a path
 * inside the plugin zip (and spaces in the install path are avoided).
 */
public final class AgentJarLocator {
    private AgentJarLocator() {
    }

    public static Path resolveAgentJar() throws IOException {
        try (InputStream in = AgentJarLocator.class.getResourceAsStream(RuntimePivotConstants.AGENT_RESOURCE)) {
            if (in == null) {
                throw new IOException("runtime-pivot-agent.jar was not packaged with the plugin");
            }
            Path dest = userHomeAgentDir().resolve("runtime-pivot-agent.jar");
            Files.createDirectories(dest.getParent());
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest;
        }
    }

    private static Path userHomeAgentDir() {
        return Path.of(System.getProperty("user.home"), ".runtime-pivot", "agent");
    }
}
