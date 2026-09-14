package com.runtime.pivot.protocol.transport;

import com.runtime.pivot.protocol.proto.CommandRequest;
import com.runtime.pivot.protocol.proto.CommandResult;

public interface CommandHandler {
    String command();

    CommandResult execute(CommandRequest request, CommandContext context) throws Exception;
}
