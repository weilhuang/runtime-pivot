package com.runtime.pivot.agent.core;

import com.runtime.pivot.agent.probe.NoOpProbeEngine;
import com.runtime.pivot.agent.probe.ProbeEngine;
import com.runtime.pivot.protocol.ConnectionConfig;
import com.runtime.pivot.protocol.PivotCommands;
import com.runtime.pivot.protocol.transport.CommandHandler;
import com.runtime.pivot.protocol.transport.OpampClient;

import java.lang.instrument.Instrumentation;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentStarter {
    private static volatile AgentRuntime runtime;

    private AgentStarter() {
    }

    public static synchronized void start(String args, Instrumentation instrumentation) throws Exception {
        if (runtime != null) {
            return;
        }
        ConnectionConfig config = ConnectionConfig.parse(args);
        ProbeEngine probes = new NoOpProbeEngine();
        TransformerRegistry transformers = new TransformerRegistry(instrumentation);
        ClassLoadingRecorder recorder = new ClassLoadingRecorder(config.getEventBufferSize());
        transformers.register("class-loading-recorder", "event", "Bounded class-loading timeline", recorder);
        Map<String, CommandHandler> handlers = new LinkedHashMap<String, CommandHandler>();
        ClassQueryService classes = new ClassQueryService(instrumentation, recorder, transformers);
        handlers.put(PivotCommands.PING, new PingHandler());
        handlers.put(PivotCommands.CLASS_LOADERS, classes.loadersHandler());
        handlers.put(PivotCommands.CLASS_LOADED, classes.loadedHandler());
        handlers.put(PivotCommands.CLASS_DUMP, classes.dumpHandler());
        handlers.put(PivotCommands.CLASS_LOADING_TIMELINE, classes.timelineHandler());
        handlers.put(PivotCommands.TRANSFORMER_LIST, classes.transformerHandler());
        OpampClient client = new OpampClient(config, agentVersion(), handlers);
        runtime = new AgentRuntime(config, instrumentation, client, recorder, transformers, probes);
        client.start();
        runtime.installShutdownHook();
    }

    public static AgentRuntime runtime() {
        return runtime;
    }

    private static String agentVersion() {
        Package pkg = AgentStarter.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }
        return "3.0.0";
    }
}
