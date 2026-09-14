package com.runtime.pivot.protocol.transport;

import com.runtime.pivot.protocol.proto.EventBatch;

public interface OpampServerListener {
    void onAgentConnected(OpampServer.AgentSession session);

    void onAgentDisconnected(OpampServer.AgentSession session);

    void onEventBatch(OpampServer.AgentSession session, EventBatch batch);
}
