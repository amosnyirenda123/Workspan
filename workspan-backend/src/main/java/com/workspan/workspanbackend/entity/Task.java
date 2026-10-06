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
    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TaskStatus status = TaskStatus.TODO;
    private LocalDate baselineStart;
    private LocalDate baselineEnd;
    private LocalDate startDate;
    private LocalDate endDate;

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
}
