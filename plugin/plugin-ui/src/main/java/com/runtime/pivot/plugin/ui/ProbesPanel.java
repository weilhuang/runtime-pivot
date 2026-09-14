package com.runtime.pivot.plugin.ui;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;

import java.awt.BorderLayout;

public final class ProbesPanel extends JBPanel<ProbesPanel> {
    public ProbesPanel() {
        super(new BorderLayout());
        add(new JBLabel("Runtime Probes (Agent instrumentation) will be completed in Phase 6. "
                + "Timers use target-JVM System.nanoTime()."), BorderLayout.NORTH);
    }
}
