package com.runtime.pivot.agent.probe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProbeDefinitionTest {
    @Test
    public void acceptsKnownKinds() {
        ProbeDefinition definition = new ProbeDefinition("p1", "method_duration", "com.example.Foo", "bar", 10, 1.0);
        assertEquals("METHOD_DURATION", definition.getKind());
        NoOpProbeEngine engine = new NoOpProbeEngine();
        engine.install(definition);
        assertEquals(1, engine.list().size());
        engine.remove("p1");
        assertEquals(0, engine.list().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownKind() {
        new ProbeDefinition("p1", "MAGIC", "com.example.Foo", "bar", 1, 1.0);
    }
}
