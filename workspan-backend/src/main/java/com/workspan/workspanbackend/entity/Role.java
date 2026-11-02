package com.workspan.workspanbackend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "name", "scope"}))
public class Role extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    @NotBlank
    @Column(nullable = false, length = 60)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleScope scope;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_space_id")
    private Space scopeSpace;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_project_id")
    private Project scopeProject;

    public Role() {
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization v) {
        organization = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public RoleScope getScope() {
        return scope;
    }

    public void setScope(RoleScope v) {
        scope = v;
    }

    public Space getScopeSpace() { return scopeSpace; }
    public void setScopeSpace(Space value) { scopeSpace = value; }
    public Project getScopeProject() { return scopeProject; }
    public void setScopeProject(Project value) { scopeProject = value; }
}
