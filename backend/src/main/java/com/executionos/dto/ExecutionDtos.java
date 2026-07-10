package com.executionos.dto;

import com.executionos.model.Enums.FocusStatus;
import com.executionos.model.Enums.FocusType;
import com.executionos.model.Enums.Priority;
import com.executionos.model.Enums.TaskStatus;
import com.executionos.model.Enums.TextFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class ExecutionDtos {
    private ExecutionDtos() {}

    public record TaskRequest(
            @NotBlank String title,
            String description,
            String category,
            Priority priority,
            TaskStatus status,
            Instant startTime,
            Instant endTime,
            Boolean recurring,
            String recurrenceRule,
            UUID scheduleId,
            Integer sortOrder) {}

    public record TaskResponse(
            UUID id,
            String title,
            String description,
            String category,
            Priority priority,
            TaskStatus status,
            Instant startTime,
            Instant endTime,
            boolean recurring,
            String recurrenceRule,
            int sortOrder,
            UUID scheduleId) {}

    public record StatusRequest(@NotNull TaskStatus status) {}

    public record ScheduleRequest(@NotBlank String name, String cronExpression, Boolean active, String color) {}
    public record ScheduleResponse(UUID id, String name, String cronExpression, boolean active, String color) {}

    public record FocusStartRequest(UUID taskId, FocusType type, Long durationSeconds) {}
    public record FocusSessionResponse(UUID id, UUID taskId, Instant startTime, Instant endTime, long durationSeconds, FocusType type, FocusStatus status) {}

    public record NoteRequest(UUID taskId, @NotBlank String title, String content, TextFormat format, Boolean pinned) {}
    public record NoteResponse(UUID id, UUID taskId, String title, String content, TextFormat format, boolean pinned) {}
}
