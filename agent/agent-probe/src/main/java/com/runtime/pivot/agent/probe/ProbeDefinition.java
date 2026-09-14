package com.runtime.pivot.agent.probe;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ProbeDefinition {
    public static final Set<String> KINDS;

    static {
        LinkedHashSet<String> kinds = new LinkedHashSet<String>();
        kinds.add("SNAPSHOT");
        kinds.add("LOG");
        kinds.add("COUNTER");
        kinds.add("TIMER_START");
        kinds.add("TIMER_END");
        kinds.add("METHOD_DURATION");
        kinds.add("EVENT");
        kinds.add("ONE_SHOT_CODE");
        KINDS = Collections.unmodifiableSet(kinds);
    }

    private final String id;
    private final String kind;
    private final String className;
    private final String methodName;
    private final int hitLimit;
    private final double sampleRate;

    public ProbeDefinition(String id, String kind, String className, String methodName, int hitLimit, double sampleRate) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("probe id is required");
        }
        if (kind == null || !KINDS.contains(kind.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("unsupported probe kind: " + kind);
        }
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("className is required");
        }
        if (sampleRate < 0 || sampleRate > 1) {
            throw new IllegalArgumentException("sampleRate must be between 0 and 1");
        }
        this.id = id;
        this.kind = kind.toUpperCase(Locale.ROOT);
        this.className = className;
        this.methodName = methodName;
        this.hitLimit = hitLimit;
        this.sampleRate = sampleRate;
    }

    public String getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public int getHitLimit() {
        return hitLimit;
    }

    public double getSampleRate() {
        return sampleRate;
    }
}
