package com.runtime.pivot.plugin.debugger;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XDebuggerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Evaluates expressions through public XDebugger APIs. Does not read debugger tree nodes.
 */
public final class ExpressionEvaluation {
    private ExpressionEvaluation() {
    }

    public static boolean canEvaluate(@Nullable XDebugSession session) {
        return evaluator(session) != null;
    }

    @Nullable
    public static XDebuggerEvaluator evaluator(@Nullable XDebugSession session) {
        if (session == null) {
            return null;
        }
        XStackFrame frame = session.getCurrentStackFrame();
        return frame == null ? null : frame.getEvaluator();
    }

    public static void evaluate(@NotNull XDebugSession session,
                                @NotNull String expression,
                                @NotNull Consumer<XValue> onSuccess,
                                @NotNull Consumer<String> onError) {
        XDebuggerEvaluator evaluator = evaluator(session);
        if (evaluator == null) {
            onError.accept("No paused stack frame evaluator");
            return;
        }
        XExpression xExpression = XDebuggerUtil.getInstance()
                .createExpression(expression, null, null, EvaluationMode.EXPRESSION);
        evaluator.evaluate(xExpression, new XDebuggerEvaluator.XEvaluationCallback() {
            @Override
            public void evaluated(@NotNull XValue result) {
                onSuccess.accept(result);
            }

            @Override
            public void errorOccurred(@NotNull String errorMessage) {
                onError.accept(errorMessage);
            }
        }, session.getCurrentPosition());
    }
}
