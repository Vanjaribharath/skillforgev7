package com.executionos.skillforge.model;

/**
 * Enumerations used by the SkillForge Enterprise assessment domain.
 */
public final class SkillForgeEnums {
    private SkillForgeEnums() {}

    public enum OrganizationStatus { ACTIVE, SUSPENDED, ARCHIVED }
    public enum UserRole { PLATFORM_ADMIN, ORG_ADMIN, TRAINER, EVALUATOR, CANDIDATE }
    public enum UserStatus { ACTIVE, INVITED, SUSPENDED, ARCHIVED }
    public enum QuestionType { MULTIPLE_CHOICE, MULTIPLE_SELECT, FILL_BLANK, CODE_OUTPUT, CODE_COMPLETION, CODING, SCENARIO, TRUE_FALSE, DRAG_DROP, ORDERING }
    public enum Difficulty { EASY, MEDIUM, HARD }
    public enum QuestionStatus { DRAFT, REVIEW, APPROVED, RETIRED }
    public enum ApprovalStatus { SUBMITTED, APPROVED, REJECTED }
    public enum AssessmentStatus { DRAFT, PUBLISHED, SCHEDULED, CLOSED, ARCHIVED }
    public enum InvitationStatus { PENDING, SENT, OPENED, CONSUMED, EXPIRED, CANCELLED }
    public enum AttemptStatus { NOT_STARTED, IN_PROGRESS, SUBMITTED, AUTO_SUBMITTED, EVALUATED, INVALIDATED }
    public enum AnswerStatus { UNANSWERED, ANSWERED, FLAGGED, NEEDS_EVALUATION, EVALUATED }
    public enum EventSeverity { INFO, WARNING, CRITICAL }
}
