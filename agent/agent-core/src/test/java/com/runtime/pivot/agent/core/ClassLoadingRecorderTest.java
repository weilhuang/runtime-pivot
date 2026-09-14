package com.runtime.pivot.agent.core;

import org.junit.Test;

import java.lang.instrument.IllegalClassFormatException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClassLoadingRecorderTest {
    @Test
    public void doesNotRetainClassLoaderAndCountsDrops() throws IllegalClassFormatException {
        ClassLoadingRecorder recorder = new ClassLoadingRecorder(1);
        recorder.transform(ClassLoadingRecorderTest.class.getClassLoader(), "com/example/A", null, null, new byte[]{1});
        recorder.transform(ClassLoadingRecorderTest.class.getClassLoader(), "com/example/B", null, null, new byte[]{1});
        assertEquals(1, recorder.snapshot(10).size());
        assertEquals(1, recorder.droppedEvents());
        assertTrue(recorder.loaderId(ClassLoadingRecorderTest.class.getClassLoader()).length() > 0);
    }
}
