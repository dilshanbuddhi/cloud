package com.ijse.eca.users.service;

import com.ijse.eca.users.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final String issuer;
    private final long expiresMinutes;

    public JwtService(JwtProperties jwtProperties) {
        String secret = requireNotBlank(jwtProperties.secret(), "security.jwt.secret is required");
        this.issuer = requireNotBlank(jwtProperties.issuer(), "security.jwt.issuer is required");
        this.expiresMinutes = jwtProperties.expiresMinutes();
        if (this.expiresMinutes < 1) {
            throw new IllegalStateException("security.jwt.expires-minutes must be at least 1");
        }
        try {
            this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } catch (WeakKeyException ex) {
            throw new IllegalStateException("security.jwt.secret is too weak (use at least 32 characters for HS256)", ex);
        }
    }

    public String issueToken(Long userId, String email) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expiresMinutes * 60);
        return Jwts.builder()
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .subject(String.valueOf(userId))
                .claim("email", email)
                .signWith(signingKey)
                .compact();
    }

    private String requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}
