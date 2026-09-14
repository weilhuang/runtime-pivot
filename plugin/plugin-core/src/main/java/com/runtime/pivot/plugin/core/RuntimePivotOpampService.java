package com.runtime.pivot.plugin.core;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.runtime.pivot.protocol.AuthTokens;
import com.runtime.pivot.protocol.ConnectionConfig;
import com.runtime.pivot.protocol.proto.CommandRequest;
import com.runtime.pivot.protocol.proto.CommandResult;
import com.runtime.pivot.protocol.proto.EventBatch;
import com.runtime.pivot.protocol.transport.OpampServer;
import com.runtime.pivot.protocol.transport.OpampServerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimePivotOpampService implements Disposable, OpampServerListener {
    private static final Logger LOG = Logger.getInstance(RuntimePivotOpampService.class);

    private final Project project;
    private final AtomicReference<OpampServer> server = new AtomicReference<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    private volatile String lastError;
    private final List<String> consoleLines = new CopyOnWriteArrayList<>();

    public RuntimePivotOpampService(Project project) {
        this.project = project;
    }

    public static RuntimePivotOpampService getInstance(Project project) {
        return project.getService(RuntimePivotOpampService.class);
    }

    public synchronized ConnectionConfig ensureStarted() throws IOException {
        OpampServer current = server.get();
        if (current != null) {
            return current.connectionConfig();
        }
        RuntimePivotSettings settings = RuntimePivotSettings.getInstance(project);
        if (!settings.isEnableAgentCommunication()) {
            throw new IOException("Agent communication is disabled");
        }
        ConnectionConfig config = ConnectionConfig.builder()
                .host("127.0.0.1")
                .wsPort(0)
                .httpPort(0)
                .token(AuthTokens.randomToken())
                .sessionId(UUID.randomUUID().toString())
                .eventBufferSize(settings.getEventBufferSize())
                .build();
        OpampServer started = OpampServer.start(config);
        started.addListener(this);
        server.set(started);
        status = ConnectionStatus.LISTENING;
        lastError = null;
        append("Listening for Agent on " + started.connectionConfig().toLogString());
        notifyListeners();
        return started.connectionConfig();
    }

    public ConnectionStatus status() {
        return status;
    }

    @Nullable
    public String lastError() {
        return lastError;
    }

    public boolean isAgentConnected() {
        OpampServer current = server.get();
        return current != null && current.isAgentConnected();
    }

    @Nullable
    public OpampServer.AgentSession agentSession() {
        OpampServer current = server.get();
        return current == null ? null : current.currentSession();
    }

    public List<String> consoleSnapshot() {
        return List.copyOf(consoleLines);
    }

    public CompletableFuture<CommandResult> sendCommand(String command, byte[] payload, long timeoutMs) {
        OpampServer current = server.get();
        if (current == null || !current.isAgentConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Agent is not connected"));
        }
        CommandRequest request = CommandRequest.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setCommand(command)
                .setTimeoutMs(timeoutMs)
                .setPayload(payload == null
                        ? com.google.protobuf.ByteString.EMPTY
                        : com.google.protobuf.ByteString.copyFrom(payload))
                .build();
        append("Command " + command + " [" + request.getRequestId() + "]");
        return current.sendCommand(request, timeoutMs);
    }

    public boolean cancel(String requestId) {
        OpampServer current = server.get();
        return current != null && current.cancel(requestId);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onAgentConnected(OpampServer.AgentSession session) {
        status = ConnectionStatus.CONNECTED;
        lastError = null;
        append("Agent connected; capabilities=" + session.getNegotiatedCustomCapabilities());
        notifyListeners();
    }

    @Override
    public void onAgentDisconnected(OpampServer.AgentSession session) {
        status = ConnectionStatus.LISTENING;
        append("Agent disconnected");
        notifyListeners();
    }

    @Override
    public void onEventBatch(OpampServer.AgentSession session, EventBatch batch) {
        append("EventBatch size=" + batch.getEventsCount() + " dropped=" + batch.getDroppedEvents());
        notifyListeners();
    }

    private void append(String line) {
        consoleLines.add(line);
        if (consoleLines.size() > 2000) {
            consoleLines.remove(0);
        }
        LOG.debug(line);
    }

    private void notifyListeners() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Listener listener : listeners) {
                listener.stateChanged(this);
            }
        }, project.getDisposed());
    }

    @Override
    public void dispose() {
        OpampServer current = server.getAndSet(null);
        if (current != null) {
            current.close();
        }
        status = ConnectionStatus.DISCONNECTED;
    }

    public enum ConnectionStatus {
        DISCONNECTED,
        LISTENING,
        CONNECTED
    }

    public interface Listener {
        void stateChanged(@NotNull RuntimePivotOpampService service);
    }
}
