package com.runtime.pivot.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Encoded as {@code -javaagent:agent.jar=host=127.0.0.1;wsPort=1;httpPort=2;token=...;session=...}.
 * Tokens are never included in {@link #toLogString()}.
 */
public final class ConnectionConfig {
    public static final String DEFAULT_PATH = "/v1/opamp";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_MAX_MESSAGE_BYTES = 4 * 1024 * 1024;
    public static final int DEFAULT_HEARTBEAT_SECONDS = 30;
    public static final int DEFAULT_EVENT_BUFFER = 10_000;

    private final String host;
    private final int wsPort;
    private final int httpPort;
    private final String path;
    private final String token;
    private final String sessionId;
    private final int maxMessageBytes;
    private final int heartbeatSeconds;
    private final int eventBufferSize;

    private ConnectionConfig(Builder builder) {
        this.host = builder.host;
        this.wsPort = builder.wsPort;
        this.httpPort = builder.httpPort;
        this.path = builder.path;
        this.token = builder.token;
        this.sessionId = builder.sessionId;
        this.maxMessageBytes = builder.maxMessageBytes;
        this.heartbeatSeconds = builder.heartbeatSeconds;
        this.eventBufferSize = builder.eventBufferSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getHost() {
        return host;
    }

    public int getWsPort() {
        return wsPort;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public String getPath() {
        return path;
    }

    public String getToken() {
        return token;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getMaxMessageBytes() {
        return maxMessageBytes;
    }

    public int getHeartbeatSeconds() {
        return heartbeatSeconds;
    }

    public int getEventBufferSize() {
        return eventBufferSize;
    }

    public String websocketUrl() {
        return "ws://" + host + ":" + wsPort + path;
    }

    public String httpUrl() {
        return "http://" + host + ":" + httpPort + path;
    }

    public String toAgentArgument() {
        StringBuilder builder = new StringBuilder();
        append(builder, "host", host);
        append(builder, "wsPort", Integer.toString(wsPort));
        append(builder, "httpPort", Integer.toString(httpPort));
        append(builder, "path", path);
        append(builder, "token", token);
        append(builder, "session", sessionId);
        append(builder, "maxMessageBytes", Integer.toString(maxMessageBytes));
        append(builder, "heartbeatSeconds", Integer.toString(heartbeatSeconds));
        append(builder, "eventBufferSize", Integer.toString(eventBufferSize));
        return builder.toString();
    }

    public String toLogString() {
        return "host=" + host
                + ";wsPort=" + wsPort
                + ";httpPort=" + httpPort
                + ";path=" + path
                + ";session=" + sessionId
                + ";token=" + AuthTokens.redact(token);
    }

    public static ConnectionConfig parse(String agentArgs) {
        if (agentArgs == null || agentArgs.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent connection arguments are required");
        }
        Map<String, String> values = split(agentArgs);
        Builder builder = builder();
        if (values.containsKey("host")) {
            builder.host(values.get("host"));
        }
        if (values.containsKey("wsPort")) {
            builder.wsPort(Integer.parseInt(values.get("wsPort")));
        }
        if (values.containsKey("httpPort")) {
            builder.httpPort(Integer.parseInt(values.get("httpPort")));
        }
        if (values.containsKey("path")) {
            builder.path(values.get("path"));
        }
        if (values.containsKey("token")) {
            builder.token(values.get("token"));
        }
        if (values.containsKey("session")) {
            builder.sessionId(values.get("session"));
        }
        if (values.containsKey("maxMessageBytes")) {
            builder.maxMessageBytes(Integer.parseInt(values.get("maxMessageBytes")));
        }
        if (values.containsKey("heartbeatSeconds")) {
            builder.heartbeatSeconds(Integer.parseInt(values.get("heartbeatSeconds")));
        }
        if (values.containsKey("eventBufferSize")) {
            builder.eventBufferSize(Integer.parseInt(values.get("eventBufferSize")));
        }
        return builder.build();
    }

    private static void append(StringBuilder builder, String key, String value) {
        if (builder.length() > 0) {
            builder.append(';');
        }
        builder.append(key).append('=').append(value);
    }

    private static Map<String, String> split(String agentArgs) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        String[] parts = agentArgs.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Invalid agent argument pair: " + trimmed);
            }
            values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return values;
    }

    public static final class Builder {
        private String host = DEFAULT_HOST;
        private int wsPort;
        private int httpPort;
        private String path = DEFAULT_PATH;
        private String token;
        private String sessionId;
        private int maxMessageBytes = DEFAULT_MAX_MESSAGE_BYTES;
        private int heartbeatSeconds = DEFAULT_HEARTBEAT_SECONDS;
        private int eventBufferSize = DEFAULT_EVENT_BUFFER;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder wsPort(int wsPort) {
            this.wsPort = wsPort;
            return this;
        }

        public Builder httpPort(int httpPort) {
            this.httpPort = httpPort;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder maxMessageBytes(int maxMessageBytes) {
            this.maxMessageBytes = maxMessageBytes;
            return this;
        }

        public Builder heartbeatSeconds(int heartbeatSeconds) {
            this.heartbeatSeconds = heartbeatSeconds;
            return this;
        }

        public Builder eventBufferSize(int eventBufferSize) {
            this.eventBufferSize = eventBufferSize;
            return this;
        }

        public ConnectionConfig build() {
            if (!DEFAULT_HOST.equals(host) && !"localhost".equalsIgnoreCase(host)) {
                throw new IllegalArgumentException("Agent communication is loopback-only; refused host " + host);
            }
            if (token == null || token.trim().isEmpty()) {
                throw new IllegalArgumentException("Connection token is required");
            }
            if (sessionId == null || sessionId.trim().isEmpty()) {
                throw new IllegalArgumentException("session id is required");
            }
            if (!path.startsWith("/")) {
                throw new IllegalArgumentException("path must start with /");
            }
            if (maxMessageBytes <= 0) {
                throw new IllegalArgumentException("maxMessageBytes must be positive");
            }
            return new ConnectionConfig(this);
        }
    }
}
