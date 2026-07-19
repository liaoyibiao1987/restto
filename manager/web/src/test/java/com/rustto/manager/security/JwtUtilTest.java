package com.rustto.manager.security;

import com.rustto.manager.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JWT 工具测试。
 */
class JwtUtilTest {

    @Test
    void generateAndParseRoundtrip() {
        JwtUtil util = new JwtUtil(new JwtProperties());
        String token = util.generate(42L, "alice");
        assertEquals(42L, (long) util.getUserId(token));
        assertEquals("alice", util.parse(token).get("username", String.class));
    }

    @Test
    void rejectsTamperedToken() {
        JwtUtil util = new JwtUtil(new JwtProperties());
        assertThrows(JwtException.class, () -> util.parse("not.a.valid.jwt"));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtProperties a = new JwtProperties();
        a.setSecret("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"); // 40 chars
        JwtProperties b = new JwtProperties();
        b.setSecret("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        JwtUtil ua = new JwtUtil(a);
        JwtUtil ub = new JwtUtil(b);
        String token = ua.generate(1L, "x");
        assertThrows(JwtException.class, () -> ub.parse(token));
    }
}
