package com.workspan.workspanbackend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Entity
@Table(name = "sprints", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "name"}))
public class Sprint extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @NotBlank @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;
    @Size(max = 1000)
    @Column(length = 1000)
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private SprintStatus status = SprintStatus.PLANNED;

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public SprintStatus getStatus() { return status; }
    public void setStatus(SprintStatus status) { this.status = status; }
}
