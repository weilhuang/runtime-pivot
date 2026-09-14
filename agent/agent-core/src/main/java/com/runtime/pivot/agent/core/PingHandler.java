package com.runtime.pivot.agent.core;

import com.runtime.pivot.protocol.PivotCommands;
import com.runtime.pivot.protocol.proto.CommandRequest;
import com.runtime.pivot.protocol.proto.CommandResult;
import com.runtime.pivot.protocol.proto.PingRequest;
import com.runtime.pivot.protocol.proto.PingResult;
import com.runtime.pivot.protocol.transport.CommandContext;
import com.runtime.pivot.protocol.transport.CommandHandler;

public final class PingHandler implements CommandHandler {
    @Override
    public String command() {
        return PivotCommands.PING;
    }

    @Override
    public CommandResult execute(CommandRequest request, CommandContext context) throws Exception {
        PingRequest ping = request.getPayload().isEmpty()
                ? PingRequest.getDefaultInstance()
                : PingRequest.parseFrom(request.getPayload());
        PingResult result = PingResult.newBuilder()
                .setEcho(ping.getEcho())
                .setAgentNanoTime(System.nanoTime())
                .setJavaVersion(javaVersion())
                .build();
        return CommandResult.newBuilder()
                .setRequestId(request.getRequestId())
                .setCommand(command())
                .setPayload(result.toByteString())
                .build();
    }

    private static int javaVersion() {
        String spec = System.getProperty("java.specification.version", "1.8");
        if (spec.startsWith("1.")) {
            return Integer.parseInt(spec.substring(2));
        }
        int dot = spec.indexOf('.');
        return Integer.parseInt(dot < 0 ? spec : spec.substring(0, dot));
    }
}
