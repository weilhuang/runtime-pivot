package com.runtime.pivot.protocol.transport;

import com.google.protobuf.ByteString;
import com.runtime.pivot.protocol.AuthTokens;
import com.runtime.pivot.protocol.ConnectionConfig;
import com.runtime.pivot.protocol.CustomMessages;
import com.runtime.pivot.protocol.OpampWire;
import com.runtime.pivot.protocol.PivotCapabilities;
import com.runtime.pivot.protocol.PivotCommands;
import com.runtime.pivot.protocol.ProtocolVersion;
import com.runtime.pivot.protocol.proto.CommandError;
import com.runtime.pivot.protocol.proto.CommandRequest;
import com.runtime.pivot.protocol.proto.CommandResult;
import com.runtime.pivot.protocol.proto.EventBatch;
import opamp.proto.v1.AgentToServer;
import opamp.proto.v1.ConnectionSettingsOffers;
import opamp.proto.v1.CustomCapabilities;
import opamp.proto.v1.CustomMessage;
import opamp.proto.v1.OpAMPConnectionSettings;
import opamp.proto.v1.ServerErrorResponse;
import opamp.proto.v1.ServerErrorResponseType;
import opamp.proto.v1.ServerToAgent;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loopback OpAMP server. IDEA plays the OpAMP Server role; the target JVM Agent connects in.
 */
public final class OpampServer implements Closeable {
    private final String token;
    private final String sessionId;
    private final int maxMessageBytes;
    private final int heartbeatSeconds;
    private final WsServer wsServer;
    private final HttpServer httpServer;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<AgentSession> session = new AtomicReference<AgentSession>();
    private final CopyOnWriteArrayList<OpampServerListener> listeners = new CopyOnWriteArrayList<OpampServerListener>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private OpampServer(String token, String sessionId, int maxMessageBytes, int heartbeatSeconds,
                        WsServer wsServer, HttpServer httpServer) {
        this.token = token;
        this.sessionId = sessionId;
        this.maxMessageBytes = maxMessageBytes;
        this.heartbeatSeconds = heartbeatSeconds;
        this.wsServer = wsServer;
        this.httpServer = httpServer;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "runtime-pivot-opamp-server");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public static OpampServer start(ConnectionConfig config) throws IOException {
        if (!isLoopback(config.getHost())) {
            throw new IOException("Refusing to bind OpAMP server on non-loopback host " + config.getHost());
        }
        InetAddress address = InetAddress.getByName(config.getHost());
        WsServer wsServer = new WsServer(new InetSocketAddress(address, config.getWsPort()), config);
        HttpServer httpServer = new HttpServer(new InetSocketAddress(address, config.getHttpPort()), config);
        OpampServer server = new OpampServer(
                config.getToken(),
                config.getSessionId(),
                config.getMaxMessageBytes(),
                config.getHeartbeatSeconds(),
                wsServer,
                httpServer
        );
        wsServer.owner = server;
        httpServer.owner = server;
        try {
            wsServer.setReuseAddr(true);
            wsServer.start();
            waitUntilWsBound(wsServer);
            httpServer.start();
        } catch (Exception e) {
            server.close();
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to start OpAMP server", e);
        }
        return server;
    }

    public ConnectionConfig connectionConfig() {
        return ConnectionConfig.builder()
                .host("127.0.0.1")
                .wsPort(getWsPort())
                .httpPort(getHttpPort())
                .path(ConnectionConfig.DEFAULT_PATH)
                .token(token)
                .sessionId(sessionId)
                .maxMessageBytes(maxMessageBytes)
                .heartbeatSeconds(heartbeatSeconds)
                .build();
    }

    public int getWsPort() {
        return wsServer.getPort();
    }

    public int getHttpPort() {
        return httpServer.getPort();
    }

    public String getSessionId() {
        return sessionId;
    }

    public AgentSession currentSession() {
        return session.get();
    }

    public boolean isAgentConnected() {
        AgentSession current = session.get();
        return current != null && current.isConnected();
    }

    public void addListener(OpampServerListener listener) {
        listeners.add(listener);
    }

    public void removeListener(OpampServerListener listener) {
        listeners.remove(listener);
    }

    public CompletableFuture<CommandResult> sendCommand(CommandRequest request, long timeoutMs) {
        AgentSession current = session.get();
        if (current == null || !current.isConnected()) {
            CompletableFuture<CommandResult> failed = new CompletableFuture<CommandResult>();
            failed.completeExceptionally(new IllegalStateException("Agent is not connected"));
            return failed;
        }
        return current.sendCommand(request, timeoutMs);
    }

