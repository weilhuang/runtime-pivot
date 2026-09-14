package com.runtime.pivot.plugin.core;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class RuntimePivotBundle {
    private static final String BUNDLE = "messages.RuntimePivotBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(RuntimePivotBundle.class, BUNDLE);

    private RuntimePivotBundle() {
    }

    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
        return INSTANCE.getMessage(key, params);
    }

    public static Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
        return INSTANCE.getLazyMessage(key, params);
    }
}
