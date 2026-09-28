package com.ilot.ilotbackend.repository;
import com.ilot.ilotbackend.domain.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface SpaceRepository extends JpaRepository<Space, UUID> { long countByOrganizationId(UUID organizationId); }
