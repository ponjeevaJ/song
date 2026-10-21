package io.isaiworld.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationHours;

    public JwtService(@Value("${isai.jwt.secret}") String secret,
                      @Value("${isai.jwt.expiration-hours}") long expirationHours) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 characters.");
        }
        if (expirationHours < 1) {
            throw new IllegalStateException("JWT_EXPIRATION_HOURS must be greater than zero.");
        }
        this.key = secret.matches("^[A-Za-z0-9_-]+$") && secret.length() >= 43
            ? Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(secret))
            : Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    public String issue(AccountPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(principal.getUsername())
            .claim("uid", principal.id())
            .claim("role", principal.role())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirationHours * 3600)))
            .signWith(key)
            .compact();
    }

    public AccountPrincipal parse(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new AccountPrincipal(claims.get("uid", Long.class), claims.getSubject(), claims.get("role", String.class));
    }
}