    public boolean cancel(String requestId) {
        AgentSession current = session.get();
        return current != null && current.cancel(requestId);
    }

    void onAgentMessage(TransportSink sink, AgentToServer message) {
        try {
            AgentSession current = session.get();
            if (current == null || !current.instanceUid.equals(message.getInstanceUid())) {
                current = handshake(sink, message);
                if (current == null) {
                    return;
                }
            }
            current.handle(message);
        } catch (ProtocolVersion.ProtocolVersionException e) {
            sink.send(error(message.getInstanceUid(), ServerErrorResponseType.ServerErrorResponseType_BadRequest, e.getMessage()));
        } catch (UnauthorizedException e) {
            sink.close(1008, e.getMessage());
        } catch (Exception e) {
            sink.send(error(message.getInstanceUid(), ServerErrorResponseType.ServerErrorResponseType_Unknown, "Internal error"));
        }
    }

    private AgentSession handshake(TransportSink sink, AgentToServer message) {
        Integer version = readProtocolVersion(message);
        if (version == null) {
            sink.send(error(message.getInstanceUid(), ServerErrorResponseType.ServerErrorResponseType_BadRequest,
                    "Missing " + ProtocolVersion.ATTRIBUTE_KEY));
            sink.close(1002, "protocol version missing");
            return null;
        }
        ProtocolVersion.requireCompatible(version);
        String remoteSession = readSessionId(message);
        if (remoteSession != null && !sessionId.equals(remoteSession)) {
            throw new UnauthorizedException("session mismatch");
        }
        AgentSession created = new AgentSession(this, sink, message);
        AgentSession previous = session.getAndSet(created);
        if (previous != null) {
            previous.close("replaced");
        }
        ServerToAgent.Builder ack = ServerToAgent.newBuilder()
                .setInstanceUid(message.getInstanceUid())
                .setCapabilities(PivotCapabilities.SERVER_OPAMP_BITS)
                .setCustomCapabilities(CustomCapabilities.newBuilder()
                        .addAllCapabilities(PivotCapabilities.serverCustomCapabilities())
                        .build())
                .setConnectionSettings(ConnectionSettingsOffers.newBuilder()
                        .setOpamp(OpAMPConnectionSettings.newBuilder()
                                .setHeartbeatIntervalSeconds(heartbeatSeconds)
                                .build())
                        .build());
        if (message.hasCustomCapabilities()) {
            created.negotiatedCustom.addAll(PivotCapabilities.negotiated(
                    message.getCustomCapabilities().getCapabilitiesList(),
                    PivotCapabilities.serverCustomCapabilities()));
        }
        sink.send(ack.build());
        for (OpampServerListener listener : listeners) {
            listener.onAgentConnected(created);
        }
        return created;
    }

    void onClosed(TransportSink sink) {
        AgentSession current = session.get();
        if (current != null && current.sink == sink) {
            session.compareAndSet(current, null);
            current.failPending(new IOException("Agent disconnected"));
            for (OpampServerListener listener : listeners) {
                listener.onAgentDisconnected(current);
            }
        }
    }

    boolean authorize(String authorization, String sessionHeader, InetAddress remote) {
        if (remote == null || !remote.isLoopbackAddress()) {
            return false;
        }
        if (!AuthTokens.matches(token, AuthTokens.fromAuthorizationHeader(authorization))) {
            return false;
        }
        return sessionHeader == null || sessionHeader.isEmpty() || sessionId.equals(sessionHeader);
    }

    private static Integer readProtocolVersion(AgentToServer message) {
        if (!message.hasAgentDescription()) {
            return null;
        }
        for (opamp.proto.v1.KeyValue attribute : message.getAgentDescription().getIdentifyingAttributesList()) {
            if (ProtocolVersion.ATTRIBUTE_KEY.equals(attribute.getKey())) {
                return com.runtime.pivot.protocol.AttributeValues.intStringValue(attribute);
            }
        }
        return null;
    }

    private static String readSessionId(AgentToServer message) {
        if (!message.hasAgentDescription()) {
            return null;
        }
        for (opamp.proto.v1.KeyValue attribute : message.getAgentDescription().getIdentifyingAttributesList()) {
            if (ProtocolVersion.SESSION_ATTRIBUTE_KEY.equals(attribute.getKey())) {
                return com.runtime.pivot.protocol.AttributeValues.stringValue(attribute);
            }
        }
        return null;
    }

