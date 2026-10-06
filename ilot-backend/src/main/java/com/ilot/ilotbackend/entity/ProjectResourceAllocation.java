package com.ilot.ilotbackend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "project_resource_allocations", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "resource_id"}))
public class ProjectResourceAllocation extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;
    @Column(name = "allocated_at", nullable = false, updatable = false)
    private Instant allocatedAt;

    @PrePersist
    void allocated() {
        allocatedAt = Instant.now();
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project v) {
        project = v;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource v) {
        resource = v;
    }

    public Instant getAllocatedAt() {
        return allocatedAt;
    }
}
