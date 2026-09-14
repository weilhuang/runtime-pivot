package com.runtime.pivot.plugin.debugger;

import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SessionLifecycleListener implements XDebuggerManagerListener {
    @Override
    public void processStopped(@NotNull XDebugProcess debugProcess) {
        // Session history stays visible in the ToolWindow; OpAMP resources are owned by the project service.
    }

    @Override
    public void currentSessionChanged(@Nullable XDebugSession previousSession, @Nullable XDebugSession currentSession) {
        // ToolWindow observes OpAMP + public XDebuggerManager state; no internal session types.
    }
}
