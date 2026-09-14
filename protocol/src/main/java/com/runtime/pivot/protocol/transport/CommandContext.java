package com.runtime.pivot.protocol.transport;

public final class CommandContext {
    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void throwIfCancelled() throws java.util.concurrent.CancellationException {
        if (cancelled) {
            throw new java.util.concurrent.CancellationException("cancelled");
        }
    }
}
