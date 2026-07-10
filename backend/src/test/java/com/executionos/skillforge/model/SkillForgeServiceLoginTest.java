package com.executionos.skillforge.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.executionos.security.JwtService;
import com.executionos.skillforge.model.SkillForgeDtos.LoginResponse;
import com.executionos.skillforge.model.SkillForgeEnums.UserRole;
import com.executionos.skillforge.model.SkillForgeEnums.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pure unit tests for the login/lockout/refresh/logout logic added in this
 * session -- the highest-risk, most security-sensitive code in the app and
 * exactly what PRODUCTION_AUDIT.md flagged as having zero coverage. Every
 * repository dependency is mocked; no Spring context, no database.
 */
class SkillForgeServiceLoginTest {

    private SfUserRepository users;
    private SfRefreshTokenRepository refreshTokens;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private EmailService emailService;
    private SkillForgeService service;

    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        users = mock(SfUserRepository.class);
        refreshTokens = mock(SfRefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        emailService = mock(EmailService.class);

        service = new SkillForgeService(
                mock(SfOrganizationRepository.class),
                users,
                mock(SfDepartmentRepository.class),
                mock(SfBatchRepository.class),
                mock(SfCandidateProfileRepository.class),
                mock(SfSubjectRepository.class),
                mock(SfQuestionRepository.class),
                mock(SfQuestionVersionRepository.class),
                mock(SfQuestionApprovalRepository.class),
                mock(SfAssessmentRepository.class),
                mock(SfAssessmentSectionRepository.class),
                mock(SfAssessmentQuestionRepository.class),
                mock(SfAssessmentInvitationRepository.class),
                mock(SfAttemptRepository.class),
                mock(SfAttemptAnswerRepository.class),
                mock(SfAttemptEventRepository.class),
                passwordEncoder,
                jwtService,
                refreshTokens,
                emailService,
                7L,
                "http://localhost:3000");

        when(jwtService.issueAccessToken(anyString(), any(UUID.class), anyString(), any(UUID.class)))
                .thenReturn("fake.jwt.token");
    }

