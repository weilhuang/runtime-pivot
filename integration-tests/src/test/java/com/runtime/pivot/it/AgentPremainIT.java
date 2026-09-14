package com.runtime.pivot.it;

import com.runtime.pivot.protocol.AuthTokens;
import com.runtime.pivot.protocol.ConnectionConfig;
import com.runtime.pivot.protocol.PivotCommands;
import com.runtime.pivot.protocol.proto.ClassLoaderTreeResult;
import com.runtime.pivot.protocol.proto.CommandRequest;
import com.runtime.pivot.protocol.proto.CommandResult;
import com.runtime.pivot.protocol.proto.LoadedClassesResult;
import com.runtime.pivot.protocol.proto.PingRequest;
import com.runtime.pivot.protocol.proto.PingResult;
import com.runtime.pivot.protocol.proto.RuntimePivotTransformerResult;
import com.runtime.pivot.protocol.transport.OpampServer;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AgentPremainIT {
    @Test
    public void premainHandshakeAndGlobalQueriesDoNotNeedBreakpoints() throws Exception {
        String agentJar = System.getProperty("runtime.pivot.agent.jar");
        String appJar = System.getProperty("runtime.pivot.testapp.jar");
        String javaHome = System.getProperty("runtime.pivot.test.java.home", System.getProperty("java.home"));
        File javaBinary = new File(javaHome, "bin/java");
        if (!javaBinary.isFile()) {
            javaBinary = new File(javaHome, "bin/java.exe");
        }
        assertTrue("agent jar missing", agentJar != null && new File(agentJar).isFile());
        assertTrue("test app jar missing", appJar != null && new File(appJar).isFile());
        assertTrue("agent java binary missing: " + javaBinary, javaBinary.isFile());

        String token = AuthTokens.randomToken();
        OpampServer server = OpampServer.start(ConnectionConfig.builder()
                .token(token)
                .sessionId("premain-session")
                .build());
        Process process = null;
        try {
            List<String> command = new ArrayList<String>();
            command.add(javaBinary.getAbsolutePath());
            command.add("-javaagent:" + agentJar + "=" + server.connectionConfig().toAgentArgument());
            command.add("-jar");
            command.add(appJar);
            command.add("8000");
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            waitUntil(server::isAgentConnected, 15_000, javaBinary);

            CommandResult ping = server.sendCommand(CommandRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setCommand(PivotCommands.PING)
                    .setTimeoutMs(8_000)
                    .setPayload(PingRequest.newBuilder().setEcho("premain").build().toByteString())
                    .build(), 8_000).get(10, TimeUnit.SECONDS);
            assertEquals("premain", PingResult.parseFrom(ping.getPayload()).getEcho());

            CommandResult loaders = server.sendCommand(CommandRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setCommand(PivotCommands.CLASS_LOADERS)
                    .setTimeoutMs(8_000)
                    .build(), 8_000).get(10, TimeUnit.SECONDS);
            assertTrue(ClassLoaderTreeResult.parseFrom(loaders.getPayload()).getNodesCount() > 0);

            CommandResult loaded = server.sendCommand(CommandRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setCommand(PivotCommands.CLASS_LOADED)
                    .setTimeoutMs(8_000)
                    .build(), 8_000).get(10, TimeUnit.SECONDS);
            assertTrue(LoadedClassesResult.parseFrom(loaded.getPayload()).getClassesCount() > 0);

            CommandResult transformers = server.sendCommand(CommandRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setCommand(PivotCommands.TRANSFORMER_LIST)
                    .setTimeoutMs(8_000)
                    .build(), 8_000).get(10, TimeUnit.SECONDS);
            RuntimePivotTransformerResult list = RuntimePivotTransformerResult.parseFrom(transformers.getPayload());
            assertTrue(list.getTransformersCount() >= 1);
        } finally {
            if (process != null) {
                process.destroy();
            }
            server.close();
        }
    }

    private static void waitUntil(Check check, long timeoutMs, File javaBinary) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Agent did not connect via OpAMP using " + javaBinary);
    }

    private interface Check {
        boolean ok();
    }
}
