package com.runtime.pivot.agent.core;

import com.runtime.pivot.agent.probe.ProbeEngine;
import com.runtime.pivot.protocol.ConnectionConfig;
import com.runtime.pivot.protocol.transport.OpampClient;

import java.lang.instrument.Instrumentation;

public final class AgentRuntime {
    private final ConnectionConfig config;
    private final Instrumentation instrumentation;
    private final OpampClient client;
    private final ClassLoadingRecorder recorder;
    private final TransformerRegistry transformers;
    private final ProbeEngine probes;

    AgentRuntime(ConnectionConfig config, Instrumentation instrumentation, OpampClient client,
                 ClassLoadingRecorder recorder, TransformerRegistry transformers, ProbeEngine probes) {
        this.config = config;
        this.instrumentation = instrumentation;
        this.client = client;
        this.recorder = recorder;
        this.transformers = transformers;
        this.probes = probes;
    }

    public ConnectionConfig config() {
        return config;
    }

    public Instrumentation instrumentation() {
        return instrumentation;
    }

    public OpampClient client() {
        return client;
    }

    public ClassLoadingRecorder recorder() {
        return recorder;
    }

    public TransformerRegistry transformers() {
        return transformers;
    }

    public ProbeEngine probes() {
        return probes;
    }

    void installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                close();
            }
        }, "runtime-pivot-agent-shutdown"));
    }

    public void close() {
        try {
            client.close();
        } catch (Exception ignored) {
        }
        transformers.close();
    }
}
