package com.executionos.model;

public final class Enums {
    private Enums() {}

    public enum Role { USER, ADMIN }
    public enum Priority { LOW, MEDIUM, HIGH, URGENT }
    public enum TaskStatus { TODO, IN_PROGRESS, DONE }
    public enum ReminderType { BROWSER, EMAIL, PUSH }
    public enum FocusType { POMODORO, DEEP_WORK }
    public enum FocusStatus { ACTIVE, PAUSED, COMPLETED, CANCELLED }
    public enum TextFormat { MARKDOWN, PLAIN }
}
