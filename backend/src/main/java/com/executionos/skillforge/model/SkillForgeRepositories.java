package com.executionos.skillforge.model;

import com.executionos.skillforge.model.SkillForgeEnums.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repositories for SkillForge aggregate persistence.
 */
interface SfOrganizationRepository extends JpaRepository<SfOrganization, UUID> {
    boolean existsBySlug(String slug);
    Optional<SfOrganization> findBySlug(String slug);
}

interface SfRefreshTokenRepository extends JpaRepository<SfRefreshToken, UUID> {
    Optional<SfRefreshToken> findByToken(String token);
}

interface SfUserRepository extends JpaRepository<SfUser, UUID> {
    Page<SfUser> findByOrganizationIdAndRole(UUID organizationId, UserRole role, Pageable pageable);
    Optional<SfUser> findByOrganizationIdAndEmail(UUID organizationId, String email);
    List<SfUser> findByEmail(String email);
    java.util.Optional<SfUser> findByResetTokenHash(String resetTokenHash);
    java.util.Optional<SfUser> findByVerifyTokenHash(String verifyTokenHash);
    long countByOrganizationIdAndRole(UUID organizationId, UserRole role);
    @Query("SELECT u FROM SfUser u WHERE u.organizationId = :orgId AND u.role = 'CANDIDATE' "
            + "AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<SfUser> searchCandidates(@Param("orgId") UUID organizationId, @Param("q") String query);
}

interface SfDepartmentRepository extends JpaRepository<SfDepartment, UUID> {
    Page<SfDepartment> findByOrganizationId(UUID organizationId, Pageable pageable);
}

interface SfBatchRepository extends JpaRepository<SfBatch, UUID> {
    Page<SfBatch> findByOrganizationId(UUID organizationId, Pageable pageable);
}

interface SfCandidateProfileRepository extends JpaRepository<SfCandidateProfile, UUID> {
    Optional<SfCandidateProfile> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
    long countByOrganizationIdAndBatchId(UUID organizationId, UUID batchId);
}

interface SfSubjectRepository extends JpaRepository<SfSubject, UUID> {
    List<SfSubject> findByOrganizationIdIsNullOrOrganizationId(UUID organizationId);
    Optional<SfSubject> findByOrganizationIdIsNullAndSlug(String slug);
}

interface SfQuestionRepository extends JpaRepository<SfQuestion, UUID> {
    Page<SfQuestion> findByOrganizationId(UUID organizationId, Pageable pageable);
    Page<SfQuestion> findByOrganizationIdAndStatus(UUID organizationId, QuestionStatus status, Pageable pageable);
    long countByOrganizationIdAndStatus(UUID organizationId, QuestionStatus status);
    Page<SfQuestion> findByOrganizationIdAndSubjectIdAndDifficultyAndStatus(UUID organizationId, UUID subjectId, Difficulty difficulty, QuestionStatus status, Pageable pageable);
    long countByOrganizationIdAndSubjectIdAndDifficultyAndStatus(UUID organizationId, UUID subjectId, Difficulty difficulty, QuestionStatus status);
    Optional<SfQuestion> findByOrganizationIdAndCode(UUID organizationId, String code);
    @Query("SELECT q FROM SfQuestion q WHERE q.organizationId = :orgId AND q.status = 'APPROVED' "
            + "AND (LOWER(q.code) LIKE LOWER(CONCAT('%', :q2, '%')) OR LOWER(q.topic) LIKE LOWER(CONCAT('%', :q2, '%')))")
    List<SfQuestion> searchQuestions(@Param("orgId") UUID organizationId, @Param("q2") String query);

    @Query("SELECT q.subjectId, q.difficulty, COUNT(q) FROM SfQuestion q WHERE q.organizationId = :organizationId AND q.status = 'APPROVED' GROUP BY q.subjectId, q.difficulty")
    List<Object[]> countBySubjectAndDifficulty(@Param("organizationId") UUID organizationId);
}

interface SfQuestionVersionRepository extends JpaRepository<SfQuestionVersion, UUID> {
    List<SfQuestionVersion> findByQuestionIdOrderByVersionNumberDesc(UUID questionId);
    List<SfQuestionVersion> findByQuestionIdInOrderByVersionNumberDesc(List<UUID> questionIds);
}

interface SfQuestionApprovalRepository extends JpaRepository<SfQuestionApproval, UUID> {
    Optional<SfQuestionApproval> findTopByQuestionIdOrderBySubmittedAtDesc(UUID questionId);
}

interface SfAssessmentTemplateRepository extends JpaRepository<SfAssessmentTemplate, UUID> {
    Page<SfAssessmentTemplate> findByOrganizationId(UUID organizationId, Pageable pageable);
}

interface SfAssessmentRepository extends JpaRepository<SfAssessment, UUID> {
    Page<SfAssessment> findByOrganizationId(UUID organizationId, Pageable pageable);
    long countByOrganizationIdAndStatus(UUID organizationId, AssessmentStatus status);
    @Query("SELECT a FROM SfAssessment a WHERE a.organizationId = :orgId AND LOWER(a.title) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<SfAssessment> searchByTitle(@Param("orgId") UUID organizationId, @Param("q") String query);
}

interface SfAssessmentSectionRepository extends JpaRepository<SfAssessmentSection, UUID> {
    List<SfAssessmentSection> findByAssessmentIdOrderBySortOrder(UUID assessmentId);
}

interface SfAssessmentQuestionRepository extends JpaRepository<SfAssessmentQuestion, UUID> {
    List<SfAssessmentQuestion> findByAssessmentIdOrderBySortOrder(UUID assessmentId);
}

interface SfAssessmentInvitationRepository extends JpaRepository<SfAssessmentInvitation, UUID> {
    Optional<SfAssessmentInvitation> findByTokenHash(String tokenHash);
    List<SfAssessmentInvitation> findByAssessmentId(UUID assessmentId);
}

interface SfAttemptRepository extends JpaRepository<SfAttempt, UUID> {
    List<SfAttempt> findByAssessmentId(UUID assessmentId);
    Page<SfAttempt> findByOrganizationIdAndCandidateUserId(UUID organizationId, UUID candidateUserId, Pageable pageable);
    long countByAssessmentIdAndStatus(UUID assessmentId, AttemptStatus status);
}

interface SfAttemptAnswerRepository extends JpaRepository<SfAttemptAnswer, UUID> {
    Optional<SfAttemptAnswer> findByAttemptIdAndQuestionId(UUID attemptId, UUID questionId);
    List<SfAttemptAnswer> findByAttemptId(UUID attemptId);
}

interface SfAttemptEventRepository extends JpaRepository<SfAttemptEvent, UUID> {
    List<SfAttemptEvent> findByAttemptIdOrderByOccurredAtDesc(UUID attemptId);
    long countByAttemptId(UUID attemptId);
}
