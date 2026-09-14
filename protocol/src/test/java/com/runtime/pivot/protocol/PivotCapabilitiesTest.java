package com.runtime.pivot.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PivotCapabilitiesTest {
    @Test
    public void negotiatesIntersection() {
        assertTrue(PivotCapabilities.negotiated(
                PivotCapabilities.agentCustomCapabilities(),
                PivotCapabilities.serverCustomCapabilities()
        ).contains(PivotCapabilities.CLASSES));
    }

    @Test
    public void reportsStatusBitIsSet() {
        assertTrue(PivotCapabilities.hasBit(
                PivotCapabilities.AGENT_OPAMP_BITS,
                opamp.proto.v1.AgentCapabilities.AgentCapabilities_ReportsStatus_VALUE));
        assertTrue(PivotCapabilities.hasBit(
                PivotCapabilities.AGENT_OPAMP_BITS,
                opamp.proto.v1.AgentCapabilities.AgentCapabilities_ReportsHeartbeat_VALUE));
    }

    @Test
    public void protocolVersionConstant() {
        assertEquals(3, ProtocolVersion.CURRENT);
    }
}
