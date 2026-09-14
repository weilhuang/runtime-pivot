package com.runtime.pivot.agent.probe;

import java.util.Collections;
import java.util.List;

/**
 * Probe engine contract. Phase 6 will provide ASM-based timers/counters.
 */
public interface ProbeEngine {
    void install(ProbeDefinition definition) throws Exception;

    void remove(String probeId);

    List<ProbeDefinition> list();

    void close();
}
