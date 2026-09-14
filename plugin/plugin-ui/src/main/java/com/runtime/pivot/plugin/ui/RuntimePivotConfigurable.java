package com.runtime.pivot.plugin.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.FormBuilder;
import com.runtime.pivot.plugin.core.RuntimePivotBundle;
import com.runtime.pivot.plugin.core.RuntimePivotSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

public final class RuntimePivotConfigurable implements Configurable {
    private final Project project;
    private JBCheckBox injectAgentCheckBox;
    private JPanel mainPanel;

    public RuntimePivotConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Runtime Pivot";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        injectAgentCheckBox = new JBCheckBox(RuntimePivotBundle.message("runtime.pivot.plugin.configurable.injectAgentCheckBox"));
        injectAgentCheckBox.setToolTipText(RuntimePivotBundle.message("runtime.pivot.plugin.configurable.tip.text"));
        JBLabel restartLabel = new JBLabel(RuntimePivotBundle.message("runtime.pivot.plugin.configurable.tip.text"));
        restartLabel.setForeground(JBColor.GRAY);
        JPanel panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(injectAgentCheckBox);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(restartLabel);
        mainPanel = FormBuilder.createFormBuilder()
                .addComponent(panel)
                .addComponentFillVertically(new JBPanel<>(), 0)
                .getPanel();
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        return injectAgentCheckBox.isSelected() != RuntimePivotSettings.getInstance(project).isInjectAgentOnLaunch();
    }

    @Override
    public void apply() {
        RuntimePivotSettings.getInstance(project).setInjectAgentOnLaunch(injectAgentCheckBox.isSelected());
    }

    @Override
    public void reset() {
        injectAgentCheckBox.setSelected(RuntimePivotSettings.getInstance(project).isInjectAgentOnLaunch());
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        injectAgentCheckBox = null;
    }
}
