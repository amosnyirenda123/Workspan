package com.workspan.workspanbackend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "task_resource_allocations", uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "project_resource_allocation_id"}))
public class TaskResourceAllocation extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_resource_allocation_id", nullable = false)
    private ProjectResourceAllocation projectResourceAllocation;
    @Column(name = "allocated_quantity")
    private Integer allocatedQuantity;
    @Column(name = "allocated_amount", precision = 15, scale = 2)
    private BigDecimal allocatedAmount;
    /** Share of the employee's day spent on the task (1-100). Null for non-human resources; null on legacy rows means 100. */
    @Column(name = "allocation_percent")
    private Integer allocationPercent;

    @Column(name = "allocated_at", nullable = false, updatable = false)
    private Instant allocatedAt;

    @PrePersist void allocate() { allocatedAt = Instant.now(); }
    public Task getTask() { return task; }
    public void setTask(Task value) { task = value; }
    public ProjectResourceAllocation getProjectResourceAllocation() { return projectResourceAllocation; }
    public void setProjectResourceAllocation(ProjectResourceAllocation value) { projectResourceAllocation = value; }
    public Integer getAllocatedQuantity() { return allocatedQuantity; }
    public void setAllocatedQuantity(Integer value) { allocatedQuantity = value; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal value) { allocatedAmount = value; }
    public Instant getAllocatedAt() { return allocatedAt; }
    public Integer getAllocationPercent() { return allocationPercent; }
    public void setAllocationPercent(Integer value) { allocationPercent = value; }
    /** Effective charge in percent for human assignments (legacy rows count as 100%). */
    public int effectivePercent() { return allocationPercent == null ? 100 : allocationPercent; }
}
