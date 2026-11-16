package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.entity.Permission;
import com.workspan.workspanbackend.repository.PermissionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
public class PermissionCatalogInitializer implements ApplicationRunner {
    private final PermissionRepository permissions;
    private final ScopedRoleProvisioner roleProvisioner;

    public PermissionCatalogInitializer(PermissionRepository permissions, ScopedRoleProvisioner roleProvisioner) {
        this.permissions = permissions; this.roleProvisioner = roleProvisioner;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (var definition : PermissionCatalog.DEFINITIONS) {
            if (permissions.findByKey(definition.key()).isEmpty()) permissions.save(new Permission(definition.key()));
        }
        permissions.flush();
        roleProvisioner.syncBuiltInScopedRoles();
    }
}
