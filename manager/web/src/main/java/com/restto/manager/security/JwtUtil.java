package com.restto.manager.security;

import com.restto.manager.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * JWT 生成与解析工具（jjwt 0.11.x，兼容 Java 8）。
 */
@Component
public class JwtUtil {

    private final Key key;

    private final long expireMillis;

    /**
     * @param properties JWT 配置
     */
    public JwtUtil(JwtProperties properties) {
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expireMillis = properties.getExpireMinutes() * 60_000L;
    }

    /**
     * 生成 Token。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @return JWT 字符串
     */
    public String generate(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析并校验 Token，失败抛 JwtException。
     *
     * @param token JWT 字符串
     * @return Claims
     */
    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从 Token 取用户 ID。
     *
     * @param token JWT 字符串
     * @return 用户 ID
     */
    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }
}
