package com.executionos.security;

import com.executionos.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final String issuer;
    private final long accessMinutes;

    public JwtService(
            @Value("${executionos.jwt.secret}") String secret,
            @Value("${executionos.jwt.issuer}") String issuer,
            @Value("${executionos.jwt.access-token-minutes}") long accessMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessMinutes = accessMinutes;
    }

    /** Original ExecutionOS-user token issuance — unchanged behavior. */
    public String issueAccessToken(User user) {
        return issueAccessToken(user.getEmail(), user.getId(), user.getRole().name(), null);
    }

    /**
     * Generalized token issuance, used by SkillForge login so a token can
     * carry an organizationId claim without coupling JwtService to the
     * SfUser entity type.
     */
    public String issueAccessToken(String subjectEmail, UUID uid, String role, UUID organizationId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject(subjectEmail)
                .claim("uid", uid.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessMinutes * 60)));
        if (organizationId != null) {
            builder.claim("orgId", organizationId.toString());
        }
        return builder.signWith(key).compact();
    }

    public String subject(String token) {
        return claims(token).getSubject();
    }

    public Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
