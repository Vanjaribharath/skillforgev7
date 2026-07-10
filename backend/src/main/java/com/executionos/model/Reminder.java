package com.executionos.model;

import com.executionos.model.Enums.ReminderType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "reminders")
public class Reminder extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    private Instant remindAt;
    @Enumerated(EnumType.STRING)
    private ReminderType type = ReminderType.BROWSER;
    private boolean sent;

    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Instant getRemindAt() { return remindAt; }
    public void setRemindAt(Instant remindAt) { this.remindAt = remindAt; }
    public ReminderType getType() { return type; }
    public void setType(ReminderType type) { this.type = type; }
    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }
}
