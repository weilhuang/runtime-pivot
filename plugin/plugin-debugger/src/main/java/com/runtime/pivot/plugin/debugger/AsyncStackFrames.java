package com.runtime.pivot.plugin.debugger;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Loads stack frames asynchronously through public XDebugger APIs. Never blocks the EDT.
 */
public final class AsyncStackFrames {
    private AsyncStackFrames() {
    }

    public static void compute(@Nullable XDebugSession session, @NotNull Consumer<List<XStackFrame>> onFrames,
                               @NotNull Consumer<String> onError) {
        if (session == null) {
            onError.accept("No debug session");
            return;
        }
        XSuspendContext context = session.getSuspendContext();
        if (context == null) {
            onError.accept("Session is not paused");
            return;
        }
        XExecutionStack stack = context.getActiveExecutionStack();
        if (stack == null) {
            onError.accept("No active execution stack");
            return;
        }
        stack.computeStackFrames(0, new XExecutionStack.XStackFrameContainer() {
            private final List<XStackFrame> frames = new ArrayList<>();

            @Override
            public void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, boolean last) {
                frames.addAll(stackFrames);
                if (last) {
                    onFrames.accept(List.copyOf(frames));
                }
            }

            @Override
            public void errorOccurred(@NotNull String errorMessage) {
                onError.accept(errorMessage);
            }
        });
    }
}
