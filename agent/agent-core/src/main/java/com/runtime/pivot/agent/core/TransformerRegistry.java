package com.runtime.pivot.agent.core;

import com.runtime.pivot.protocol.proto.TransformerInfo;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks Runtime Pivot transformers only. Does not enumerate third-party transformers.
 */
public final class TransformerRegistry {
    private final Instrumentation instrumentation;
    private final CopyOnWriteArrayList<RegisteredTransformer> registered = new CopyOnWriteArrayList<RegisteredTransformer>();

    public TransformerRegistry(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    public void register(String id, String kind, String description, ClassFileTransformer transformer) {
        instrumentation.addTransformer(transformer, true);
        registered.add(new RegisteredTransformer(id, kind, description, transformer));
    }

    public List<TransformerInfo> list() {
        List<TransformerInfo> infos = new ArrayList<TransformerInfo>();
        for (RegisteredTransformer item : registered) {
            infos.add(TransformerInfo.newBuilder()
                    .setId(item.id)
                    .setKind(item.kind)
                    .setDescription(item.description)
                    .build());
        }
        return infos;
    }

    public boolean isRetransformSupported() {
        return instrumentation.isRetransformClassesSupported();
    }

    public boolean isRedefineSupported() {
        return instrumentation.isRedefineClassesSupported();
    }

    public void close() {
        for (RegisteredTransformer item : registered) {
            instrumentation.removeTransformer(item.transformer);
        }
        registered.clear();
    }

    private static final class RegisteredTransformer {
        private final String id;
        private final String kind;
        private final String description;
        private final ClassFileTransformer transformer;

        private RegisteredTransformer(String id, String kind, String description, ClassFileTransformer transformer) {
            this.id = id;
            this.kind = kind;
            this.description = description;
            this.transformer = transformer;
        }
    }
}
