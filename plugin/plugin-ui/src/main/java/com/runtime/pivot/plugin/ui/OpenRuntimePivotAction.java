package com.runtime.pivot.plugin.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.runtime.pivot.plugin.core.RuntimePivotConstants;
import com.runtime.pivot.plugin.core.RuntimePivotOpampService;
import org.jetbrains.annotations.NotNull;

public final class OpenRuntimePivotAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        try {
            RuntimePivotOpampService.getInstance(project).ensureStarted();
        } catch (Exception ignored) {
        }
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(RuntimePivotConstants.TOOL_WINDOW_ID);
        if (window != null) {
            window.show(null);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
}
