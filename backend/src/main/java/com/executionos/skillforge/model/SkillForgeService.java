package com.executionos.skillforge.model;

import com.executionos.security.JwtService;
import com.executionos.skillforge.model.SkillForgeDtos.*;
import com.executionos.skillforge.model.SkillForgeEnums.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for SkillForge organization, question, assessment, invitation, attempt, and reporting workflows.
 */
@Service
@Transactional
public class SkillForgeService {
    private final SfOrganizationRepository organizations;
    private final SfUserRepository users;
    private final SfDepartmentRepository departments;
    private final SfBatchRepository batches;
    private final SfCandidateProfileRepository candidateProfiles;
    private final SfSubjectRepository subjects;
    private final SfQuestionRepository questions;
    private final SfQuestionVersionRepository questionVersions;
    private final SfQuestionApprovalRepository approvals;
    private final SfAssessmentRepository assessments;
    private final SfAssessmentSectionRepository sections;
    private final SfAssessmentQuestionRepository assessmentQuestions;
    private final SfAssessmentInvitationRepository invitations;
    private final SfAttemptRepository attempts;
    private final SfAttemptAnswerRepository answers;
    private final SfAttemptEventRepository events;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SfRefreshTokenRepository refreshTokens;
    private final long refreshTokenDays;
    private final EmailService emailService;
    private final String frontendBaseUrl;

