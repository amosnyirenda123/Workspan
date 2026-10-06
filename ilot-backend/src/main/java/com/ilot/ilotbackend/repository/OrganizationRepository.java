package com.ilot.ilotbackend.repository;
import com.ilot.ilotbackend.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface OrganizationRepository extends JpaRepository<Organization, UUID> { boolean existsByNameIgnoreCase(String name); }
