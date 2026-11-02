package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<UserAccount> findAllByOrganizationIdAndStatusOrderByDisplayNameAsc(UUID organizationId, String status);
    Optional<UserAccount> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
