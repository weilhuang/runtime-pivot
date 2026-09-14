package com.runtime.pivot.protocol;

import com.runtime.pivot.protocol.proto.CommandRequest;
import com.runtime.pivot.protocol.proto.CommandResult;
import com.runtime.pivot.protocol.proto.PingRequest;
import com.runtime.pivot.protocol.proto.PingResult;
import com.runtime.pivot.protocol.transport.CommandContext;
import com.runtime.pivot.protocol.transport.CommandHandler;
import com.runtime.pivot.protocol.transport.OpampClient;
import com.runtime.pivot.protocol.transport.OpampServer;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OpampHandshakeTest {
    @Test
    public void websocketHandshakeAndPing() throws Exception {
        String token = AuthTokens.randomToken();
        ConnectionConfig serverConfig = ConnectionConfig.builder()
                .host("127.0.0.1")
                .wsPort(0)
                .httpPort(0)
                .token(token)
                .sessionId("session-handshake")
                .heartbeatSeconds(30)
                .build();
        OpampServer server = OpampServer.start(serverConfig);
        OpampClient client = new OpampClient(server.connectionConfig(), "3.0.0-test",
                Collections.<String, CommandHandler>singletonMap(PivotCommands.PING, pingHandler()));
        try {
            client.start();
            waitUntil(server::isAgentConnected, 8_000);
            CommandResult result = server.sendCommand(CommandRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setCommand(PivotCommands.PING)
                    .setTimeoutMs(5_000)
                    .setPayload(PingRequest.newBuilder().setEcho("hello").build().toByteString())
                    .build(), 5_000).get(8, TimeUnit.SECONDS);
            PingResult ping = PingResult.parseFrom(result.getPayload());
            assertEquals("hello", ping.getEcho());
            assertTrue(ping.getAgentNanoTime() > 0);
            assertTrue(server.currentSession().getNegotiatedCustomCapabilities().contains(PivotCapabilities.CLASSES));
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    public void websocketDisconnectClearsConnectedSession() throws Exception {
        String token = AuthTokens.randomToken();
        OpampServer server = OpampServer.start(ConnectionConfig.builder()
                .token(token)
                .sessionId("session-disconnect")
                .build());
        OpampClient client = new OpampClient(server.connectionConfig(), "3.0.0-test",
                Collections.<String, CommandHandler>emptyMap());
        try {
            client.start();
            waitUntil(server::isAgentConnected, 8_000);
            client.close();
            waitUntil(new Check() {
                @Override
                public boolean ok() {
                    return !server.isAgentConnected();
                }
            }, 8_000);
        } finally {
            server.close();
        }
    }

    @Test
    public void httpFallbackHandshakeAndPing() throws Exception {
        String token = AuthTokens.randomToken();
        OpampServer server = OpampServer.start(ConnectionConfig.builder()
                .token(token)
                .sessionId("session-http")
                .heartbeatSeconds(1)
                .build());
        ConnectionConfig httpOnly = ConnectionConfig.builder()
                .host("127.0.0.1")
                .wsPort(1)
                .httpPort(server.getHttpPort())
                .token(token)
                .sessionId("session-http")
                .heartbeatSeconds(1)
                .build();
        OpampClient client = new OpampClient(httpOnly, "3.0.0-test",
                Collections.<String, CommandHandler>singletonMap(PivotCommands.PING, pingHandler()));
        try {
            client.start();
            waitUntil(server::isAgentConnected, 8_000);
            CommandResult result = server.sendCommand(CommandRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setCommand(PivotCommands.PING)
                    .setTimeoutMs(8_000)
                    .setPayload(PingRequest.newBuilder().setEcho("http").build().toByteString())
                    .build(), 8_000).get(12, TimeUnit.SECONDS);
            assertEquals("http", PingResult.parseFrom(result.getPayload()).getEcho());
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    public void connectionConfigPreservesEventBufferSize() throws Exception {
        OpampServer server = OpampServer.start(ConnectionConfig.builder()
                .token(AuthTokens.randomToken())
                .sessionId("session-buffer")
                .eventBufferSize(42)
                .path("/v1/opamp")
                .build());
        try {
            assertEquals(42, server.connectionConfig().getEventBufferSize());
            assertEquals("/v1/opamp", server.connectionConfig().getPath());
            ConnectionConfig parsed = ConnectionConfig.parse(server.connectionConfig().toAgentArgument());
            assertEquals(42, parsed.getEventBufferSize());
        } finally {
            server.close();
        }
    }

    @Test
    public void rejectsWrongToken() throws Exception {
        ConnectionConfig serverConfig = ConnectionConfig.builder()
                .host("127.0.0.1")
                .token(AuthTokens.randomToken())
                .sessionId("session-auth")
                .build();
        OpampServer server = OpampServer.start(serverConfig);
        ConnectionConfig bad = ConnectionConfig.builder()
                .host("127.0.0.1")
                .wsPort(server.getWsPort())
                .httpPort(server.getHttpPort())
                .token(AuthTokens.randomToken())
                .sessionId("session-auth")
                .build();
        OpampClient client = new OpampClient(bad, "3.0.0-test", Collections.<String, CommandHandler>emptyMap());
        try {
            try {
                client.start();
            } catch (Exception ignored) {
                // WebSocket may fail fast; HTTP fallback may 401.
            }
            Thread.sleep(500);
            assertTrue(!server.isAgentConnected());
        } finally {
            client.close();
            server.close();
        }
    }

    @Test
    public void versionMismatchIsRejected() throws Exception {
        String token = AuthTokens.randomToken();
        OpampServer server = OpampServer.start(ConnectionConfig.builder()
                .token(token)
                .sessionId("session-version")
                .build());
        try {
            // Direct HTTP post without protocol version attribute.
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                    new java.net.URL(server.connectionConfig().httpUrl()).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", OpampWire.CONTENT_TYPE);
            connection.setRequestProperty(AuthTokens.HEADER, AuthTokens.bearer(token));
            opamp.proto.v1.AgentToServer message = opamp.proto.v1.AgentToServer.newBuilder()
                    .setInstanceUid(InstanceUids.randomV7())
                    .setSequenceNum(1)
                    .setCapabilities(PivotCapabilities.AGENT_OPAMP_BITS)
                    .build();
            byte[] body = message.toByteArray();
            connection.getOutputStream().write(body);
            assertEquals(200, connection.getResponseCode());
            opamp.proto.v1.ServerToAgent response = OpampWire.decodeServerToAgentHttp(
                    read(connection), ConnectionConfig.DEFAULT_MAX_MESSAGE_BYTES);
            assertTrue(response.hasErrorResponse());
            assertTrue(response.getErrorResponse().getErrorMessage().contains(ProtocolVersion.ATTRIBUTE_KEY));
            assertTrue(!server.isAgentConnected());
        } finally {
            server.close();
        }
    }

    private static CommandHandler pingHandler() {
        return new CommandHandler() {
            @Override
            public String command() {
                return PivotCommands.PING;
            }

            @Override
            public CommandResult execute(CommandRequest request, CommandContext context) throws Exception {
                PingRequest ping = PingRequest.parseFrom(request.getPayload());
                return CommandResult.newBuilder()
                        .setRequestId(request.getRequestId())
                        .setCommand(command())
                        .setPayload(PingResult.newBuilder().setEcho(ping.getEcho()).setAgentNanoTime(1L).build().toByteString())
                        .build();
            }
        };
    }

    private static void waitUntil(Check check, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(50);
        }
        fail("condition not met");
    }

    private static byte[] read(java.net.HttpURLConnection connection) throws Exception {
        java.io.InputStream in = connection.getInputStream();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private interface Check {
        boolean ok();
    }
}