    private static ServerToAgent error(ByteString instanceUid, ServerErrorResponseType type, String message) {
        return ServerToAgent.newBuilder()
                .setInstanceUid(instanceUid)
                .setErrorResponse(ServerErrorResponse.newBuilder()
                        .setType(type)
                        .setErrorMessage(message == null ? "" : message)
                        .build())
                .build();
    }

    private static boolean isLoopback(String host) {
        return ConnectionConfig.DEFAULT_HOST.equals(host) || "localhost".equalsIgnoreCase(host);
    }

    private static void waitUntilWsBound(WsServer server) throws IOException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (server.getPort() > 0) {
                return;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while starting WebSocket server", e);
            }
        }
        throw new IOException("WebSocket server did not bind a loopback port");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        AgentSession current = session.getAndSet(null);
        if (current != null) {
            current.close("server closed");
        }
        scheduler.shutdownNow();
        try {
            wsServer.stop(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        }
        httpServer.close();
    }

    public static final class AgentSession {
        private final OpampServer server;
        private final TransportSink sink;
        private final ByteString instanceUid;
        private final long agentCapabilities;
        private final Set<String> negotiatedCustom = ConcurrentHashMap.newKeySet();
        private final ConcurrentHashMap<String, CompletableFuture<CommandResult>> pending =
                new ConcurrentHashMap<String, CompletableFuture<CommandResult>>();
        private final AtomicBoolean connected = new AtomicBoolean(true);
        private volatile String healthStatus = "unknown";
        private volatile boolean healthy;

        AgentSession(OpampServer server, TransportSink sink, AgentToServer hello) {
            this.server = server;
            this.sink = sink;
            this.instanceUid = hello.getInstanceUid();
            this.agentCapabilities = hello.getCapabilities();
            if (hello.hasHealth()) {
                this.healthy = hello.getHealth().getHealthy();
                this.healthStatus = hello.getHealth().getStatus();
            }
        }

        public boolean isConnected() {
            return connected.get();
        }

        public ByteString getInstanceUid() {
            return instanceUid;
        }

        public long getAgentCapabilities() {
            return agentCapabilities;
        }

        public Set<String> getNegotiatedCustomCapabilities() {
            return Collections.unmodifiableSet(negotiatedCustom);
        }

        public boolean isHealthy() {
            return healthy;
        }

        public String getHealthStatus() {
            return healthStatus;
        }

        CompletableFuture<CommandResult> sendCommand(CommandRequest request, long timeoutMs) {
            final CompletableFuture<CommandResult> future = new CompletableFuture<CommandResult>();
            pending.put(request.getRequestId(), future);
            ServerToAgent message = ServerToAgent.newBuilder()
                    .setInstanceUid(instanceUid)
                    .setCustomMessage(CustomMessages.commandRequest(request))
                    .build();
            sink.send(message);
            long timeout = timeoutMs > 0 ? timeoutMs : request.getTimeoutMs();
            if (timeout > 0) {
                scheduleTimeout(future, request.getRequestId(), timeout);
            }
            return future;
        }

        boolean cancel(String requestId) {
            CompletableFuture<CommandResult> future = pending.remove(requestId);
            sink.send(ServerToAgent.newBuilder()
                    .setInstanceUid(instanceUid)
                    .setCustomMessage(CustomMessages.cancel(requestId))
                    .build());
            if (future != null) {
                future.completeExceptionally(new java.util.concurrent.CancellationException(requestId));
                return true;
            }
            return false;
        }

        void handle(AgentToServer message) {
            if (message.hasHealth()) {
                healthy = message.getHealth().getHealthy();
                healthStatus = message.getHealth().getStatus();
            }
            if (message.hasCustomMessage()) {
                handleCustom(message.getCustomMessage());
            }
            if (message.hasAgentDisconnect()) {
                sink.close(1000, "agent disconnect");
            }
        }

        private void handleCustom(CustomMessage custom) {
            try {
                if (PivotCommands.TYPE_COMMAND_RESULT.equals(custom.getType())) {
                    CommandResult result = CommandResult.parseFrom(custom.getData());
                    CompletableFuture<CommandResult> future = pending.remove(result.getRequestId());
                    if (future != null) {
                        future.complete(result);
                    }
                } else if (PivotCommands.TYPE_COMMAND_ERROR.equals(custom.getType())) {
                    CommandError error = CommandError.parseFrom(custom.getData());
                    CompletableFuture<CommandResult> future = pending.remove(error.getRequestId());
                    if (future != null) {
                        future.completeExceptionally(new CommandFailedException(error));
                    }
                } else if (PivotCommands.TYPE_EVENT_BATCH.equals(custom.getType())) {
                    EventBatch batch = EventBatch.parseFrom(custom.getData());
                    for (OpampServerListener listener : server.listeners) {
                        listener.onEventBatch(this, batch);
                    }
                }
            } catch (IOException e) {
                sink.send(error(instanceUid, ServerErrorResponseType.ServerErrorResponseType_BadRequest, e.getMessage()));
            }
        }

        void failPending(Exception error) {
            for (CompletableFuture<CommandResult> future : pending.values()) {
                future.completeExceptionally(error);
            }
            pending.clear();
        }

        void close(String reason) {
            if (connected.compareAndSet(true, false)) {
                failPending(new IOException(reason));
                sink.close(1000, reason);
            }
        }

        private void scheduleTimeout(final CompletableFuture<CommandResult> future, final String requestId, long timeoutMs) {
            server.scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    if (pending.remove(requestId, future)) {
                        future.completeExceptionally(new java.util.concurrent.TimeoutException(requestId));
                    }
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);
        }
    }

    public static final class CommandFailedException extends Exception {
        private final CommandError error;

        public CommandFailedException(CommandError error) {
            super(error.getCode() + ": " + error.getMessage());
            this.error = error;
        }

        public CommandError getError() {
            return error;
        }
    }

    static final class UnauthorizedException extends RuntimeException {
        UnauthorizedException(String message) {
            super(message);
        }
    }

    interface TransportSink {
        void send(ServerToAgent message);

        void close(int code, String reason);
    }

    private static final class WsServer extends WebSocketServer {
        private OpampServer owner;
        private final ConnectionConfig config;

        private WsServer(InetSocketAddress address, ConnectionConfig config) {
            super(address);
            this.config = config;
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            InetSocketAddress remote = conn.getRemoteSocketAddress();
            InetAddress address = remote == null ? null : remote.getAddress();
            String authorization = handshake.getFieldValue(AuthTokens.HEADER);
            String session = handshake.getFieldValue(AuthTokens.SESSION_HEADER);
            if (!owner.authorize(authorization, session, address)) {
                conn.close(1008, "unauthorized");
            }
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            owner.onClosed(new WsSink(conn, config.getMaxMessageBytes()));
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            conn.close(1003, "OpAMP WebSocket requires binary frames");
        }

        @Override
        public void onMessage(WebSocket conn, ByteBuffer message) {
            try {
                byte[] data = new byte[message.remaining()];
                message.get(data);
                AgentToServer parsed = OpampWire.decodeAgentToServerWs(data, config.getMaxMessageBytes());
                owner.onAgentMessage(new WsSink(conn, config.getMaxMessageBytes()), parsed);
            } catch (Exception e) {
                conn.close(1002, "malformed OpAMP frame");
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            if (conn != null) {
                conn.close();
            }
        }

        @Override
        public void onStart() {
            // Port is available via getPort() after bind.
        }
    }

    private static final class WsSink implements TransportSink {
        private final WebSocket socket;
        private final int maxBytes;

        private WsSink(WebSocket socket, int maxBytes) {
            this.socket = socket;
            this.maxBytes = maxBytes;
        }

        @Override
        public void send(ServerToAgent message) {
            if (socket == null || !socket.isOpen()) {
                return;
            }
            try {
                socket.send(OpampWire.encodeServerToAgentWs(message, maxBytes));
            } catch (IOException ignored) {
            }
        }

        @Override
        public void close(int code, String reason) {
            if (socket != null && socket.isOpen()) {
                socket.close(code, reason);
            }
        }
    }

    private static final class HttpServer implements Closeable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final ConnectionConfig config;
        private OpampServer owner;

        private HttpServer(InetSocketAddress address, ConnectionConfig config) throws IOException {
            this.config = config;
            this.serverSocket = new ServerSocket();
            this.serverSocket.bind(address);
            this.thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    acceptLoop();
                }
            }, "runtime-pivot-opamp-http");
            this.thread.setDaemon(true);
        }

        void start() {
            thread.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        private void acceptLoop() {
            while (running.get()) {
                try {
                    Socket socket = serverSocket.accept();
                    handle(socket);
                } catch (SocketException closed) {
                    if (!running.get()) {
                        return;
                    }
                } catch (IOException ignored) {
                }
            }
        }

        private void handle(Socket socket) {
            try {
                if (!socket.getInetAddress().isLoopbackAddress()) {
                    socket.close();
                    return;
                }
                InputStream in = socket.getInputStream();
                ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
                int state = 0;
                int read;
                while ((read = in.read()) != -1) {
                    headerBuf.write(read);
                    if (state == 0 && read == '\r') {
                        state = 1;
                    } else if (state == 1 && read == '\n') {
                        state = 2;
                    } else if (state == 2 && read == '\r') {
                        state = 3;
                    } else if (state == 3 && read == '\n') {
                        break;
                    } else {
                        state = 0;
                    }
                    if (headerBuf.size() > 16 * 1024) {
                        socket.close();
                        return;
                    }
                }
                String headerText = new String(headerBuf.toByteArray(), StandardCharsets.US_ASCII);
                HttpRequest request = HttpRequest.parse(headerText);
                if (!"POST".equals(request.method) || !config.getPath().equals(request.path)) {
                    write(socket, 404, "Not Found", new byte[0]);
                    return;
                }
                if (!owner.authorize(request.authorization, request.session, socket.getInetAddress())) {
                    write(socket, 401, "Unauthorized", new byte[0]);
                    return;
                }
                byte[] body = readFully(in, request.contentLength, config.getMaxMessageBytes());
                AgentToServer message = OpampWire.decodeAgentToServerHttp(body, config.getMaxMessageBytes());
                HttpSink sink = new HttpSink(config.getMaxMessageBytes());
                owner.onAgentMessage(sink, message);
                byte[] response = sink.body == null ? new byte[0] : sink.body;
                write(socket, 200, "OK", response);
            } catch (Exception e) {
                try {
                    write(socket, 400, "Bad Request", new byte[0]);
                } catch (IOException ignored) {
                }
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private static byte[] readFully(InputStream in, int length, int max) throws IOException {
            if (length < 0 || length > max) {
                throw new IOException("Invalid Content-Length");
            }
            byte[] body = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = in.read(body, offset, length - offset);
                if (read < 0) {
                    throw new IOException("Unexpected EOF");
                }
                offset += read;
            }
            return body;
        }

        private static void write(Socket socket, int status, String reason, byte[] body) throws IOException {
            OutputStream out = socket.getOutputStream();
            String header = "HTTP/1.1 " + status + " " + reason + "\r\n"
                    + "Content-Type: " + OpampWire.CONTENT_TYPE + "\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
        }

        @Override
        public void close() {
            running.set(false);
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final class HttpSink implements TransportSink {
        private final int maxBytes;
        private volatile byte[] body;

        private HttpSink(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public void send(ServerToAgent message) {
            try {
                body = message.toByteArray();
                if (body.length > maxBytes) {
                    body = new byte[0];
                }
            } catch (Exception ignored) {
            }
        }

        @Override
        public void close(int code, String reason) {
            // HTTP is request/response; connection is closed by the server after writing.
        }
    }

    private static final class HttpRequest {
        private final String method;
        private final String path;
        private final int contentLength;
        private final String authorization;
        private final String session;

        private HttpRequest(String method, String path, int contentLength, String authorization, String session) {
            this.method = method;
            this.path = path;
            this.contentLength = contentLength;
            this.authorization = authorization;
            this.session = session;
        }

        static HttpRequest parse(String headerText) {
            String[] lines = headerText.split("\r\n");
            if (lines.length == 0) {
                throw new IllegalArgumentException("Empty HTTP request");
            }
            String[] requestLine = lines[0].split(" ");
            String method = requestLine[0];
            String path = requestLine.length > 1 ? requestLine[1] : "/";
            int q = path.indexOf('?');
            if (q >= 0) {
                path = path.substring(0, q);
            }
            int contentLength = 0;
            String authorization = null;
            String session = null;
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if ("content-length".equals(name)) {
                    contentLength = Integer.parseInt(value);
                } else if ("authorization".equals(name)) {
                    authorization = value;
                } else if (AuthTokens.SESSION_HEADER.toLowerCase(Locale.ROOT).equals(name)) {
                    session = value;
                }
            }
            return new HttpRequest(method, path, contentLength, authorization, session);
        }
    }
}
