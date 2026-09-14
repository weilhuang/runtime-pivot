package com.runtime.pivot.plugin.unit;

import com.runtime.pivot.plugin.core.RuntimePivotConstants;
import com.runtime.pivot.protocol.AuthTokens;
import com.runtime.pivot.protocol.ConnectionConfig;
import com.runtime.pivot.protocol.ProtocolVersion;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuntimePivotConstantsTest {
    @Test
    public void pluginIdMatchesDeclaredId() {
        assertEquals("com.runtime.pivot.plugin", RuntimePivotConstants.PLUGIN_ID);
    }

    @Test
    public void agentJarNameIsStable() {
        assertEquals("runtime-pivot-agent", RuntimePivotConstants.AGENT_JAR_NAME);
    }

    @Test
    public void protocolVersionIs3() {
        assertEquals(3, ProtocolVersion.CURRENT);
    }

    @Test
    public void connectionConfigIsLoopbackOnly() {
        ConnectionConfig config = ConnectionConfig.builder()
                .token(AuthTokens.randomToken())
                .sessionId("s")
                .wsPort(1)
                .httpPort(2)
                .build();
        assertTrue(config.websocketUrl().startsWith("ws://127.0.0.1:"));
        assertFalse(config.toLogString().contains(config.getToken()));
    }
}
