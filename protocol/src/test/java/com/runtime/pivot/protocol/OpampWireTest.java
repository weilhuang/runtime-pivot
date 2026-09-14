package com.runtime.pivot.protocol;

import opamp.proto.v1.AgentToServer;
import opamp.proto.v1.ServerToAgent;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class OpampWireTest {
    @Test
    public void websocketRoundTripPrependsZeroHeader() throws Exception {
        AgentToServer original = AgentToServer.newBuilder()
                .setSequenceNum(7)
                .setCapabilities(PivotCapabilities.AGENT_OPAMP_BITS)
                .build();
        byte[] frame = OpampWire.encodeAgentToServerWs(original, 1024);
        assertEquals(0, frame[0]);
        AgentToServer decoded = OpampWire.decodeAgentToServerWs(frame, 1024);
        assertEquals(original.getSequenceNum(), decoded.getSequenceNum());
        assertEquals(original.getCapabilities(), decoded.getCapabilities());
    }

    @Test
    public void httpBodyIsRawProtobuf() throws Exception {
        ServerToAgent original = ServerToAgent.newBuilder()
                .setCapabilities(PivotCapabilities.SERVER_OPAMP_BITS)
                .build();
        byte[] body = original.toByteArray();
        ServerToAgent decoded = OpampWire.decodeServerToAgentHttp(body, 1024);
        assertEquals(original.getCapabilities(), decoded.getCapabilities());
        assertArrayEquals(body, original.toByteArray());
    }
}
