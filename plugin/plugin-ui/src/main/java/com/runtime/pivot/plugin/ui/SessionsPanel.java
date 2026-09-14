package com.runtime.pivot.plugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.FormBuilder;
import com.runtime.pivot.plugin.core.RuntimePivotOpampService;
import com.runtime.pivot.protocol.transport.OpampServer;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class SessionsPanel extends JBPanel<SessionsPanel> implements RuntimePivotOpampService.Listener {
    private final RuntimePivotOpampService service;
    private final JBLabel status = new JBLabel();
    private final JBLabel capabilities = new JBLabel();
    private final JBLabel health = new JBLabel();

    public SessionsPanel(Project project, RuntimePivotOpampService service) {
        super(new BorderLayout());
        this.service = service;
        JButton start = new JButton("Start loopback server");
        start.addActionListener(event -> {
            try {
                service.ensureStarted();
            } catch (Exception e) {
                status.setText("Error: " + e.getMessage());
            }
        });
        JPanel form = FormBuilder.createFormBuilder()
                .addLabeledComponent("Status", status)
                .addLabeledComponent("Capabilities", capabilities)
                .addLabeledComponent("Health", health)
                .addComponent(start)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        add(form, BorderLayout.CENTER);
        service.addListener(this);
        stateChanged(service);
    }

    @Override
    public void stateChanged(@NotNull RuntimePivotOpampService updated) {
        status.setText(updated.status().name());
        OpampServer.AgentSession session = updated.agentSession();
        if (session == null) {
            capabilities.setText("-");
            health.setText("-");
        } else {
            capabilities.setText(String.valueOf(session.getNegotiatedCustomCapabilities()));
            health.setText(session.isHealthy() + " / " + session.getHealthStatus());
        }
    }
}
