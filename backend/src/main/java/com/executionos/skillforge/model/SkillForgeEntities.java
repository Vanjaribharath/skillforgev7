package com.executionos.skillforge.model;

import com.executionos.skillforge.model.SkillForgeEnums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Shared audit columns for SkillForge entities.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
abstract class SkillForgeEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @LastModifiedDate
    private Instant updatedAt = Instant.now();
    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

/**
 * Tenant root for organization-scoped assessment data and branding.
 */
@Entity
@Table(name = "sf_organizations")
class SfOrganization extends SkillForgeEntity {
    @Column(nullable = false) private String name;
    @Column(nullable = false, unique = true) private String slug;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrganizationStatus status = OrganizationStatus.ACTIVE;
    private String logoUrl;
    @Column(nullable = false) private String primaryColor = "#1D4ED8";
    @Column(nullable = false) private String secondaryColor = "#0F766E";
    @Column(nullable = false) private String accentColor = "#F59E0B";
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private String certificateTemplate = "{}";
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private String settings = "{}";
    @Column(nullable = false) private String certificatePrefix = "CERT";
    @Column(nullable = false) private String locale = "en";
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public OrganizationStatus getStatus() { return status; }
    public void setStatus(OrganizationStatus status) { this.status = status; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }
    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }
    public String getCertificateTemplate() { return certificateTemplate; }
    public void setCertificateTemplate(String certificateTemplate) { this.certificateTemplate = certificateTemplate; }
    public String getSettings() { return settings; }
    public void setSettings(String settings) { this.settings = settings; }
    public String getCertificatePrefix() { return certificatePrefix; }
    public void setCertificatePrefix(String certificatePrefix) { this.certificatePrefix = certificatePrefix; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
}

/**
 * Organization-scoped platform user for admins, trainers, evaluators, and candidates.
 */
