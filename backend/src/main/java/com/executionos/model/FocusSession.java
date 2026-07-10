package com.executionos.model;

import com.executionos.model.Enums.FocusStatus;
import com.executionos.model.Enums.FocusType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "focus_sessions")
public class FocusSession extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;
    private Instant startTime = Instant.now();
    private Instant endTime;
    private long durationSeconds;
    @Enumerated(EnumType.STRING)
    private FocusType type = FocusType.DEEP_WORK;
    @Enumerated(EnumType.STRING)
    private FocusStatus status = FocusStatus.ACTIVE;

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }
    public FocusType getType() { return type; }
    public void setType(FocusType type) { this.type = type; }
    public FocusStatus getStatus() { return status; }
    public void setStatus(FocusStatus status) { this.status = status; }
}
