package com.executionos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "journal_entries")
public class JournalEntry extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    private LocalDate entryDate;
    @Column(columnDefinition = "jsonb")
    private String completedTasks = "[]";
    @Column(columnDefinition = "TEXT")
    private String blockers;
    @Column(columnDefinition = "TEXT")
    private String learnings;
    @Column(columnDefinition = "jsonb")
    private String aiToolsExplored = "[]";
    @Column(columnDefinition = "jsonb")
    private String linksSaved = "[]";

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public String getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(String completedTasks) { this.completedTasks = completedTasks; }
    public String getBlockers() { return blockers; }
    public void setBlockers(String blockers) { this.blockers = blockers; }
    public String getLearnings() { return learnings; }
    public void setLearnings(String learnings) { this.learnings = learnings; }
    public String getAiToolsExplored() { return aiToolsExplored; }
    public void setAiToolsExplored(String aiToolsExplored) { this.aiToolsExplored = aiToolsExplored; }
    public String getLinksSaved() { return linksSaved; }
    public void setLinksSaved(String linksSaved) { this.linksSaved = linksSaved; }
}
