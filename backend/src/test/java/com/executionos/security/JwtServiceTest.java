package com.executionos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests -- no Spring context, no database. JwtService only needs
 * its three constructor values, so these run fast and don't depend on
 * anything else in the application being wired correctly.
 */
class JwtServiceTest {

    private static final String SECRET = "test-only-secret-value-that-is-at-least-32-characters-long";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, "execution-os-test", 15);
    }

    @Test
    void issuedTokenCarriesAllExpectedClaims() {
        UUID uid = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        String token = jwtService.issueAccessToken("admin@apex.example", uid, "ORG_ADMIN", orgId);
        Claims claims = jwtService.claims(token);

        assertThat(claims.getSubject()).isEqualTo("admin@apex.example");
        assertThat(claims.getIssuer()).isEqualTo("execution-os-test");
        assertThat(claims.get("uid", String.class)).isEqualTo(uid.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("ORG_ADMIN");
        assertThat(claims.get("orgId", String.class)).isEqualTo(orgId.toString());
    }

    @Test
    void omittingOrganizationIdOmitsTheClaimEntirely() {
        // The ExecutionOS-side (non-SkillForge) login path calls this with
        // organizationId = null -- confirms that doesn't blow up and simply
        // produces a token without an orgId claim, rather than "null" as a
        // string.
        String token = jwtService.issueAccessToken("user@example.com", UUID.randomUUID(), "USER", null);
        Claims claims = jwtService.claims(token);

        assertThat(claims.get("orgId")).isNull();
    }

    @Test
    void subjectHelperReturnsTheEmailClaim() {
        String token = jwtService.issueAccessToken("trainer@apex.example", UUID.randomUUID(), "TRAINER", UUID.randomUUID());
        assertThat(jwtService.subject(token)).isEqualTo("trainer@apex.example");
    }

    @Test
    void expiredTokenFailsToParse() {
        // access-token-minutes = -1 means "expired the instant it was issued" --
        // confirms JwtAuthenticationFilter's catch-block path (which relies on
        // claims() throwing rather than silently returning stale data) actually
        // has something to catch.
        JwtService almostExpiredService = new JwtService(SECRET, "execution-os-test", -1);
        String token = almostExpiredService.issueAccessToken("user@example.com", UUID.randomUUID(), "USER", null);

        assertThatThrownBy(() -> almostExpiredService.claims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        String token = jwtService.issueAccessToken("user@example.com", UUID.randomUUID(), "USER", null);
        JwtService otherService = new JwtService("a-completely-different-secret-value-of-32-plus-chars", "execution-os-test", 15);

        assertThatThrownBy(() -> otherService.claims(token)).isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }
}
