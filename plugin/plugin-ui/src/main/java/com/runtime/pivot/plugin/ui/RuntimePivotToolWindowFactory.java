package com.runtime.pivot.plugin.ui;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.runtime.pivot.plugin.core.RuntimePivotOpampService;
import org.jetbrains.annotations.NotNull;

public final class RuntimePivotToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        RuntimePivotOpampService service = RuntimePivotOpampService.getInstance(project);
        ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        SessionsPanel sessions = new SessionsPanel(project, service);
        ClassesPanel classes = new ClassesPanel(project, service, consoleView);
        ObjectsPanel objects = new ObjectsPanel(project, consoleView);
        ProbesPanel probes = new ProbesPanel();
        ConsolePanel console = new ConsolePanel(consoleView, service);

        JBTabbedPane tabs = new JBTabbedPane();
        tabs.addTab("Sessions", sessions);
        tabs.addTab("Classes", classes);
        tabs.addTab("Objects", objects);
        tabs.addTab("Probes", probes);
        tabs.addTab("Console", console);

        Content content = ContentFactory.getInstance().createContent(tabs, "", false);
        content.setDisposer(() -> consoleView.dispose());
        toolWindow.getContentManager().addContent(content);
    }
}
