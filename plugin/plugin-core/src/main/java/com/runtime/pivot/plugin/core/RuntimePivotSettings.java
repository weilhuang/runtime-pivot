package com.runtime.pivot.plugin.core;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = RuntimePivotConstants.STORAGE_ID, storages = @Storage(RuntimePivotConstants.STORAGE_FILE_NAME))
public class RuntimePivotSettings implements PersistentStateComponent<RuntimePivotSettings.State> {
    public static class State {
        /**
         * 2.x persisted this as {@code attachAgent}. Kept so XmlSerializer still binds the old
         * option; {@link #migrateFromLegacy()} copies it onto {@link #injectAgentOnLaunch}.
         */
        public Boolean attachAgent;
        public boolean injectAgentOnLaunch = true;
        public boolean enableAgentCommunication = true;
        public int eventBufferSize = 10_000;

        public void migrateFromLegacy() {
            if (attachAgent != null) {
                injectAgentOnLaunch = attachAgent.booleanValue();
                attachAgent = null;
            }
        }
    }

    private State state = new State();
    private final Project project;

    public RuntimePivotSettings(Project project) {
        this.project = project;
    }

    public static RuntimePivotSettings getInstance(Project project) {
        return project.getService(RuntimePivotSettings.class);
    }

    public Project getProject() {
        return project;
    }

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        state.migrateFromLegacy();
        this.state = state;
    }

    public boolean isInjectAgentOnLaunch() {
        return state.injectAgentOnLaunch;
    }

    public void setInjectAgentOnLaunch(boolean injectAgentOnLaunch) {
        state.injectAgentOnLaunch = injectAgentOnLaunch;
    }

    /** @deprecated Use {@link #isInjectAgentOnLaunch()} */
    @Deprecated
    public boolean isAttachAgent() {
        return isInjectAgentOnLaunch();
    }

    public boolean isEnableAgentCommunication() {
        return state.enableAgentCommunication;
    }

    public void setEnableAgentCommunication(boolean enableAgentCommunication) {
        state.enableAgentCommunication = enableAgentCommunication;
    }

    public int getEventBufferSize() {
        return state.eventBufferSize;
    }

    public void setEventBufferSize(int eventBufferSize) {
        state.eventBufferSize = eventBufferSize;
    }
}
