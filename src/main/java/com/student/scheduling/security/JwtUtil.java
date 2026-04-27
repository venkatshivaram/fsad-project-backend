package com.student.scheduling.security;

import com.student.scheduling.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

@Component
public class JwtUtil {
    private static final long TOKEN_VALIDITY_MS = 60 * 60 * 1000;

    private final SecretKey secretKey;

    public JwtUtil(@Value("${app.jwt.secret:${JWT_SECRET:}}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be set with at least 32 characters using JWT_SECRET or app.jwt.secret");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        String role = normalizeRole(user.getRole());

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("username", user.getUsername())
                .claim("role", role)
                .claim("userId", user.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(TOKEN_VALIDITY_MS)))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }
}
