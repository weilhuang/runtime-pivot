package com.runtime.pivot.protocol;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PathSafetyAndQueueTest {
    @Test
    public void boundedQueueCountsDroppedEvents() {
        BoundedEventQueue<String> queue = new BoundedEventQueue<String>(2);
        assertTrue(queue.offer("a"));
        assertTrue(queue.offer("b"));
        assertFalse(queue.offer("c"));
        assertEquals(1, queue.droppedCount());
        assertEquals(2, queue.drain(10).size());
        assertEquals(0, queue.size());
    }

    @Test
    public void pathSafetyRejectsTraversal() throws Exception {
        File root = Files.createTempDirectory("rp-path").toFile();
        File allowed = PathSafety.resolveUnder(root, "out/dump.class");
        assertTrue(allowed.getCanonicalPath().startsWith(root.getCanonicalPath()));
        try {
            PathSafety.resolveUnder(root, "../outside.class");
            fail("expected traversal to be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("escapes"));
        }
    }

    @Test
    public void connectionConfigRoundTripOmitsRawTokenFromLogs() {
        ConnectionConfig config = ConnectionConfig.builder()
                .host("127.0.0.1")
                .wsPort(9)
                .httpPort(10)
                .token("0123456789abcdef0123456789abcdef")
                .sessionId("session-1")
                .build();
        ConnectionConfig parsed = ConnectionConfig.parse(config.toAgentArgument());
        assertEquals(9, parsed.getWsPort());
        assertEquals("session-1", parsed.getSessionId());
        assertFalse(config.toLogString().contains("0123456789abcdef0123456789abcdef"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void connectionConfigRejectsNonLoopbackHost() {
        ConnectionConfig.builder()
                .host("0.0.0.0")
                .token("0123456789abcdef0123456789abcdef")
                .sessionId("s")
                .build();
    }
}
