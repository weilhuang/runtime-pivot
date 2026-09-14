package com.runtime.pivot.agent.core;

import com.runtime.pivot.protocol.PivotCommands;
import com.runtime.pivot.protocol.proto.ClassDumpRequest;
import com.runtime.pivot.protocol.proto.ClassDumpResult;
import com.runtime.pivot.protocol.proto.ClassLoaderNode;
import com.runtime.pivot.protocol.proto.ClassLoaderTreeResult;
import com.runtime.pivot.protocol.proto.ClassLoadingTimelineRequest;
import com.runtime.pivot.protocol.proto.ClassLoadingTimelineResult;
import com.runtime.pivot.protocol.proto.CommandRequest;
import com.runtime.pivot.protocol.proto.CommandResult;
import com.runtime.pivot.protocol.proto.LoadedClassInfo;
import com.runtime.pivot.protocol.proto.LoadedClassesRequest;
import com.runtime.pivot.protocol.proto.LoadedClassesResult;
import com.runtime.pivot.protocol.proto.RuntimePivotTransformerResult;
import com.runtime.pivot.protocol.transport.CommandContext;
import com.runtime.pivot.protocol.transport.CommandHandler;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClassQueryService {
    private final Instrumentation instrumentation;
    private final ClassLoadingRecorder recorder;
    private final TransformerRegistry transformers;

    public ClassQueryService(Instrumentation instrumentation, ClassLoadingRecorder recorder,
                             TransformerRegistry transformers) {
        this.instrumentation = instrumentation;
        this.recorder = recorder;
        this.transformers = transformers;
    }

    public CommandHandler loadersHandler() {
        return new CommandHandler() {
            @Override
            public String command() {
                return PivotCommands.CLASS_LOADERS;
            }

            @Override
            public CommandResult execute(CommandRequest request, CommandContext context) {
                return result(request, loaders().toByteString());
            }
        };
    }

    public CommandHandler loadedHandler() {
        return new CommandHandler() {
            @Override
            public String command() {
                return PivotCommands.CLASS_LOADED;
            }

            @Override
            public CommandResult execute(CommandRequest request, CommandContext context) throws Exception {
                LoadedClassesRequest payload = request.getPayload().isEmpty()
                        ? LoadedClassesRequest.getDefaultInstance()
                        : LoadedClassesRequest.parseFrom(request.getPayload());
                return result(request, loaded(payload).toByteString());
            }
        };
    }

    public CommandHandler dumpHandler() {
        return new CommandHandler() {
            @Override
            public String command() {
                return PivotCommands.CLASS_DUMP;
            }

            @Override
            public CommandResult execute(CommandRequest request, CommandContext context) throws Exception {
                ClassDumpRequest payload = ClassDumpRequest.parseFrom(request.getPayload());
                return result(request, dump(payload).toByteString());
            }
        };
    }

    public CommandHandler timelineHandler() {
        return new CommandHandler() {
            @Override
            public String command() {
                return PivotCommands.CLASS_LOADING_TIMELINE;
            }

            @Override
            public CommandResult execute(CommandRequest request, CommandContext context) throws Exception {
                ClassLoadingTimelineRequest payload = request.getPayload().isEmpty()
                        ? ClassLoadingTimelineRequest.getDefaultInstance()
                        : ClassLoadingTimelineRequest.parseFrom(request.getPayload());
                int limit = payload.getLimit() > 0 ? payload.getLimit() : 1000;
                ClassLoadingTimelineResult result = ClassLoadingTimelineResult.newBuilder()
                        .addAllEvents(recorder.snapshot(limit))
                        .setDroppedEvents(recorder.droppedEvents())
                        .build();
                return result(request, result.toByteString());
            }
        };
    }

    public CommandHandler transformerHandler() {
        return new CommandHandler() {
            @Override
            public String command() {
                return PivotCommands.TRANSFORMER_LIST;
            }

            @Override
            public CommandResult execute(CommandRequest request, CommandContext context) {
                RuntimePivotTransformerResult result = RuntimePivotTransformerResult.newBuilder()
                        .addAllTransformers(transformers.list())
                        .setRetransformSupported(transformers.isRetransformSupported())
                        .setRedefineSupported(transformers.isRedefineSupported())
                        .build();
                return result(request, result.toByteString());
            }
        };
    }

    ClassLoaderTreeResult loaders() {
        Map<String, ClassLoaderNode.Builder> nodes = new LinkedHashMap<String, ClassLoaderNode.Builder>();
        Class[] loaded = instrumentation.getAllLoadedClasses();
        for (Class<?> type : loaded) {
            ClassLoader loader = type.getClassLoader();
            addLoaderChain(nodes, loader);
            String id = recorder.loaderId(loader);
            ClassLoaderNode.Builder node = nodes.get(id);
            if (node != null) {
                node.setLoadedClassCount(node.getLoadedClassCount() + 1);
            }
        }
        ClassLoaderTreeResult.Builder result = ClassLoaderTreeResult.newBuilder();
        for (ClassLoaderNode.Builder node : nodes.values()) {
            result.addNodes(node.build());
        }
        return result.build();
    }

    LoadedClassesResult loaded(LoadedClassesRequest request) {
        int limit = request.getLimit() > 0 ? request.getLimit() : 10_000;
        String prefix = request.getNamePrefix() == null ? "" : request.getNamePrefix();
        String loaderId = request.getLoaderId();
        LoadedClassesResult.Builder result = LoadedClassesResult.newBuilder();
        int truncated = 0;
        int added = 0;
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            if (!prefix.isEmpty() && !type.getName().startsWith(prefix)) {
                continue;
            }
            String id = recorder.loaderId(type.getClassLoader());
            if (loaderId != null && !loaderId.isEmpty() && !loaderId.equals(id)) {
                continue;
            }
            if (added >= limit) {
                truncated++;
                continue;
            }
            result.addClasses(LoadedClassInfo.newBuilder()
                    .setClassName(type.getName())
                    .setLoaderId(id)
                    .setInterfaceType(type.isInterface())
                    .build());
            added++;
        }
        return result.setTruncated(truncated).build();
    }

    ClassDumpResult dump(ClassDumpRequest request) throws Exception {
        if (request.getClassName() == null || request.getClassName().trim().isEmpty()) {
            throw new IllegalArgumentException("class_name is required");
        }
        List<Class<?>> matches = new ArrayList<Class<?>>();
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            if (!request.getClassName().equals(type.getName())) {
                continue;
            }
            if (request.getLoaderId() != null && !request.getLoaderId().isEmpty()
                    && !request.getLoaderId().equals(recorder.loaderId(type.getClassLoader()))) {
                continue;
            }
            matches.add(type);
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Class not found: " + request.getClassName());
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Multiple ClassLoaders define " + request.getClassName()
                    + "; pass loader_id to select one");
        }
        Class<?> target = matches.get(0);
        if (!instrumentation.isModifiableClass(target)) {
            throw new IllegalStateException("Class is not modifiable: " + target.getName());
        }
        DumpTransformer transformer = new DumpTransformer(target);
        instrumentation.addTransformer(transformer, true);
        try {
            instrumentation.retransformClasses(target);
        } finally {
            instrumentation.removeTransformer(transformer);
        }
        if (transformer.bytecode == null) {
            throw new IllegalStateException("Failed to capture bytecode for " + target.getName());
        }
        return ClassDumpResult.newBuilder()
                .setClassName(target.getName())
                .setLoaderId(recorder.loaderId(target.getClassLoader()))
                .setBytecode(com.google.protobuf.ByteString.copyFrom(transformer.bytecode))
                .setSha256(sha256(transformer.bytecode))
                .setModifiable(true)
                .build();
    }

    private void addLoaderChain(Map<String, ClassLoaderNode.Builder> nodes, ClassLoader loader) {
        ClassLoader current = loader;
        while (true) {
            String id = recorder.loaderId(current);
            if (!nodes.containsKey(id)) {
                ClassLoader parent = current == null ? null : current.getParent();
                nodes.put(id, ClassLoaderNode.newBuilder()
                        .setLoaderId(id)
                        .setName(recorder.loaderName(current))
                        .setParentId(current == null ? "" : recorder.loaderId(parent)));
            }
            if (current == null) {
                return;
            }
            current = current.getParent();
        }
    }

    private static CommandResult result(CommandRequest request, com.google.protobuf.ByteString payload) {
        return CommandResult.newBuilder()
                .setRequestId(request.getRequestId())
                .setCommand(request.getCommand())
                .setPayload(payload)
                .build();
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    private static final class DumpTransformer implements ClassFileTransformer {
        private final Class<?> target;
        private volatile byte[] bytecode;

        private DumpTransformer(Class<?> target) {
            this.target = target;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            if (classBeingRedefined == target) {
                bytecode = classfileBuffer;
            }
            return null;
        }
    }
}
