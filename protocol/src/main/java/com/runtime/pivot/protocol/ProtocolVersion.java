package com.runtime.pivot.protocol;

/**
 * Runtime Pivot protocol version carried as an OpAMP AgentDescription identifying attribute.
 * OpAMP protobuf message shapes follow the official spec; this version is the
 * Runtime Pivot custom-capability contract inside CustomMessage payloads.
 */
public final class ProtocolVersion {
    public static final int CURRENT = 3;
    public static final String ATTRIBUTE_KEY = "runtime.pivot.protocol.version";
    public static final String SESSION_ATTRIBUTE_KEY = "runtime.pivot.session.id";
    public static final String SERVICE_NAME = "runtime-pivot-agent";
    public static final String SERVICE_NAME_KEY = "service.name";
    public static final String SERVICE_VERSION_KEY = "service.version";
    public static final String SERVICE_INSTANCE_ID_KEY = "service.instance.id";

    private ProtocolVersion() {
    }

    public static void requireCompatible(int remoteVersion) {
        if (remoteVersion != CURRENT) {
            throw new ProtocolVersionException(remoteVersion);
        }
    }

    public static final class ProtocolVersionException extends RuntimeException {
        private final int remoteVersion;

        public ProtocolVersionException(int remoteVersion) {
            super("Runtime Pivot protocol version mismatch: expected " + CURRENT + " but was " + remoteVersion);
            this.remoteVersion = remoteVersion;
        }

        public int getRemoteVersion() {
            return remoteVersion;
        }
    }
}
