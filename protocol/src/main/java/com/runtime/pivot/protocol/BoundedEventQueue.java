package com.runtime.pivot.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded ring buffer with a dropped-event counter for Agent event backpressure.
 */
public final class BoundedEventQueue<T> {
    private final int capacity;
    private final Object lock = new Object();
    private final ArrayList<T> items;
    private final AtomicLong dropped = new AtomicLong();

    public BoundedEventQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.items = new ArrayList<T>(capacity);
    }

    public boolean offer(T item) {
        synchronized (lock) {
            if (items.size() >= capacity) {
                items.remove(0);
                dropped.incrementAndGet();
            }
            items.add(item);
            return true;
        }
    }

    public List<T> drain(int max) {
        synchronized (lock) {
            int count = Math.min(max, items.size());
            List<T> snapshot = new ArrayList<T>(items.subList(0, count));
            items.subList(0, count).clear();
            return snapshot;
        }
    }

    public List<T> snapshot(int max) {
        synchronized (lock) {
            int count = Math.min(max, items.size());
            return Collections.unmodifiableList(new ArrayList<T>(items.subList(Math.max(0, items.size() - count), items.size())));
        }
    }

    public int size() {
        synchronized (lock) {
            return items.size();
        }
    }

    public long droppedCount() {
        return dropped.get();
    }

    public int remainingCapacity() {
        synchronized (lock) {
            return capacity - items.size();
        }
    }
}
