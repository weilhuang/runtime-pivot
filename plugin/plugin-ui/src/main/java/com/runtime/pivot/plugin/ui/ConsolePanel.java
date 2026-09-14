package com.runtime.pivot.plugin.ui;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ui.components.JBPanel;
import com.runtime.pivot.plugin.core.RuntimePivotOpampService;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

public final class ConsolePanel extends JBPanel<ConsolePanel> implements RuntimePivotOpampService.Listener {
    private final ConsoleView consoleView;
    private final RuntimePivotOpampService service;

    public ConsolePanel(ConsoleView consoleView, RuntimePivotOpampService service) {
        super(new BorderLayout());
        this.consoleView = consoleView;
        this.service = service;
        add(consoleView.getComponent(), BorderLayout.CENTER);
        JButton clear = new JButton("Clear");
        clear.addActionListener(event -> consoleView.clear());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(clear);
        add(buttons, BorderLayout.SOUTH);
        service.addListener(this);
        stateChanged(service);
    }

    @Override
    public void stateChanged(@NotNull RuntimePivotOpampService updated) {
        consoleView.print("Status: " + updated.status().name() + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
        if (updated.lastError() != null) {
            consoleView.print(updated.lastError() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
        }
    }
}
