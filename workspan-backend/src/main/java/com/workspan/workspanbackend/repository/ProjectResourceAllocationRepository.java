package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.ProjectResourceAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectResourceAllocationRepository extends JpaRepository<ProjectResourceAllocation, UUID> {
    List<ProjectResourceAllocation> findAllByProjectIdOrderByResourceTypeAscResourceNameAsc(UUID projectId);
    Optional<ProjectResourceAllocation> findByProjectIdAndResourceId(UUID projectId, UUID resourceId);
    boolean existsByProjectId(UUID projectId);
    boolean existsByResourceId(UUID resourceId);
    List<ProjectResourceAllocation> findAllByResourceId(UUID resourceId);
    @Query("select coalesce(sum(a.allocatedQuantity), 0) from ProjectResourceAllocation a where a.resource.id = :resourceId and (a.project.startDate is null or a.project.startDate <= current_date) and (a.project.endDate is null or a.project.endDate >= current_date)")
    Long sumAllocatedQuantity(@Param("resourceId") UUID resourceId);
    @Query("select coalesce(sum(a.allocatedAmount), 0) from ProjectResourceAllocation a where a.resource.id = :resourceId")
    BigDecimal sumAllocatedAmount(@Param("resourceId") UUID resourceId);
}
