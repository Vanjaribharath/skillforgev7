package com.executionos.skillforge.model;

import com.executionos.skillforge.model.SkillForgeDtos.*;
import com.executionos.skillforge.model.SkillForgeEnums.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for SkillForge Enterprise Phase 3 backend workflows.
 */
@RestController
@RequestMapping("/api/v1/skillforge")
public class SkillForgeController {
    private final SkillForgeService service;

    public SkillForgeController(SkillForgeService service) {
        this.service = service;
    }

    @PostMapping("/auth/register-organization")
    public OrganizationResponse registerOrganization(@Valid @RequestBody RegisterOrganizationRequest request) {
        return service.registerOrganization(request);
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request.email(), request.password());
    }

    @PostMapping("/auth/forgot-password")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        service.forgotPassword(request.email());
    }

    @PostMapping("/auth/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(request.token(), request.newPassword());
    }

    @PostMapping("/auth/verify-email")
    public void verifyEmail(@RequestBody Map<String, String> body) {
        service.verifyEmail(body.get("token"));
    }

    @PostMapping("/auth/google")
    public LoginResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return service.googleLogin(request.idToken());
    }

    @PostMapping("/auth/refresh")
    public LoginResponse refresh(@RequestBody Map<String, String> body) {
        return service.refresh(body.get("refreshToken"));
    }

    @PostMapping("/auth/logout")
    public void logout(@RequestBody Map<String, String> body) {
        service.logout(body.get("refreshToken"));
    }

    @GetMapping("/organizations/{id}")
    public OrganizationResponse organization(@PathVariable UUID id, HttpServletRequest request) {
        Object callerOrgId = request.getAttribute(com.executionos.security.JwtAuthenticationFilter.ORGANIZATION_ID_ATTRIBUTE);
        if (callerOrgId == null || !callerOrgId.equals(id)) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot view another organization");
        }
        return service.getOrganization(id);
    }

    @PatchMapping("/organizations/{id}")
    public OrganizationResponse updateOrganization(@PathVariable UUID id, @RequestBody UpdateOrganizationSettingsRequest request, HttpServletRequest httpRequest) {
        Object callerOrgId = httpRequest.getAttribute(com.executionos.security.JwtAuthenticationFilter.ORGANIZATION_ID_ATTRIBUTE);
        if (callerOrgId == null || !callerOrgId.equals(id)) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot modify another organization");
        }
        return service.updateOrganizationSettings(id, request);
    }

    @GetMapping("/departments")
    public Object departments(@RequestParam UUID organizationId, Pageable pageable) {
        return service.listDepartments(organizationId, pageable);
    }

    @PostMapping("/departments")
    public DepartmentResponse createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return service.createDepartment(request);
    }

    @GetMapping("/batches")
    public Object batches(@RequestParam UUID organizationId, Pageable pageable) {
        return service.listBatches(organizationId, pageable);
    }

    @PostMapping("/batches")
    public BatchResponse createBatch(@Valid @RequestBody BatchRequest request) {
        return service.createBatch(request);
    }

    @GetMapping("/users")
    public Object users(@RequestParam UUID organizationId, @RequestParam(required = false) UserRole role, Pageable pageable) {
        return service.listUsers(organizationId, role, pageable);
    }

    @PostMapping("/users")
    public UserResponse createUser(@Valid @RequestBody UserRequest request) {
        return service.createUser(request);
    }

    @GetMapping("/trainers")
    public Object trainers(@RequestParam UUID organizationId, Pageable pageable) {
        return service.listUsers(organizationId, UserRole.TRAINER, pageable);
    }

    @PostMapping("/trainers")
    public UserResponse createTrainer(@Valid @RequestBody UserRequest request) {
        return service.createUser(new UserRequest(request.organizationId(), request.email(), request.fullName(), UserRole.TRAINER, request.departmentId(), request.batchId(), request.phone(), request.externalRef(), request.password()));
    }

    @GetMapping("/candidates")
    public Object candidates(@RequestParam UUID organizationId, Pageable pageable) {
        return service.listUsers(organizationId, UserRole.CANDIDATE, pageable);
    }

    @PostMapping("/candidates")
    public UserResponse createCandidate(@Valid @RequestBody UserRequest request) {
        return service.createUser(new UserRequest(request.organizationId(), request.email(), request.fullName(), UserRole.CANDIDATE, request.departmentId(), request.batchId(), request.phone(), request.externalRef(), request.password()));
    }

    @GetMapping("/questions")
    public Object questions(@RequestParam UUID organizationId, @RequestParam(required = false) QuestionStatus status, Pageable pageable) {
        return service.listQuestions(organizationId, status, pageable);
    }

    @PostMapping("/questions")
    public QuestionResponse createQuestion(@Valid @RequestBody QuestionRequest request) {
        return service.createQuestion(request);
    }

    @PostMapping("/questions/{id}/submit-review")
    public QuestionResponse submitQuestionForReview(@PathVariable UUID id) {
        return service.submitQuestionForReview(id);
    }

    @PostMapping("/questions/{id}/approve")
    public QuestionResponse approveQuestion(@PathVariable UUID id, @RequestBody ApprovalRequest request) {
        return service.approveQuestion(id, request);
    }

    @GetMapping("/assessments")
    public Object assessments(@RequestParam UUID organizationId, Pageable pageable) {
        return service.listAssessments(organizationId, pageable);
    }

    @PostMapping("/assessments")
    public AssessmentResponse createAssessment(@Valid @RequestBody AssessmentRequest request) {
        return service.createAssessment(request);
    }

    @PostMapping("/assessments/{id}/sections")
    public SfAssessmentSection addSection(@PathVariable UUID id, @Valid @RequestBody SectionRequest request) {
        return service.addSection(id, request);
    }

    @PostMapping("/assessments/{id}/questions")
    public SfAssessmentQuestion addQuestion(@PathVariable UUID id, @Valid @RequestBody AddQuestionRequest request) {
        return service.addQuestionToAssessment(id, request);
    }

    @PostMapping("/assessments/{id}/publish")
    public AssessmentResponse publishAssessment(@PathVariable UUID id) {
        return service.publishAssessment(id);
    }

    @PostMapping("/assessments/{id}/schedule")
    public AssessmentResponse scheduleAssessment(@PathVariable UUID id, @Valid @RequestBody AssessmentRequest request) {
        return service.scheduleAssessment(id, request);
    }

    @PostMapping("/assessments/{id}/invite")
    public Object inviteCandidates(@PathVariable UUID id, @Valid @RequestBody InviteRequest request) {
        return service.inviteCandidates(id, request);
    }

    @GetMapping("/assessments/{id}/live")
    public LiveDashboardResponse liveDashboard(@PathVariable UUID id) {
        return service.liveDashboard(id);
    }

    @PostMapping("/candidate/link/validate")
    public ValidateLinkResponse validateLink(@Valid @RequestBody ValidateLinkRequest request) {
        return service.validateLink(request);
    }

    @PostMapping("/candidate/attempts/start/{invitationId}")
    public AttemptResponse startAttempt(@PathVariable UUID invitationId, HttpServletRequest request) {
        return service.startAttempt(invitationId, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PutMapping("/candidate/attempts/{attemptId}/answers/{questionId}")
    public SfAttemptAnswer saveAnswer(@PathVariable UUID attemptId, @PathVariable UUID questionId, @RequestBody AnswerRequest request) {
        return service.saveAnswer(attemptId, questionId, request);
    }

    @PostMapping("/candidate/attempts/{attemptId}/events")
    public SfAttemptEvent recordEvent(@PathVariable UUID attemptId, @Valid @RequestBody EventRequest request) {
        return service.recordEvent(attemptId, request);
    }

    @PostMapping("/candidate/attempts/{attemptId}/submit")
    public AttemptResponse submitAttempt(@PathVariable UUID attemptId) {
        return service.submitAttempt(attemptId);
    }

    @GetMapping("/candidate/attempts/{attemptId}/questions")
    public List<CandidateQuestionView> examQuestions(@PathVariable UUID attemptId) {
        return service.examQuestions(attemptId);
    }

    // @PreAuthorize is a second, independent check enforced by Spring's method
    // security layer -- it still applies even if a URL-level rule were ever
    // loosened, so answers can't leak from this specific endpoint regardless.
    @PreAuthorize("hasAnyRole('TRAINER','ORG_ADMIN','EVALUATOR','PLATFORM_ADMIN','ADMIN')")
    @GetMapping("/trainer/attempts/{attemptId}/answer-key")
    public AnswerKeyResponse answerKey(@PathVariable UUID attemptId) {
        return service.answerKey(attemptId);
    }

    @GetMapping("/reports/candidates/{candidateUserId}")
    public Map<String, Object> candidateReport(@RequestParam UUID organizationId, @PathVariable UUID candidateUserId) {
        return service.candidateReport(organizationId, candidateUserId);
    }

    @GetMapping("/health-dashboard")
    public HealthDashboardResponse healthDashboard(@RequestParam UUID organizationId) {
        return service.healthDashboard(organizationId);
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam UUID organizationId, @RequestParam String q, @RequestParam(required = false) String type) {
        return service.search(organizationId, q, type);
    }

    @GetMapping("/catalog/coverage")
    public Object catalogCoverage(@RequestParam UUID organizationId) {
        return service.catalogCoverage(organizationId);
    }

    @GetMapping("/catalog/questions")
    public Object catalogQuestions(
            @RequestParam UUID organizationId,
            @RequestParam(defaultValue = "java") String subject,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.generatedCatalog(organizationId, subject, difficulty, page, size);
    }

    /** Includes the correct answer — role-gated in SecurityConfig, never call this from an unauthenticated/candidate context. */
    @GetMapping("/questions/{questionId}/full")
    public TrainerCatalogQuestion trainerQuestionDetail(@RequestParam UUID organizationId, @PathVariable UUID questionId) {
        return service.trainerQuestionDetail(organizationId, questionId);
    }

    @PostMapping("/questions/import/csv")
    public QuestionImportReport importQuestionsCsv(@RequestParam UUID organizationId, @RequestParam UUID createdBy, @RequestBody String csvContent) {
        return service.importQuestionsFromCsv(organizationId, createdBy, csvContent);
    }

    @PostMapping("/demo/bootstrap")
    public DemoBootstrapResponse bootstrapDemo() {
        return service.bootstrapDemo();
    }

    @PostMapping("/exports")
    public ResponseEntity<Map<String, String>> createExportJob() {
        return ResponseEntity.accepted().body(Map.of("status", "accepted", "message", "Export job queue integration is planned for the RabbitMQ worker phase."));
    }

    @GetMapping("/webhooks")
    public Map<String, String> webhooks() {
        return Map.of("status", "planned", "message", "Webhook endpoint persistence is part of the database design and delivery worker implementation remains pending.");
    }
}
