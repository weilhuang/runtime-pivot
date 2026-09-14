package com.runtime.pivot.agent.core;

import com.runtime.pivot.protocol.BoundedEventQueue;
import com.runtime.pivot.protocol.proto.ClassLoadingEvent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.ref.WeakReference;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Records class-loading events without retaining Class or ClassLoader instances.
 */
public final class ClassLoadingRecorder implements ClassFileTransformer {
    private final BoundedEventQueue<ClassLoadingEvent> events;
    private final WeakHashMap<ClassLoader, String> loaderIds = new WeakHashMap<ClassLoader, String>();
    private final Object loaderLock = new Object();

    public ClassLoadingRecorder(int capacity) {
        this.events = new BoundedEventQueue<ClassLoadingEvent>(capacity);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null) {
            return null;
        }
        String binaryName = className.replace('/', '.');
        ClassLoadingEvent event = ClassLoadingEvent.newBuilder()
                .setClassName(binaryName)
                .setLoaderId(loaderId(loader))
                .setTimestampNanos(System.nanoTime())
                .setEventKind(classBeingRedefined == null ? "DEFINE" : "RETRANSFORM")
                .build();
        events.offer(event);
        return null;
    }

    public String loaderId(ClassLoader loader) {
        if (loader == null) {
            return "bootstrap";
        }
        synchronized (loaderLock) {
            String existing = loaderIds.get(loader);
            if (existing != null) {
                return existing;
            }
            String id = Integer.toHexString(System.identityHashCode(loader));
            loaderIds.put(loader, id);
            return id;
        }
    }

    public String loaderName(ClassLoader loader) {
        if (loader == null) {
            return "bootstrap";
        }
        String name = loader.getClass().getName();
        String loaderName = loader.toString();
        return name + ":" + loaderName;
    }

    public List<ClassLoadingEvent> snapshot(int limit) {
        return events.snapshot(limit);
    }

    public long droppedEvents() {
        return events.droppedCount();
    }

    public List<LoaderRef> knownLoaders() {
        synchronized (loaderLock) {
            List<LoaderRef> refs = new ArrayList<LoaderRef>();
            for (ClassLoader loader : loaderIds.keySet()) {
                refs.add(new LoaderRef(loaderIds.get(loader), new WeakReference<ClassLoader>(loader)));
            }
            return refs;
        }
    }

    public static final class LoaderRef {
        public final String id;
        public final WeakReference<ClassLoader> loader;

        LoaderRef(String id, WeakReference<ClassLoader> loader) {
            this.id = id;
            this.loader = loader;
        }
    }
}
