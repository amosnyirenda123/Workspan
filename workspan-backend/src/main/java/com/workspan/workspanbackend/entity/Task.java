package com.workspan.workspanbackend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    private Sprint sprint;
    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TaskStatus status = TaskStatus.TODO;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, columnDefinition = "varchar(16) default 'NORMAL'")
    private TaskPriority priority = TaskPriority.NORMAL;
    @Column(name = "estimate_points")
    private Integer estimatePoints;
    @Column(name = "due_date")
    private LocalDate dueDate;
    private LocalDate baselineStart;
    private LocalDate baselineEnd;
    private LocalDate startDate;
    private LocalDate endDate;

    /** Completion percentage (0-100) reported for the task. */
    @Column(nullable = false, columnDefinition = "int default 0")
    private int progress;

    public Task() {
    }

    public Task(Project project, String name) {
        this.project = project;
        this.name = name;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project v) {
        project = v;
    }

    public Sprint getSprint() { return sprint; }
    public void setSprint(Sprint sprint) { this.sprint = sprint; }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus v) {
        status = v;
    }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public Integer getEstimatePoints() { return estimatePoints; }
    public void setEstimatePoints(Integer estimatePoints) { this.estimatePoints = estimatePoints; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public LocalDate getBaselineStart() {
        return baselineStart;
    }

    public void setBaselineStart(LocalDate v) {
        baselineStart = v;
    }

    public LocalDate getBaselineEnd() {
        return baselineEnd;
    }

    public void setBaselineEnd(LocalDate v) {
        baselineEnd = v;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate v) {
        startDate = v;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate v) {
        endDate = v;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int v) {
        progress = v;
    }
}