    private SfUser activeUser(String email, String passwordHash) {
        SfUser user = new SfUser();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setOrganizationId(ORG_ID);
        user.setEmail(email);
        user.setFullName("Test User");
        user.setPasswordHash(passwordHash);
        user.setRole(UserRole.TRAINER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Test
    void successfulLoginReturnsAccessAndRefreshTokensAndResetsFailedAttempts() {
        SfUser user = activeUser("trainer@apex.example", "hashed-password");
        user.setFailedLoginAttempts(3);
        when(users.findByEmail("trainer@apex.example")).thenReturn(List.of(user));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);

        LoginResponse response = service.login("trainer@apex.example", "correct-password");

        assertThat(response.accessToken()).isEqualTo("fake.jwt.token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("trainer@apex.example");
        // A successful login must clear any prior failed-attempt count.
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(refreshTokens, times(1)).save(any(SfRefreshToken.class));
    }

    @Test
    void wrongPasswordThrowsGenericMessageAndIncrementsFailedAttempts() {
        SfUser user = activeUser("trainer@apex.example", "hashed-password");
        user.setFailedLoginAttempts(0);
        when(users.findByEmail("trainer@apex.example")).thenReturn(List.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> service.login("trainer@apex.example", "wrong-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid email or password");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void accountLocksAfterFiveFailedAttempts() {
        SfUser user = activeUser("trainer@apex.example", "hashed-password");
        user.setFailedLoginAttempts(4); // one more failure should trip the lock
        when(users.findByEmail("trainer@apex.example")).thenReturn(List.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> service.login("trainer@apex.example", "wrong-password"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull().isAfter(Instant.now());
    }

    @Test
    void lockedAccountIsRejectedEvenWithTheCorrectPassword() {
        // A lock must not be bypassable by guessing correctly while locked --
        // otherwise the lockout would only ever stop the legitimate user,
        // not an attacker who eventually gets the password right.
        SfUser user = activeUser("trainer@apex.example", "hashed-password");
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().plusSeconds(900));
        when(users.findByEmail("trainer@apex.example")).thenReturn(List.of(user));

        assertThatThrownBy(() -> service.login("trainer@apex.example", "correct-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("locked");

        // Must reject before even checking the password.
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void expiredLockAutomaticallyAllowsLoginAttemptsAgain() {
        SfUser user = activeUser("trainer@apex.example", "hashed-password");
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().minusSeconds(60)); // lock window already passed
        when(users.findByEmail("trainer@apex.example")).thenReturn(List.of(user));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);

        LoginResponse response = service.login("trainer@apex.example", "correct-password");

        assertThat(response.accessToken()).isEqualTo("fake.jwt.token");
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void invitedAccountGetsAClearVerifyYourEmailMessageNotAGenericOne() {
        SfUser user = activeUser("newadmin@company.example", "hashed-password");
        user.setStatus(UserStatus.INVITED);
        when(users.findByEmail("newadmin@company.example")).thenReturn(List.of(user));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);

        assertThatThrownBy(() -> service.login("newadmin@company.example", "correct-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verify your email");
    }

    @Test
    void suspendedAccountGetsTheGenericNotActiveMessage() {
        SfUser user = activeUser("suspended@apex.example", "hashed-password");
        user.setStatus(UserStatus.SUSPENDED);
        when(users.findByEmail("suspended@apex.example")).thenReturn(List.of(user));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);

        assertThatThrownBy(() -> service.login("suspended@apex.example", "correct-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account is not active");
    }

    @Test
    void refreshRotatesTheTokenAndRevokesTheOldOne() {
        SfUser user = activeUser("trainer@apex.example", "hashed-password");
        SfRefreshToken oldToken = new SfRefreshToken();
        oldToken.setSfUserId(user.getId());
        oldToken.setToken("old-refresh-token");
        oldToken.setExpiresAt(Instant.now().plusSeconds(3600));
        oldToken.setRevoked(false);

        when(refreshTokens.findByToken("old-refresh-token")).thenReturn(Optional.of(oldToken));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        LoginResponse response = service.refresh("old-refresh-token");

        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(response.refreshToken()).isNotEqualTo("old-refresh-token");
        assertThat(response.accessToken()).isEqualTo("fake.jwt.token");
    }

    @Test
    void refreshRejectsAnExpiredToken() {
        SfRefreshToken expiredToken = new SfRefreshToken();
        expiredToken.setSfUserId(UUID.randomUUID());
        expiredToken.setToken("expired-token");
        expiredToken.setExpiresAt(Instant.now().minusSeconds(60));
        when(refreshTokens.findByToken("expired-token")).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> service.refresh("expired-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void refreshRejectsAnAlreadyRevokedToken() {
        SfRefreshToken revokedToken = new SfRefreshToken();
        revokedToken.setSfUserId(UUID.randomUUID());
        revokedToken.setToken("revoked-token");
        revokedToken.setExpiresAt(Instant.now().plusSeconds(3600));
        revokedToken.setRevoked(true);
        when(refreshTokens.findByToken("revoked-token")).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> service.refresh("revoked-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void refreshRejectsAnUnknownToken() {
        when(refreshTokens.findByToken("never-issued")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("never-issued"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void logoutRevokesTheMatchingToken() {
        SfRefreshToken token = new SfRefreshToken();
        token.setSfUserId(UUID.randomUUID());
        token.setToken("session-to-end");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setRevoked(false);
        when(refreshTokens.findByToken("session-to-end")).thenReturn(Optional.of(token));

        service.logout("session-to-end");

        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void logoutOnAnUnknownTokenDoesNothingAndDoesNotThrow() {
        when(refreshTokens.findByToken("never-issued")).thenReturn(Optional.empty());

        // Should be a silent no-op -- logging out shouldn't reveal whether a
        // given token string ever existed.
        service.logout("never-issued");
    }

    @Test
    void verifyEmailActivatesAnInvitedAccountAndClearsTheToken() {
        SfUser user = activeUser("newadmin@company.example", null);
        user.setStatus(UserStatus.INVITED);
        user.setVerifyTokenHash("irrelevant-because-hash-is-mocked");
        user.setVerifyTokenExpiresAt(Instant.now().plusSeconds(3600));
        when(users.findByVerifyTokenHash(anyString())).thenReturn(Optional.of(user));

        service.verifyEmail("raw-token-from-the-email-link");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getVerifyTokenHash()).isNull();
        assertThat(user.getVerifyTokenExpiresAt()).isNull();
    }

    @Test
    void verifyEmailRejectsAnExpiredToken() {
        SfUser user = activeUser("newadmin@company.example", null);
        user.setStatus(UserStatus.INVITED);
        user.setVerifyTokenHash("some-hash");
        user.setVerifyTokenExpiresAt(Instant.now().minusSeconds(60));
        when(users.findByVerifyTokenHash(anyString())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.verifyEmail("raw-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid or has expired");
        assertThat(user.getStatus()).isEqualTo(UserStatus.INVITED); // unchanged
    }

    @Test
    void verifyEmailRejectsAnUnknownToken() {
        when(users.findByVerifyTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmail("never-issued"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid or has expired");
    }
}
