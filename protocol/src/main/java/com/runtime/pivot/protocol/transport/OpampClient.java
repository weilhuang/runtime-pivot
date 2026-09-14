package com.runtime.pivot.protocol.transport;

import com.google.protobuf.ByteString;
import com.runtime.pivot.protocol.AttributeValues;
import com.runtime.pivot.protocol.AuthTokens;
import com.runtime.pivot.protocol.ConnectionConfig;
import com.runtime.pivot.protocol.CustomMessages;
import com.runtime.pivot.protocol.InstanceUids;
import com.runtime.pivot.protocol.OpampWire;
import com.runtime.pivot.protocol.PivotCapabilities;
import com.runtime.pivot.protocol.PivotCommands;
import com.runtime.pivot.protocol.ProtocolVersion;
import com.runtime.pivot.protocol.proto.CancelCommand;
import com.runtime.pivot.protocol.proto.CommandError;
import com.runtime.pivot.protocol.proto.CommandRequest;
import com.runtime.pivot.protocol.proto.CommandResult;
import opamp.proto.v1.AgentDescription;
import opamp.proto.v1.AgentDisconnect;
import opamp.proto.v1.AgentToServer;
import opamp.proto.v1.ComponentHealth;
import opamp.proto.v1.CustomCapabilities;
import opamp.proto.v1.CustomMessage;
import opamp.proto.v1.ServerErrorResponse;
import opamp.proto.v1.ServerToAgent;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OpAMP Agent client. Prefers WebSocket for push/events and can fall back to HTTP polling.
 */
public final class OpampClient implements Closeable {
    private final ConnectionConfig config;
    private final String agentVersion;
    private final ByteString instanceUid;
    private final CommandDispatcher dispatcher;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService workers;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ConcurrentHashMap<String, CommandContext> inFlight = new ConcurrentHashMap<String, CommandContext>();
    private final long startNanos = System.nanoTime();
    private final long startUnixNano = System.currentTimeMillis() * 1_000_000L;
    private volatile ClientWs ws;
    private volatile boolean websocketTransport = true;
    private volatile long heartbeatSeconds;
    private final AtomicBoolean handshakeComplete = new AtomicBoolean();

