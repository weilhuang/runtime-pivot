package com.runtime.pivot.plugin.debugger;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.Nullable;

public final class DebuggerSessions {
    private DebuggerSessions() {
    }

    @Nullable
    public static XDebugSession current(@Nullable AnActionEvent event) {
        if (event != null) {
            XDebugSession fromData = event.getData(XDebugSession.DATA_KEY);
            if (fromData != null) {
                return fromData;
            }
            if (event.getProject() != null) {
                return XDebuggerManager.getInstance(event.getProject()).getCurrentSession();
            }
        }
        return null;
    }
}
