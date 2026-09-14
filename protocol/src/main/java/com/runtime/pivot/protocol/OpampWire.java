package com.runtime.pivot.protocol;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import opamp.proto.v1.AgentToServer;
import opamp.proto.v1.ServerToAgent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * OpAMP WebSocket frames are {@code header varint || protobuf}. HTTP bodies are protobuf only.
 */
public final class OpampWire {
    public static final int HEADER_VALUE = 0;
    public static final String CONTENT_TYPE = "application/x-protobuf";
    public static final String WS_SUBPROTOCOL = "opamp.v1";

    private OpampWire() {
    }

    public static byte[] encodeAgentToServerWs(AgentToServer message, int maxBytes) throws IOException {
        return encodeWs(message.toByteArray(), maxBytes);
    }

    public static byte[] encodeServerToAgentWs(ServerToAgent message, int maxBytes) throws IOException {
        return encodeWs(message.toByteArray(), maxBytes);
    }

    public static AgentToServer decodeAgentToServerWs(byte[] frame, int maxBytes) throws IOException {
        return AgentToServer.parseFrom(decodeWs(frame, maxBytes));
    }

    public static ServerToAgent decodeServerToAgentWs(byte[] frame, int maxBytes) throws IOException {
        return ServerToAgent.parseFrom(decodeWs(frame, maxBytes));
    }

    public static AgentToServer decodeAgentToServerHttp(byte[] body, int maxBytes) throws IOException {
        requireSize(body, maxBytes);
        return AgentToServer.parseFrom(body);
    }

    public static ServerToAgent decodeServerToAgentHttp(byte[] body, int maxBytes) throws IOException {
        requireSize(body, maxBytes);
        return ServerToAgent.parseFrom(body);
    }

    public static byte[] encodeWs(byte[] protobuf, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(protobuf.length + 10);
        CodedOutputStream coded = CodedOutputStream.newInstance(out);
        coded.writeUInt64NoTag(HEADER_VALUE);
        coded.flush();
        out.write(protobuf);
        byte[] frame = out.toByteArray();
        requireSize(frame, maxBytes);
        return frame;
    }

    public static byte[] decodeWs(byte[] frame, int maxBytes) throws IOException {
        requireSize(frame, maxBytes);
        CodedInputStream input = CodedInputStream.newInstance(frame);
        long header = input.readUInt64();
        if (header != HEADER_VALUE) {
            throw new IOException("Unsupported OpAMP WebSocket header " + header);
        }
        int position = input.getTotalBytesRead();
        int remaining = frame.length - position;
        byte[] protobuf = new byte[remaining];
        System.arraycopy(frame, position, protobuf, 0, remaining);
        return protobuf;
    }

    private static void requireSize(byte[] data, int maxBytes) throws IOException {
        if (data == null) {
            throw new IOException("Empty OpAMP payload");
        }
        if (data.length > maxBytes) {
            throw new IOException("OpAMP payload exceeds max size " + maxBytes);
        }
    }
}
