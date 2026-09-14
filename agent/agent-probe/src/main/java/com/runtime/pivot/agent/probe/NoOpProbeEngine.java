package com.runtime.pivot.agent.probe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class NoOpProbeEngine implements ProbeEngine {
    private final ConcurrentHashMap<String, ProbeDefinition> probes = new ConcurrentHashMap<String, ProbeDefinition>();

    @Override
    public void install(ProbeDefinition definition) {
        probes.put(definition.getId(), definition);
    }

    @Override
    public void remove(String probeId) {
        probes.remove(probeId);
    }

    @Override
    public List<ProbeDefinition> list() {
        return Collections.unmodifiableList(new ArrayList<ProbeDefinition>(probes.values()));
    }

    @Override
    public void close() {
        probes.clear();
    }
}
