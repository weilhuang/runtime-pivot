package com.runtime.pivot.plugin.ui;

import com.runtime.pivot.plugin.core.RuntimePivotConstants;
import com.runtime.pivot.protocol.PivotCapabilities;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RuntimePivotSwingComponentTest {
    @Test
    public void buildsToolWindowTabsOnEventDispatchThread() throws Exception {
        final JTabbedPane[] holder = new JTabbedPane[1];
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Sessions", labeled(RuntimePivotConstants.MSG_TITLE));
            tabs.addTab("Classes", new JPanel());
            tabs.addTab("Objects", new JPanel());
            tabs.addTab("Probes", new JPanel());
            tabs.addTab("Console", new JPanel());
            holder[0] = tabs;
        });
        assertNotNull(holder[0]);
        assertEquals(5, holder[0].getTabCount());
        assertEquals("Sessions", holder[0].getTitleAt(0));
    }

    @Test
    public void capabilityLabelRendersCustomCapabilityName() throws Exception {
        final JLabel[] holder = new JLabel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new JLabel(PivotCapabilities.CLASSES));
        assertEquals(PivotCapabilities.CLASSES, holder[0].getText());
        assertTrue(holder[0].getText().startsWith("io.runtime.pivot"));
    }

    private static JPanel labeled(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(text), BorderLayout.NORTH);
        return panel;
    }
}
