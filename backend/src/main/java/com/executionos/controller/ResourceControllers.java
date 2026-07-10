package com.executionos.controller;

import com.executionos.dto.ExecutionDtos.*;
import com.executionos.model.*;
import com.executionos.model.Enums.FocusStatus;
import com.executionos.repository.*;
import com.executionos.service.CurrentUserService;
import com.executionos.service.ScheduleService;
import com.executionos.service.TaskService;
import com.executionos.service.UploadValidationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

public final class ResourceControllers {
    private ResourceControllers() {}

    @RestController
    @RequestMapping("/api/v1/tasks")
    public static class TaskController {
        private final TaskService tasks;
        private final CurrentUserService currentUser;
        public TaskController(TaskService tasks, CurrentUserService currentUser) {
            this.tasks = tasks;
            this.currentUser = currentUser;
        }
        @GetMapping Object list(@RequestParam(required = false) Enums.TaskStatus status, Pageable pageable, Principal principal) {
            return tasks.list(principal.getName(), status, pageable);
        }
        @PostMapping TaskResponse create(@Valid @RequestBody TaskRequest request, Principal principal) {
            return tasks.create(currentUser.requireUser(principal), request);
        }
        @GetMapping("/{id}") TaskResponse get(@PathVariable UUID id, Principal principal) {
            return tasks.get(principal.getName(), id);
        }
        @PutMapping("/{id}") TaskResponse update(@PathVariable UUID id, @Valid @RequestBody TaskRequest request, Principal principal) {
            return tasks.update(principal.getName(), id, request);
        }
        @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
            tasks.delete(principal.getName(), id);
            return ResponseEntity.noContent().build();
        }
        @PatchMapping("/{id}/status") TaskResponse status(@PathVariable UUID id, @Valid @RequestBody StatusRequest request, Principal principal) {
            return tasks.status(principal.getName(), id, request);
        }
        @PatchMapping("/reorder") ResponseEntity<Void> reorder() { return ResponseEntity.accepted().build(); }
    }

    @RestController
    @RequestMapping("/api/v1/schedules")
    public static class ScheduleController {
        private final ScheduleService schedules;
        private final CurrentUserService currentUser;
        public ScheduleController(ScheduleService schedules, CurrentUserService currentUser) {
            this.schedules = schedules;
            this.currentUser = currentUser;
        }
        @GetMapping Object list(Principal principal) { return schedules.list(principal.getName()); }
        @PostMapping ScheduleResponse create(@Valid @RequestBody ScheduleRequest request, Principal principal) {
            return schedules.create(currentUser.requireUser(principal), request);
        }
        @PutMapping("/{id}") ScheduleResponse update(@PathVariable UUID id, @Valid @RequestBody ScheduleRequest request, Principal principal) {
            return schedules.update(principal.getName(), id, request);
        }
        @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
            schedules.delete(principal.getName(), id);
            return ResponseEntity.noContent().build();
        }
    }

    @RestController
    @RequestMapping("/api/v1/focus-sessions")
    public static class FocusController {
        private final FocusSessionRepository repo;
        private final TaskRepository taskRepo;
        private final CurrentUserService currentUser;
        FocusController(FocusSessionRepository repo, TaskRepository taskRepo, CurrentUserService currentUser) {
            this.repo = repo;
            this.taskRepo = taskRepo;
            this.currentUser = currentUser;
        }
        // Fixed: previously saved whatever "user" object (if any) was in the
        // request body directly -- user_id is NOT NULL, so a normal frontend
        // POST (which doesn't know internal DB structure) would 500. Now
        // resolves the user server-side from the authenticated principal,
        // matching the pattern TaskController/ScheduleController already use.
        @PostMapping("/start") FocusSession start(@RequestBody FocusStartRequest request, Principal principal) {
            FocusSession session = new FocusSession();
            session.setUser(currentUser.requireUser(principal));
            if (request.taskId() != null) {
                taskRepo.findById(request.taskId()).ifPresent(session::setTask);
            }
            if (request.type() != null) session.setType(request.type());
            return repo.save(session);
        }
        @PatchMapping("/{id}/pause") FocusSession pause(@PathVariable UUID id) { return set(id, FocusStatus.PAUSED); }
        @PatchMapping("/{id}/resume") FocusSession resume(@PathVariable UUID id) { return set(id, FocusStatus.ACTIVE); }
        @PatchMapping("/{id}/complete") FocusSession complete(@PathVariable UUID id) {
            FocusSession session = set(id, FocusStatus.COMPLETED);
            session.setEndTime(Instant.now());
            return repo.save(session);
        }
        @GetMapping Object list(Pageable pageable, Principal principal) {
            return repo.findByUserId(currentUser.requireUser(principal).getId(), pageable);
        }
        // NOTE: this remains a hardcoded placeholder, not a real aggregate
        // query -- flagged explicitly rather than presented as real data.
        // See MERGE_AND_FIX_REPORT.md / the frontend focus page comments.
        @GetMapping("/stats") Object stats() { return Map.of(
                "deepWorkSeconds", 12600, "sessions", 7, "completionRate", 0.82,
                "isPlaceholder", true); }
        private FocusSession set(UUID id, FocusStatus status) { FocusSession s = repo.findById(id).orElseThrow(); s.setStatus(status); return repo.save(s); }
    }

    @RestController
    @RequestMapping("/api/v1/notes")
    public static class NoteController {
        private final NoteRepository repo;
        private final TaskRepository taskRepo;
        private final CurrentUserService currentUser;
        NoteController(NoteRepository repo, TaskRepository taskRepo, CurrentUserService currentUser) {
            this.repo = repo;
            this.taskRepo = taskRepo;
            this.currentUser = currentUser;
        }
        @GetMapping Object list(Pageable pageable, Principal principal) {
            return repo.findByUserId(currentUser.requireUser(principal).getId(), pageable);
        }
        // Fixed: same user_id NOT NULL issue as FocusController -- now
        // resolves the user server-side instead of trusting the request body.
        @PostMapping NoteResponse create(@Valid @RequestBody NoteRequest request, Principal principal) {
            Note note = new Note();
            note.setUser(currentUser.requireUser(principal));
            applyNoteFields(note, request);
            return toResponse(repo.save(note));
        }
        @GetMapping("/{id}") NoteResponse get(@PathVariable UUID id, Principal principal) {
            return toResponse(requireOwned(id, principal));
        }
        @PutMapping("/{id}") NoteResponse update(@PathVariable UUID id, @Valid @RequestBody NoteRequest request, Principal principal) {
            Note note = requireOwned(id, principal);
            applyNoteFields(note, request);
            return toResponse(repo.save(note));
        }
        @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
            repo.delete(requireOwned(id, principal));
            return ResponseEntity.noContent().build();
        }
        private Note requireOwned(UUID id, Principal principal) {
            Note note = repo.findById(id).orElseThrow();
            if (!note.getUser().getId().equals(currentUser.requireUser(principal).getId())) {
                throw new IllegalArgumentException("Not found");
            }
            return note;
        }
        private void applyNoteFields(Note note, NoteRequest request) {
            note.setTitle(request.title());
            note.setContent(request.content());
            if (request.format() != null) note.setFormat(request.format());
            if (request.pinned() != null) note.setPinned(request.pinned());
            if (request.taskId() != null) {
                taskRepo.findById(request.taskId()).ifPresent(note::setTask);
            }
        }
        private NoteResponse toResponse(Note note) {
            return new NoteResponse(note.getId(), note.getTask() == null ? null : note.getTask().getId(),
                    note.getTitle(), note.getContent(), note.getFormat(), note.isPinned());
        }
    }

    @RestController
    @RequestMapping("/api/v1/attachments")
    public static class AttachmentController {
        private final AttachmentRepository repo;
        private final CurrentUserService currentUser;
        private final UploadValidationService uploadValidation;
        public AttachmentController(AttachmentRepository repo, CurrentUserService currentUser, UploadValidationService uploadValidation) {
            this.repo = repo;
            this.currentUser = currentUser;
            this.uploadValidation = uploadValidation;
        }
        @GetMapping Object list(Pageable pageable, Principal principal) {
            return repo.findByUserId(currentUser.requireUser(principal).getId(), pageable);
        }
        @PostMapping("/upload") Attachment upload(@RequestParam MultipartFile file, Principal principal) {
            uploadValidation.validate(file);
            Attachment attachment = new Attachment();
            attachment.setUser(currentUser.requireUser(principal));
            attachment.setFileName(file.getOriginalFilename());
            attachment.setFileSize(file.getSize());
            attachment.setFileType(file.getContentType());
            attachment.setStorageUrl("local://" + file.getOriginalFilename());
            return repo.save(attachment);
        }
        @GetMapping("/{id}/download") Attachment download(@PathVariable UUID id) { return repo.findById(id).orElseThrow(); }
        @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable UUID id) { repo.deleteById(id); return ResponseEntity.noContent().build(); }
    }

    @RestController
    @RequestMapping("/api/v1/knowledge")
    public static class KnowledgeController {
        private final CategoryRepository categories;
        private final KnowledgeItemRepository items;
        private final CurrentUserService currentUser;
        KnowledgeController(CategoryRepository categories, KnowledgeItemRepository items, CurrentUserService currentUser) {
            this.categories = categories;
            this.items = items;
            this.currentUser = currentUser;
        }
        @GetMapping("/categories") Object categories(Pageable pageable, Principal principal) {
            return categories.findByUserId(currentUser.requireUser(principal).getId(), pageable);
        }
        // Fixed: same user_id NOT NULL issue -- resolves server-side now.
        @PostMapping("/categories") Category createCategory(@RequestBody Category category, Principal principal) {
            category.setUser(currentUser.requireUser(principal));
            return categories.save(category);
        }
        @GetMapping("/items") Object items(Pageable pageable, Principal principal) {
            return items.findByUserId(currentUser.requireUser(principal).getId(), pageable);
        }
        @PostMapping("/items") KnowledgeItem createItem(@RequestBody KnowledgeItem item, Principal principal) {
            item.setUser(currentUser.requireUser(principal));
            return items.save(item);
        }
        @GetMapping("/items/{id}") KnowledgeItem item(@PathVariable UUID id) { return items.findById(id).orElseThrow(); }
        @PutMapping("/items/{id}") KnowledgeItem updateItem(@PathVariable UUID id, @RequestBody KnowledgeItem item, Principal principal) {
            KnowledgeItem existing = items.findById(id).orElseThrow();
            existing.setTitle(item.getTitle());
            existing.setContent(item.getContent());
            existing.setFormat(item.getFormat());
            existing.setPinned(item.isPinned());
            existing.setTags(item.getTags());
            if (item.getCategory() != null) existing.setCategory(item.getCategory());
            return items.save(existing);
        }
        @DeleteMapping("/items/{id}") ResponseEntity<Void> deleteItem(@PathVariable UUID id) { items.deleteById(id); return ResponseEntity.noContent().build(); }
    }

    @RestController
    @RequestMapping("/api/v1/journal")
    public static class JournalController {
        private final JournalEntryRepository repo;
        private final CurrentUserService currentUser;
        JournalController(JournalEntryRepository repo, CurrentUserService currentUser) {
            this.repo = repo;
            this.currentUser = currentUser;
        }
        @GetMapping JournalEntry byDate(@RequestParam LocalDate date, Principal principal) {
            UUID userId = currentUser.requireUser(principal).getId();
            return repo.findByUserIdAndEntryDate(userId, date).orElseThrow();
        }
        // Fixed: same user_id NOT NULL issue -- resolves server-side now.
        @PostMapping JournalEntry create(@RequestBody JournalEntry entry, Principal principal) {
            entry.setUser(currentUser.requireUser(principal));
            return repo.save(entry);
        }
        @PutMapping("/{id}") JournalEntry update(@PathVariable UUID id, @RequestBody JournalEntry entry, Principal principal) {
            JournalEntry existing = repo.findById(id).orElseThrow();
            existing.setCompletedTasks(entry.getCompletedTasks());
            existing.setBlockers(entry.getBlockers());
            existing.setLearnings(entry.getLearnings());
            existing.setAiToolsExplored(entry.getAiToolsExplored());
            existing.setLinksSaved(entry.getLinksSaved());
            return repo.save(existing);
        }
        @GetMapping("/entries") Object entries(Pageable pageable, Principal principal) {
            return repo.findByUserId(currentUser.requireUser(principal).getId(), pageable);
        }
    }

    // The ReadModelController that used to sit here (dashboard/analytics/
    // search) was removed entirely: its endpoints were hardcoded
    // placeholders (isPlaceholder: true) with no real query behind them,
    // and the /dashboard/* + /analytics/* ones were unreachable dead code
    // (no nav entry ever linked to them). Real, working search now lives
    // at GET /api/v1/skillforge/search -- see SkillForgeController/Service.
    // Building real personal-productivity analytics (task completion,
    // focus-session aggregates) is genuine new feature work, not a wiring
    // fix, and remains out of scope -- see PRODUCTION_AUDIT.md.

    @RestController
    @RequestMapping("/api/v1/admin")
    public static class AdminController {
        private final com.executionos.repository.UserRepository users;
        private final ActivityLogRepository logs;
        AdminController(com.executionos.repository.UserRepository users, ActivityLogRepository logs) { this.users = users; this.logs = logs; }

        // Was: `Object users(Pageable pageable) { return users.findAll(pageable); }`
        // -- returned the raw User entity, which includes getPasswordHash().
        // Jackson serializes every public getter by default, so this was a
        // real password-hash leak to any caller with ROLE_ADMIN, not just a
        // style issue. Now maps to a DTO with no password field at all.
        public record AdminUserView(UUID id, String email, String name, String role, java.time.Instant createdAt) {}

        @GetMapping("/users")
        Object users(Pageable pageable) {
            return users.findAll(pageable).map(u -> new AdminUserView(u.getId(), u.getEmail(), u.getName(), u.getRole().name(), u.getCreatedAt()));
        }

        // Was: `Object logs(Pageable pageable) { return logs.findAll(pageable); }`
        // -- ActivityLog.user is a lazy @ManyToOne. Serializing it directly
        // either throws (session already closed) or serializes a Hibernate
        // proxy Jackson doesn't know how to handle, and would ALSO leak the
        // linked User's passwordHash for the same reason as above once it
        // resolves. Flattened to userEmail only.
        public record AdminLogView(UUID id, String action, String entityType, UUID entityId, String userEmail, java.time.Instant createdAt) {}

        @GetMapping("/logs")
        Object logs(Pageable pageable) {
            return logs.findAll(pageable).map(l -> new AdminLogView(
                    l.getId(), l.getAction(), l.getEntityType(), l.getEntityId(),
                    l.getUser() != null ? l.getUser().getEmail() : null, l.getCreatedAt()));
        }

        @GetMapping("/health") Object health() { return Map.of("status", "ok"); }
    }
}
