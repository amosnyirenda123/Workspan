package com.workspan.workspanbackend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "project_resource_allocations", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "resource_id"}))
public class ProjectResourceAllocation extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;
    @Column(name = "allocated_quantity")
    private Integer allocatedQuantity;
    @Column(name = "allocated_amount", precision = 15, scale = 2)
    private BigDecimal allocatedAmount;
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

    public Integer getAllocatedQuantity() { return allocatedQuantity; }
    public void setAllocatedQuantity(Integer value) { allocatedQuantity = value; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal value) { allocatedAmount = value; }
}
