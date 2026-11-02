package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {
    List<Resource> findAllBySpaceIdOrderByTypeAscNameAsc(UUID spaceId);
    Optional<Resource> findByIdAndSpaceOrganizationId(UUID id, UUID organizationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id and r.space.organization.id = :organizationId")
    Optional<Resource> findForAllocation(@Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
