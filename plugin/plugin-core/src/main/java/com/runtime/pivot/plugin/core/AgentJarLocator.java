package com.runtime.pivot.plugin.core;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public final class AgentJarLocator {
    private AgentJarLocator() {
    }

    public static Path resolveAgentJar() throws IOException {
        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId(RuntimePivotConstants.PLUGIN_ID));
        if (descriptor != null && descriptor.getPluginPath() != null) {
            Path found = findJar(descriptor.getPluginPath());
            if (found != null) {
                return copyToUserHome(found);
            }
        }
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

    private static Path findJar(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith(RuntimePivotConstants.AGENT_JAR_NAME) && name.endsWith(".jar");
            }).findFirst().orElse(null);
        }
    }

    private static Path copyToUserHome(Path source) throws IOException {
        Path dest = userHomeAgentDir().resolve(source.getFileName().toString());
        Files.createDirectories(dest.getParent());
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    private static Path userHomeAgentDir() {
        return Path.of(System.getProperty("user.home"), ".runtime-pivot", "agent");
    }
}
