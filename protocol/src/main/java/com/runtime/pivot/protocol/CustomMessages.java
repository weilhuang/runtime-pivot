package com.runtime.pivot.protocol;

import com.google.protobuf.ByteString;
import com.runtime.pivot.protocol.proto.CancelCommand;
import com.runtime.pivot.protocol.proto.CommandAccepted;
import com.runtime.pivot.protocol.proto.CommandError;
import com.runtime.pivot.protocol.proto.CommandRequest;
import com.runtime.pivot.protocol.proto.CommandResult;
import opamp.proto.v1.CustomMessage;

public final class CustomMessages {
    private CustomMessages() {
    }

    public static CustomMessage commandRequest(CommandRequest request) {
        return message(PivotCommands.TYPE_COMMAND_REQUEST, request.toByteString());
    }

    public static CustomMessage commandAccepted(String requestId) {
        return message(PivotCommands.TYPE_COMMAND_ACCEPTED, CommandAccepted.newBuilder().setRequestId(requestId).build().toByteString());
    }

    public static CustomMessage commandResult(CommandResult result) {
        return message(PivotCommands.TYPE_COMMAND_RESULT, result.toByteString());
    }

    public static CustomMessage commandError(CommandError error) {
        return message(PivotCommands.TYPE_COMMAND_ERROR, error.toByteString());
    }

    public static CustomMessage cancel(String requestId) {
        return message(PivotCommands.TYPE_CANCEL_COMMAND, CancelCommand.newBuilder().setRequestId(requestId).build().toByteString());
    }

    public static CustomMessage message(String type, ByteString data) {
        return CustomMessage.newBuilder()
                .setCapability(PivotCapabilities.CUSTOM_CAPABILITY)
                .setType(type)
                .setData(data)
                .build();
    }
}
