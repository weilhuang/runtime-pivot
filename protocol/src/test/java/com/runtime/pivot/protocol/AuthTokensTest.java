package com.runtime.pivot.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthTokensTest {
    @Test
    public void generates128BitHexToken() {
        String token = AuthTokens.randomToken();
        assertEquals(32, token.length());
        assertTrue(AuthTokens.matches(token, token));
        assertFalse(AuthTokens.matches(token, "00" + token.substring(2)));
    }

    @Test
    public void parsesBearerHeader() {
        String token = "abcd";
        assertEquals(token, AuthTokens.fromAuthorizationHeader(AuthTokens.bearer(token)));
    }

    @Test
    public void redactsTokenForLogs() {
        String redacted = AuthTokens.redact("0123456789abcdef");
        assertFalse(redacted.contains("89ab"));
        assertTrue(redacted.contains("…"));
    }
}
