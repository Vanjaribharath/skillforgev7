package com.executionos.service;

import com.executionos.dto.ExecutionDtos.*;
import com.executionos.model.Schedule;
import com.executionos.model.Task;
import com.executionos.model.User;
import com.executionos.repository.ScheduleRepository;
import com.executionos.repository.TaskRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private final TaskRepository tasks;
    private final ScheduleRepository schedules;

    public TaskService(TaskRepository tasks, ScheduleRepository schedules) {
        this.tasks = tasks;
        this.schedules = schedules;
    }

    public Page<TaskResponse> list(String email, com.executionos.model.Enums.TaskStatus status, Pageable pageable) {
        Page<Task> page = status == null ? tasks.findByUserEmail(email, pageable) : tasks.findByUserEmailAndStatus(email, status, pageable);
        return page.map(this::toResponse);
    }

    public TaskResponse get(String email, UUID id) {
        return toResponse(require(email, id));
    }

    @Transactional
    public TaskResponse create(User user, TaskRequest request) {
        Task task = new Task();
        task.setUser(user);
        apply(task, request);
        return toResponse(tasks.save(task));
    }

    @Transactional
    public TaskResponse update(String email, UUID id, TaskRequest request) {
        Task task = require(email, id);
        apply(task, request);
        return toResponse(tasks.save(task));
    }

    @Transactional
    public TaskResponse status(String email, UUID id, StatusRequest request) {
        Task task = require(email, id);
        task.setStatus(request.status());
        return toResponse(tasks.save(task));
    }

    @Transactional
    public void delete(String email, UUID id) {
        tasks.delete(require(email, id));
    }

    private Task require(String email, UUID id) {
        Task task = tasks.findById(id).orElseThrow();
        if (!task.getUser().getEmail().equals(email)) {
            throw new IllegalArgumentException("Task does not belong to the current user");
        }
        return task;
    }

    private void apply(Task task, TaskRequest request) {
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setCategory(request.category());
        if (request.priority() != null) task.setPriority(request.priority());
        if (request.status() != null) task.setStatus(request.status());
        task.setStartTime(request.startTime());
        task.setEndTime(request.endTime());
        task.setRecurring(Boolean.TRUE.equals(request.recurring()));
        task.setRecurrenceRule(request.recurrenceRule());
        task.setSortOrder(request.sortOrder() == null ? task.getSortOrder() : request.sortOrder());
        if (request.scheduleId() != null) {
            Schedule schedule = schedules.findById(request.scheduleId()).orElseThrow();
            task.setSchedule(schedule);
        }
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getCategory(), task.getPriority(),
                task.getStatus(), task.getStartTime(), task.getEndTime(), task.isRecurring(), task.getRecurrenceRule(),
                task.getSortOrder(), task.getSchedule() == null ? null : task.getSchedule().getId());
    }
}
