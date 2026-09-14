package com.runtime.pivot.protocol;

import opamp.proto.v1.AgentCapabilities;
import opamp.proto.v1.ServerCapabilities;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Maps Runtime Pivot data-plane features onto OpAMP capability bits and custom capabilities.
 */
public final class PivotCapabilities {
    public static final String CUSTOM_CAPABILITY = "io.runtime.pivot.v1";
    public static final String CLASSES = "io.runtime.pivot.v1.classes";
    public static final String OBJECTS = "io.runtime.pivot.v1.objects";
    public static final String PROBES = "io.runtime.pivot.v1.probes";
    public static final String EVENTS = "io.runtime.pivot.v1.events";

    public static final long AGENT_OPAMP_BITS =
            AgentCapabilities.AgentCapabilities_ReportsStatus_VALUE
                    | AgentCapabilities.AgentCapabilities_ReportsHealth_VALUE
                    | AgentCapabilities.AgentCapabilities_ReportsHeartbeat_VALUE;

    public static final long SERVER_OPAMP_BITS =
            ServerCapabilities.ServerCapabilities_AcceptsStatus_VALUE
                    | ServerCapabilities.ServerCapabilities_OffersConnectionSettings_VALUE;

    private PivotCapabilities() {
    }

    public static Set<String> agentCustomCapabilities() {
        return unmodifiable(CLASSES, EVENTS, OBJECTS, PROBES, CUSTOM_CAPABILITY);
    }

    public static Set<String> serverCustomCapabilities() {
        return unmodifiable(CLASSES, EVENTS, OBJECTS, PROBES, CUSTOM_CAPABILITY);
    }

    public static Set<String> negotiated(Collection<String> agent, Collection<String> server) {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        for (String capability : agent) {
            if (server.contains(capability)) {
                result.add(capability);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public static boolean hasBit(long bits, int flag) {
        return (bits & flag) == flag;
    }

    private static Set<String> unmodifiable(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(values)));
    }
}
