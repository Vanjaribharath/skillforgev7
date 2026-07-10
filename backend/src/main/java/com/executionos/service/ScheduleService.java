package com.executionos.service;

import com.executionos.dto.ExecutionDtos.ScheduleRequest;
import com.executionos.dto.ExecutionDtos.ScheduleResponse;
import com.executionos.model.Schedule;
import com.executionos.model.User;
import com.executionos.repository.ScheduleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {
    private final ScheduleRepository schedules;

    public ScheduleService(ScheduleRepository schedules) {
        this.schedules = schedules;
    }

    public List<ScheduleResponse> list(String email) {
        return schedules.findByUserEmailOrderByCreatedAtDesc(email).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ScheduleResponse create(User user, ScheduleRequest request) {
        Schedule schedule = new Schedule();
        schedule.setUser(user);
        apply(schedule, request);
        return toResponse(schedules.save(schedule));
    }

    @Transactional
    public ScheduleResponse update(String email, UUID id, ScheduleRequest request) {
        Schedule schedule = require(email, id);
        apply(schedule, request);
        return toResponse(schedules.save(schedule));
    }

    @Transactional
    public void delete(String email, UUID id) {
        schedules.delete(require(email, id));
    }

    private Schedule require(String email, UUID id) {
        Schedule schedule = schedules.findById(id).orElseThrow();
        if (!schedule.getUser().getEmail().equals(email)) {
            throw new IllegalArgumentException("Schedule does not belong to the current user");
        }
        return schedule;
    }

    private void apply(Schedule schedule, ScheduleRequest request) {
        schedule.setName(request.name());
        schedule.setCronExpression(request.cronExpression());
        schedule.setActive(request.active() == null || request.active());
        schedule.setColor(request.color() == null ? "#1a73e8" : request.color());
    }

    private ScheduleResponse toResponse(Schedule schedule) {
        return new ScheduleResponse(schedule.getId(), schedule.getName(), schedule.getCronExpression(), schedule.isActive(), schedule.getColor());
    }
}
