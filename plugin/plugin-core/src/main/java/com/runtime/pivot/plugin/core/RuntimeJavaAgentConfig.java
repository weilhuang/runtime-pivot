package com.runtime.pivot.plugin.core;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.runtime.pivot.protocol.ConnectionConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RuntimeJavaAgentConfig extends JavaProgramPatcher {
    @Override
    public void patchJavaParameters(Executor executor, RunProfile configuration, JavaParameters javaParameters) {
        if (!(configuration instanceof RunConfiguration runConfiguration)) {
            return;
        }
        Project project = runConfiguration.getProject();
        RuntimePivotSettings settings = RuntimePivotSettings.getInstance(project);
        if (!settings.isInjectAgentOnLaunch() || !settings.isEnableAgentCommunication()) {
            return;
        }
        Sdk jdk = javaParameters.getJdk();
        if (jdk == null) {
            return;
        }
        JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
        if (version == null || version.compareTo(JavaSdkVersion.JDK_1_8) < 0) {
            return;
        }
        try {
            Path agentJar = AgentJarLocator.resolveAgentJar();
            ConnectionConfig config = RuntimePivotOpampService.getInstance(project).ensureStarted();
            String argument = "-javaagent:" + agentJar + "=" + config.toAgentArgument();
            List<String> parameters = new ArrayList<>();
            parameters.add(argument);
            parameters.addAll(javaParameters.getVMParametersList().getParameters());
            javaParameters.getVMParametersList().clearAll();
            javaParameters.getVMParametersList().addAll(parameters);
        } catch (Exception ignored) {
            // Injection failures must not prevent the user program from launching.
        }
    }
}
