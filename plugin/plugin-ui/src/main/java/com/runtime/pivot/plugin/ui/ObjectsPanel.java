package com.runtime.pivot.plugin.ui;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.runtime.pivot.plugin.debugger.ExpressionEvaluation;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * Expression-based object workflow. Does not read debugger tree internal nodes.
 */
public final class ObjectsPanel extends JBPanel<ObjectsPanel> {
    public ObjectsPanel(Project project, ConsoleView consoleView) {
        super(new BorderLayout());
        JBTextField expression = new JBTextField();
        JButton evaluate = new JButton("Evaluate");
        evaluate.addActionListener(event -> {
            XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
            if (session == null) {
                consoleView.print("No paused debug session. Evaluate uses the current JDI stack frame.\n",
                        ConsoleViewContentType.ERROR_OUTPUT);
                return;
            }
            ExpressionEvaluation.evaluate(session, expression.getText(),
                    value -> consoleView.print("Evaluated: " + value + "\n", ConsoleViewContentType.NORMAL_OUTPUT),
                    error -> consoleView.print(error + "\n", ConsoleViewContentType.ERROR_OUTPUT));
        });
        JPanel north = new JPanel(new BorderLayout());
        north.add(new JBLabel("Expression"), BorderLayout.WEST);
        north.add(expression, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(evaluate);
        add(north, BorderLayout.NORTH);
        add(buttons, BorderLayout.SOUTH);
    }
}