    public SkillForgeService(
            SfOrganizationRepository organizations,
            SfUserRepository users,
            SfDepartmentRepository departments,
            SfBatchRepository batches,
            SfCandidateProfileRepository candidateProfiles,
            SfSubjectRepository subjects,
            SfQuestionRepository questions,
            SfQuestionVersionRepository questionVersions,
            SfQuestionApprovalRepository approvals,
            SfAssessmentRepository assessments,
            SfAssessmentSectionRepository sections,
            SfAssessmentQuestionRepository assessmentQuestions,
            SfAssessmentInvitationRepository invitations,
            SfAttemptRepository attempts,
            SfAttemptAnswerRepository answers,
            SfAttemptEventRepository events,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SfRefreshTokenRepository refreshTokens,
            EmailService emailService,
            @Value("${executionos.jwt.refresh-token-days:30}") long refreshTokenDays,
            @Value("${executionos.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.organizations = organizations;
        this.users = users;
        this.departments = departments;
        this.batches = batches;
        this.candidateProfiles = candidateProfiles;
        this.subjects = subjects;
        this.questions = questions;
        this.questionVersions = questionVersions;
        this.approvals = approvals;
        this.assessments = assessments;
        this.sections = sections;
        this.assessmentQuestions = assessmentQuestions;
        this.invitations = invitations;
        this.attempts = attempts;
        this.answers = answers;
        this.events = events;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.refreshTokenDays = refreshTokenDays;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public OrganizationResponse registerOrganization(RegisterOrganizationRequest request) {
        String slug = normalizeSlug(request.slug());
        if (organizations.existsBySlug(slug)) {
            throw new IllegalArgumentException("Organization slug already exists");
        }
        SfOrganization organization = new SfOrganization();
        organization.setName(request.organizationName());
        organization.setSlug(slug);
        organization = organizations.save(organization);

        SfUser admin = new SfUser();
        admin.setOrganizationId(organization.getId());
        admin.setEmail(request.adminEmail().toLowerCase());
        admin.setFullName(request.adminName());
        admin.setRole(UserRole.ORG_ADMIN);
        if (request.adminPassword() != null && !request.adminPassword().isBlank()) {
            admin.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
        }
        // This is the one signup path with no existing admin vouching for the
        // email address (every other account is created BY an admin who
        // already typed the email deliberately) — so this is the one place
        // that actually needs to confirm the caller owns the email before
        // granting an active account. Starts INVITED, not ACTIVE, until the
        // verification link is clicked.
        admin.setStatus(UserStatus.INVITED);
        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        admin.setVerifyTokenHash(hash(rawToken));
        admin.setVerifyTokenExpiresAt(Instant.now().plus(java.time.Duration.ofHours(24)));
        users.save(admin);
        emailService.send(admin.getEmail(), "Verify your SkillForge account",
                "Welcome to SkillForge. Verify your email to activate your organization admin account:\n\n"
                        + frontendBaseUrl + "/verify-email?token=" + rawToken
                        + "\n\nOr enter this token directly: " + rawToken
                        + "\n\nThis link expires in 24 hours.");
        return organizationResponse(organization);
    }

    /** Activates an account created via registerOrganization once the emailed link/token is used. */
    @Transactional
    public void verifyEmail(String rawToken) {
        SfUser user = users.findByVerifyTokenHash(hash(rawToken))
                .filter(u -> u.getVerifyTokenExpiresAt() != null && u.getVerifyTokenExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("Verification link is invalid or has expired"));
        user.setStatus(UserStatus.ACTIVE);
        user.setVerifyTokenHash(null);
        user.setVerifyTokenExpiresAt(null);
        users.save(user);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(UUID id) {
        return organizationResponse(requireOrganization(id));
    }

    public DepartmentResponse createDepartment(DepartmentRequest request) {
        requireOrganization(request.organizationId());
        SfDepartment department = new SfDepartment();
        department.setOrganizationId(request.organizationId());
        department.setName(request.name());
        department.setCode(request.code());
        return departmentResponse(departments.save(department));
    }

    @Transactional(readOnly = true)
    public Page<SfDepartment> listDepartments(UUID organizationId, Pageable pageable) {
        requireOrganization(organizationId);
        return departments.findByOrganizationId(organizationId, pageable);
    }

    public BatchResponse createBatch(BatchRequest request) {
        requireOrganization(request.organizationId());
        SfBatch batch = new SfBatch();
        batch.setOrganizationId(request.organizationId());
        batch.setDepartmentId(request.departmentId());
        batch.setName(request.name());
        batch.setCode(request.code());
        batch.setStartsOn(request.startsOn());
        batch.setEndsOn(request.endsOn());
        return batchResponse(batches.save(batch));
    }

    @Transactional(readOnly = true)
    public Page<SfBatch> listBatches(UUID organizationId, Pageable pageable) {
        requireOrganization(organizationId);
        return batches.findByOrganizationId(organizationId, pageable);
    }

    public UserResponse createUser(UserRequest request) {
        requireOrganization(request.organizationId());
        users.findByOrganizationIdAndEmail(request.organizationId(), request.email().toLowerCase())
                .ifPresent(existing -> { throw new IllegalArgumentException("User email already exists in organization"); });
        SfUser user = new SfUser();
        user.setOrganizationId(request.organizationId());
        user.setEmail(request.email().toLowerCase());
        user.setFullName(request.fullName());
        user.setRole(request.role());
        boolean passwordWasGenerated = request.password() == null || request.password().isBlank();
        String rawPassword = passwordWasGenerated
                ? java.util.UUID.randomUUID().toString().substring(0, 12)
                : request.password();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user = users.save(user);
        if (request.role() == UserRole.CANDIDATE) {
            SfCandidateProfile profile = new SfCandidateProfile();
            profile.setOrganizationId(request.organizationId());
            profile.setUserId(user.getId());
            profile.setDepartmentId(request.departmentId());
            profile.setBatchId(request.batchId());
            profile.setPhone(request.phone());
            profile.setExternalRef(request.externalRef());
            candidateProfiles.save(profile);
        }
        sendInviteEmail(user, rawPassword);
        return new UserResponse(user.getId(), user.getOrganizationId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.getStatus(), passwordWasGenerated ? rawPassword : null);
    }

    private void sendInviteEmail(SfUser user, String rawPassword) {
        emailService.send(user.getEmail(), "Your SkillForge account is ready",
                "An account has been created for you on SkillForge.\n\n"
                        + "Email: " + user.getEmail() + "\n"
                        + "Temporary password: " + rawPassword + "\n\n"
                        + "Sign in and change your password as soon as possible.");
    }

    /**
     * Validates SkillForge login credentials (org admins, trainers, evaluators,
     * and candidates all share this — they're all SfUser rows distinguished
     * by role). Email is unique per-organization, not globally, so more than
     * one account can share an email across different organizations; this
     * checks the password against every matching account and logs into the
     * first one it matches.
     */
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15;

    @Transactional
    public LoginResponse login(String email, String rawPassword) {
        List<SfUser> candidates = users.findByEmail(email.toLowerCase());
        Instant now = Instant.now();

        // Only IP-based rate limiting existed before this -- useless against
        // a distributed attacker credential-stuffing one specific account
        // from many IPs. If every account sharing this email is currently
        // locked, reject before even checking the password (so a lockout
        // can't be probed/bypassed by guessing correctly while locked).
        boolean allLocked = !candidates.isEmpty()
                && candidates.stream().allMatch(u -> u.getLockedUntil() != null && u.getLockedUntil().isAfter(now));
        if (allLocked) {
            throw new IllegalArgumentException("Account temporarily locked due to repeated failed sign-in attempts — try again in a few minutes");
        }

        Optional<SfUser> matched = candidates.stream()
                .filter(u -> u.getLockedUntil() == null || u.getLockedUntil().isBefore(now))
                .filter(u -> u.getPasswordHash() != null && passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .findFirst();

        if (matched.isEmpty()) {
            // Record the failure against every currently-unlocked account
            // sharing this email (email is unique per-organization, not
            // globally, so more than one account can share it).
            for (SfUser u : candidates) {
                if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) continue;
                u.setFailedLoginAttempts(u.getFailedLoginAttempts() + 1);
                if (u.getFailedLoginAttempts() >= MAX_FAILED_LOGIN_ATTEMPTS) {
                    u.setLockedUntil(now.plusSeconds(LOCKOUT_MINUTES * 60));
                }
                users.save(u);
            }
            throw new IllegalArgumentException("Invalid email or password");
        }

        SfUser user = matched.get();
        if (user.getStatus() == UserStatus.INVITED) {
            throw new IllegalArgumentException("Please verify your email before signing in — check your inbox for the verification link");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active");
        }
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            users.save(user);
        }
        return issueSession(user);
    }

    /**
     * Exchanges a valid, unexpired, unrevoked refresh token for a brand new
     * access token + a rotated refresh token (the old one is revoked so it
     * can't be replayed). Without this, a SkillForge session had no renewal
     * path at all and hard-expired every 15 minutes.
     */
    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        SfRefreshToken token = refreshTokens.findByToken(rawRefreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        token.setRevoked(true);
        SfUser user = users.findById(token.getSfUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active");
        }
        return issueSession(user);
    }

    /** Revokes a refresh token so it can no longer be used to renew a session (sign-out). */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByToken(rawRefreshToken).ifPresent(t -> t.setRevoked(true));
    }

    private LoginResponse issueSession(SfUser user) {
        String accessToken = jwtService.issueAccessToken(user.getEmail(), user.getId(), user.getRole().name(), user.getOrganizationId());
        SfRefreshToken refreshToken = new SfRefreshToken();
        refreshToken.setSfUserId(user.getId());
        refreshToken.setToken(UUID.randomUUID() + "." + UUID.randomUUID());
        refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenDays * 24 * 3600));
        refreshTokens.save(refreshToken);
        return new LoginResponse(accessToken, refreshToken.getToken(), userResponse(user));
    }

    /**
     * Always returns normally, whether or not the email matches an account —
     * this deliberately avoids leaking which emails have accounts on this
     * platform. If SMTP isn't configured, EmailService logs the reset link
     * instead of failing, so this remains safe to call in any environment.
     */
    public void forgotPassword(String email) {
        List<SfUser> matches = users.findByEmail(email.toLowerCase());
        for (SfUser user : matches) {
            String rawToken = java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "");
            user.setResetTokenHash(hash(rawToken));
            user.setResetTokenExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(1)));
            users.save(user);
            emailService.send(user.getEmail(), "Reset your SkillForge password",
                    "A password reset was requested for your account. Use this token within 1 hour to reset it:\n\n" + rawToken
                            + "\n\nIf you didn't request this, you can safely ignore this email.");
        }
    }

    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = hash(rawToken);
        SfUser user = users.findByResetTokenHash(tokenHash)
                .filter(u -> u.getResetTokenExpiresAt() != null && u.getResetTokenExpiresAt().isAfter(java.time.Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("Reset token is invalid or has expired"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetTokenHash(null);
        user.setResetTokenExpiresAt(null);
        users.save(user);
    }

    /**
     * "Sign in with Google" — deliberately scoped to EXISTING accounts only.
     * The frontend obtains a Google ID token via Google's own client-side
     * Sign-In SDK; this verifies it server-side against Google's tokeninfo
     * endpoint (audience must match GOOGLE_CLIENT_ID, email must be
     * verified), then looks up a matching SfUser by email. It deliberately
     * does NOT auto-create a new account with an inferred role — someone
     * must already have been invited/registered through the normal flow.
     * Returns a normal error (not a crash) if GOOGLE_CLIENT_ID isn't
     * configured, so this stays safe to call in any environment.
     */
    @Value("${executionos.google.client-id:}")
    private String googleClientId;

    @Transactional
    public LoginResponse googleLogin(String idToken) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new IllegalStateException("Google sign-in is not configured on this server (GOOGLE_CLIENT_ID not set)");
        }
        var restTemplate = new org.springframework.web.client.RestTemplate();
        Map<String, Object> claims;
        try {
            claims = restTemplate.getForObject(
                    "https://oauth2.googleapis.com/tokeninfo?id_token=" + java.net.URLEncoder.encode(idToken, java.nio.charset.StandardCharsets.UTF_8),
                    Map.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Google token verification failed");
        }
        if (claims == null || !googleClientId.equals(claims.get("aud")) || !"true".equals(String.valueOf(claims.get("email_verified")))) {
            throw new IllegalArgumentException("Invalid Google token");
        }
        String email = String.valueOf(claims.get("email")).toLowerCase();
        List<SfUser> matches = users.findByEmail(email);
        SfUser user = matches.stream().filter(u -> u.getStatus() == UserStatus.ACTIVE).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No SkillForge account exists for " + email + " — ask an admin to invite you first"));
        return issueSession(user);
    }

    @Transactional(readOnly = true)
    public Page<SfUser> listUsers(UUID organizationId, UserRole role, Pageable pageable) {
        requireOrganization(organizationId);
        return role == null ? users.findAll(pageable) : users.findByOrganizationIdAndRole(organizationId, role, pageable);
    }

    public QuestionResponse createQuestion(QuestionRequest request) {
        requireOrganization(request.organizationId());
        SfQuestion question = new SfQuestion();
        question.setOrganizationId(request.organizationId());
        question.setSubjectId(request.subjectId());
        question.setCode(request.code());
        question.setType(request.type());
        question.setDifficulty(request.difficulty());
        question.setTags(defaultJson(request.tagsJson(), "[]"));
        question.setExpectedTimeSeconds(valueOr(request.expectedTimeSeconds(), 60));
        question.setDefaultMarks(valueOr(request.defaultMarks(), BigDecimal.ONE));
        question.setNegativeMarks(valueOr(request.negativeMarks(), BigDecimal.ZERO));
        question = questions.save(question);

        SfQuestionVersion version = buildVersion(question.getId(), 1, request);
        version = questionVersions.save(version);
        question.setCurrentVersionId(version.getId());
        return questionResponse(questions.save(question));
    }

    @Transactional(readOnly = true)
    public Page<SfQuestion> listQuestions(UUID organizationId, QuestionStatus status, Pageable pageable) {
        requireOrganization(organizationId);
        return status == null
                ? questions.findByOrganizationId(organizationId, pageable)
                : questions.findByOrganizationIdAndStatus(organizationId, status, pageable);
    }

    public QuestionResponse submitQuestionForReview(UUID questionId) {
        SfQuestion question = requireQuestion(questionId);
        question.setStatus(QuestionStatus.REVIEW);
        SfQuestionApproval approval = new SfQuestionApproval();
        approval.setQuestionId(question.getId());
        approval.setVersionId(question.getCurrentVersionId());
        approval.setStatus(ApprovalStatus.SUBMITTED);
        approvals.save(approval);
        return questionResponse(questions.save(question));
    }

    public QuestionResponse approveQuestion(UUID questionId, ApprovalRequest request) {
        SfQuestion question = requireQuestion(questionId);
        question.setStatus(QuestionStatus.APPROVED);
        SfQuestionApproval approval = approvals.findTopByQuestionIdOrderBySubmittedAtDesc(questionId).orElseGet(SfQuestionApproval::new);
        approval.setQuestionId(question.getId());
        approval.setVersionId(question.getCurrentVersionId());
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setReviewedBy(request.reviewerId());
        approval.setReviewNotes(request.notes());
        approval.setReviewedAt(Instant.now());
        approvals.save(approval);
        return questionResponse(questions.save(question));
    }

    public AssessmentResponse createAssessment(AssessmentRequest request) {
        requireOrganization(request.organizationId());
        SfAssessment assessment = new SfAssessment();
        assessment.setOrganizationId(request.organizationId());
        assessment.setTemplateId(request.templateId());
        assessment.setTitle(request.title());
        assessment.setDescription(request.description());
        assessment.setDurationMinutes(valueOr(request.durationMinutes(), 60));
        assessment.setPassingPercentage(valueOr(request.passingPercentage(), new BigDecimal("60.00")));
        assessment.setStartAt(request.startAt());
        assessment.setEndAt(request.endAt());
        assessment.setGracePeriodMinutes(valueOr(request.gracePeriodMinutes(), 0));
        assessment.setCandidateLimit(request.candidateLimit());
        assessment.setShuffleQuestions(valueOr(request.shuffleQuestions(), true));
        assessment.setShowResultImmediately(valueOr(request.showResultImmediately(), false));
        assessment.setCreatedBy(request.createdBy());
        return assessmentResponse(assessments.save(assessment));
    }

    @Transactional(readOnly = true)
    public Page<SfAssessment> listAssessments(UUID organizationId, Pageable pageable) {
        requireOrganization(organizationId);
        return assessments.findByOrganizationId(organizationId, pageable);
    }

    public SfAssessmentSection addSection(UUID assessmentId, SectionRequest request) {
        requireAssessment(assessmentId);
        SfAssessmentSection section = new SfAssessmentSection();
        section.setAssessmentId(assessmentId);
        section.setName(request.name());
        section.setInstructions(request.instructions());
        section.setSortOrder(valueOr(request.sortOrder(), 0));
        section.setDurationMinutes(request.durationMinutes());
        section.setTotalMarks(valueOr(request.totalMarks(), BigDecimal.ZERO));
        section.setRules(defaultJson(request.rulesJson(), "{}"));
        return sections.save(section);
    }

    public SfAssessmentQuestion addQuestionToAssessment(UUID assessmentId, AddQuestionRequest request) {
        SfAssessment assessment = requireAssessment(assessmentId);
        if (assessment.getStatus() != AssessmentStatus.DRAFT) {
            throw new IllegalArgumentException("Only draft assessments can be edited");
        }
        SfQuestion question = requireQuestion(request.questionId());
        if (question.getStatus() != QuestionStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved questions can be added to assessments");
        }
        SfAssessmentQuestion selected = new SfAssessmentQuestion();
        selected.setAssessmentId(assessmentId);
        selected.setQuestionId(question.getId());
        selected.setQuestionVersionId(question.getCurrentVersionId());
        selected.setMarks(valueOr(request.marks(), question.getDefaultMarks()));
        selected.setNegativeMarks(valueOr(request.negativeMarks(), question.getNegativeMarks()));
        selected.setSortOrder(valueOr(request.sortOrder(), 0));
        question.setUsageCount(question.getUsageCount() + 1);
        questions.save(question);
        return assessmentQuestions.save(selected);
    }

    public AssessmentResponse publishAssessment(UUID assessmentId) {
        SfAssessment assessment = requireAssessment(assessmentId);
        if (assessmentQuestions.findByAssessmentIdOrderBySortOrder(assessmentId).isEmpty()) {
            throw new IllegalArgumentException("Assessment requires at least one approved question before publishing");
        }
        assessment.setStatus(AssessmentStatus.PUBLISHED);
        assessment.setPublishedAt(Instant.now());
        return assessmentResponse(assessments.save(assessment));
    }

    public AssessmentResponse scheduleAssessment(UUID assessmentId, AssessmentRequest request) {
        SfAssessment assessment = requireAssessment(assessmentId);
        assessment.setStartAt(request.startAt());
        assessment.setEndAt(request.endAt());
        assessment.setGracePeriodMinutes(valueOr(request.gracePeriodMinutes(), assessment.getGracePeriodMinutes()));
        assessment.setCandidateLimit(request.candidateLimit());
        assessment.setStatus(AssessmentStatus.SCHEDULED);
        return assessmentResponse(assessments.save(assessment));
    }

    public List<InviteResponse> inviteCandidates(UUID assessmentId, InviteRequest request) {
        SfAssessment assessment = requireAssessment(assessmentId);
        Instant expiresAt = request.expiresAt() == null
                ? valueOr(assessment.getEndAt(), Instant.now().plusSeconds(7 * 24 * 3600))
                : request.expiresAt();
        return request.candidateUserIds().stream().map(candidateId -> {
            SfUser candidate = users.findById(candidateId).orElseThrow();
            if (!candidate.getOrganizationId().equals(assessment.getOrganizationId())) {
                throw new IllegalArgumentException("Candidate belongs to a different organization");
            }
            String token = UUID.randomUUID() + "." + UUID.randomUUID();
            SfAssessmentInvitation invitation = new SfAssessmentInvitation();
            invitation.setOrganizationId(assessment.getOrganizationId());
            invitation.setAssessmentId(assessment.getId());
            invitation.setCandidateUserId(candidateId);
            invitation.setTokenHash(hash(token));
            invitation.setExpiresAt(expiresAt);
            invitation.setStatus(InvitationStatus.SENT);
            invitation.setEmailStatus("QUEUED");
            invitation.setSentAt(Instant.now());
            invitation = invitations.save(invitation);

            // The plaintext token exists only in this method's stack frame --
            // tokenHash is one-way, so this is the only point in the entire
            // application where it's ever recoverable. Previously nothing
            // sent it anywhere (emailStatus was set to "QUEUED" as a string
            // with no queue behind it), so a candidate invitation was a dead
            // end: no email, no log, no way for anyone to actually start the
            // assessment. This routes it through the same EmailService every
            // other transactional message uses -- with real SMTP configured
            // it emails the candidate a real link; without SMTP configured
            // (e.g. local/dev), it logs the full token/link at INFO level on
            // the backend so a trainer can retrieve it from server logs.
            String candidateLink = frontendBaseUrl + "/candidate?token=" + token;
            emailService.send(candidate.getEmail(), "You're invited to " + assessment.getTitle(),
                    "You've been invited to take \"" + assessment.getTitle() + "\".\n\n"
                            + "Open this link to begin: " + candidateLink + "\n\n"
                            + "Or open the Test Player and paste this invitation token: " + token + "\n\n"
                            + "This link expires " + expiresAt + ".");

            return new InviteResponse(invitation.getId(), candidateId, token.substring(0, 12) + "...", expiresAt);
        }).toList();
    }

    public ValidateLinkResponse validateLink(ValidateLinkRequest request) {
        SfAssessmentInvitation invitation = invitations.findByTokenHash(hash(request.token()))
                .orElseThrow(() -> new NoSuchElementException("Invitation not found"));
        boolean valid = invitation.getExpiresAt().isAfter(Instant.now()) && invitation.getConsumedAt() == null;
        if (valid && invitation.getOpenedAt() == null) {
            invitation.setOpenedAt(Instant.now());
            invitation.setStatus(InvitationStatus.OPENED);
            invitations.save(invitation);
        }
        return new ValidateLinkResponse(valid, invitation.getId(), invitation.getAssessmentId(), invitation.getCandidateUserId(), invitation.getStatus().name());
    }

    public AttemptResponse startAttempt(UUID invitationId, String ipAddress, String userAgent) {
        SfAssessmentInvitation invitation = invitations.findById(invitationId).orElseThrow();
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Invitation has expired");
        }
        SfAssessment assessment = requireAssessment(invitation.getAssessmentId());
        SfAttempt attempt = new SfAttempt();
        attempt.setOrganizationId(invitation.getOrganizationId());
        attempt.setAssessmentId(invitation.getAssessmentId());
        attempt.setInvitationId(invitation.getId());
        attempt.setCandidateUserId(invitation.getCandidateUserId());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(Instant.now());
        attempt.setExpiresAt(Instant.now().plusSeconds((long) assessment.getDurationMinutes() * 60));
        attempt.setIpAddress(ipAddress);
        attempt.setUserAgent(userAgent);
        invitation.setConsumedAt(Instant.now());
        invitation.setStatus(InvitationStatus.CONSUMED);
        invitations.save(invitation);
        return attemptResponse(attempts.save(attempt));
    }

    public SfAttemptAnswer saveAnswer(UUID attemptId, UUID questionId, AnswerRequest request) {
        SfAttempt attempt = requireAttempt(attemptId);
        SfAssessmentQuestion selected = assessmentQuestions.findByAssessmentIdOrderBySortOrder(attempt.getAssessmentId()).stream()
                .filter(item -> item.getQuestionId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Question is not part of this assessment"));
        SfAttemptAnswer answer = answers.findByAttemptIdAndQuestionId(attemptId, questionId).orElseGet(SfAttemptAnswer::new);
        answer.setAttemptId(attemptId);
        answer.setQuestionId(questionId);
        answer.setQuestionVersionId(selected.getQuestionVersionId());
        answer.setAnswer(defaultJson(request.answerJson(), "{}"));
        answer.setStatus(request.flaggedForReview() ? AnswerStatus.FLAGGED : AnswerStatus.ANSWERED);
        answer.setFlaggedForReview(request.flaggedForReview());
        answer.setAutoSavedAt(Instant.now());
        return answers.save(answer);
    }

    public SfAttemptEvent recordEvent(UUID attemptId, EventRequest request) {
        requireAttempt(attemptId);
        SfAttemptEvent event = new SfAttemptEvent();
        event.setAttemptId(attemptId);
        event.setEventType(request.eventType());
        event.setSeverity(request.severity() == null ? EventSeverity.INFO : request.severity());
        event.setDetails(defaultJson(request.detailsJson(), "{}"));
        return events.save(event);
    }

    public AttemptResponse submitAttempt(UUID attemptId) {
        SfAttempt attempt = requireAttempt(attemptId);
        BigDecimal total = assessmentQuestions.findByAssessmentIdOrderBySortOrder(attempt.getAssessmentId()).stream()
                .map(SfAssessmentQuestion::getMarks)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal awarded = answers.findByAttemptId(attemptId).stream()
                .map(answer -> answer.getAwardedMarks() == null ? BigDecimal.ZERO : answer.getAwardedMarks())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal percentage = total.signum() == 0 ? BigDecimal.ZERO : awarded.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP);
        SfAssessment assessment = requireAssessment(attempt.getAssessmentId());
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(Instant.now());
        attempt.setScore(awarded);
        attempt.setPercentage(percentage);
        attempt.setPassed(percentage.compareTo(assessment.getPassingPercentage()) >= 0);
        long eventCount = events.countByAttemptId(attemptId);
        attempt.setSuspiciousScore(BigDecimal.valueOf(Math.min(100, eventCount * 5L)));
        return attemptResponse(attempts.save(attempt));
    }

    @Transactional(readOnly = true)
    public LiveDashboardResponse liveDashboard(UUID assessmentId) {
        List<SfAttempt> all = attempts.findByAssessmentId(assessmentId);
        return new LiveDashboardResponse(
                attempts.countByAssessmentIdAndStatus(assessmentId, AttemptStatus.NOT_STARTED),
                attempts.countByAssessmentIdAndStatus(assessmentId, AttemptStatus.IN_PROGRESS),
                attempts.countByAssessmentIdAndStatus(assessmentId, AttemptStatus.SUBMITTED),
                attempts.countByAssessmentIdAndStatus(assessmentId, AttemptStatus.EVALUATED),
                all);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> candidateReport(UUID organizationId, UUID candidateUserId) {
        return Map.of(
                "candidateUserId", candidateUserId,
                "attempts", attempts.findByOrganizationIdAndCandidateUserId(organizationId, candidateUserId, Pageable.unpaged()).getContent());
    }

    @Transactional(readOnly = true)
    public HealthDashboardResponse healthDashboard(UUID organizationId) {
        return new HealthDashboardResponse(
                "ok",
                organizations.count(),
                users.countByOrganizationIdAndRole(organizationId, UserRole.TRAINER),
                users.countByOrganizationIdAndRole(organizationId, UserRole.CANDIDATE),
                questions.countByOrganizationIdAndStatus(organizationId, QuestionStatus.DRAFT),
                questions.countByOrganizationIdAndStatus(organizationId, QuestionStatus.APPROVED),
                assessments.countByOrganizationIdAndStatus(organizationId, AssessmentStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public List<SubjectCoverage> catalogCoverage(UUID organizationId) {
        Map<UUID, long[]> countsBySubject = new java.util.HashMap<>(); // [easy, medium, hard]
        for (Object[] row : questions.countBySubjectAndDifficulty(organizationId)) {
            UUID subjectId = (UUID) row[0];
            Difficulty difficulty = (Difficulty) row[1];
            long count = (Long) row[2];
            long[] bucket = countsBySubject.computeIfAbsent(subjectId, k -> new long[3]);
            switch (difficulty) {
                case EASY -> bucket[0] += count;
                case MEDIUM -> bucket[1] += count;
                case HARD -> bucket[2] += count;
            }
        }
        return subjects.findByOrganizationIdIsNullOrOrganizationId(organizationId).stream()
                .map(subject -> {
                    long[] bucket = countsBySubject.getOrDefault(subject.getId(), new long[3]);
                    long total = bucket[0] + bucket[1] + bucket[2];
                    return new SubjectCoverage(subject.getName(), subject.getSlug(), total, bucket[0], bucket[1], bucket[2]);
                })
                .toList();
    }

    /**
     * Public, unauthenticated question browsing (used by the trainer question
     * bank UI and, provisionally, the candidate exam player — see
     * candidate-player.tsx for the known gap: candidates currently get a
     * subject-filtered browse of the real bank rather than their specific
     * assessment's actual question set, since the attempt->questions
     * resolution endpoint doesn't exist yet). Deliberately never includes
     * correctAnswer or explanation — see TrainerCatalogQuestion for the
     * role-gated equivalent that does.
     */
    @Transactional(readOnly = true)
    public List<CatalogQuestion> generatedCatalog(UUID organizationId, String subjectSlug, Difficulty difficulty, int page, int size) {
        String normalizedSlug = (subjectSlug == null || subjectSlug.isBlank()) ? "java" : normalizeSlug(subjectSlug);
        Difficulty selectedDifficulty = difficulty == null ? Difficulty.EASY : difficulty;
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);

        SfSubject subject = subjects.findByOrganizationIdIsNullAndSlug(normalizedSlug).orElse(null);
        if (subject == null) return List.of();

        Page<SfQuestion> page1 = questions.findByOrganizationIdAndSubjectIdAndDifficultyAndStatus(
                organizationId, subject.getId(), selectedDifficulty, QuestionStatus.APPROVED,
                org.springframework.data.domain.PageRequest.of(safePage, safeSize));
        List<UUID> versionIds = page1.getContent().stream().map(SfQuestion::getCurrentVersionId).filter(java.util.Objects::nonNull).toList();
        Map<UUID, SfQuestionVersion> versionsById = questionVersions.findAllById(versionIds).stream()
                .collect(java.util.stream.Collectors.toMap(SfQuestionVersion::getId, v -> v));

        return page1.getContent().stream()
                .map(q -> {
                    SfQuestionVersion version = versionsById.get(q.getCurrentVersionId());
                    if (version == null) return null;
                    return new CatalogQuestion(
                            q.getId().toString(), subject.getName(), version.getTitle(), q.getType(), q.getDifficulty(),
                            version.getPrompt(), version.getOptions(), q.getExpectedTimeSeconds());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Role-gated (see SecurityConfig) — the one place correctAnswer is allowed to leave the server. */
    @Transactional(readOnly = true)
    public TrainerCatalogQuestion trainerQuestionDetail(UUID organizationId, UUID questionId) {
        SfQuestion question = questions.findById(questionId)
                .filter(q -> q.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new NoSuchElementException("Question not found"));
        SfQuestionVersion version = questionVersions.findById(question.getCurrentVersionId())
                .orElseThrow(() -> new NoSuchElementException("Question has no current version"));
        String subjectName = subjects.findByOrganizationIdIsNullOrOrganizationId(organizationId).stream()
                .filter(s -> s.getId().equals(question.getSubjectId()))
                .findFirst().map(SfSubject::getName).orElse("Unknown");
        return new TrainerCatalogQuestion(
                question.getId().toString(), subjectName, version.getTitle(), question.getType(), question.getDifficulty(),
                version.getPrompt(), version.getOptions(), version.getCorrectAnswer(), version.getExplanation(), question.getExpectedTimeSeconds());
    }

    public DemoBootstrapResponse bootstrapDemo() {
        SfOrganization organization = organizations.findBySlug("apex-learning-cloud").orElseGet(() -> {
            SfOrganization created = new SfOrganization();
            created.setName("Apex Learning Cloud");
            created.setSlug("apex-learning-cloud");
            return organizations.save(created);
        });
        users.findByOrganizationIdAndEmail(organization.getId(), "admin@apex.example").orElseGet(() -> {
            SfUser admin = new SfUser();
            admin.setOrganizationId(organization.getId());
            admin.setEmail("admin@apex.example");
            admin.setFullName("Apex Admin");
            admin.setRole(UserRole.ORG_ADMIN);
            admin.setPasswordHash(passwordEncoder.encode("Demo@12345"));
            return users.save(admin);
        });
        users.findByOrganizationIdAndEmail(organization.getId(), "trainer@apex.example").orElseGet(() -> {
            SfUser trainer = new SfUser();
            trainer.setOrganizationId(organization.getId());
            trainer.setEmail("trainer@apex.example");
            trainer.setFullName("Apex Trainer");
            trainer.setRole(UserRole.TRAINER);
            trainer.setPasswordHash(passwordEncoder.encode("Demo@12345"));
            return users.save(trainer);
        });
        users.findByOrganizationIdAndEmail(organization.getId(), "candidate@apex.example").orElseGet(() -> {
            SfUser candidate = new SfUser();
            candidate.setOrganizationId(organization.getId());
            candidate.setEmail("candidate@apex.example");
            candidate.setFullName("Apex Candidate");
            candidate.setRole(UserRole.CANDIDATE);
            candidate.setPasswordHash(passwordEncoder.encode("Demo@12345"));
            return users.save(candidate);
        });
        long realQuestionCount = questions.count();
        return new DemoBootstrapResponse(organization.getId(), organization.getName(), realQuestionCount, "trainer@apex.example", "candidate@apex.example");
    }

    /**
     * CSV columns (header row required):
     *   subject,topic,type,difficulty,prompt,options,correct_answer,explanation,expected_time_seconds,marks
     *
     * - type: one of MULTIPLE_CHOICE, MULTIPLE_SELECT, FILL_BLANK, CODE_OUTPUT,
     *   CODE_COMPLETION, CODING, SCENARIO, TRUE_FALSE, DRAG_DROP, ORDERING
     * - difficulty: EASY, MEDIUM, or HARD
     * - options: pipe-separated choices, e.g. "Data hiding|Inheritance|Polymorphism"
     *   (leave blank for FILL_BLANK/CODING/CODE_OUTPUT/SCENARIO question types)
     * - correct_answer: for MULTIPLE_CHOICE/TRUE_FALSE, the exact text of the
     *   correct option; for MULTIPLE_SELECT, pipe-separated correct options;
     *   for other types, the expected answer text
     * - expected_time_seconds, marks: optional, default to 60 and 1
     *
     * Imported questions are created with status APPROVED directly (skipping
     * the manual one-by-one review workflow) — the import itself, restricted
     * to ADMIN/TRAINER/EVALUATOR roles, is the trust boundary. Re-importing
     * the same CSV is safe: each row's dedup code is a deterministic hash of
     * subject+prompt, so identical rows are skipped as duplicates rather than
     * creating a second copy.
     */
    private static final int MAX_CSV_BYTES = 2 * 1024 * 1024; // 2MB
    private static final int MAX_CSV_ROWS = 5000;

    public QuestionImportReport importQuestionsFromCsv(UUID organizationId, UUID createdBy, String csvContent) {
        if (csvContent == null || csvContent.isBlank()) {
            throw new IllegalArgumentException("CSV content is empty");
        }
        if (csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_CSV_BYTES) {
            throw new IllegalArgumentException("CSV is too large (max " + (MAX_CSV_BYTES / 1024 / 1024) + "MB) — split it into smaller files");
        }
        List<String> warnings = new java.util.ArrayList<>();
        List<Map<String, String>> rows = parseCsv(csvContent, warnings);
        if (rows.size() > MAX_CSV_ROWS) {
            throw new IllegalArgumentException("CSV has " + rows.size() + " rows — max " + MAX_CSV_ROWS + " per import, split it into smaller files");
        }
        int imported = 0, duplicates = 0, invalid = 0, failed = 0;

        Map<String, SfSubject> subjectBySlug = subjects.findByOrganizationIdIsNullOrOrganizationId(organizationId).stream()
                .collect(java.util.stream.Collectors.toMap(SfSubject::getSlug, s -> s, (a, b) -> a));

        for (Map<String, String> row : rows) {
            try {
                String subjectName = row.getOrDefault("subject", "").trim();
                String prompt = row.getOrDefault("prompt", "").trim();
                String typeRaw = row.getOrDefault("type", "").trim().toUpperCase();
                String difficultyRaw = row.getOrDefault("difficulty", "").trim().toUpperCase();

                if (subjectName.isEmpty() || prompt.isEmpty() || typeRaw.isEmpty() || difficultyRaw.isEmpty()) {
                    invalid++;
                    warnings.add("Skipped row (missing subject/prompt/type/difficulty): " + truncate(prompt, 60));
                    continue;
                }
                SfSubject subject = subjectBySlug.get(normalizeSlug(subjectName));
                if (subject == null) {
                    invalid++;
                    warnings.add("Unknown subject '" + subjectName + "' for question: " + truncate(prompt, 60));
                    continue;
                }
                QuestionType type;
                Difficulty difficulty;
                try {
                    type = QuestionType.valueOf(typeRaw);
                    difficulty = Difficulty.valueOf(difficultyRaw);
                } catch (IllegalArgumentException ex) {
                    invalid++;
                    warnings.add("Invalid type/difficulty for question: " + truncate(prompt, 60));
                    continue;
                }

                String optionsCell = row.getOrDefault("options", "").trim();
                List<String> optionList = optionsCell.isEmpty() ? List.of() :
                        java.util.Arrays.stream(optionsCell.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                String correctAnswerCell = row.getOrDefault("correct_answer", "").trim();
                if ((type == QuestionType.MULTIPLE_CHOICE || type == QuestionType.TRUE_FALSE) && !optionList.isEmpty()
                        && !optionList.contains(correctAnswerCell)) {
                    invalid++;
                    warnings.add("correct_answer does not match any option for question: " + truncate(prompt, 60));
                    continue;
                }

                String code = normalizeSlug(subjectName) + "-" + hash(subjectName + "|" + prompt).substring(0, 12);
                if (questions.findByOrganizationIdAndCode(organizationId, code).isPresent()) {
                    duplicates++;
                    continue;
                }

                SfQuestion question = new SfQuestion();
                question.setOrganizationId(organizationId);
                question.setSubjectId(subject.getId());
                question.setCode(code);
                question.setType(type);
                question.setDifficulty(difficulty);
                question.setStatus(QuestionStatus.APPROVED);
                question.setExpectedTimeSeconds(parseIntOrDefault(row.get("expected_time_seconds"), 60));
                question.setDefaultMarks(parseDecimalOrDefault(row.get("marks"), BigDecimal.ONE));
                question.setCreatedBy(createdBy);
                question.setTopic(row.getOrDefault("topic", "").trim());
                question = questions.save(question);

                SfQuestionVersion version = new SfQuestionVersion();
                version.setQuestionId(question.getId());
                version.setVersionNumber(1);
                version.setTitle(row.getOrDefault("topic", subjectName).trim());
                version.setPrompt(prompt);
                version.setOptions(toJsonArray(optionList));
                version.setCorrectAnswer(type == QuestionType.MULTIPLE_SELECT
                        ? toJsonArray(java.util.Arrays.stream(correctAnswerCell.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList())
                        : "\"" + correctAnswerCell.replace("\"", "\\\"") + "\"");
                version.setExplanation(row.getOrDefault("explanation", "").trim());
                version.setCreatedBy(createdBy);
                version = questionVersions.save(version);

                question.setCurrentVersionId(version.getId());
                questions.save(question);
                imported++;
            } catch (Exception ex) {
                failed++;
                warnings.add("Row failed: " + ex.getMessage());
            }
        }
        return new QuestionImportReport(rows.size(), imported, duplicates, invalid, failed, warnings);
    }

    private List<Map<String, String>> parseCsv(String content, List<String> warnings) {
        List<List<String>> lines = new java.util.ArrayList<>();
        List<String> current = new java.util.ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < content.length() && content.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (c == '"') inQuotes = false;
                else field.append(c);
            } else {
                if (c == '"') inQuotes = true;
                else if (c == ',') { current.add(field.toString()); field.setLength(0); }
                else if (c == '\n' || c == '\r') {
                    if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                    current.add(field.toString()); field.setLength(0);
                    if (current.size() > 1 || !current.get(0).isBlank()) lines.add(new java.util.ArrayList<>(current));
                    current.clear();
                } else field.append(c);
            }
        }
        if (field.length() > 0 || !current.isEmpty()) { current.add(field.toString()); lines.add(current); }

        if (lines.isEmpty()) return List.of();
        List<String> header = lines.get(0).stream().map(h -> h.trim().toLowerCase()).toList();
        List<Map<String, String>> rows = new java.util.ArrayList<>();
        for (int r = 1; r < lines.size(); r++) {
            List<String> line = lines.get(r);
            if (line.size() != header.size()) {
                warnings.add("Row " + (r + 1) + " has " + line.size() + " columns, expected " + header.size() + " — skipped");
                continue;
            }
            Map<String, String> row = new java.util.LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) row.put(header.get(c), line.get(c));
            rows.add(row);
        }
        return rows;
    }

    private String toJsonArray(List<String> values) {
        return values.stream()
                .map(v -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private int parseIntOrDefault(String raw, int fallback) {
        try { return (raw == null || raw.isBlank()) ? fallback : Integer.parseInt(raw.trim()); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private BigDecimal parseDecimalOrDefault(String raw, BigDecimal fallback) {
        try { return (raw == null || raw.isBlank()) ? fallback : new BigDecimal(raw.trim()); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    /**
     * The candidate's real, attempt-specific question set — resolved from
     * the assessment's actual blueprint, not a generic subject browse. This
     * closes the gap the previous catalog-based approach had (candidates
     * were getting a subject-filtered slice of the whole bank rather than
     * their assigned assessment's real questions). Shuffled deterministically
     * per attempt (same candidate reloading sees the same order; different
     * candidates get different shuffles) within each difficulty band, so
     * questions still present easy-to-hard.
     */
    @Transactional(readOnly = true)
    public List<CandidateQuestionView> examQuestions(UUID attemptId) {
        SfAttempt attempt = requireAttempt(attemptId);
        List<SfAssessmentQuestion> blueprint = assessmentQuestions.findByAssessmentIdOrderBySortOrder(attempt.getAssessmentId());

        List<UUID> questionIds = blueprint.stream().map(SfAssessmentQuestion::getQuestionId).toList();
        Map<UUID, SfQuestion> questionById = questions.findAllById(questionIds).stream()
                .collect(java.util.stream.Collectors.toMap(SfQuestion::getId, q -> q));
        Map<UUID, SfQuestionVersion> latestVersionByQuestion = questionVersions.findByQuestionIdInOrderByVersionNumberDesc(questionIds).stream()
                .collect(java.util.stream.Collectors.toMap(SfQuestionVersion::getQuestionId, v -> v, (a, b) -> a));

        Map<Difficulty, List<SfAssessmentQuestion>> byDifficulty = new java.util.EnumMap<>(Difficulty.class);
        for (Difficulty d : Difficulty.values()) byDifficulty.put(d, new java.util.ArrayList<>());
        for (SfAssessmentQuestion bp : blueprint) {
            SfQuestion q = questionById.get(bp.getQuestionId());
            if (q != null) byDifficulty.get(q.getDifficulty()).add(bp);
        }

        long seed = attemptId.getMostSignificantBits();
        List<CandidateQuestionView> ordered = new java.util.ArrayList<>();
        for (Difficulty d : new Difficulty[]{Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD}) {
            List<SfAssessmentQuestion> pool = byDifficulty.get(d);
            java.util.Collections.shuffle(pool, new java.util.Random(seed + d.ordinal()));
            for (SfAssessmentQuestion bp : pool) {
                SfQuestion question = questionById.get(bp.getQuestionId());
                SfQuestionVersion version = latestVersionByQuestion.get(bp.getQuestionId());
                if (question == null || version == null) continue;
                ordered.add(new CandidateQuestionView(
                        question.getId(), question.getCode(), question.getType(), question.getDifficulty(),
                        question.getTopic(), version.getTitle(), version.getPrompt(), version.getOptions(),
                        question.getExpectedTimeSeconds(), bp.getMarks()));
            }
        }
        return ordered;
    }

    /**
     * Trainer/evaluator-only. Full answer key with correct answers, explanations,
     * and the candidate's own responses side-by-side. Only ever returns data once
     * the attempt has actually been submitted, auto-submitted, or evaluated —
     * calling this while a candidate is still mid-exam throws, so answers can
     * never leak to a live attempt regardless of what the URL-level security allows.
     * Also role-gated via @PreAuthorize on the controller method as defense in depth.
     */
    @Transactional(readOnly = true)
    public AnswerKeyResponse answerKey(UUID attemptId) {
        SfAttempt attempt = requireAttempt(attemptId);
        if (attempt.getStatus() == AttemptStatus.NOT_STARTED || attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("Answer key is only available after the candidate submits the attempt");
        }
        List<SfAssessmentQuestion> blueprint = assessmentQuestions.findByAssessmentIdOrderBySortOrder(attempt.getAssessmentId());

        List<UUID> questionIds = blueprint.stream().map(SfAssessmentQuestion::getQuestionId).toList();
        Map<UUID, SfQuestion> questionById = questions.findAllById(questionIds).stream()
                .collect(java.util.stream.Collectors.toMap(SfQuestion::getId, q -> q));
        Map<UUID, SfQuestionVersion> latestVersionByQuestion = questionVersions.findByQuestionIdInOrderByVersionNumberDesc(questionIds).stream()
                .collect(java.util.stream.Collectors.toMap(SfQuestionVersion::getQuestionId, v -> v, (a, b) -> a));
        Map<UUID, SfAttemptAnswer> answerByQuestion = answers.findByAttemptId(attemptId).stream()
                .collect(java.util.stream.Collectors.toMap(SfAttemptAnswer::getQuestionId, a -> a));

        List<AnswerKeyItem> items = new java.util.ArrayList<>();
        for (SfAssessmentQuestion bp : blueprint) {
            SfQuestion question = questionById.get(bp.getQuestionId());
            SfQuestionVersion version = latestVersionByQuestion.get(bp.getQuestionId());
            if (question == null || version == null) continue;
            SfAttemptAnswer answer = answerByQuestion.get(bp.getQuestionId());
            boolean correct = answer != null && answer.getAwardedMarks() != null && answer.getAwardedMarks().signum() > 0;
            items.add(new AnswerKeyItem(
                    question.getId(), question.getCode(), version.getTitle(), version.getPrompt(),
                    question.getDifficulty(), question.getTopic(), version.getOptions(),
                    version.getCorrectAnswer(), version.getExplanation(),
                    answer == null ? null : answer.getAnswer(),
                    answer == null ? AnswerStatus.UNANSWERED : answer.getStatus(),
                    answer == null ? null : answer.getAwardedMarks(),
                    bp.getMarks(), correct));
        }
        return new AnswerKeyResponse(attempt.getId(), attempt.getAssessmentId(), attempt.getCandidateUserId(),
                attempt.getStatus(), attempt.getScore(), attempt.getPercentage(), attempt.getPassed(), items);
    }

    private SfOrganization requireOrganization(UUID id) {
        return organizations.findById(id).orElseThrow();
    }

    private SfQuestion requireQuestion(UUID id) {
        return questions.findById(id).orElseThrow();
    }

    private SfAssessment requireAssessment(UUID id) {
        return assessments.findById(id).orElseThrow();
    }

    private SfAttempt requireAttempt(UUID id) {
        return attempts.findById(id).orElseThrow();
    }

    private SfQuestionVersion buildVersion(UUID questionId, int versionNumber, QuestionRequest request) {
        SfQuestionVersion version = new SfQuestionVersion();
        version.setQuestionId(questionId);
        version.setVersionNumber(versionNumber);
        version.setTitle(request.title());
        version.setPrompt(request.prompt());
        version.setOptions(defaultJson(request.optionsJson(), "[]"));
        version.setCorrectAnswer(defaultJson(request.correctAnswerJson(), "{}"));
        version.setExplanation(request.explanation());
        version.setReferences(defaultJson(request.referencesJson(), "[]"));
        version.setScoring(defaultJson(request.scoringJson(), "{}"));
        return version;
    }

    private OrganizationResponse organizationResponse(SfOrganization organization) {
        return new OrganizationResponse(organization.getId(), organization.getName(), organization.getSlug(), organization.getStatus(),
                organization.getPrimaryColor(), organization.getSecondaryColor(), organization.getAccentColor(),
                organization.getCertificatePrefix(), organization.getLocale());
    }

    /**
     * Real persistence for the Settings page's organization branding card —
     * previously "Save changes" was local component state only with no
     * backend endpoint at all (see PRODUCTION_AUDIT.md, finding C4). The
     * SMTP/security-policy/integrations cards that used to sit alongside
     * this were removed from the frontend rather than given a fake save
     * too: their toggles were never actually read anywhere else in the
     * codebase (candidate-player.tsx doesn't consult them), so persisting
     * them would still have been misleading even with a working save
     * button. Real per-org SMTP/proctoring enforcement is genuine new
     * feature work, not a wiring fix.
     */
    @Transactional
    public OrganizationResponse updateOrganizationSettings(UUID organizationId, UpdateOrganizationSettingsRequest request) {
        SfOrganization organization = requireOrganization(organizationId);
        if (request.name() != null && !request.name().isBlank()) {
            organization.setName(request.name());
        }
        if (request.primaryColor() != null && !request.primaryColor().isBlank()) {
            organization.setPrimaryColor(request.primaryColor());
        }
        if (request.certificatePrefix() != null && !request.certificatePrefix().isBlank()) {
            organization.setCertificatePrefix(request.certificatePrefix());
        }
        if (request.locale() != null && !request.locale().isBlank()) {
            organization.setLocale(request.locale());
        }
        return organizationResponse(organizations.save(organization));
    }

    private DepartmentResponse departmentResponse(SfDepartment department) {
        return new DepartmentResponse(department.getId(), department.getOrganizationId(), department.getName(), department.getCode(), department.isActive());
    }

    private BatchResponse batchResponse(SfBatch batch) {
        return new BatchResponse(batch.getId(), batch.getOrganizationId(), batch.getDepartmentId(), batch.getName(), batch.getCode(), batch.isActive());
    }

    private UserResponse userResponse(SfUser user) {
        return new UserResponse(user.getId(), user.getOrganizationId(), user.getEmail(), user.getFullName(), user.getRole(), user.getStatus(), null);
    }

    private QuestionResponse questionResponse(SfQuestion question) {
        return new QuestionResponse(question.getId(), question.getOrganizationId(), question.getCode(), question.getType(), question.getDifficulty(), question.getStatus(), question.getCurrentVersionId());
    }

    private AssessmentResponse assessmentResponse(SfAssessment assessment) {
        return new AssessmentResponse(assessment.getId(), assessment.getOrganizationId(), assessment.getTitle(), assessment.getStatus(), assessment.getDurationMinutes(), assessment.getPassingPercentage(), assessment.getStartAt(), assessment.getEndAt());
    }

    private AttemptResponse attemptResponse(SfAttempt attempt) {
        return new AttemptResponse(attempt.getId(), attempt.getAssessmentId(), attempt.getCandidateUserId(), attempt.getStatus(), attempt.getScore(), attempt.getPercentage(), attempt.getPassed(), attempt.getSuspiciousScore());
    }

    private String normalizeSlug(String value) {
        return value.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String defaultJson(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Real search across candidates, approved questions, and assessments
     * for the caller's own organization. Replaces the old ExecutionOS-side
     * /search stub that always returned zero results regardless of query
     * (see PRODUCTION_AUDIT.md, finding C3). Simple case-insensitive
     * substring matching (SQL LIKE) rather than a dedicated search index --
     * a real, working implementation appropriate for this app's scale
     * (hundreds to low thousands of rows per organization), not a
     * placeholder.
     */
    @Transactional(readOnly = true)
    public SearchResponse search(UUID organizationId, String rawQuery, String type) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.isEmpty()) {
            return new SearchResponse(q, List.of(), List.of(), List.of());
        }
        boolean wantCandidates = type == null || type.isBlank() || type.equalsIgnoreCase("candidates");
        boolean wantQuestions = type == null || type.isBlank() || type.equalsIgnoreCase("questions");
        boolean wantAssessments = type == null || type.isBlank() || type.equalsIgnoreCase("assessments");

        List<CandidateSearchResult> candidateResults = !wantCandidates ? List.of() : users.searchCandidates(organizationId, q).stream()
                .limit(25)
                .map(u -> new CandidateSearchResult(u.getId(), u.getFullName(), u.getEmail()))
                .toList();
        List<QuestionSearchResult> questionResults = !wantQuestions ? List.of() : questions.searchQuestions(organizationId, q).stream()
                .limit(25)
                .map(qq -> new QuestionSearchResult(qq.getId(), qq.getCode(), qq.getTopic(), qq.getDifficulty()))
                .toList();
        List<AssessmentSearchResult> assessmentResults = !wantAssessments ? List.of() : assessments.searchByTitle(organizationId, q).stream()
                .limit(25)
                .map(a -> new AssessmentSearchResult(a.getId(), a.getTitle(), a.getStatus()))
                .toList();

        return new SearchResponse(q, candidateResults, questionResults, assessmentResults);
    }

    private <T> T valueOr(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash value", ex);
        }
    }

}