@Entity
@Table(name = "sf_users")
class SfUser extends SkillForgeEntity {
    @Column(nullable = false) private UUID organizationId;
    @Column(nullable = false) private String email;
    @Column(nullable = false) private String fullName;
    private String passwordHash;
    private String resetTokenHash;
    private Instant resetTokenExpiresAt;
    private String verifyTokenHash;
    private Instant verifyTokenExpiresAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UserRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UserStatus status = UserStatus.ACTIVE;
    @Column(nullable = false) private String locale = "en";
    @Column(nullable = false) private String timezone = "UTC";
    private Instant lastLoginAt;
    @Column(nullable = false) private int failedLoginAttempts = 0;
    private Instant lockedUntil;
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getResetTokenHash() { return resetTokenHash; }
    public void setResetTokenHash(String resetTokenHash) { this.resetTokenHash = resetTokenHash; }
    public Instant getResetTokenExpiresAt() { return resetTokenExpiresAt; }
    public void setResetTokenExpiresAt(Instant resetTokenExpiresAt) { this.resetTokenExpiresAt = resetTokenExpiresAt; }
    public String getVerifyTokenHash() { return verifyTokenHash; }
    public void setVerifyTokenHash(String verifyTokenHash) { this.verifyTokenHash = verifyTokenHash; }
    public Instant getVerifyTokenExpiresAt() { return verifyTokenExpiresAt; }
    public void setVerifyTokenExpiresAt(Instant verifyTokenExpiresAt) { this.verifyTokenExpiresAt = verifyTokenExpiresAt; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
}

/**
 * Department used to organize candidates and reports.
 */
@Entity
@Table(name = "sf_departments")
class SfDepartment extends SkillForgeEntity {
    @Column(nullable = false) private UUID organizationId;
    @Column(nullable = false) private String name;
    private String code;
    @Column(nullable = false) private boolean active = true;
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

/**
 * Batch or cohort used for invitations and batch reports.
 */
@Entity
@Table(name = "sf_batches")
class SfBatch extends SkillForgeEntity {
    @Column(nullable = false) private UUID organizationId;
    private UUID departmentId;
    @Column(nullable = false) private String name;
    private String code;
    private LocalDate startsOn;
    private LocalDate endsOn;
    @Column(nullable = false) private boolean active = true;
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public LocalDate getStartsOn() { return startsOn; }
    public void setStartsOn(LocalDate startsOn) { this.startsOn = startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public void setEndsOn(LocalDate endsOn) { this.endsOn = endsOn; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

/**
 * Candidate profile linked to a SkillForge user.
 */
@Entity
@Table(name = "sf_candidate_profiles")
class SfCandidateProfile extends SkillForgeEntity {
    @Column(nullable = false) private UUID organizationId;
    @Column(nullable = false) private UUID userId;
    private UUID departmentId;
    private UUID batchId;
    private String externalRef;
    private String phone;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private String metadata = "{}";
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}

/**
 * Subject taxonomy entry used for question classification.
 */
@Entity
@Table(name = "sf_subjects")
class SfSubject extends SkillForgeEntity {
    private UUID organizationId;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String slug;
    @Column(nullable = false) private boolean active = true;
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

/**
 * Governed question identity with mutable lifecycle and immutable versions.
 */
@Entity
@Table(name = "sf_questions")
class SfQuestion extends SkillForgeEntity {
    @Column(nullable = false) private UUID organizationId;
    private UUID subjectId;
    private UUID currentVersionId;
    @Column(nullable = false) private String code;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private QuestionType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Difficulty difficulty;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private QuestionStatus status = QuestionStatus.DRAFT;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private String tags = "[]";
    @Column(nullable = false) private Integer expectedTimeSeconds = 60;
    @Column(nullable = false) private BigDecimal defaultMarks = BigDecimal.ONE;
    @Column(nullable = false) private BigDecimal negativeMarks = BigDecimal.ZERO;
    @Column(nullable = false) private Long usageCount = 0L;
    private String topic;
    @Column(nullable = false) private Integer sortOrder = 0;
    @Column(nullable = false) private String shuffleGroup = "STANDARD";
    private UUID createdBy;
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    public UUID getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(UUID currentVersionId) { this.currentVersionId = currentVersionId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public QuestionType getType() { return type; }
    public void setType(QuestionType type) { this.type = type; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
    public QuestionStatus getStatus() { return status; }
    public void setStatus(QuestionStatus status) { this.status = status; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public Integer getExpectedTimeSeconds() { return expectedTimeSeconds; }
    public void setExpectedTimeSeconds(Integer expectedTimeSeconds) { this.expectedTimeSeconds = expectedTimeSeconds; }
    public BigDecimal getDefaultMarks() { return defaultMarks; }
    public void setDefaultMarks(BigDecimal defaultMarks) { this.defaultMarks = defaultMarks; }
    public BigDecimal getNegativeMarks() { return negativeMarks; }
    public void setNegativeMarks(BigDecimal negativeMarks) { this.negativeMarks = negativeMarks; }
    public Long getUsageCount() { return usageCount; }
    public void setUsageCount(Long usageCount) { this.usageCount = usageCount; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getShuffleGroup() { return shuffleGroup; }
    public void setShuffleGroup(String shuffleGroup) { this.shuffleGroup = shuffleGroup; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}

/**
 * Immutable version of a question, preserving historical assessment integrity.
 */
@Entity
@Table(name = "sf_question_versions")
class SfQuestionVersion extends SkillForgeEntity {
    @Column(nullable = false) private UUID questionId;
    @Column(nullable = false) private Integer versionNumber;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String prompt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private String options = "[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private String correctAnswer = "{}";
    private String explanation;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "reference_links", columnDefinition = "jsonb", nullable = false) private String references = "[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private String scoring = "{}";
    private UUID createdBy;
    public UUID getQuestionId() { return questionId; }
    public void setQuestionId(UUID questionId) { this.questionId = questionId; }
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getReferences() { return references; }
    public void setReferences(String references) { this.references = references; }
    public String getScoring() { return scoring; }
    public void setScoring(String scoring) { this.scoring = scoring; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}

/**
 * Review record for question approval workflow.
 */
@Entity
@Table(name = "sf_question_approvals")
class SfQuestionApproval extends SkillForgeEntity {
    @Column(nullable = false) private UUID questionId;
    @Column(nullable = false) private UUID versionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ApprovalStatus status;
    private UUID submittedBy;
    private UUID reviewedBy;
    private String reviewNotes;
    @Column(nullable = false) private Instant submittedAt = Instant.now();
    private Instant reviewedAt;
    public UUID getQuestionId() { return questionId; }
    public void setQuestionId(UUID questionId) { this.questionId = questionId; }
    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }
    public UUID getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(UUID submittedBy) { this.submittedBy = submittedBy; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}

/**
 * Reusable assessment setup blueprint.
 */
@Entity
@Table(name = "sf_assessment_templates")
class SfAssessmentTemplate extends SkillForgeEntity {
    @Column(nullable = false) private UUID organizationId;
    @Column(nullable = false) private String name;
    private String description;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private String blueprint = "{}";
    @Column(nullable = false) private boolean active = true;
    private UUID createdBy;
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBlueprint() { return blueprint; }
    public void setBlueprint(String blueprint) { this.blueprint = blueprint; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}

/**
 * Assessment configured for publishing, scheduling, invitation, and scoring.
 */
@Entity
@Table(name = "sf_assessments")
class SfAssessment extends SkillForgeEntity {
    @Column(nullable = false) private UUID organizationId;
    private UUID templateId;
    @Column(nullable = false) private String title;
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AssessmentStatus status = AssessmentStatus.DRAFT;
    @Column(nullable = false) private Integer durationMinutes = 60;
    @Column(nullable = false) private BigDecimal passingPercentage = new BigDecimal("60.00");
    private Instant startAt;
    private Instant endAt;
    @Column(nullable = false) private Integer gracePeriodMinutes = 0;
    private Integer candidateLimit;
    @Column(nullable = false) private boolean shuffleQuestions = true;
    @Column(nullable = false) private boolean showResultImmediately = false;
    private UUID createdBy;
    private Instant publishedAt;
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AssessmentStatus getStatus() { return status; }
    public void setStatus(AssessmentStatus status) { this.status = status; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public BigDecimal getPassingPercentage() { return passingPercentage; }
    public void setPassingPercentage(BigDecimal passingPercentage) { this.passingPercentage = passingPercentage; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public Integer getGracePeriodMinutes() { return gracePeriodMinutes; }
    public void setGracePeriodMinutes(Integer gracePeriodMinutes) { this.gracePeriodMinutes = gracePeriodMinutes; }
    public Integer getCandidateLimit() { return candidateLimit; }
    public void setCandidateLimit(Integer candidateLimit) { this.candidateLimit = candidateLimit; }
    public boolean isShuffleQuestions() { return shuffleQuestions; }
    public void setShuffleQuestions(boolean shuffleQuestions) { this.shuffleQuestions = shuffleQuestions; }
    public boolean isShowResultImmediately() { return showResultImmediately; }
    public void setShowResultImmediately(boolean showResultImmediately) { this.showResultImmediately = showResultImmediately; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}

/**
 * Custom section inside an assessment.
 */
@Entity
@Table(name = "sf_assessment_sections")
class SfAssessmentSection extends SkillForgeEntity {
    @Column(nullable = false) private UUID assessmentId;
    @Column(nullable = false) private String name;
    private String instructions;
    @Column(nullable = false) private Integer sortOrder = 0;
    private Integer durationMinutes;
    @Column(nullable = false) private BigDecimal totalMarks = BigDecimal.ZERO;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private String rules = "{}";
    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID assessmentId) { this.assessmentId = assessmentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public BigDecimal getTotalMarks() { return totalMarks; }
    public void setTotalMarks(BigDecimal totalMarks) { this.totalMarks = totalMarks; }
    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }
}

/**
 * Fixed question version selected for an assessment.
 */
@Entity
@Table(name = "sf_assessment_questions")
class SfAssessmentQuestion extends SkillForgeEntity {
    @Column(nullable = false) private UUID assessmentId;
    private UUID sectionId;
    @Column(nullable = false) private UUID questionId;
    @Column(nullable = false) private UUID questionVersionId;
    @Column(nullable = false) private BigDecimal marks = BigDecimal.ONE;
    @Column(nullable = false) private BigDecimal negativeMarks = BigDecimal.ZERO;
    @Column(nullable = false) private Integer sortOrder = 0;
    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID assessmentId) { this.assessmentId = assessmentId; }
    public UUID getSectionId() { return sectionId; }
    public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
    public UUID getQuestionId() { return questionId; }
    public void setQuestionId(UUID questionId) { this.questionId = questionId; }
    public UUID getQuestionVersionId() { return questionVersionId; }
    public void setQuestionVersionId(UUID questionVersionId) { this.questionVersionId = questionVersionId; }
    public BigDecimal getMarks() { return marks; }
    public void setMarks(BigDecimal marks) { this.marks = marks; }
    public BigDecimal getNegativeMarks() { return negativeMarks; }
    public void setNegativeMarks(BigDecimal negativeMarks) { this.negativeMarks = negativeMarks; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}

/**
 * Refresh token for SkillForge sessions. SkillForge access tokens are
 * short-lived (15 minutes, same as the ExecutionOS side) — without this,
 * every admin/trainer/candidate session would hard-expire and force a full
 * re-login every 15 minutes with no way to silently renew it.
 */
@Entity
@Table(name = "sf_refresh_tokens")
class SfRefreshToken extends SkillForgeEntity {
    @Column(nullable = false) private UUID sfUserId;
    @Column(nullable = false, unique = true) private String token;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private boolean revoked = false;
    public UUID getSfUserId() { return sfUserId; }
    public void setSfUserId(UUID sfUserId) { this.sfUserId = sfUserId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
}

/**
 * Secure candidate invitation record.
 */
@Entity
@Table(name = "sf_assessment_invitations")
class SfAssessmentInvitation extends SkillForgeEntity {
    @Column(nullable = false) private UUID organizationId;
    @Column(nullable = false) private UUID assessmentId;
    @Column(nullable = false) private UUID candidateUserId;
    @Column(nullable = false, unique = true) private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private InvitationStatus status = InvitationStatus.PENDING;
    @Column(nullable = false) private String emailStatus = "PENDING";
    @Column(nullable = false) private Instant expiresAt;
    private Instant sentAt;
    private Instant openedAt;
    private Instant consumedAt;
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID assessmentId) { this.assessmentId = assessmentId; }
    public UUID getCandidateUserId() { return candidateUserId; }
    public void setCandidateUserId(UUID candidateUserId) { this.candidateUserId = candidateUserId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public InvitationStatus getStatus() { return status; }
    public void setStatus(InvitationStatus status) { this.status = status; }
    public String getEmailStatus() { return emailStatus; }
    public void setEmailStatus(String emailStatus) { this.emailStatus = emailStatus; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
}

/**
 * Candidate assessment attempt.
 */
@Entity
@Table(name = "sf_attempts")
class SfAttempt extends SkillForgeEntity {
    @Column(nullable = false) private UUID organizationId;
    @Column(nullable = false) private UUID assessmentId;
    private UUID invitationId;
    @Column(nullable = false) private UUID candidateUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AttemptStatus status = AttemptStatus.NOT_STARTED;
    private Instant startedAt;
    private Instant submittedAt;
    private Instant expiresAt;
    private BigDecimal score;
    private BigDecimal percentage;
    private Boolean passed;
    @Column(nullable = false) private BigDecimal suspiciousScore = BigDecimal.ZERO;
    private String ipAddress;
    private String userAgent;
    private String deviceFingerprint;
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID assessmentId) { this.assessmentId = assessmentId; }
    public UUID getInvitationId() { return invitationId; }
    public void setInvitationId(UUID invitationId) { this.invitationId = invitationId; }
    public UUID getCandidateUserId() { return candidateUserId; }
    public void setCandidateUserId(UUID candidateUserId) { this.candidateUserId = candidateUserId; }
    public AttemptStatus getStatus() { return status; }
    public void setStatus(AttemptStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
    public BigDecimal getSuspiciousScore() { return suspiciousScore; }
    public void setSuspiciousScore(BigDecimal suspiciousScore) { this.suspiciousScore = suspiciousScore; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
}

/**
 * Autosaved or submitted answer for a presented question version.
 */
@Entity
@Table(name = "sf_attempt_answers")
class SfAttemptAnswer extends SkillForgeEntity {
    @Column(nullable = false) private UUID attemptId;
    @Column(nullable = false) private UUID questionId;
    @Column(nullable = false) private UUID questionVersionId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private String answer = "{}";
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AnswerStatus status = AnswerStatus.UNANSWERED;
    private BigDecimal awardedMarks;
    private String evaluatorNotes;
    @Column(nullable = false) private boolean flaggedForReview = false;
    private Instant autoSavedAt;
    public UUID getAttemptId() { return attemptId; }
    public void setAttemptId(UUID attemptId) { this.attemptId = attemptId; }
    public UUID getQuestionId() { return questionId; }
    public void setQuestionId(UUID questionId) { this.questionId = questionId; }
    public UUID getQuestionVersionId() { return questionVersionId; }
    public void setQuestionVersionId(UUID questionVersionId) { this.questionVersionId = questionVersionId; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public AnswerStatus getStatus() { return status; }
    public void setStatus(AnswerStatus status) { this.status = status; }
    public BigDecimal getAwardedMarks() { return awardedMarks; }
    public void setAwardedMarks(BigDecimal awardedMarks) { this.awardedMarks = awardedMarks; }
    public String getEvaluatorNotes() { return evaluatorNotes; }
    public void setEvaluatorNotes(String evaluatorNotes) { this.evaluatorNotes = evaluatorNotes; }
    public boolean isFlaggedForReview() { return flaggedForReview; }
    public void setFlaggedForReview(boolean flaggedForReview) { this.flaggedForReview = flaggedForReview; }
    public Instant getAutoSavedAt() { return autoSavedAt; }
    public void setAutoSavedAt(Instant autoSavedAt) { this.autoSavedAt = autoSavedAt; }
}

/**
 * Security, lifecycle, and anti-cheating event captured during an attempt.
 */
@Entity
@Table(name = "sf_attempt_events")
class SfAttemptEvent extends SkillForgeEntity {
    @Column(nullable = false) private UUID attemptId;
    @Column(nullable = false) private String eventType;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private EventSeverity severity = EventSeverity.INFO;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private String details = "{}";
    @Column(nullable = false) private Instant occurredAt = Instant.now();
    public UUID getAttemptId() { return attemptId; }
    public void setAttemptId(UUID attemptId) { this.attemptId = attemptId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public EventSeverity getSeverity() { return severity; }
    public void setSeverity(EventSeverity severity) { this.severity = severity; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
