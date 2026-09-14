package com.runtime.pivot.plugin.integration;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.runtime.pivot.plugin.core.RuntimePivotConstants;
import com.runtime.pivot.plugin.core.RuntimePivotOpampService;
import com.runtime.pivot.plugin.core.RuntimePivotSettings;
import com.runtime.pivot.protocol.ConnectionConfig;

public class RuntimePivotPluginIntegrationTest extends BasePlatformTestCase {
    public void testPluginIsLoaded() {
        assertNotNull(PluginManager.getInstance().findEnabledPlugin(
                PluginId.getId(RuntimePivotConstants.PLUGIN_ID)));
    }

    public void testProjectServicesAreRegistered() {
        RuntimePivotSettings settings = RuntimePivotSettings.getInstance(getProject());
        assertNotNull(settings);
        assertTrue(settings.isInjectAgentOnLaunch());
        RuntimePivotOpampService service = RuntimePivotOpampService.getInstance(getProject());
        assertNotNull(service);
        assertEquals(RuntimePivotOpampService.ConnectionStatus.DISCONNECTED, service.status());
    }

    public void testOpenActionIsRegistered() {
        assertNotNull(ActionManager.getInstance().getAction("RuntimePivot.OpenToolWindow"));
    }

    public void testOpampServerBindsLoopbackOnly() throws Exception {
        RuntimePivotOpampService service = RuntimePivotOpampService.getInstance(getProject());
        ConnectionConfig config = service.ensureStarted();
        assertEquals("127.0.0.1", config.getHost());
        assertTrue(config.getWsPort() > 0);
        assertTrue(config.getHttpPort() > 0);
        assertFalse(config.toLogString().contains(config.getToken()));
        assertEquals(RuntimePivotOpampService.ConnectionStatus.LISTENING, service.status());
    }
}
