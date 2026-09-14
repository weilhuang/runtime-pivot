package com.runtime.pivot.protocol;

public final class PivotCommands {
    public static final String PING = "ping";
    public static final String CLASS_LOADERS = "class.loaders";
    public static final String CLASS_LOADED = "class.loaded";
    public static final String CLASS_DUMP = "class.dump";
    public static final String CLASS_LOADING_TIMELINE = "class.loading.timeline";
    public static final String TRANSFORMER_LIST = "transformer.list";

    public static final String TYPE_COMMAND_REQUEST = "CommandRequest";
    public static final String TYPE_COMMAND_ACCEPTED = "CommandAccepted";
    public static final String TYPE_COMMAND_PROGRESS = "CommandProgress";
    public static final String TYPE_COMMAND_RESULT = "CommandResult";
    public static final String TYPE_COMMAND_ERROR = "CommandError";
    public static final String TYPE_CANCEL_COMMAND = "CancelCommand";
    public static final String TYPE_EVENT_BATCH = "EventBatch";

    public static final String ERROR_UNSUPPORTED = "UNSUPPORTED";
    public static final String ERROR_TIMEOUT = "TIMEOUT";
    public static final String ERROR_CANCELLED = "CANCELLED";
    public static final String ERROR_BAD_REQUEST = "BAD_REQUEST";
    public static final String ERROR_INTERNAL = "INTERNAL";
    public static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_VERSION = "PROTOCOL_VERSION";

    private PivotCommands() {
    }
}
