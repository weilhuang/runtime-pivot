package com.runtime.pivot.plugin.debugger;

import com.intellij.util.ThreeState;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XDropFrameHandler;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.Nullable;

/**
 * Isolates the Experimental {@link XDropFrameHandler} API. Do not use XDropFrameHandler
 * outside this class.
 */
public final class DropFrameCapability {
    private DropFrameCapability() {
    }

    public static boolean isAvailable(@Nullable XDebugSession session, @Nullable XStackFrame frame) {
        XDropFrameHandler handler = handler(session);
        return handler != null && frame != null && handler.canDropFrame(frame) == ThreeState.YES;
    }

    public static void drop(@Nullable XDebugSession session, @Nullable XStackFrame frame) {
        XDropFrameHandler handler = handler(session);
        if (handler != null && frame != null && handler.canDropFrame(frame) == ThreeState.YES) {
            handler.drop(frame);
        }
    }

    @Nullable
    private static XDropFrameHandler handler(@Nullable XDebugSession session) {
        if (session == null) {
            return null;
        }
        XDebugProcess process = session.getDebugProcess();
        return process.getDropFrameHandler();
    }
}
