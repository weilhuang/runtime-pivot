package com.runtime.pivot.protocol;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

public final class AuthTokens {
    public static final String HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String SESSION_HEADER = "X-Runtime-Pivot-Session-Id";

    private static final int TOKEN_BYTES = 16;

    private AuthTokens() {
    }

    public static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return toHex(bytes);
    }

    public static String bearer(String token) {
        return BEARER_PREFIX + token;
    }

    public static String fromAuthorizationHeader(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String trimmed = headerValue.trim();
        if (trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return trimmed.substring(BEARER_PREFIX.length()).trim();
        }
        return trimmed;
    }

    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] left = expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] right = actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (left.length != right.length) {
            MessageDigest.isEqual(left, left);
            return false;
        }
        return MessageDigest.isEqual(left, right);
    }

    public static String redact(String token) {
        if (token == null || token.length() < 8) {
            return "<redacted>";
        }
        return token.substring(0, 4) + "…" + token.substring(token.length() - 2);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return builder.toString();
    }
}
