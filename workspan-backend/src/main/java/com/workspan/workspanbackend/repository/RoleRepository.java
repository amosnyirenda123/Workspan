package com.workspan.workspanbackend.repository;

import com.workspan.workspanbackend.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {}