    public OpampClient(ConnectionConfig config, String agentVersion, Map<String, CommandHandler> handlers) {
        this.config = config;
        this.agentVersion = agentVersion == null ? "3.0.0" : agentVersion;
        this.instanceUid = InstanceUids.randomV7();
        this.dispatcher = new CommandDispatcher(handlers);
        this.heartbeatSeconds = config.getHeartbeatSeconds();
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                thread.setName("runtime-pivot-opamp-client");
                return thread;
            }
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(factory);
        this.workers = Executors.newCachedThreadPool(factory);
    }

    public void start() throws Exception {
        try {
            connectWebSocket();
        } catch (Exception wsFailure) {
            websocketTransport = false;
            sendHttp(statusReport(true));
            scheduleHttpPoll();
        }
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                heartbeat();
            }
        }, heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
    }

    public boolean isConnected() {
        if (closed.get()) {
            return false;
        }
        if (websocketTransport) {
            return ws != null && ws.isOpen() && handshakeComplete.get();
        }
        return handshakeComplete.get();
    }

    public ByteString getInstanceUid() {
        return instanceUid;
    }

    private void connectWebSocket() throws Exception {
        URI uri = URI.create(config.websocketUrl());
        ws = new ClientWs(uri);
        ws.addHeader(AuthTokens.HEADER, AuthTokens.bearer(config.getToken()));
        ws.addHeader(AuthTokens.SESSION_HEADER, config.getSessionId());
        if (!ws.connectBlocking(5, TimeUnit.SECONDS) || !ws.isOpen()) {
            throw new IOException("WebSocket connect failed");
        }
        ws.send(OpampWire.encodeAgentToServerWs(statusReport(true), config.getMaxMessageBytes()));
    }

    private void heartbeat() {
        if (closed.get()) {
            return;
        }
        try {
            if (websocketTransport && ws != null && ws.isOpen()) {
                ws.send(OpampWire.encodeAgentToServerWs(statusReport(false), config.getMaxMessageBytes()));
            } else if (!websocketTransport) {
                sendHttp(statusReport(false));
            }
        } catch (Exception ignored) {
        }
    }

    private void scheduleHttpPoll() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (websocketTransport || closed.get()) {
                    return;
                }
                try {
                    sendHttp(statusReport(false));
                } catch (Exception ignored) {
                }
            }
        }, heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
    }

    private AgentToServer statusReport(boolean full) {
        AgentToServer.Builder builder = AgentToServer.newBuilder()
                .setInstanceUid(instanceUid)
                .setSequenceNum(sequence.incrementAndGet())
                .setCapabilities(PivotCapabilities.AGENT_OPAMP_BITS)
                .setHealth(ComponentHealth.newBuilder()
                        .setHealthy(true)
                        .setStartTimeUnixNano(startUnixNano)
                        .setStatus("ok")
                        .build());
        if (full) {
            builder.setAgentDescription(AgentDescription.newBuilder()
                    .addIdentifyingAttributes(AttributeValues.stringAttribute(ProtocolVersion.SERVICE_NAME_KEY, ProtocolVersion.SERVICE_NAME))
                    .addIdentifyingAttributes(AttributeValues.stringAttribute(ProtocolVersion.SERVICE_VERSION_KEY, agentVersion))
                    .addIdentifyingAttributes(AttributeValues.stringAttribute(ProtocolVersion.SERVICE_INSTANCE_ID_KEY, InstanceUids.fromBytes(instanceUid).toString()))
                    .addIdentifyingAttributes(AttributeValues.stringAttribute(ProtocolVersion.ATTRIBUTE_KEY, Integer.toString(ProtocolVersion.CURRENT)))
                    .addIdentifyingAttributes(AttributeValues.stringAttribute(ProtocolVersion.SESSION_ATTRIBUTE_KEY, config.getSessionId()))
                    .addNonIdentifyingAttributes(AttributeValues.stringAttribute("os.type", System.getProperty("os.name")))
                    .addNonIdentifyingAttributes(AttributeValues.stringAttribute("java.version", System.getProperty("java.version")))
                    .addNonIdentifyingAttributes(AttributeValues.stringAttribute("process.pid", Long.toString(processId())))
                    .build());
            builder.setCustomCapabilities(CustomCapabilities.newBuilder()
                    .addAllCapabilities(PivotCapabilities.agentCustomCapabilities())
                    .build());
        }
        return builder.build();
    }

    private void handleServerMessage(ServerToAgent message) {
        if (message.hasErrorResponse()) {
            ServerErrorResponse error = message.getErrorResponse();
            handshakeComplete.set(false);
            throw new IllegalStateException("OpAMP server error: " + error.getErrorMessage());
        }
        handshakeComplete.set(true);
        if (message.hasConnectionSettings() && message.getConnectionSettings().hasOpamp()) {
            long offered = message.getConnectionSettings().getOpamp().getHeartbeatIntervalSeconds();
            if (offered > 0) {
                heartbeatSeconds = offered;
            }
        }
        if (message.hasCustomMessage()) {
            handleCustom(message.getCustomMessage());
        }
    }

    private void handleCustom(final CustomMessage custom) {
        try {
            if (PivotCommands.TYPE_COMMAND_REQUEST.equals(custom.getType())) {
                final CommandRequest request = CommandRequest.parseFrom(custom.getData());
                final CommandContext context = new CommandContext();
                inFlight.put(request.getRequestId(), context);
                workers.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            sendCustom(CustomMessages.commandAccepted(request.getRequestId()));
                            CommandResult result = dispatcher.dispatch(request, context);
                            sendCustom(CustomMessages.commandResult(result));
                        } catch (java.util.concurrent.CancellationException cancelled) {
                            sendCustom(CustomMessages.commandError(CommandError.newBuilder()
                                    .setRequestId(request.getRequestId())
                                    .setCode(PivotCommands.ERROR_CANCELLED)
                                    .setMessage("cancelled")
                                    .build()));
                        } catch (Exception e) {
                            sendCustom(CustomMessages.commandError(CommandError.newBuilder()
                                    .setRequestId(request.getRequestId())
                                    .setCode(dispatcher.isUnsupported(e) ? PivotCommands.ERROR_UNSUPPORTED : PivotCommands.ERROR_INTERNAL)
                                    .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                                    .setStack(stack(e))
                                    .build()));
                        } finally {
                            inFlight.remove(request.getRequestId());
                        }
                    }
                });
            } else if (PivotCommands.TYPE_CANCEL_COMMAND.equals(custom.getType())) {
                CancelCommand cancel = CancelCommand.parseFrom(custom.getData());
                CommandContext context = inFlight.get(cancel.getRequestId());
                if (context != null) {
                    context.cancel();
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void sendCustom(CustomMessage custom) {
        AgentToServer message = AgentToServer.newBuilder()
                .setInstanceUid(instanceUid)
                .setSequenceNum(sequence.incrementAndGet())
                .setCustomMessage(custom)
                .build();
        try {
            if (websocketTransport && ws != null && ws.isOpen()) {
                ws.send(OpampWire.encodeAgentToServerWs(message, config.getMaxMessageBytes()));
            } else {
                sendHttp(message);
            }
        } catch (Exception ignored) {
        }
    }

    private void sendHttp(AgentToServer message) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(config.httpUrl()).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Content-Type", OpampWire.CONTENT_TYPE);
        connection.setRequestProperty(AuthTokens.HEADER, AuthTokens.bearer(config.getToken()));
        connection.setRequestProperty(AuthTokens.SESSION_HEADER, config.getSessionId());
        byte[] body = message.toByteArray();
        connection.setFixedLengthStreamingMode(body.length);
        OutputStream out = connection.getOutputStream();
        try {
            out.write(body);
        } finally {
            out.close();
        }
        int status = connection.getResponseCode();
        InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        byte[] response = readAll(in);
        if (status == 401) {
            throw new IOException("unauthorized");
        }
        if (status != 200) {
            throw new IOException("HTTP " + status);
        }
        if (response.length > 0) {
            handleServerMessage(OpampWire.decodeServerToAgentHttp(response, config.getMaxMessageBytes()));
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static long processId() {
        try {
            String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            int at = name.indexOf('@');
            return at > 0 ? Long.parseLong(name.substring(0, at)) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String stack(Exception e) {
        StringBuilder builder = new StringBuilder();
        builder.append(e.getClass().getName()).append(": ").append(e.getMessage());
        StackTraceElement[] frames = e.getStackTrace();
        int limit = Math.min(8, frames.length);
        for (int i = 0; i < limit; i++) {
            builder.append("\n  at ").append(frames[i]);
        }
        return builder.toString();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            AgentToServer disconnect = AgentToServer.newBuilder()
                    .setInstanceUid(instanceUid)
                    .setSequenceNum(sequence.incrementAndGet())
                    .setAgentDisconnect(AgentDisconnect.getDefaultInstance())
                    .build();
            if (websocketTransport && ws != null && ws.isOpen()) {
                ws.send(OpampWire.encodeAgentToServerWs(disconnect, config.getMaxMessageBytes()));
                ws.closeBlocking();
            }
        } catch (Exception ignored) {
        }
        scheduler.shutdownNow();
        workers.shutdownNow();
    }

    private final class ClientWs extends WebSocketClient {
        private ClientWs(URI uri) {
            super(uri);
            setTcpNoDelay(true);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
        }

        @Override
        public void onMessage(String message) {
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            try {
                byte[] data = new byte[bytes.remaining()];
                bytes.get(data);
                handleServerMessage(OpampWire.decodeServerToAgentWs(data, config.getMaxMessageBytes()));
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            handshakeComplete.set(false);
        }

        @Override
        public void onError(Exception ex) {
        }
    }

    static final class CommandDispatcher {
        private final Map<String, CommandHandler> handlers;

        CommandDispatcher(Map<String, CommandHandler> handlers) {
            this.handlers = handlers;
        }

        CommandResult dispatch(CommandRequest request, CommandContext context) throws Exception {
            CommandHandler handler = handlers.get(request.getCommand());
            if (handler == null) {
                throw new UnsupportedCommandException(request.getCommand());
            }
            context.throwIfCancelled();
            return handler.execute(request, context);
        }

        boolean isUnsupported(Exception e) {
            return e instanceof UnsupportedCommandException;
        }
    }

    static final class UnsupportedCommandException extends Exception {
        UnsupportedCommandException(String command) {
            super("Unsupported command: " + command);
        }
    }
}
