package com.workspan.workspanbackend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "task_assignees", uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "resource_id"}))
public class TaskAssignee extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @PrePersist
    void assigned() {
        assignedAt = Instant.now();
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task v) {
        task = v;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource v) {
        resource = v;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
