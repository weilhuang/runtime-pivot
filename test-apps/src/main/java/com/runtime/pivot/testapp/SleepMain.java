package com.runtime.pivot.testapp;

/**
 * Long-running Java 8 process used by Agent premain integration tests.
 */
public final class SleepMain {
    public static void main(String[] args) throws Exception {
        System.out.println("runtime-pivot-test-app-started");
        long durationMs = 15_000L;
        if (args.length > 0) {
            durationMs = Long.parseLong(args[0]);
        }
        Thread.sleep(durationMs);
        System.out.println("runtime-pivot-test-app-stopped");
    }
}
